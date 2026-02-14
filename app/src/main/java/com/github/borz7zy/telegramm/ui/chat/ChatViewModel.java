package com.github.borz7zy.telegramm.ui.chat;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.ui.model.MessageItem;
import com.github.borz7zy.telegramm.ui.model.PhotoData;
import com.github.borz7zy.telegramm.utils.Logger;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatViewModel extends ViewModel implements Client.ResultHandler {

    // --------------------
    // LiveData for UI
    // --------------------
    private final MutableLiveData<List<MessageItem>> messages = new MutableLiveData<>();
    private final MutableLiveData<String> chatTitle = new MutableLiveData<>();
    private final MutableLiveData<TdApi.ChatPhotoInfo> chatAvatar = new MutableLiveData<>();
    private final MutableLiveData<String> typingStatus = new MutableLiveData<>();
    private final MutableLiveData<Boolean> topLoading = new MutableLiveData<>();
    private final MutableLiveData<UiEvent> uiEvents = new MutableLiveData<>();

    // --------------------
    // Internal states
    // --------------------
    private final Map<Long, MessageItem> byId = new ConcurrentHashMap<>();
    private final Map<Long, TdApi.Message> rawMessages = new ConcurrentHashMap<>();
    private final Map<Long, Long> albumGroups = new ConcurrentHashMap<>();
    private final Map<Long, String> userNameCache = new ConcurrentHashMap<>();

    private final Set<Integer> requestedFiles = ConcurrentHashMap.newKeySet();

    private AccountSession session;
    private long chatId;
    private MessageUiMapper uiMapper;

    private boolean loading = false;
    private boolean hasMore = true;
    private long oldestId = 0;

    private boolean isInitialLoad = true;
    private int initialLoadRetryCount = 0;
    private int previousListSize = 0;
    private static final int PAGE_SIZE = 70;
    private static final int MAX_INITIAL_RETRIES = 3;

    private static final long BATCH_DELAY_MS = 200;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object batchLock = new Object();
    private boolean updateScheduled = false;
    private boolean pendingPagination = false;
    private boolean pendingScrollToBottom = false;

    // --------------------
    // Getters
    // --------------------
    public LiveData<List<MessageItem>> getMessages() { return messages; }
    public LiveData<String> getChatTitle() { return chatTitle; }
    public LiveData<TdApi.ChatPhotoInfo> getChatAvatar() { return chatAvatar; }
    public LiveData<String> getTypingStatus() { return typingStatus; }
    public LiveData<Boolean> getTopLoading() { return topLoading; }
    public LiveData<UiEvent> getUiEvents() { return uiEvents; }

    // --------------------
    // Initialization
    // --------------------
    public void init(long chatId, String initialTitle, AccountSession session) {
        this.chatId = chatId;
        this.session = session;
        if (initialTitle != null) {
            chatTitle.setValue(initialTitle);
        }

        session.addUpdateHandler(this);

        AccountStorage.getInstance().getCurrentActive(account -> {
            if (account == null) return;

            uiMapper = new MessageUiMapper(
                    account.getAccountId(),
                    userNameCache::get,
                    this::onUserLoaded
            );

            TdMediaRepository.get().setCurrentAccountId(account.getAccountId());
            requestInitialHistory();
        });
    }

    private void requestInitialHistory() {
        if (loading || session == null) return;
        loading = true;
        hasMore = true;
        oldestId = 0;

        isInitialLoad = true;
        initialLoadRetryCount = 0;
        previousListSize = 0;

        byId.clear();
        rawMessages.clear();
        albumGroups.clear();

        session.send(new TdApi.GetChatHistory(chatId, 0L, 0, PAGE_SIZE, false), this);
    }

    public void loadMore() {
        if (loading || !hasMore || session == null) return;
        loading = true;
        topLoading.postValue(true);

        long offsetId = oldestId;
        session.send(new TdApi.GetChatHistory(chatId, offsetId, -1, PAGE_SIZE, false), this);
    }

    // --------------------
    // Sending message
    // --------------------

    public void sendMessage(String text) {
        if (session == null || TextUtils.isEmpty(text)) return;

        TdApi.InputMessageContent content =
                new TdApi.InputMessageText(new TdApi.FormattedText(text, null), null, true);

        session.send(new TdApi.SendMessage(chatId, null, null, null, null, content), null);
    }

    public void sendVoice(File audioFile, int durationSec, byte[] waveform) {
        if (session == null || audioFile == null || !audioFile.exists()) return;

        TdApi.InputFile input = new TdApi.InputFileLocal(audioFile.getAbsolutePath());
        TdApi.InputMessageContent content = new TdApi.InputMessageVoiceNote(
                input, durationSec, waveform != null ? waveform : new byte[0], null, null
        );
        session.send(new TdApi.SendMessage(chatId, null, null, null, null, content), null);
    }

    public void handleUiClick(long msgId, UiContent.UiButton btn) {
        if (btn.isUrl()) {
            uiEvents.postValue(new UiEvent.OpenUrl(btn.url));
            return;
        }

        if (session == null) return;

        if (btn.data != null) {
            TdApi.GetCallbackQueryAnswer req = new TdApi.GetCallbackQueryAnswer(
                    chatId, msgId, new TdApi.CallbackQueryPayloadData(btn.data)
            );
            session.send(req, null);
        } else {
            TdApi.InputMessageContent content =
                    new TdApi.InputMessageText(new TdApi.FormattedText(btn.text, null), null, true);
            session.send(new TdApi.SendMessage(chatId, null, null, null, null, content), null);
        }
    }

    @Override
    public void onResult(TdApi.Object object) {
        if (object instanceof TdApi.Messages) {
            handleHistory((TdApi.Messages) object);
        }
        else if (object instanceof TdApi.UpdateNewMessage) {
            TdApi.Message m = ((TdApi.UpdateNewMessage) object).message;
            if (m != null && m.chatId == chatId) {
                processMessageAndPut(m);
                if (m.isOutgoing) {
                    scheduleUiUpdate(false, true);
                } else {
                    scheduleUiUpdate(false, false);
                }
            }
        }
        else if (object instanceof TdApi.UpdateMessageSendSucceeded) {
            TdApi.UpdateMessageSendSucceeded u = (TdApi.UpdateMessageSendSucceeded) object;
            if (u.message != null && u.message.chatId == chatId) {
                byId.remove(u.oldMessageId);
                rawMessages.remove(u.oldMessageId);

                processMessageAndPut(u.message);
                scheduleUiUpdate(false, true);
            }
        }
        else if (object instanceof TdApi.UpdateDeleteMessages) {
            TdApi.UpdateDeleteMessages u = (TdApi.UpdateDeleteMessages) object;
            if (u.chatId == chatId) {
                for (long id : u.messageIds) {
                    byId.remove(id);
                    rawMessages.remove(id);
                }
                scheduleUiUpdate(false, false);
            }
        }
        else if (object instanceof TdApi.UpdateMessageEdited) {
            TdApi.UpdateMessageEdited u = (TdApi.UpdateMessageEdited) object;
            if (u.chatId == chatId) {
                handleMessageEdit(u);
            }
        }
        else if (object instanceof TdApi.UpdateMessageContent) {
            TdApi.UpdateMessageContent u = (TdApi.UpdateMessageContent) object;
            if (u.chatId == chatId) {
                TdApi.Message m = rawMessages.get(u.messageId);
                if (m != null) {
                    m.content = u.newContent;
                    processMessageAndPut(m);
                    scheduleUiUpdate(false, false);
                }
            }
        }
        else if (object instanceof TdApi.Chat) {
            TdApi.Chat chat = (TdApi.Chat) object;
            if (chat.id == chatId) {
                chatTitle.postValue(chat.title);
                chatAvatar.postValue(chat.photo);
            }
        }
        else if (object instanceof TdApi.UpdateChatTitle) {
            TdApi.UpdateChatTitle u = (TdApi.UpdateChatTitle) object;
            if (u.chatId == chatId) chatTitle.postValue(u.title);
        }
        else if (object instanceof TdApi.UpdateChatPhoto) {
            TdApi.UpdateChatPhoto u = (TdApi.UpdateChatPhoto) object;
            if (u.chatId == chatId) chatAvatar.postValue(u.photo);
        }
        else if (object instanceof TdApi.UpdateChatAction) {
            TdApi.UpdateChatAction u = (TdApi.UpdateChatAction) object;
            if (u.chatId == chatId) handleTyping(u);
        }
        else if (object instanceof TdApi.UpdateUser) {
            onUserLoaded(((TdApi.UpdateUser) object).user);
        }
        else if (object instanceof TdApi.User) {
            onUserLoaded((TdApi.User) object);
        }
    }

    private void handleHistory(TdApi.Messages msgs) {
        int count = (msgs == null || msgs.messages == null) ? 0 : msgs.messages.length;

        if (count == 0) {
            hasMore = false;
            loading = false;
            if(isInitialLoad){
                isInitialLoad = false;
                scheduleUiUpdate(false, true);
            }else {
                topLoading.postValue(false);
            }
            return;
        }

        for (TdApi.Message m : msgs.messages) {
            if (m == null || m.chatId != chatId) continue;
            processMessageAndPut(m);
            if (oldestId == 0 || m.id < oldestId) oldestId = m.id;
        }

        if (isInitialLoad) {
            int currentListSize = byId.size();
            if (count < PAGE_SIZE && initialLoadRetryCount < MAX_INITIAL_RETRIES) {
                if (currentListSize > previousListSize) {
                    previousListSize = currentListSize;
                    ++initialLoadRetryCount;

                    scheduleUiUpdate(false, true);

                    session.send(new TdApi.GetChatHistory(chatId, oldestId, -1, PAGE_SIZE, false), this);
                    return;
                }
            }

            isInitialLoad = false;
            scheduleUiUpdate(false, true);
        }

        if(!isInitialLoad){
            hasMore = oldestId > 1;
            scheduleUiUpdate(true, false);
        }
    }

    private void processMessageAndPut(TdApi.Message m) {
        rawMessages.put(m.id, m);
        long albumId = m.mediaAlbumId;
        PhotoData photoData = null;
        String caption = "";

        if (m.content instanceof TdApi.MessagePhoto) {
            TdApi.MessagePhoto photoContent = (TdApi.MessagePhoto) m.content;
            photoData = extractPhoto(m.id, photoContent.photo);
            if (photoContent.caption != null && !TextUtils.isEmpty(photoContent.caption.text)) {
                caption = photoContent.caption.text;
            }
        }

        if (albumId != 0 && albumGroups.containsKey(albumId)) {
            Long mainMessageId = albumGroups.get(albumId);
            if(mainMessageId != null) {
                MessageItem existingItem = byId.get(mainMessageId);
                if (existingItem != null && photoData != null && existingItem.ui instanceof UiContent.Media) {
                    boolean alreadyExists = false;
                    if (existingItem.photos != null) {
                        for (PhotoData p : existingItem.photos) {
                            if (p.fileId == photoData.fileId) {
                                alreadyExists = true;
                                break;
                            }
                        }
                    }
                    if (!alreadyExists) {
                        MessageItem updatedItem = existingItem.withAddedPhoto(photoData, caption);
                        byId.put(mainMessageId, updatedItem);
                    }
                    return;
                }
            }
        }

        MessageItem newItem = toItem(m);
        byId.put(m.id, newItem);
        if (albumId != 0) {
            albumGroups.put(albumId, m.id);
        }
    }

    private void handleMessageEdit(TdApi.UpdateMessageEdited u) {
        TdApi.Message raw = rawMessages.get(u.messageId);
        if (raw != null) raw.replyMarkup = u.replyMarkup;

        MessageItem cur = byId.get(u.messageId);
        if (cur != null) {
            UiContent newUi;
            if (raw != null) {
                newUi = uiMapper.map(raw);
            } else {
                newUi = cur.ui;
            }
            MessageItem updated = cur.withUi(newUi);
            byId.put(u.messageId, updated);
            scheduleUiUpdate(false, false);
        }
    }

    private MessageItem toItem(TdApi.Message m) {
        if (uiMapper == null) {
            return new MessageItem(m.id, m.chatId, m.isOutgoing, "", null, m.mediaAlbumId, new UiContent.Unknown());
        }

        UiContent ui = uiMapper.map(m);
        String time = formatTime(m.date);

        List<PhotoData> photos = new ArrayList<>();
        if (m.content instanceof TdApi.MessagePhoto) {
            PhotoData pd = extractPhoto(m.id, ((TdApi.MessagePhoto) m.content).photo);
            if (pd != null) photos.add(pd);
        }

        return new MessageItem(
                m.id, m.chatId, m.isOutgoing,
                time, photos, m.mediaAlbumId,
                ui
        );
    }

    private String formatTime(int unixSeconds) {
        long ms = unixSeconds * 1000L;
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(ms));
    }

    private void onUserLoaded(TdApi.User user) {
        String fullName = (user.firstName + " " + user.lastName).trim();
        String oldName = userNameCache.get(user.id);

        if (oldName == null || !oldName.equals(fullName)) {
            userNameCache.put(user.id, fullName);

            new Thread(() -> {
                boolean needUpdate = false;
                for (MessageItem item : byId.values()) {
                    if (item.chatId == user.id) {
                        TdApi.Message raw = rawMessages.get(item.id);
                        if (raw != null) {
                            byId.put(item.id, toItem(raw));
                            needUpdate = true;
                        }
                    }
                }
                if (needUpdate) {
                    scheduleUiUpdate(false, false);
                }
            }).start();
        }
    }

    private void handleTyping(TdApi.UpdateChatAction uca) {
        if (uca.action == null) return;
        mainHandler.post(() -> {
            switch (uca.action.getConstructor()) {
                case TdApi.ChatActionTyping.CONSTRUCTOR:
                    typingStatus.setValue("печатает…");
                    mainHandler.removeCallbacks(hideTypingRunnable);
                    mainHandler.postDelayed(hideTypingRunnable, 5000);
                    break;
                case TdApi.ChatActionCancel.CONSTRUCTOR:
                    hideTypingRunnable.run();
                    break;
            }
        });
    }

    private final Runnable hideTypingRunnable = () -> typingStatus.setValue(null);

    // --------------------
    // Media Helpers
    // --------------------

    private PhotoData extractPhoto(long rowMessageId, TdApi.Photo photo) {
        if (photo == null || photo.sizes.length == 0) return null;

        TdApi.PhotoSize best = findBestPhotoSize(photo.sizes);
        int fileId = best.photo.id;
        String localPath = best.photo.local != null ? best.photo.local.path : null;
        boolean completed = best.photo.local != null && best.photo.local.isDownloadingCompleted;

        if (TextUtils.isEmpty(localPath) || !completed) {
            if(requestedFiles.add(fileId)){
                TdMediaRepository.get().getPathOrRequest(fileId, p -> {
                    if (!TextUtils.isEmpty(p)) {
                        notifyItemChanged(rowMessageId);
                    }
                });
            }
            String cached = TdMediaRepository.get().getCachedPath(fileId);
            if (!TextUtils.isEmpty(cached)) localPath = cached;
        }
        return new PhotoData(fileId, localPath, best.width, best.height);
    }

    private void notifyItemChanged(long msgId) {
        MessageItem item = byId.get(msgId);
        if (item != null) {
            TdApi.Message raw = rawMessages.get(msgId);
            if (raw != null) {
                byId.put(msgId, toItem(raw));
                scheduleUiUpdate(false, false);
            }
        }
    }

    private TdApi.PhotoSize findBestPhotoSize(TdApi.PhotoSize[] sizes) {
        TdApi.PhotoSize sizeX = null, sizeY = null, sizeM = null;
        for (TdApi.PhotoSize sz : sizes) {
            switch (sz.type) {
                case "y": sizeY = sz; break;
                case "x": sizeX = sz; break;
                case "m": sizeM = sz; break;
            }
        }
        if (sizeY != null) return sizeY;
        if (sizeX != null) return sizeX;
        if (sizeM != null) return sizeM;
        return sizes[sizes.length - 1];
    }

    // --------------------
    // UI Update Batching
    // --------------------

    private void scheduleUiUpdate(boolean isPagination, boolean scrollToBottom) {
        synchronized (batchLock) {
            if (isPagination) pendingPagination = true;
            if (scrollToBottom && !isPagination) pendingScrollToBottom = true;

            if (!updateScheduled) {
                updateScheduled = true;
                mainHandler.postDelayed(this::performBufferedUpdate, BATCH_DELAY_MS);
            }
        }
    }

    private void performBufferedUpdate() {
        boolean doScroll;
        boolean doPagination;

        synchronized (batchLock) {
            doScroll = pendingScrollToBottom;
            doPagination = pendingPagination;
            pendingScrollToBottom = false;
            pendingPagination = false;
            updateScheduled = false;
        }

        new Thread(() -> {
            ArrayList<MessageItem> list = new ArrayList<>(byId.values());
            Collections.sort(list, (m1, m2) -> Long.compare(m1.id, m2.id));

            messages.postValue(list);

            if (doPagination) {
                uiEvents.postValue(new UiEvent.PaginationApplied());
                topLoading.postValue(false);
                loading = false;
            }
            if (doScroll) {
                uiEvents.postValue(new UiEvent.ScrollToBottom());
            }
        }).start();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (session != null) {
            session.removeUpdateHandler(this);
            session.send(new TdApi.CloseChat(chatId), null);
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    public static abstract class UiEvent {
        public static class ScrollToBottom extends UiEvent {}
        public static class PaginationApplied extends UiEvent {}
        public static class OpenUrl extends UiEvent {
            public final String url;
            public OpenUrl(String url) { this.url = url; }
        }
    }
}