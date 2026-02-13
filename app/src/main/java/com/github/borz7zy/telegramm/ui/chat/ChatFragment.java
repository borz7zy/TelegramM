package com.github.borz7zy.telegramm.ui.chat;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.BackEventCompat;
import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramDialogFragment;
import com.github.borz7zy.telegramm.ui.model.MessageItem;
import com.github.borz7zy.telegramm.ui.model.PhotoData;
import com.github.borz7zy.telegramm.ui.widget.EdgeSwipeDismissLayout;
import com.github.borz7zy.telegramm.ui.widget.SpringRecyclerView;
import com.github.borz7zy.telegramm.ui.widget.TypingDrawable;
import com.github.borz7zy.telegramm.utils.Logger;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;
import com.masoudss.lib.WaveformSeekBar;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class ChatFragment extends BaseTelegramDialogFragment implements Client.ResultHandler {

    private static final String ARG_CHAT_ID = "chat_id";
    private static final String ARG_TITLE = "title";
    private final float BLUR_RADIUS = 20.f;

    private long chatId;
    private String title;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Long, MessageItem> byId = new ConcurrentHashMap<>();
    private final Map<Long, TdApi.Message> rawMessages = new ConcurrentHashMap<>();
    private final Map<Long, Long> albumGroups = new ConcurrentHashMap<>();
    private final Map<Long, String> userNameCache = new ConcurrentHashMap<>();
    private MessageUiMapper uiMapper;
    private boolean loading = false;
    private boolean hasMore = true;
    private long oldestId = 0;
    private boolean isInitialLoad = true;
    private View content;
    private ImageView ivChatAvatar;
    private View typingBar;
    private ImageView typingIcon;
    private TypingDrawable typingDrawable;

    private SpringRecyclerView rv;
    private LinearLayoutManager lm;
    private TopLoadingAdapter topLoading;
    private ChatAdapter adapter;
    private EditText et;
    private ImageView btnSend;

    private ImageView btnAttach;
    private ImageView btnAction;
    private WaveformSeekBar waveRecord;

    private enum InputMode { TEXT, VOICE }
    private InputMode inputMode = InputMode.TEXT;

    private VoiceWavRecorder voiceRecorder;
    private File voiceTempFile;
    private boolean voicePaused = false;

    private final ArrayList<Integer> voiceLevels = new ArrayList<>();
    private static final int MAX_VOICE_POINTS = 240;
    private static final int REQ_RECORD_AUDIO = 501;

    private EdgeSwipeDismissLayout edge;
    private FrameLayout sheet;
    private View scrim;

    private boolean closing = false;
    private OnBackPressedCallback backCallback;

    private static final long BATCH_DELAY_MS = 200;
    private final Object batchLock = new Object();
    private boolean updateScheduled = false;
    private boolean pendingScrollToBottom = false;
    private boolean pendingPagination = false;

    public static ChatFragment newInstance(long chatId, String title) {
        ChatFragment f = new ChatFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_CHAT_ID, chatId);
        b.putString(ARG_TITLE, title);
        f.setArguments(b);
        return f;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_chat_swipe, container, false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Logger.LOGD("ChatFragment", "onAttach");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setCancelable(false);

        backCallback = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                closeAnimated();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    @Override
    public void dismiss() {
        if (closing) super.dismiss();
        else closeAnimated();
    }

    @Override
    public void dismissAllowingStateLoss() {
        if (closing) super.dismissAllowingStateLoss();
        else closeAnimated();
    }

    @Override
    public int getTheme() {
        return R.style.Theme_TelegramM_FullscreenDialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        ComponentDialog d = new ComponentDialog(requireContext(), getTheme());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window w = d.getWindow();
        if (w != null) {
            WindowCompat.setDecorFitsSystemWindows(w, false);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            w.setNavigationBarContrastEnforced(false);
        }

        setCancelable(false);
        d.setCanceledOnTouchOutside(false);

        d.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackStarted(@NonNull BackEventCompat e) {
                if (edge == null) return;
                boolean fromLeft = (e.getSwipeEdge() == BackEventCompat.EDGE_LEFT);
                edge.onPredictiveBackStarted();
                edge.setPredictiveBackProgress(0f, fromLeft);
            }
            @Override public void handleOnBackProgressed(@NonNull BackEventCompat e) {
                if (edge == null) return;
                boolean fromLeft = (e.getSwipeEdge() == BackEventCompat.EDGE_LEFT);
                edge.setPredictiveBackProgress(e.getProgress(), fromLeft);
            }
            @Override public void handleOnBackCancelled() {
                if (edge != null) edge.onPredictiveBackCancelled();
            }
            @Override public void handleOnBackPressed() {
                closeAnimated();
            }
        });

        d.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                closeAnimated();
                return true;
            }
            return false;
        });

        return d;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        edge = view.findViewById(R.id.edge_root);
        scrim = view.findViewById(R.id.scrim);
        sheet = view.findViewById(R.id.sheet);

        content = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_chat, sheet, false);
        sheet.addView(content);

        typingBar = content.findViewById(R.id.typing_bottom);
        typingIcon = content.findViewById(R.id.ic_typing);
        typingDrawable = new TypingDrawable(requireContext(), R.drawable.ic_typing_list);
        typingIcon.setImageDrawable(typingDrawable);
        typingBar.setVisibility(View.GONE);

        edge.setTargets(sheet, scrim);
        edge.setDismissListener(ChatFragment.super::dismissAllowingStateLoss);
        edge.setEdgeWidthDp(200);

        int w = requireContext().getResources().getDisplayMetrics().widthPixels;
        sheet.setTranslationX(w);
        sheet.animate().translationX(0f).setDuration(300).start();

        scrim.setOnClickListener(v -> closeAnimated());

        setupBlur(content);

        Bundle args = requireArguments();
        chatId = args.getLong(ARG_CHAT_ID);
        title = args.getString(ARG_TITLE, "Chat");

        TextView tvTitle = content.findViewById(R.id.tv_title);
        ImageView btnClose = content.findViewById(R.id.btn_close);
        tvTitle.setText(title);
        btnClose.setOnClickListener(v -> closeAnimated());

        ivChatAvatar = findImageView(content, "chat_avatar", "iv_avatar", "avatar", "image_avatar");
        if (ivChatAvatar != null) ivChatAvatar.setImageResource(R.drawable.bg_badge);

        rv = content.findViewById(R.id.rv_messages);
        et = content.findViewById(R.id.et_message);

        btnAttach = content.findViewById(R.id.btn_attach);
        btnAction = content.findViewById(R.id.btnClear);
        btnSend = content.findViewById(R.id.btn_send);

        waveRecord = content.findViewById(R.id.wave_record);
        if (waveRecord != null) waveRecord.setOnTouchListener((v1, e) -> true);

        btnSend.setOnClickListener(v -> onSendClicked());

        btnSend.setOnLongClickListener(v -> {
            String t = et.getText() == null ? "" : et.getText().toString().trim();
            if (!TextUtils.isEmpty(t)) return true;

            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            startVoiceFlow();
            return true;
        });

        btnAttach.setOnClickListener(v -> {
            if (inputMode == InputMode.VOICE) cancelVoiceRecording();
            // else TODO: attach
        });

        btnAction.setOnClickListener(v -> {
            if (inputMode == InputMode.VOICE) toggleVoicePause();
            // else TODO: stickers
        });

        topLoading = new TopLoadingAdapter();
        adapter = new ChatAdapter();
        lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);

        adapter.setBtnListener((item, btn) -> handleClick(item.chatId, item.id, btn));

        ConcatAdapter concat = new ConcatAdapter(
                new ConcatAdapter.Config.Builder()
                        .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
                        .setIsolateViewTypes(true).build(),
                topLoading,
                adapter
        );

        rv.setLayoutManager(lm);
        rv.setAdapter(concat);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy >= 0) return;

                int first = lm.findFirstVisibleItemPosition();
                if (first == RecyclerView.NO_POSITION) return;

                int threshold = 3 + (topLoading.isVisible() ? 1 : 0);

                if (first <= threshold && !loading && hasMore) {
                    requestOlder();
                }
            }
        });

        applyInsets(view, content);
    }

    @Override
    protected void onAuthorized() {
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
        byId.clear();
        rawMessages.clear();
        albumGroups.clear();

        session.send(new TdApi.GetChatHistory(chatId, 0L, 0, 70, false), this);
    }

    private void requestOlder() {
        if (loading || !hasMore || session == null || oldestId == 0) return;
        loading = true;
        setTopLoading(true);
        session.send(new TdApi.GetChatHistory(chatId, oldestId, -1, 50, false), this);
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
                boolean forceScroll = m.isOutgoing;
                if(forceScroll){
                    mainHandler.postDelayed(() -> rv.smoothScrollToPosition(adapter.getItemCount() - 1), 300);
                }
                scheduleUiUpdate(false, true);
            }
        }
        else if (object instanceof TdApi.UpdateMessageSendSucceeded) {
            TdApi.UpdateMessageSendSucceeded u = (TdApi.UpdateMessageSendSucceeded) object;
            if (u.message != null && u.message.chatId == chatId) {
                byId.remove(u.oldMessageId);
                rawMessages.remove(u.oldMessageId);

                rawMessages.put(u.message.id, u.message);
                byId.put(u.message.id, toItem(u.message));

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
                TdApi.Message raw = rawMessages.get(u.messageId);
                if (raw != null) raw.replyMarkup = u.replyMarkup;

                MessageItem cur = byId.get(u.messageId);
                if (cur != null) {
                    UiContent newUi;
                    if (raw != null) {
                        newUi = uiMapper.map(raw);
                    } else {
                        newUi = cur.ui;
                        if (u.replyMarkup == null
                                || u.replyMarkup.getConstructor() == TdApi.ReplyMarkupRemoveKeyboard.CONSTRUCTOR) {
                        }
                    }
                    MessageItem updated = cur.withUi(newUi);
                    byId.put(u.messageId, updated);
                    scheduleUiUpdate(false, false);
                }
            }
        }
        else if (object instanceof TdApi.Chat chat) {
            if (chat.id == chatId) applyChatHeader(chat);
        }
        else if (object instanceof TdApi.UpdateChatTitle u) {
            if (u.chatId == chatId) {
                title = u.title;
                mainHandler.post(() -> {
                    TextView tv = content.findViewById(R.id.tv_title);
                    if (tv != null) tv.setText(u.title);
                });
            }
        }
        else if (object instanceof TdApi.UpdateChatPhoto u) {
            if (u.chatId == chatId) applyChatHeaderPhoto(u.photo);
        }
        else if (object instanceof TdApi.UpdateChatAction u) {
            if (u.chatId == chatId) {
                handleTyping(u);
            }
        }
        else if (object instanceof TdApi.UpdateUser) {
            onUserLoaded(((TdApi.UpdateUser) object).user);
        }
        else if (object instanceof TdApi.User) {
            onUserLoaded((TdApi.User) object);
        }
    }

    private void handleHistory(TdApi.Messages messages) {
        int count = (messages == null || messages.messages == null) ? 0 : messages.messages.length;

        if (count == 0) {
            hasMore = false;
            loading = false;
            setTopLoading(false);
            return;
        }

        for (TdApi.Message m : messages.messages) {
            if (m == null || m.chatId != chatId) continue;
            processMessageAndPut(m);
            if (oldestId == 0 || m.id < oldestId) oldestId = m.id;
        }

        if (isInitialLoad) {
            scheduleUiUpdate(false, true);
            isInitialLoad = false;
        } else {
            scheduleUiUpdate(true, false);
        }
    }

    private void handleTyping(TdApi.UpdateChatAction uca) {
        if (uca.action == null) return;
        mainHandler.post(() -> {
            switch (uca.action.getConstructor()) {
                case TdApi.ChatActionTyping.CONSTRUCTOR:
                    showTyping("печатает…");
                    break;
                case TdApi.ChatActionCancel.CONSTRUCTOR:
                    hideTyping();
                    break;
                default:
                    break;
            }
        });
    }

    private void processMessageAndPut(TdApi.Message m) {
        rawMessages.put(m.id, m);
        long albumId = m.mediaAlbumId;
        PhotoData photoData = null;
        String caption = "";

        if (m.content instanceof TdApi.MessagePhoto photoContent) {
            photoData = extractPhoto(m.id, photoContent.photo);
            if (photoContent.caption != null && !TextUtils.isEmpty(photoContent.caption.text)) {
                caption = photoContent.caption.text;
            }
        }

        if (albumId != 0 && albumGroups.containsKey(albumId)) {
            long mainMessageId = albumGroups.get(albumId);
            MessageItem existingItem = byId.get(mainMessageId);

            if (existingItem != null && photoData != null && existingItem.ui instanceof UiContent.Media) {
                boolean alreadyExists = false;
                if (existingItem.photos != null) {
                    for (PhotoData p : existingItem.photos) {
                        if (p.localPath != null && p.localPath.equals(photoData.localPath)) {
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

        MessageItem newItem = toItem(m);
        byId.put(m.id, newItem);
        if (albumId != 0) {
            albumGroups.put(albumId, m.id);
        }
    }

    private MessageItem toItem(TdApi.Message m) {
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

    private void scheduleUiUpdate(boolean isPagination, boolean scrollToBottom) {
        synchronized (batchLock) {
            if (isPagination) pendingPagination = true;
            if (scrollToBottom) pendingScrollToBottom = true;

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

        if (!isAdded()) return;

        new Thread(() -> {
            ArrayList<MessageItem> list = new ArrayList<>(byId.values());
            list.sort((m1, m2) -> Long.compare(m1.id, m2.id));

            mainHandler.post(() -> applyListToAdapter(list, doPagination, doScroll));
        }).start();
    }

    private void applyListToAdapter(List<MessageItem> list, boolean isPagination, boolean maybeScrollToBottom) {
        if (!isAdded()) return;

        long anchorMsgId = -1;
        int anchorOffset = 0;

        boolean wasAtBottom = isNearBottom();

        if (isPagination) {
            int firstPos = lm.findFirstVisibleItemPosition();
            if (firstPos != RecyclerView.NO_POSITION) {
                View child = lm.findViewByPosition(firstPos);
                boolean isLoader = (topLoading.isVisible() && firstPos == 0);

                if (isLoader) {
                    int msgPos = firstPos + 1;
                    if (msgPos < adapter.getItemCount() + 1) {
                        View msgView = lm.findViewByPosition(msgPos);
                        if (msgView != null) {
                            int adapterIndex = msgPos - 1;
                            if (adapterIndex >= 0 && adapterIndex < adapter.getItemCount()) {
                                anchorMsgId = adapter.getItemId(adapterIndex);
                                anchorOffset = msgView.getTop();
                            }
                        }
                    }
                } else {
                    if (child != null) {
                        int adapterIndex = firstPos - (topLoading.isVisible() ? 1 : 0);
                        if (adapterIndex >= 0 && adapterIndex < adapter.getItemCount()) {
                            anchorMsgId = adapter.getItemId(adapterIndex);
                            anchorOffset = child.getTop();
                        }
                    }
                }
            }
        }

        final long finalAnchorId = anchorMsgId;
        final int finalAnchorOffset = anchorOffset;

        adapter.submitList(list, () -> {
            if (isPagination) {
                setTopLoading(false);
                if (finalAnchorId != -1) {
                    int newPos = adapter.findPositionById(finalAnchorId);
                    if (newPos != RecyclerView.NO_POSITION) {
                        lm.scrollToPositionWithOffset(newPos, finalAnchorOffset);
                    }
                }
            } else {
                if (maybeScrollToBottom && wasAtBottom) {
                    rv.scrollToPosition(adapter.getItemCount() - 1);
                }
            }
            loading = false;
        });
    }

    private void sendMessage() {
        if (session == null) return;
        String text = et.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        et.setText("");

        TdApi.InputMessageContent content =
                new TdApi.InputMessageText(new TdApi.FormattedText(text, null), null, true);

        session.send(new TdApi.SendMessage(chatId, null, null, null, null, content), null);
    }

    private void handleClick(long chatId, long msgId, UiContent.UiButton btn) {
        if (btn.isUrl()) {
            if (isAdded()) {
                mainHandler.post(() -> {
                    Intent i = new Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse(btn.url)
                    );
                    startActivity(i);
                });
            }
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

    private void applyChatHeader(TdApi.Chat chat) {
        if (!isAdded()) return;
        title = chat.title;
        mainHandler.post(() -> {
            TextView tvTitle = content.findViewById(R.id.tv_title);
            tvTitle.setText(chat.title);
            applyChatAvatar(chat.photo);
        });
    }

    private void applyChatHeaderPhoto(TdApi.ChatPhotoInfo photo) {
        if (!isAdded()) return;
        mainHandler.post(() -> applyChatAvatar(photo));
    }

    private void applyChatAvatar(TdApi.ChatPhotoInfo photo) {
        if (ivChatAvatar == null) return;

        Glide.with(ivChatAvatar).clear(ivChatAvatar);
        ivChatAvatar.setImageResource(R.drawable.bg_badge);

        if (photo == null || photo.small == null) return;

        int fid = photo.small.id;
        if (fid == 0) return;

        adapter.setChatAvatar(fid);

        final String tag = "chat:" + chatId + ":" + fid;
        ivChatAvatar.setTag(tag);

        String cached = TdMediaRepository.get().getCachedPath(fid);
        if (!TextUtils.isEmpty(cached)) {
            Glide.with(ivChatAvatar)
                    .load(cached)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_badge)
                    .error(R.drawable.bg_badge)
                    .into(ivChatAvatar);
            return;
        }

        WeakReference<ImageView> refInfo = new WeakReference<>(ivChatAvatar);

        TdMediaRepository.get().getPathOrRequest(fid, p -> {
            ImageView iv = refInfo.get();
            if (iv == null) return;

            Object cur = iv.getTag();
            if (!(cur instanceof String) || !tag.equals(cur)) return;
            if (TextUtils.isEmpty(p)) return;

            iv.post(() -> {
                Glide.with(iv)
                        .load(p)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.bg_badge)
                        .error(R.drawable.bg_badge)
                        .into(iv);
            });
        });
    }

    @Override
    public void onDestroyView() {
        closing = false;
        if (inputMode == InputMode.VOICE) {
            cancelVoiceRecording();
        }
        if (session != null) {
            session.send(new TdApi.CloseChat(chatId), null);
            session.removeUpdateHandler(this);
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void setTopLoading(boolean v) {
        if (!isAdded() || rv == null) return;
        rv.post(() -> topLoading.setVisible(v));
    }

    private boolean isNearBottom() {
        int count = adapter.getItemCount();
        if (count == 0) return true;

        int lastVisiblePosition = lm.findLastVisibleItemPosition();

        return lastVisiblePosition >= (count - 1) - 2;
    }

    private void showTyping(String text) {
        if (!isAdded() || typingDrawable == null) return;
        TextView tv = typingBar.findViewById(R.id.typing_text);
        if (tv != null) tv.setText(text);
        typingBar.setVisibility(View.VISIBLE);
        typingDrawable.start();
        mainHandler.removeCallbacksAndMessages("typing");
        mainHandler.postDelayed(() -> hideTyping(), 5000);
    }

    private void hideTyping() {
        if (typingDrawable != null) typingDrawable.stop();
        if (typingBar != null) typingBar.setVisibility(View.GONE);
    }

    private String formatTime(int unixSeconds) {
        long ms = unixSeconds * 1000L;
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private void notifyMessageChanged(long msgId, int payload) {
        if (!isAdded()) return;
        mainHandler.post(() -> {
            int pos = adapter.findPositionById(msgId);
            if (pos >= 0) adapter.notifyItemChanged(pos, payload);
        });
    }

    private PhotoData extractPhoto(long rowMessageId, TdApi.Photo photo) {
        if (photo == null || photo.sizes.length == 0) return null;

        TdApi.PhotoSize best = findBestPhotoSize(photo.sizes);
        int fileId = best.photo.id;
        String localPath = best.photo.local != null ? best.photo.local.path : null;
        boolean completed = best.photo.local != null && best.photo.local.isDownloadingCompleted;

        if (TextUtils.isEmpty(localPath) || !completed) {
            TdMediaRepository.get().getPathOrRequest(fileId, p -> {
                if (!TextUtils.isEmpty(p)) {
                    notifyMessageChanged(rowMessageId, ChatAdapter.PAYLOAD_MEDIA);
                }
            });
            String cached = TdMediaRepository.get().getCachedPath(fileId);
            if (!TextUtils.isEmpty(cached)) localPath = cached;
        }
        return new PhotoData(fileId, localPath, best.width, best.height);
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

    private void applyInsets(View root, View content) {
        final int[] statusTop = {0};
        final int[] navBottom = {0};

        BlurView header = content.findViewById(R.id.header_blur);
        BlurView inputBar = content.findViewById(R.id.input_blur);
        SpringRecyclerView rv = content.findViewById(R.id.rv_messages);

        final int headerBaseTop = header.getPaddingTop();
        final int headerBaseBottom = header.getPaddingBottom();
        final int headerBaseLeft = header.getPaddingLeft();
        final int headerBaseRight = header.getPaddingRight();

        final int inputBaseTop = inputBar.getPaddingTop();
        final int inputBaseBottom = inputBar.getPaddingBottom();
        final int inputBaseLeft = inputBar.getPaddingLeft();
        final int inputBaseRight = inputBar.getPaddingRight();

        final int rvBaseTop = rv.getPaddingTop();
        final int rvBaseBottom = rv.getPaddingBottom();
        final int rvBaseLeft = rv.getPaddingLeft();
        final int rvBaseRight = rv.getPaddingRight();

        rv.setClipToPadding(false);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            statusTop[0] = status.top;
            navBottom[0] = nav.bottom;
            boolean imeVisible = ime.bottom > 0;

            header.setPadding(headerBaseLeft, headerBaseTop + status.top, headerBaseRight, headerBaseBottom);
            inputBar.setPadding(inputBaseLeft, inputBaseTop, inputBaseRight, inputBaseBottom + nav.bottom);

            rv.setPadding(rvBaseLeft, rvBaseTop + statusTop[0], rvBaseRight,
                    rvBaseBottom + navBottom[0] + ime.bottom);

            return insets;
        });

        ViewCompat.setWindowInsetsAnimationCallback(root, new WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @NonNull @Override
            public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                inputBar.setTranslationY(-ime.bottom);
                rv.setPadding(rvBaseLeft, rvBaseTop + statusTop[0], rvBaseRight, rvBaseBottom + navBottom[0] + ime.bottom);
                return insets;
            }
        });
    }

    private void setupBlur(View view) {
        BlurTarget target = view.findViewById(R.id.blur_target);
        BlurView header = view.findViewById(R.id.header_blur);
        BlurView input = view.findViewById(R.id.input_blur);
        Drawable bg = requireActivity().getWindow().getDecorView().getBackground();
        header.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
        input.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
    }

    private void closeAnimated() {
        if (closing) return;
        closing = true;
        if (edge != null) edge.animateDismiss();
        else super.dismissAllowingStateLoss();
    }

    private static ImageView findImageView(View root, String... names) {
        String pkg = root.getContext().getPackageName();
        for (String n : names) {
            int id = root.getResources().getIdentifier(n, "id", pkg);
            if (id != 0) {
                View v = root.findViewById(id);
                if (v instanceof ImageView) return (ImageView) v;
            }
        }
        return null;
    }

    private void onSendClicked() {
        if (inputMode == InputMode.VOICE) finishAndSendVoice();
        else sendMessage();
    }

    private void startVoiceFlow() {
        if (!hasRecordAudioPermission()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        startVoiceRecording();
    }

    private boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startVoiceRecording() {
        if (inputMode == InputMode.VOICE) return;
        hideKeyboard();
        et.clearFocus();
        inputMode = InputMode.VOICE;
        voicePaused = false;
        voiceLevels.clear();
        updateVoiceUi(true);
        voiceTempFile = new File(requireContext().getCacheDir(), "voice_" + System.currentTimeMillis() + ".wav");
        voiceRecorder = new VoiceWavRecorder(44100, voiceTempFile, level -> {
            if (!isAdded() || waveRecord == null) return;
            mainHandler.post(() -> {
                if (inputMode != InputMode.VOICE || voicePaused) return;
                voiceLevels.add(level);
                if (voiceLevels.size() > MAX_VOICE_POINTS) voiceLevels.remove(0);
                int[] arr = new int[voiceLevels.size()];
                for (int i = 0; i < voiceLevels.size(); ++i) arr[i] = voiceLevels.get(i);
                waveRecord.setSampleFrom(arr);
                waveRecord.setMaxProgress((float) arr.length);
                waveRecord.setProgress((float) arr.length);
            });
        });
        voiceRecorder.start();
    }

    private void toggleVoicePause() {
        if (inputMode != InputMode.VOICE || voiceRecorder == null) return;
        voicePaused = !voicePaused;
        if (voicePaused) {
            voiceRecorder.pause();
            btnAction.setImageResource(android.R.drawable.ic_media_play);
        } else {
            voiceRecorder.resume();
            btnAction.setImageResource(android.R.drawable.ic_media_pause);
        }
    }

    private void cancelVoiceRecording() {
        if (inputMode != InputMode.VOICE) return;
        if (voiceRecorder != null) {
            voiceRecorder.stopAndFinalize();
            voiceRecorder = null;
        }
        if (voiceTempFile != null) {
            voiceTempFile.delete();
            voiceTempFile = null;
        }
        inputMode = InputMode.TEXT;
        voicePaused = false;
        updateVoiceUi(false);
    }

    private void finishAndSendVoice() {
        if (inputMode != InputMode.VOICE) return;
        if (voiceRecorder != null) {
            voiceRecorder.stopAndFinalize();
            voiceRecorder = null;
        }
        File wav = voiceTempFile;
        voiceTempFile = null;
        inputMode = InputMode.TEXT;
        voicePaused = false;
        updateVoiceUi(false);
        if (wav == null || !wav.exists() || wav.length() == 0) return;

        final ArrayList<Integer> levels = new ArrayList<>(voiceLevels);
        voiceLevels.clear();
        File m4a = new File(requireContext().getCacheDir(), "voice_" + System.currentTimeMillis() + ".m4a");

        new Thread(() -> {
            try {
                int durationSec = durationSecFromWav(wav, 44100, 1);
                byte[] waveform = buildTelegramWaveform5bit(levels, 100);
                convertWavToM4a(wav, m4a, 44100, 1, 64000);
                wav.delete();
                sendVoiceNote(m4a, durationSec, waveform);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "VoiceConvertSend").start();
    }

    private void sendVoiceNote(File audioFile, int durationSec, byte[] waveform) {
        if (session == null || audioFile == null || !audioFile.exists()) return;

        TdApi.InputFile input = new TdApi.InputFileLocal(audioFile.getAbsolutePath());
        TdApi.InputMessageContent content = new TdApi.InputMessageVoiceNote(
                input, durationSec, waveform != null ? waveform : new byte[0], null, null
        );
        session.send(new TdApi.SendMessage(chatId, null, null, null, null, content), null);
    }

    private void updateVoiceUi(boolean voiceMode) {
        if (waveRecord != null) {
            if (voiceMode) {
                waveRecord.setAlpha(0f);
                waveRecord.setVisibility(View.VISIBLE);
                waveRecord.animate().alpha(1f).setDuration(180).start();
            } else {
                waveRecord.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    waveRecord.setVisibility(View.GONE);
                    waveRecord.setAlpha(1f);
                }).start();
            }
        }
        if (et != null) {
            if (voiceMode) {
                et.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    et.setVisibility(View.GONE);
                    et.setAlpha(1f);
                }).start();
            } else {
                et.setAlpha(0f);
                et.setVisibility(View.VISIBLE);
                et.animate().alpha(1f).setDuration(180).start();
            }
        }
        if (btnAttach != null) {
            btnAttach.setImageResource(voiceMode ? android.R.drawable.ic_menu_close_clear_cancel : R.drawable.ic_attach_outline);
        }
        if (btnAction != null) {
            btnAction.setImageResource(voiceMode ? android.R.drawable.ic_media_pause : R.drawable.ic_sticker_smile_outline);
        }
    }

    private void hideKeyboard() {
        if (!isAdded()) return;
        View v = requireActivity().getCurrentFocus();
        if (v == null) v = getView();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private static int durationSecFromWav(File wav, int sampleRate, int channels) {
        long bytes = Math.max(0, wav.length() - 44);
        double seconds = bytes / (double) (sampleRate * channels * 2);
        return Math.max(1, (int) Math.ceil(seconds));
    }

    private static void convertWavToM4a(File wav, File m4a,
                                        int sampleRate, int channels, int bitRate) throws IOException {

        MediaCodec codec = null;
        MediaMuxer muxer = null;
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(wav);
            skipFully(fis, 44);

            MediaFormat format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024);

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();

            muxer = new MediaMuxer(m4a.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            int trackIndex = -1;
            boolean muxerStarted = false;

            byte[] temp = new byte[16 * 1024];
            long totalSamples = 0;

            while (!outputDone) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                        if (inBuf != null) {
                            inBuf.clear();
                            int toRead = Math.min(inBuf.remaining(), temp.length);
                            int read = fis.read(temp, 0, toRead);

                            if (read == -1) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                inBuf.put(temp, 0, read);

                                int samplesRead = read / (2 * channels);
                                long ptsUs = totalSamples * 1_000_000L / sampleRate;
                                totalSamples += samplesRead;

                                codec.queueInputBuffer(inIndex, 0, read, ptsUs, 0);
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw new IllegalStateException("Format changed twice");
                    MediaFormat outFormat = codec.getOutputFormat();
                    trackIndex = muxer.addTrack(outFormat);
                    muxer.start();
                    muxerStarted = true;

                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {

                } else if (outIndex >= 0) {
                    ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                    if (outBuf == null) throw new IllegalStateException("Output buffer is null");

                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0;
                    }

                    if (info.size > 0) {
                        if (!muxerStarted) throw new IllegalStateException("Muxer not started");
                        outBuf.position(info.offset);
                        outBuf.limit(info.offset + info.size);
                        muxer.writeSampleData(trackIndex, outBuf, info);
                    }

                    codec.releaseOutputBuffer(outIndex, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Throwable ignored) {}
                try { codec.release(); } catch (Throwable ignored) {}
            }
            if (muxer != null) {
                try { muxer.stop(); } catch (Throwable ignored) {}
                try { muxer.release(); } catch (Throwable ignored) {}
            }
            if (fis != null) {
                try { fis.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void skipFully(FileInputStream fis, long bytes) throws IOException {
        long left = bytes;
        while (left > 0) {
            long skipped = fis.skip(left);
            if (skipped <= 0) {
                if (fis.read() == -1) break;
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static byte[] buildTelegramWaveform5bit(List<Integer> levels0to100, int targetPoints) {
        if (levels0to100 == null || levels0to100.isEmpty() || targetPoints <= 0) return new byte[0];

        int n = levels0to100.size();
        int[] p = new int[targetPoints];
        for (int i = 0; i < targetPoints; ++i) {
            int start = (int) ((long) i * n / targetPoints);
            int end = (int) ((long) (i + 1) * n / targetPoints);
            if (end <= start) end = Math.min(start + 1, n);

            long sum = 0;
            int cnt = 0;
            for (int j = start; j < end; ++j) {
                int v = levels0to100.get(j);
                if (v < 0) v = 0;
                if (v > 100) v = 100;
                sum += v;
                ++cnt;
            }
            int avg = (cnt == 0) ? 0 : (int) (sum / cnt);

            int v5 = Math.round(avg * 31f / 100f);
            if (v5 < 0) v5 = 0;
            if (v5 > 31) v5 = 31;
            p[i] = v5;
        }

        int bits = targetPoints * 5;
        int bytes = (bits + 7) / 8;
        byte[] out = new byte[bytes];

        int bitPos = 0;
        for (int i = 0; i < targetPoints; ++i) {
            int v = p[i] & 0x1F;
            for (int b = 0; b < 5; ++b) {
                if (((v >> b) & 1) != 0) {
                    int byteIndex = (bitPos + b) / 8;
                    int bitIndex = (bitPos + b) % 8;
                    out[byteIndex] |= (byte) (1 << bitIndex);
                }
            }
            bitPos += 5;
        }
        return out;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording();
        }
    }
}