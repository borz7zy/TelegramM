package com.github.borz7zy.telegramm.ui.chat;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final LruCache<Long, TdApi.User> fullUserCache = new LruCache<>(2048);
    private final LruCache<Long, String> chatMembersTags = new LruCache<>(2048);

    private final Set<Long> pendingMemberRequests = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingUserRequests = ConcurrentHashMap.newKeySet();
    private final Set<Integer> requestedFiles = ConcurrentHashMap.newKeySet();

    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();

    private AccountSession session;
    private long chatId;
    private MessageUiMapper uiMapper;

    private boolean loading = false;
    private boolean hasMore = true;
    private long oldestId = 0;

    private boolean isInitialLoad = true;
    private int initialLoadRetryCount = 0;
    private int previousListSize = 0;
    private static final int INITIAL_PAGE_SIZE = 100;
    private static final int PAGE_SIZE = 50;

    private static final long BATCH_DELAY_MS = 300;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object batchLock = new Object();
    private boolean updateScheduled = false;
    private boolean pendingPagination = false;
    private boolean pendingScrollToBottom = false;

    private volatile boolean isCleared = false;

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
    public void init(long chatId, String initialTitle) {
        this.chatId = chatId;

        AccountStorage.getInstance().getCurrentActive(account -> {
            this.session = AccountManager.getInstance().getSession(account.getAccountId()); // TODO: LiveData

            if(session != null){
                session.addUpdateHandler(this);

                session.send(new TdApi.GetChat(chatId), obj->{
                    if(obj instanceof TdApi.Chat){
                        TdApi.Chat chat = (TdApi.Chat)obj;
                        Log.d("ChatViewModel", "GetChat success. Title: " + chat.title);

                        chatTitle.postValue(chat.title);
                        chatAvatar.postValue(chat.photo);
                    }else if(obj instanceof TdApi.Error){
                        TdApi.Error err = (TdApi.Error)obj;
                        Logger.LOGE("ChatViewModel", "GetChat ERROR: " + err.code + " - " + err.message);
                    }else{
                        Logger.LOGW("ChatViewModel", "GetChat Unknown result: " + obj.toString());
                    }
                });

                session.send(new TdApi.OpenChat(chatId));
            }else{
                Toast.makeText(AppManager.getInstance().getContext(), "session is null!", Toast.LENGTH_SHORT).show();
            }
        });

        if (initialTitle != null) {
            chatTitle.setValue(initialTitle);
        }

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
        byId.clear();
        rawMessages.clear();
        albumGroups.clear();

        session.send(new TdApi.GetChatHistory(chatId, 0L, 0, INITIAL_PAGE_SIZE, false), this);
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
                scheduleUiUpdate(false, true);
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
            topLoading.postValue(false);
            if(isInitialLoad){
                isInitialLoad = false;
                scheduleUiUpdate(false, true);
            }
            return;
        }

        for (TdApi.Message m : msgs.messages) {
            if (m == null || m.chatId != chatId) continue;
            processMessageAndPut(m);
            if (oldestId == 0 || m.id < oldestId) oldestId = m.id;
        }

        hasMore = count >= PAGE_SIZE && oldestId > 1;

        if (isInitialLoad) {
            isInitialLoad = false;
            loading = false;
            scheduleUiUpdate(false, true);

            if (count < INITIAL_PAGE_SIZE) {
                mainHandler.postDelayed(() -> {
                    if (isCleared) return;
                    hasMore = true;
                    loading = true;
                    session.send(new TdApi.GetChatHistory(chatId, oldestId, -1, PAGE_SIZE, false), this);
                }, 800);
            }
        } else {
            loading = false;
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
            return new MessageItem(
                    m.id,
                    m.chatId,
                    m.isOutgoing,
                    "",
                    null,
                    m.mediaAlbumId,
                    new UiContent.Unknown(),
                    extractSenderAvatarFileId(m),
                    extractSenderName(m),
                    extractGcTag(m));
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
                ui, extractSenderAvatarFileId(m),
                extractSenderName(m),
                extractGcTag(m)
        );
    }

    private int extractSenderAvatarFileId(TdApi.Message m) {
        if (m.isOutgoing) return 0;

        long senderUserId = 0;
        if (m.senderId instanceof TdApi.MessageSenderUser) {
            senderUserId = ((TdApi.MessageSenderUser) m.senderId).userId;
        }

        if (senderUserId != 0) {
            TdApi.User user = fullUserCache.get(senderUserId);
            if (user != null && user.profilePhoto != null && user.profilePhoto.small != null) {
                return user.profilePhoto.small.id;
            }
        }
        return 0;
    }

    private String extractSenderName(TdApi.Message m) {
        if (m.senderId instanceof TdApi.MessageSenderUser) {
            long userId = ((TdApi.MessageSenderUser) m.senderId).userId;
            TdApi.User user = fullUserCache.get(userId);
            if (user != null) {
                String first = user.firstName != null ? user.firstName : "";
                String last = user.lastName != null ? user.lastName : "";
                return (first + " " + last).trim();
            } else {
                if (!pendingUserRequests.contains(userId)) {
                    pendingUserRequests.add(userId);
                    session.send(new TdApi.GetUser(userId), result -> {
                        pendingUserRequests.remove(userId);
                        if (result instanceof TdApi.User) {
                            onUserLoaded((TdApi.User) result);
                        }
                    });
                }
                return null; // loading...
            }
        }
        if (m.senderId instanceof TdApi.MessageSenderChat) {
            return null; // channel
        }
        return null; // unknown
    }


    private String extractGcTag(TdApi.Message m) {
        if (m.senderId instanceof TdApi.MessageSenderUser) {
            long userId = ((TdApi.MessageSenderUser) m.senderId).userId;

            String cachedTag = chatMembersTags.get(userId);
            if (cachedTag != null) {
                return cachedTag.isEmpty() ? null : cachedTag;
            }

            if (!pendingMemberRequests.contains(userId)) {
                pendingMemberRequests.add(userId);

                session.send(new TdApi.GetChatMember(chatId, new TdApi.MessageSenderUser(userId)), result -> {
                    pendingMemberRequests.remove(userId);

                    if (result instanceof TdApi.ChatMember) {
                        TdApi.ChatMember member = (TdApi.ChatMember) result;
                        String tag = getTagFromStatus(member.status);

                        chatMembersTags.put(userId, tag == null ? "" : tag);

                        if (tag != null) {
                            triggerUpdateForUser(userId);
                        }
                    } else {
                        chatMembersTags.put(userId, "");
                    }
                });
            }
            return null;
        }
        return null;
    }

    private String getTagFromStatus(TdApi.ChatMemberStatus status) {
        switch (status.getConstructor()) {
            case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
                TdApi.ChatMemberStatusCreator creator = (TdApi.ChatMemberStatusCreator) status;
                return !TextUtils.isEmpty(creator.customTitle) ? creator.customTitle : "Owner";

            case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
                TdApi.ChatMemberStatusAdministrator admin = (TdApi.ChatMemberStatusAdministrator) status;
                return !TextUtils.isEmpty(admin.customTitle) ? admin.customTitle : "Admin";

            case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
                return null;

            default:
                return null;
        }
    }

    private void triggerUpdateForUser(long userId) {
        updateExecutor.execute(() -> {
            boolean needUpdate = false;
            for (MessageItem item : byId.values()) {
                TdApi.Message raw = rawMessages.get(item.id);
                if (raw != null && raw.senderId instanceof TdApi.MessageSenderUser) {
                    if (((TdApi.MessageSenderUser) raw.senderId).userId == userId) {
                        byId.put(item.id, toItem(raw));
                        needUpdate = true;
                    }
                }
            }
            if (needUpdate) {
                scheduleUiUpdate(false, false);
            }
        });
    }

    private String formatTime(int unixSeconds) {
        long ms = unixSeconds * 1000L;
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(ms));
    }

    private void onUserLoaded(TdApi.User user) {
        fullUserCache.put(user.id, user);

        String fullName = (user.firstName + " " + user.lastName).trim();
        String oldName = userNameCache.get(user.id);

        boolean nameChanged = oldName == null || !oldName.equals(fullName);
        if (nameChanged) {
            userNameCache.put(user.id, fullName);
        }

        triggerUpdateForUser(user.id);
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
        if (isCleared) return;
        synchronized (batchLock) {
            if (isCleared) return;

            if (isPagination) pendingPagination = true;
            if (scrollToBottom && !isPagination) pendingScrollToBottom = true;

            if (!updateScheduled) {
                updateScheduled = true;
                mainHandler.postDelayed(this::performBufferedUpdate, BATCH_DELAY_MS);
            }
        }
    }

    private void performBufferedUpdate() {
        if (isCleared) return;

        boolean doScroll;
        boolean doPagination;

        synchronized (batchLock) {
            if (isCleared) return;
            doScroll = pendingScrollToBottom;
            doPagination = pendingPagination;
            pendingScrollToBottom = false;
            pendingPagination = false;
            updateScheduled = false;
        }

        if (isCleared || updateExecutor.isShutdown()) return;

        updateExecutor.execute(()->{
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
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        isCleared = true;
        if (session != null) {
            session.removeUpdateHandler(this);
            session.send(new TdApi.CloseChat(chatId), null);
        }
        mainHandler.removeCallbacksAndMessages(null);
        updateExecutor.shutdown();
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