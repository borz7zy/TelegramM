package com.github.borz7zy.telegramm.ui.chat;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.BackEventCompat;
import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.core.TdMessages;
import com.github.borz7zy.telegramm.ui.base.BaseTdCustomSheetDialogFragment;
import com.github.borz7zy.telegramm.ui.model.MessageItem;
import com.github.borz7zy.telegramm.ui.model.PhotoData;
import com.github.borz7zy.telegramm.ui.widget.EdgeSwipeDismissLayout;
import com.github.borz7zy.telegramm.ui.widget.SpringRecyclerView;
import com.github.borz7zy.telegramm.ui.widget.TypingDrawable;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class ChatFragment extends BaseTdCustomSheetDialogFragment {

    private static final String ARG_CHAT_ID = "chat_id";
    private static final String ARG_TITLE = "title";
    private final float BLUR_RADIUS = 20.f;
    private View content;
    private ImageView ivChatAvatar;

    public static ChatFragment newInstance(long chatId, String title) {
        ChatFragment f = new ChatFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_CHAT_ID, chatId);
        b.putString(ARG_TITLE, title);
        f.setArguments(b);
        return f;
    }

    private long chatId;
    private String title;

    private View typingBar;
    private ImageView typingIcon;
    private TypingDrawable typingDrawable;

    private RecyclerView rv;
    private LinearLayoutManager lm;
    private MessagesAdapter adapter;
    private EditText et;
    private ImageView btnSend;

    private EdgeSwipeDismissLayout edge;
    private FrameLayout sheet;
    private View scrim;

    private boolean closing = false;
    private OnBackPressedCallback backCallback;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_chat_swipe, container, false);
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
        if (closing) ChatFragment.super.dismiss();
        else closeAnimated();
    }

    @Override
    public void dismissAllowingStateLoss() {
        if (closing) ChatFragment.super.dismissAllowingStateLoss();
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

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat e) {
                if (edge == null) return;
                boolean fromLeft = (e.getSwipeEdge() == BackEventCompat.EDGE_LEFT);
                edge.onPredictiveBackStarted();
                edge.setPredictiveBackProgress(0f, fromLeft);
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat e) {
                if (edge == null) return;
                boolean fromLeft = (e.getSwipeEdge() == BackEventCompat.EDGE_LEFT);
                edge.setPredictiveBackProgress(e.getProgress(), fromLeft);
            }

            @Override
            public void handleOnBackCancelled() {
                if (edge != null) edge.onPredictiveBackCancelled();
            }

            @Override
            public void handleOnBackPressed() {
                closeAnimated();
            }
        });

        d.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
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

        typingDrawable = new TypingDrawable(
                requireContext(),
                R.drawable.ic_typing_list
        );

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
        if (ivChatAvatar != null) {
            ivChatAvatar.setImageResource(R.drawable.bg_badge);
        }

        rv = content.findViewById(R.id.rv_messages);
        et = content.findViewById(R.id.et_message);
        btnSend = content.findViewById(R.id.btn_send);

        adapter = new MessagesAdapter();
        lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);

        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (lm.findFirstVisibleItemPosition() == 0) {
                    if (uiActorRef != null) uiActorRef.tell(new RequestOlder());
                }
            }
        });

        applyInsets(view, content);
    }

    private void sendMessage() {
        if (clientActorRef == null) return;

        String text = et.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        et.setText("");

        TdApi.InputMessageContent content =
                new TdApi.InputMessageText(new TdApi.FormattedText(text, null), null, true);

        TdApi.SendMessage req = new TdApi.SendMessage(
                chatId,
                null,
                null,
                null,
                null,
                content
        );

        clientActorRef.tell(new TdMessages.Send(req));
    }

    @Override
    protected AbstractActor createActor() {
        return new ChatActor();
    }

    private static class RequestOlder {}

    private final class ChatActor extends BaseUiActor {

        private static final long REQ_CHAT_INFO = 9001L;

        private final HashMap<Long, MessageItem> byId = new HashMap<>();
        private final HashMap<Long, TdApi.Message> rawMessages = new HashMap<>();
        private final HashMap<Long, Long> albumGroups = new HashMap<>();
        private final HashMap<Long, String> userNameCache = new HashMap<>();
        private final MessageUiMapper uiMapper = new MessageUiMapper(
                0,
                userNameCache::get,
                this::onUserLoaded
        );

        private boolean loading = false;
        private boolean hasMore = true;
        private long oldestId = 0;

        @Override
        public void onReceive(Object message) {
            super.onReceive(message);

            if (message instanceof com.github.borz7zy.telegramm.actor.ActorRef) {
                if (clientActorRef != null) {
                    clientActorRef.tell(new TdMessages.Send(new TdApi.OpenChat(chatId)));
                    requestChatInfo();
                    requestInitial();
                }
            }
        }

        @Override
        protected void onReceiveMessage(Object message) {
            if (message instanceof RequestOlder) {
                requestOlder();
                return;
            }

            if (message instanceof TdMessages.ChatHistoryLoaded) {
                onHistory(((TdMessages.ChatHistoryLoaded) message).messages);
                return;
            }

            if (message instanceof TdMessages.TdUpdate) {
                handleUpdate(((TdMessages.TdUpdate) message).object);
            }

            if (message instanceof TdMessages.ResultWithId r) {
                if (r.requestId == REQ_CHAT_INFO && r.result instanceof TdApi.Chat chat) {
                    applyChatHeader(chat);
                }
                return;
            }
        }

        private void requestInitial() {
            if (loading) return;
            loading = true;
            hasMore = true;
            oldestId = 0;
            byId.clear();
            if (clientActorRef != null) {
                clientActorRef.tell(new TdMessages.GetChatHistory(chatId, 0L, 0, 50, self()));
            }
        }

        private void requestChatInfo() {
            if (clientActorRef != null) {
                clientActorRef.tell(new TdMessages.SendWithId(REQ_CHAT_INFO, new TdApi.GetChat(chatId), self()));
            }
        }

        private void requestOlder() {
            if (loading || !hasMore) return;
            if (oldestId == 0) return;

            loading = true;
            if (clientActorRef != null) {
                clientActorRef.tell(new TdMessages.GetChatHistory(chatId, oldestId, -1, 50, self()));
            }
        }

        private void onHistory(TdApi.Messages messages) {
            loading = false;

            if (messages == null || messages.messages == null || messages.messages.length == 0) {
                hasMore = false;
                return;
            }

            for (TdApi.Message m : messages.messages) {
                if (m == null || m.chatId != chatId) continue;

                processMessageAndPut(m);

                if (oldestId == 0 || m.id < oldestId) oldestId = m.id;
            }

            publishSorted(false);
        }

        private void handleUpdate(TdApi.Object update) {
            if (update instanceof TdApi.UpdateNewMessage u) {
                TdApi.Message m = u.message;
                if (m != null && m.chatId == chatId) {
                    processMessageAndPut(m);
                    publishSorted(true);
                }
            }
            else if (update instanceof TdApi.UpdateMessageSendSucceeded u) {
                if (u.message != null) {
                    if (u.message.chatId != chatId) return;

                    byId.remove(u.oldMessageId);
                    byId.put(u.message.id, toItem(u.message));
                    publishSorted(true);
                }
            }
            else if (update instanceof TdApi.UpdateDeleteMessages u) {
                if (u.chatId != chatId) return;
                for (long id : u.messageIds) {
                    byId.remove(id);
                    rawMessages.remove(id);
                }
                publishSorted(false);
            }else if (update instanceof TdApi.UpdateChatPhoto u) {
                if (u.chatId == chatId) applyChatHeaderPhoto(u.photo);
            }
            else if (update instanceof TdApi.UpdateChatTitle u) {
                if (u.chatId == chatId) {
                    title = u.title;
                    if (isAdded()) requireActivity().runOnUiThread(() -> {
                        TextView tvTitle = content.findViewById(R.id.tv_title);
                        tvTitle.setText(u.title);
                    });
                }
            }else if(update instanceof TdApi.UpdateChatAction uca){
                if(uca.chatId == chatId){
                    if(uca.action != null){
                        TdApi.ChatAction ca = uca.action;
                        switch (ca.getConstructor()) {
                            case TdApi.ChatActionTyping.CONSTRUCTOR -> { showTyping("печатает…"); }
                            case TdApi.ChatActionRecordingVideo.CONSTRUCTOR -> { }
                            case TdApi.ChatActionUploadingVideo.CONSTRUCTOR -> { }
                            case TdApi.ChatActionRecordingVoiceNote.CONSTRUCTOR -> { }
                            case TdApi.ChatActionUploadingVoiceNote.CONSTRUCTOR -> { }
                            case TdApi.ChatActionUploadingPhoto.CONSTRUCTOR -> { }
                            case TdApi.ChatActionUploadingDocument.CONSTRUCTOR -> { }
                            case TdApi.ChatActionChoosingSticker.CONSTRUCTOR -> { }
                            case TdApi.ChatActionChoosingLocation.CONSTRUCTOR -> { }
                            case TdApi.ChatActionChoosingContact.CONSTRUCTOR -> { }
                            case TdApi.ChatActionStartPlayingGame.CONSTRUCTOR -> { }
                            case TdApi.ChatActionRecordingVideoNote.CONSTRUCTOR -> { }
                            case TdApi.ChatActionUploadingVideoNote.CONSTRUCTOR -> { }
                            case TdApi.ChatActionWatchingAnimations.CONSTRUCTOR -> { }
                            case TdApi.ChatActionCancel.CONSTRUCTOR -> { hideTyping(); }
                        }
                    }
                }
            }
        }

        private void showTyping(String text) {
            if (!isAdded() || typingDrawable == null) return;

            TextView tv = typingBar.findViewById(R.id.typing_text);
            if (tv != null) tv.setText(text);

            typingBar.setVisibility(View.VISIBLE);
            typingDrawable.start();
        }

        private void hideTyping() {
            if (typingDrawable != null) {
                typingDrawable.stop();
            }
            if (typingBar != null) {
                typingBar.setVisibility(View.GONE);
            }
        }

        private void applyChatHeader(TdApi.Chat chat) {
            if (!isAdded()) return;

            title = chat.title;

            requireActivity().runOnUiThread(() -> {
                TextView tvTitle = content.findViewById(R.id.tv_title);
                tvTitle.setText(chat.title);

                applyChatAvatar(chat.photo);
            });
        }

        private void applyChatHeaderPhoto(TdApi.ChatPhotoInfo photo) {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> applyChatAvatar(photo));
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

            TdMediaRepository.get().getPathOrRequest(fid, p -> {
                Object cur = ivChatAvatar.getTag();
                if (!(cur instanceof String) || !tag.equals(cur)) return;
                if (TextUtils.isEmpty(p)) return;

                Glide.with(ivChatAvatar)
                        .load(p)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.bg_badge)
                        .error(R.drawable.bg_badge)
                        .into(ivChatAvatar);
            });
        }


        private void processMessageAndPut(TdApi.Message m) {
            rawMessages.put(m.id, m);

            long albumId = m.mediaAlbumId;

            PhotoData photoData = null;
            String caption = "";

            if (m.content instanceof TdApi.MessagePhoto photoContent) {
                photoData = extractPhoto(photoContent.photo);
                if (photoContent.caption != null && !TextUtils.isEmpty(photoContent.caption.text)) {
                    caption = photoContent.caption.text;
                }
            }

            if (albumId != 0 && albumGroups.containsKey(albumId)) {
                long mainMessageId = albumGroups.get(albumId);
                MessageItem existingItem = byId.get(mainMessageId);

                if (existingItem != null
                        && photoData != null
                        && existingItem.ui instanceof UiContent.Media) {

                    boolean alreadyExists = false;
                    for (PhotoData p : existingItem.photos) {
                        if (p.localPath != null && p.localPath.equals(photoData.localPath)) {
                            alreadyExists = true;
                            break;
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

        private void publishSorted(boolean maybeScrollToBottom) {
            ArrayList<MessageItem> list = new ArrayList<>(byId.values());
            list.sort((a, b) -> Long.compare(a.id, b.id));

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                boolean nearBottom = isNearBottom();
                adapter.submitList(list, () -> {
                    if (maybeScrollToBottom && nearBottom) {
                        rv.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                    }
                    if (adapter.getItemCount() > 0 && lm.findLastVisibleItemPosition() < 0) {
                        rv.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            });
        }

        private boolean isNearBottom() {
            int last = lm.findLastVisibleItemPosition();
            int total = adapter.getItemCount();
            return total == 0 || last >= total - 3;
        }

        private MessageItem toItem(TdApi.Message m) {
            long senderId = 0;
            if(m.senderId instanceof TdApi.MessageSenderUser msu) {
                senderId = msu.userId;
            }

            UiContent ui = uiMapper.map(m.content, senderId);
            String time = formatTime(m.date);

            List<PhotoData> photos = new ArrayList<>();
            if (m.content instanceof TdApi.MessagePhoto photoContent) {
                PhotoData pd = extractPhoto(photoContent.photo);
                if (pd != null) photos.add(pd);
            }

            return new MessageItem(
                    m.id, m.chatId, m.isOutgoing,
                    time, photos, m.mediaAlbumId,
                    ui
            );
        }

        private PhotoData extractPhoto(TdApi.Photo photo) {
            if (photo == null || photo.sizes.length == 0) return null;

            TdApi.PhotoSize best = findBestPhotoSize(photo.sizes);

            int fileId = best.photo.id;
            String localPath = best.photo.local != null ? best.photo.local.path : null;

            if (TextUtils.isEmpty(localPath) || (best.photo.local != null && !best.photo.local.isDownloadingCompleted)) {
                TdMediaRepository.get().getPathOrRequest(fileId, p -> {  });
                localPath = TdMediaRepository.get().getCachedPath(fileId);
            }

            return new PhotoData(fileId, localPath, best.width, best.height);
        }

        private TdApi.PhotoSize findBestPhotoSize(TdApi.PhotoSize[] sizes) {
            TdApi.PhotoSize sizeX = null;
            TdApi.PhotoSize sizeY = null;
            TdApi.PhotoSize sizeM = null;

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

        private String formatTime(int unixSeconds) {
            long ms = unixSeconds * 1000L;
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
        }

        private void onUserLoaded(TdApi.User user) {
            String fullName = (user.firstName + " " + user.lastName).trim();
            if (fullName.isEmpty()) fullName = "Deleted Account";

            userNameCache.put(user.id, fullName);

            boolean needUpdate = false;

            for (TdApi.Message msg : rawMessages.values()) {
                long senderId = getSenderId(msg);

                if (senderId == user.id) {
                    MessageItem updatedItem = toItem(msg);

                    byId.put(msg.id, updatedItem);
                    needUpdate = true;
                }
            }

            if (needUpdate) {
                publishSorted(false);
            }
        }

        private long getSenderId(TdApi.Message msg) {
            if (msg.senderId instanceof TdApi.MessageSenderUser) {
                return ((TdApi.MessageSenderUser) msg.senderId).userId;
            }
            return 0;
        }
    }

    @Override
    public void onDestroyView() {
        closing = false;
        if (clientActorRef != null) {
            clientActorRef.tell(new TdMessages.Send(new TdApi.CloseChat(chatId)));
        }
        super.onDestroyView();
    }

    private void applyInsets(View root, View content) {
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
            int bottom = Math.max(nav.bottom, ime.bottom);

            header.setPadding(
                    headerBaseLeft,
                    headerBaseTop + status.top,
                    headerBaseRight,
                    headerBaseBottom
            );

            inputBar.setPadding(
                    inputBaseLeft,
                    inputBaseTop,
                    inputBaseRight,
                    inputBaseBottom + bottom
            );

            rv.setPadding(
                    rvBaseLeft,
                    rvBaseTop + status.top,
                    rvBaseRight,
                    rvBaseBottom + bottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
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

        if (edge != null) {
            edge.animateDismiss();
        } else {
            ChatFragment.super.dismissAllowingStateLoss();
        }
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
}
