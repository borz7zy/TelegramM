#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
#include <libavutil/imgutils.h>
#include <libavutil/pixdesc.h>
#include <libavutil/intreadwrite.h>
#include <libavutil/dict.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr uint64_t ALPHA_BLOCK_ADD_ID = 1;

struct Decoder {
    AVFormatContext *fmt = nullptr;
    AVCodecContext *codec = nullptr;
    AVCodecContext *alphaCodec = nullptr;
    SwsContext *sws = nullptr;
    AVFrame *frame = nullptr;
    AVFrame *alphaFrame = nullptr;
    AVPacket *pkt = nullptr;
    AVPacket *alphaPkt = nullptr;
    int videoStream = -1;
    bool hasAlphaThisFrame = false;

    int swsSrcW = 0, swsSrcH = 0, swsSrcFmt = -1;
    int swsDstW = 0, swsDstH = 0;
};

void closeDecoder(Decoder *d) {
    if (!d) return;
    if (d->sws) sws_freeContext(d->sws);
    if (d->frame) av_frame_free(&d->frame);
    if (d->alphaFrame) av_frame_free(&d->alphaFrame);
    if (d->pkt) av_packet_free(&d->pkt);
    if (d->alphaPkt) av_packet_free(&d->alphaPkt);
    if (d->codec) avcodec_free_context(&d->codec);
    if (d->alphaCodec) avcodec_free_context(&d->alphaCodec);
    if (d->fmt) avformat_close_input(&d->fmt);
    delete d;
}

void feedAlpha(Decoder *d, AVPacket *colorPkt) {
    if (!d->alphaCodec) return;

    size_t sdSize = 0;
    uint8_t *sd = av_packet_get_side_data(
            colorPkt, AV_PKT_DATA_MATROSKA_BLOCKADDITIONAL, &sdSize);
    if (!sd || sdSize <= 8) return;
    if (AV_RB64(sd) != ALPHA_BLOCK_ADD_ID) return;

    int n = (int) (sdSize - 8);
    if (av_new_packet(d->alphaPkt, n) < 0) return;
    memcpy(d->alphaPkt->data, sd + 8, n);
    avcodec_send_packet(d->alphaCodec, d->alphaPkt);
    av_packet_unref(d->alphaPkt);
}

int nextFrame(Decoder *d) {
    for (;;) {
        int ret = avcodec_receive_frame(d->codec, d->frame);
        if (ret == 0) {
            d->hasAlphaThisFrame = false;
            if (d->alphaCodec &&
                avcodec_receive_frame(d->alphaCodec, d->alphaFrame) == 0) {
                d->hasAlphaThisFrame =
                        d->alphaFrame->width == d->frame->width &&
                        d->alphaFrame->height == d->frame->height &&
                        d->frame->format == AV_PIX_FMT_YUV420P;
            }
            return 0;
        }
        if (ret == AVERROR_EOF) return 1;
        if (ret != AVERROR(EAGAIN)) return ret;

        int rd = av_read_frame(d->fmt, d->pkt);
        if (rd == AVERROR_EOF) {
            avcodec_send_packet(d->codec, nullptr);
            if (d->alphaCodec) avcodec_send_packet(d->alphaCodec, nullptr);
            continue;
        }
        if (rd < 0) return rd;

        if (d->pkt->stream_index == d->videoStream) {
            feedAlpha(d, d->pkt);
            int sret = avcodec_send_packet(d->codec, d->pkt);
            av_packet_unref(d->pkt);
            if (sret < 0 && sret != AVERROR(EAGAIN)) return sret;
        } else {
            av_packet_unref(d->pkt);
        }
    }
}

void premultiply(uint8_t *pixels, int w, int h, int stride) {
    for (int y = 0; y < h; ++y) {
        uint8_t *row = pixels + (size_t) y * stride;
        for (int x = 0; x < w; ++x) {
            uint8_t *p = row + x * 4;
            uint32_t a = p[3];
            if (a == 255) continue;
            p[0] = (uint8_t) (p[0] * a / 255);
            p[1] = (uint8_t) (p[1] * a / 255);
            p[2] = (uint8_t) (p[2] * a / 255);
        }
    }
}

}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_github_borz7zy_ffmpeg_FFmpegVideoDecoder_nativeOpen(JNIEnv *env, jclass, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return 0;

    auto *d = new Decoder();

    int ret = avformat_open_input(&d->fmt, path, nullptr, nullptr);
    env->ReleaseStringUTFChars(jpath, path);
    if (ret < 0) {
        LOGE("avformat_open_input failed: %d", ret);
        closeDecoder(d);
        return 0;
    }

    if (avformat_find_stream_info(d->fmt, nullptr) < 0) {
        LOGE("avformat_find_stream_info failed");
        closeDecoder(d);
        return 0;
    }

    const AVCodec *dec = nullptr;
    d->videoStream = av_find_best_stream(d->fmt, AVMEDIA_TYPE_VIDEO, -1, -1, &dec, 0);
    if (d->videoStream < 0 || !dec) {
        LOGE("no video stream");
        closeDecoder(d);
        return 0;
    }

    AVStream *st = d->fmt->streams[d->videoStream];

    d->codec = avcodec_alloc_context3(dec);
    if (!d->codec || avcodec_parameters_to_context(d->codec, st->codecpar) < 0 ||
        avcodec_open2(d->codec, dec, nullptr) < 0) {
        LOGE("colour codec init failed");
        closeDecoder(d);
        return 0;
    }

    if (av_dict_get(st->metadata, "alpha_mode", nullptr, 0)) {
        d->alphaCodec = avcodec_alloc_context3(dec);
        if (d->alphaCodec &&
            (avcodec_parameters_to_context(d->alphaCodec, st->codecpar) < 0 ||
             avcodec_open2(d->alphaCodec, dec, nullptr) < 0)) {
            avcodec_free_context(&d->alphaCodec);
            d->alphaCodec = nullptr;
        }
    }

    d->frame = av_frame_alloc();
    d->alphaFrame = av_frame_alloc();
    d->pkt = av_packet_alloc();
    d->alphaPkt = av_packet_alloc();
    if (!d->frame || !d->alphaFrame || !d->pkt || !d->alphaPkt) {
        closeDecoder(d);
        return 0;
    }

    return reinterpret_cast<jlong>(d);
}

JNIEXPORT void JNICALL
Java_com_github_borz7zy_ffmpeg_FFmpegVideoDecoder_nativeGetInfo(JNIEnv *env, jclass, jlong handle,
                                                                jintArray jout) {
    auto *d = reinterpret_cast<Decoder *>(handle);
    if (!d || env->GetArrayLength(jout) < 5) return;

    AVStream *st = d->fmt->streams[d->videoStream];

    jint out[5];
    out[0] = d->codec->width;
    out[1] = d->codec->height;

    int64_t durMs = 0;
    if (d->fmt->duration != AV_NOPTS_VALUE && d->fmt->duration > 0) {
        durMs = d->fmt->duration / (AV_TIME_BASE / 1000);
    }
    out[2] = static_cast<jint>(durMs);

    AVRational fps = st->avg_frame_rate;
    if (fps.num == 0 || fps.den == 0) fps = st->r_frame_rate;
    if (fps.num == 0 || fps.den == 0) { fps.num = 30; fps.den = 1; }
    out[3] = fps.num;
    out[4] = fps.den;

    env->SetIntArrayRegion(jout, 0, 5, out);
}

JNIEXPORT jint JNICALL
Java_com_github_borz7zy_ffmpeg_FFmpegVideoDecoder_nativeRenderNextFrame(JNIEnv *env, jclass,
                                                                        jlong handle, jobject bitmap) {
    auto *d = reinterpret_cast<Decoder *>(handle);
    if (!d) return -1;

    int fr = nextFrame(d);
    if (fr != 0) return fr; // EOF or error

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        av_frame_unref(d->frame);
        if (d->hasAlphaThisFrame) av_frame_unref(d->alphaFrame);
        return -2;
    }

    int dstW = static_cast<int>(info.width);
    int dstH = static_cast<int>(info.height);

    const uint8_t *srcData[4];
    int srcStride[4];
    int srcFmt;

    if (d->hasAlphaThisFrame) {
        srcFmt = AV_PIX_FMT_YUVA420P;
        srcData[0] = d->frame->data[0];      srcStride[0] = d->frame->linesize[0];
        srcData[1] = d->frame->data[1];      srcStride[1] = d->frame->linesize[1];
        srcData[2] = d->frame->data[2];      srcStride[2] = d->frame->linesize[2];
        srcData[3] = d->alphaFrame->data[0]; srcStride[3] = d->alphaFrame->linesize[0];
    } else {
        srcFmt = d->frame->format;
        for (int i = 0; i < 4; ++i) {
            srcData[i] = d->frame->data[i];
            srcStride[i] = d->frame->linesize[i];
        }
    }

    if (!d->sws ||
        d->swsSrcW != d->frame->width || d->swsSrcH != d->frame->height ||
        d->swsSrcFmt != srcFmt || d->swsDstW != dstW || d->swsDstH != dstH) {
        if (d->sws) { sws_freeContext(d->sws); d->sws = nullptr; }
        d->sws = sws_getContext(
                d->frame->width, d->frame->height, static_cast<AVPixelFormat>(srcFmt),
                dstW, dstH, AV_PIX_FMT_RGBA,
                SWS_BILINEAR, nullptr, nullptr, nullptr);
        if (!d->sws) {
            av_frame_unref(d->frame);
            if (d->hasAlphaThisFrame) av_frame_unref(d->alphaFrame);
            return -3;
        }
        d->swsSrcW = d->frame->width;
        d->swsSrcH = d->frame->height;
        d->swsSrcFmt = srcFmt;
        d->swsDstW = dstW;
        d->swsDstH = dstH;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        av_frame_unref(d->frame);
        if (d->hasAlphaThisFrame) av_frame_unref(d->alphaFrame);
        return -4;
    }

    uint8_t *dstData[4] = { static_cast<uint8_t *>(pixels), nullptr, nullptr, nullptr };
    int dstStride[4] = { static_cast<int>(info.stride), 0, 0, 0 };

    sws_scale(d->sws, srcData, srcStride, 0, d->frame->height, dstData, dstStride);

    if (d->hasAlphaThisFrame) {
        premultiply(static_cast<uint8_t *>(pixels), dstW, dstH, static_cast<int>(info.stride));
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    av_frame_unref(d->frame);
    if (d->hasAlphaThisFrame) av_frame_unref(d->alphaFrame);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_github_borz7zy_ffmpeg_FFmpegVideoDecoder_nativeSeekToStart(JNIEnv *, jclass, jlong handle) {
    auto *d = reinterpret_cast<Decoder *>(handle);
    if (!d) return;
    av_seek_frame(d->fmt, d->videoStream, 0, AVSEEK_FLAG_BACKWARD);
    avcodec_flush_buffers(d->codec);
    if (d->alphaCodec) avcodec_flush_buffers(d->alphaCodec);
}

JNIEXPORT void JNICALL
Java_com_github_borz7zy_ffmpeg_FFmpegVideoDecoder_nativeClose(JNIEnv *, jclass, jlong handle) {
    closeDecoder(reinterpret_cast<Decoder *>(handle));
}

}
