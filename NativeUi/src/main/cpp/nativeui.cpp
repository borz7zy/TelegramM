#include <jni.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <algorithm>
#include <vector>
#include <utility>
#include <cstdint>
#include <android/log.h>

#define LOG_TAG "AsyncRecycler"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

struct LayoutItem {
    int position;
    int top;
    int bottom;
};

constexpr int MAX_VISIBLE_ITEMS = 256;

struct LayoutSnapshot {
    int generation;
    int scrollOffset;
    int maxScrollOffset;
    int itemCount;
    int visibleCount;
    LayoutItem items[MAX_VISIBLE_ITEMS];
};

static constexpr int DEFAULT_ITEM_HEIGHT = 200;
static constexpr int DEFAULT_VIEWPORT_H  = 1920;

enum : uint32_t {
    P_SCROLL   = 1u << 0,
    P_DATA     = 1u << 1,
    P_VIEWPORT = 1u << 2,
    P_INSETS   = 1u << 3,
    P_HEIGHTS  = 1u << 4,
    P_SETSCROLL= 1u << 5
};

struct Engine {
    LayoutSnapshot snapshots[2]{};
    std::atomic<int> writeIndex{0};
    std::atomic<LayoutSnapshot*> published{nullptr};
    std::atomic<int> generation{0};

    std::thread worker;
    std::atomic<bool> running{false};

    std::atomic<int> pendingScrollDy{0};
    std::atomic<int> pendingItemCount{-1};
    std::atomic<int> pendingViewportW{0};
    std::atomic<int> pendingViewportH{0};
    std::atomic<int> pendingPadTop{0};
    std::atomic<int> pendingPadBottom{0};
    std::atomic<int> pendingSetScroll{-1};
    std::atomic<uint32_t> pendingMask{0};

    std::mutex wakeMutex;
    std::condition_variable wakeCv;

    std::mutex heightsMutex;
    std::vector<std::pair<int,int>> heightUpdates;

    int itemCount = 0;
    int scrollOffset = 0;
    int viewportW = 0;
    int viewportH = DEFAULT_VIEWPORT_H;
    int padTop = 0;
    int padBottom = 0;

    std::vector<int> heights;
    std::vector<int> prefix;
    bool prefixDirty = true;

    void publishSnapshot(const LayoutSnapshot& src) {
        int w = writeIndex.load(std::memory_order_relaxed);
        LayoutSnapshot& dst = snapshots[w];
        dst = src;
        published.store(&dst, std::memory_order_release);
        writeIndex.store(w ^ 1, std::memory_order_relaxed);
    }

    void rebuildPrefix() {
        prefix.resize((size_t)itemCount + 1);
        prefix[0] = 0;
        for (int i = 0; i < itemCount; ++i) {
            int h = heights[(size_t)i];
            if (h <= 0) h = DEFAULT_ITEM_HEIGHT;
            prefix[(size_t)i + 1] = prefix[(size_t)i] + h;
        }
        prefixDirty = false;
    }

    int totalContentH() const {
        if (prefix.empty()) return 0;
        return prefix.back();
    }

    int maxScroll() const {
        const int vh = (viewportH > 0) ? viewportH : DEFAULT_VIEWPORT_H;
        return std::max(0, totalContentH() - vh);
    }

    void computeAndPublish() {
        const int vh = (viewportH > 0) ? viewportH : DEFAULT_VIEWPORT_H;

        if (itemCount <= 0) {
            LayoutSnapshot snap{};
            snap.generation      = generation.fetch_add(1, std::memory_order_relaxed) + 1;
            snap.scrollOffset    = 0;
            snap.maxScrollOffset = 0;
            snap.itemCount       = 0;
            snap.visibleCount    = 0;
            publishSnapshot(snap);
            return;
        }

        if (prefixDirty) rebuildPrefix();

        const int ms = maxScroll();
        scrollOffset = std::clamp(scrollOffset, 0, ms);

        const int visibleStart = scrollOffset - padTop;
        const int visibleEnd   = scrollOffset + vh + padBottom;

        auto itStart = std::upper_bound(prefix.begin(), prefix.end(), visibleStart);
        int startPos = (int)(itStart - prefix.begin()) - 1;
        if (startPos < 0) startPos = 0;
        if (startPos >= itemCount) startPos = itemCount - 1;

        auto itEnd = std::lower_bound(prefix.begin(), prefix.end(), visibleEnd);
        int endPos = (int)(itEnd - prefix.begin()) - 1;
        if (endPos < 0) endPos = 0;
        if (endPos >= itemCount) endPos = itemCount - 1;
        endPos = std::min(itemCount - 1, endPos + 1);

        LayoutSnapshot snap{};
        snap.generation      = generation.fetch_add(1, std::memory_order_relaxed) + 1;
        snap.scrollOffset    = scrollOffset;
        snap.maxScrollOffset = ms;
        snap.itemCount       = itemCount;

        int count = 0;
        for (int i = startPos; i <= endPos && count < MAX_VISIBLE_ITEMS; ++i) {
            int top = prefix[(size_t)i] - scrollOffset;
            int h   = heights[(size_t)i];
            if (h <= 0) h = DEFAULT_ITEM_HEIGHT;
            snap.items[count] = LayoutItem{ i, top, top + h };
            count++;
        }
        snap.visibleCount = count;

        publishSnapshot(snap);

        // LOGD("pub gen=%d scroll=%d max=%d itemCount=%d vis=%d", snap.generation, snap.scrollOffset, snap.maxScrollOffset, snap.itemCount, snap.visibleCount);
    }

    void applyHeightUpdates() {
        std::vector<std::pair<int,int>> local;
        {
            std::lock_guard<std::mutex> lk(heightsMutex);
            if (heightUpdates.empty()) return;
            local.swap(heightUpdates);
        }
        bool changed = false;
        for (auto &p : local) {
            int pos = p.first;
            int h = p.second;
            if (pos < 0 || pos >= itemCount) continue;
            if (h <= 0) continue;
            if (heights[(size_t)pos] != h) {
                heights[(size_t)pos] = h;
                changed = true;
            }
        }
        if (changed) prefixDirty = true;
    }

    void loop() {
        while (running.load(std::memory_order_acquire)) {
            uint32_t mask = pendingMask.exchange(0, std::memory_order_acq_rel);
            if (mask == 0) {
                std::unique_lock<std::mutex> lk(wakeMutex);
                wakeCv.wait(lk, [&] {
                    return !running.load(std::memory_order_acquire) ||
                           pendingMask.load(std::memory_order_acquire) != 0;
                });
                continue;
            }

            if (mask & P_DATA) {
                int c = pendingItemCount.exchange(-1, std::memory_order_acq_rel);
                if (c >= 0) {
                    if (c != itemCount) {
                        itemCount = c;
                        heights.resize((size_t)itemCount, DEFAULT_ITEM_HEIGHT);
                        prefixDirty = true;
                    }
                }
            }

            if (mask & P_VIEWPORT) {
                viewportW = pendingViewportW.load(std::memory_order_relaxed);
                viewportH = pendingViewportH.load(std::memory_order_relaxed);
            }

            if (mask & P_INSETS) {
                padTop = pendingPadTop.load(std::memory_order_relaxed);
                padBottom = pendingPadBottom.load(std::memory_order_relaxed);
            }

            if (mask & P_HEIGHTS) {
                applyHeightUpdates();
            }

            if (mask & P_SETSCROLL) {
                int abs = pendingSetScroll.exchange(-1, std::memory_order_acq_rel);
                if (abs >= 0) scrollOffset = abs;
            }

            if (mask & P_SCROLL) {
                scrollOffset += pendingScrollDy.exchange(0, std::memory_order_acq_rel);
            }

            computeAndPublish();
        }
    }

    void start() {
        bool expected = false;
        if (running.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
            worker = std::thread([this]{ loop(); });
        }
    }

    void stop() {
        bool was = running.exchange(false, std::memory_order_acq_rel);
        if (was) {
            wakeCv.notify_one();
            if (worker.joinable()) worker.join();
        }
    }

    void wake(uint32_t bits) {
        pendingMask.fetch_or(bits, std::memory_order_release);
        wakeCv.notify_one();
    }
};

static inline Engine* fromHandle(jlong h) {
    return reinterpret_cast<Engine*>(h);
}

// ----------------
// JNI
// ----------------

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeCreate(JNIEnv*, jclass) {
    Engine* e = new Engine();
    e->start();
    return reinterpret_cast<jlong>(e);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    Engine* e = fromHandle(handle);
    if (!e) return;
    e->stop();
    delete e;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeSubmitScroll(JNIEnv*, jclass, jlong handle, jint dy) {
    Engine* e = fromHandle(handle);
    if (!e || dy == 0) return;
    e->pendingScrollDy.fetch_add((int)dy, std::memory_order_relaxed);
    e->wake(P_SCROLL);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeSetItemCount(JNIEnv*, jclass, jlong handle, jint count) {
    Engine* e = fromHandle(handle);
    if (!e) return;
    e->pendingItemCount.store((int)count, std::memory_order_relaxed);
    e->wake(P_DATA);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeSetViewport(JNIEnv*, jclass, jlong handle, jint w, jint h) {
    Engine* e = fromHandle(handle);
    if (!e) return;
    e->pendingViewportW.store((int)w, std::memory_order_relaxed);
    e->pendingViewportH.store((int)h, std::memory_order_relaxed);
    e->wake(P_VIEWPORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeSetInsets(JNIEnv*, jclass, jlong handle, jint top, jint bottom) {
    Engine* e = fromHandle(handle);
    if (!e) return;
    e->pendingPadTop.store((int)top, std::memory_order_relaxed);
    e->pendingPadBottom.store((int)bottom, std::memory_order_relaxed);
    e->wake(P_INSETS);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeSetItemHeight(JNIEnv*, jclass, jlong handle, jint position, jint px) {
    Engine* e = fromHandle(handle);
    if (!e) return;
    if (px <= 0) return;
    {
        std::lock_guard<std::mutex> lk(e->heightsMutex);
        e->heightUpdates.emplace_back((int)position, (int)px);
    }
    e->wake(P_HEIGHTS);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeSetScroll(JNIEnv*, jclass, jlong handle, jint absPx) {
    Engine* e = fromHandle(handle);
    if (!e) return;
    e->pendingSetScroll.store((int)absPx, std::memory_order_relaxed);
    e->wake(P_SETSCROLL);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_github_borz7zy_nativeui_NativeBridge_nativeCopyLayoutSnapshot(JNIEnv* env, jclass, jlong handle, jintArray outArr) {
    Engine* e = fromHandle(handle);
    if (!e) return 0;

    LayoutSnapshot* snap = e->published.load(std::memory_order_acquire);
    if (!snap) return 0;

    const jsize outLen = env->GetArrayLength(outArr);
    if (outLen < 5) return 0;

    const int maxTriples = std::max(0, (int)((outLen - 5) / 3));
    const int n = std::min(snap->visibleCount, maxTriples);

    jint tmp[5 + MAX_VISIBLE_ITEMS * 3];
    tmp[0] = snap->generation;
    tmp[1] = snap->scrollOffset;
    tmp[2] = snap->maxScrollOffset;
    tmp[3] = snap->itemCount;
    tmp[4] = n;

    for (int i = 0; i < n; ++i) {
        const int base = 5 + i * 3;
        tmp[base + 0] = snap->items[i].position;
        tmp[base + 1] = snap->items[i].top;
        tmp[base + 2] = snap->items[i].bottom;
    }

    env->SetIntArrayRegion(outArr, 0, 5 + n * 3, tmp);
    return snap->generation;
}
