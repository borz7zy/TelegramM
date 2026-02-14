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
import android.widget.Toast;

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
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagingData;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramDialogFragment;
import com.github.borz7zy.telegramm.ui.model.MessageItem;
import com.github.borz7zy.telegramm.ui.widget.EdgeSwipeDismissLayout;
import com.github.borz7zy.telegramm.ui.widget.SpringRecyclerView;
import com.github.borz7zy.telegramm.ui.widget.TypingDrawable;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;
import com.masoudss.lib.WaveformSeekBar;

import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class ChatFragment extends BaseTelegramDialogFragment {

    private static final String ARG_CHAT_ID = "chat_id";
    private static final String ARG_TITLE = "title";
    private final float BLUR_RADIUS = 20.f;

    private long chatId;
    private String title;

    private ChatViewModel viewModel;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View content;
    private ImageView ivChatAvatar;
    private TextView tvTitle;
    private View typingBar;
    private ImageView typingIcon;
    private TypingDrawable typingDrawable;

    private SpringRecyclerView rv;
    private ConcatAdapter concat;
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

    private long pendingAnchorId = -1;
    private int pendingAnchorOffset = 0;
    private boolean isPaginationInProgress = false;
    private boolean suppressAnchorRestore = false;

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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

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

        tvTitle = content.findViewById(R.id.tv_title);
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
        });
        btnAction.setOnClickListener(v -> {
            if (inputMode == InputMode.VOICE) toggleVoicePause();
        });

        topLoading = new TopLoadingAdapter();
        adapter = new ChatAdapter();
        lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);

        adapter.setBtnListener((item, btn) -> viewModel.handleUiClick(item.id, btn));

        adapter.setLoadMoreListener(() -> {
            if (!topLoading.isVisible()) {
                captureScrollAnchor();
            }
            suppressAnchorRestore = true;
            viewModel.loadMore();
        });

        concat = new ConcatAdapter(
                new ConcatAdapter.Config.Builder()
                        .setStableIdMode(ConcatAdapter.Config.StableIdMode.NO_STABLE_IDS)
                        .setIsolateViewTypes(true).build(),
                topLoading,
                adapter
        );

        adapter.addOnPagesUpdatedListener(() -> {
            if (!suppressAnchorRestore && isPaginationInProgress) {
                handlePaginationRestore();
                isPaginationInProgress = false;
            }
            suppressAnchorRestore = false;
            return kotlin.Unit.INSTANCE;
        });

        rv.setLayoutManager(lm);
        rv.setAdapter(concat);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!isPaginationInProgress && !topLoading.isVisible()) {
                    int firstVisible = lm.findFirstVisibleItemPosition();
                    if (firstVisible != RecyclerView.NO_POSITION && firstVisible <= 2) {
                        captureScrollAnchor();
                        viewModel.loadMore();
                    }
                }
            }
        });

        applyInsets(view, content);
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getMessages().observe(getViewLifecycleOwner(), list -> {
            adapter.submitData(getLifecycle(), PagingData.from(list));
        });

        viewModel.getTopLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading) {
                isPaginationInProgress = true;
            }
            topLoading.setVisible(isLoading);
        });

        viewModel.getChatTitle().observe(getViewLifecycleOwner(), t -> {
            if (tvTitle != null) tvTitle.setText(t);
        });

        viewModel.getChatAvatar().observe(getViewLifecycleOwner(), this::applyChatAvatar);

        viewModel.getTypingStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) showTyping(status);
            else hideTyping();
        });

        viewModel.getUiEvents().observe(getViewLifecycleOwner(), event -> {
            if (event instanceof ChatViewModel.UiEvent.ScrollToBottom) {
                if (adapter.getItemCount() == 0) return;

                int lastVisible = lm.findLastCompletelyVisibleItemPosition();
                int totalItems = adapter.getItemCount() - 1;
                int realLastPosition = totalItems + (topLoading.isVisible() ? 1 : 0);

                if (lastVisible >= realLastPosition) {
                    return;
                }

                if (lastVisible >= realLastPosition - 1) {
                    return;
                }

                rv.post(() -> {
                    rv.smoothScrollToPosition(realLastPosition);
                });
            }
            else if (event instanceof ChatViewModel.UiEvent.OpenUrl) {
                String url = ((ChatViewModel.UiEvent.OpenUrl) event).url;
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Error opening link", Toast.LENGTH_SHORT).show();
                }
            }
            else if (event instanceof ChatViewModel.UiEvent.PaginationApplied) {
            }
        });
    }

    @Override
    protected void onAuthorized() {
        if (session != null) {
            viewModel.init(chatId, title, session);
        }
    }

    private void captureScrollAnchor() {
        if (pendingAnchorId != -1) return;

        int firstPos = lm.findFirstVisibleItemPosition();
        if (firstPos == RecyclerView.NO_POSITION) return;

        boolean isLoaderVisible = topLoading.isVisible();

        int checkPos = (isLoaderVisible && firstPos == 0) ? 1 : firstPos;

        View child = lm.findViewByPosition(checkPos);

        int adapterIndex = checkPos - (isLoaderVisible ? 1 : 0);

        if (child != null && adapterIndex >= 0 && adapterIndex < adapter.getItemCount()) {
            List<MessageItem> items = adapter.snapshot().getItems();
            if (adapterIndex < items.size()) {
                MessageItem item = items.get(adapterIndex);
                if (item != null) {
                    pendingAnchorId = item.id;
                    pendingAnchorOffset = child.getTop();
                }
            }
        }
    }

    private void handlePaginationRestore() {
        if (pendingAnchorId == -1) return;
        int newPosInAdapter = findAdapterPositionById(pendingAnchorId);

        if (newPosInAdapter == -1) {
            rv.scrollToPosition(0);
            pendingAnchorId = -1;
            return;
        }

        int offsetHeader = topLoading.isVisible() ? 1 : 0;
        int targetPosition = newPosInAdapter + offsetHeader;

        if (isAtBottom()) {
            pendingAnchorId = -1;
            return;
        }

        lm.scrollToPositionWithOffset(targetPosition, pendingAnchorOffset);
        pendingAnchorId = -1;
        pendingAnchorOffset = 0;
    }

    private boolean isAtBottom() {
        if (lm == null || rv == null) return false;

        int lastVisible = lm.findLastCompletelyVisibleItemPosition();
        if (lastVisible == RecyclerView.NO_POSITION) return false;

        int total = concat.getItemCount() - 1;
        return lastVisible >= total - 1;
    }

    private int findAdapterPositionById(long id) {
        List<MessageItem> items = adapter.snapshot().getItems();
        for (int i = 0; i < items.size(); ++i) {
            if (items.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    private void showTyping(String text) {
        if (!isAdded() || typingDrawable == null) return;
        TextView tv = typingBar.findViewById(R.id.typing_text);
        if (tv != null) tv.setText(text);
        typingBar.setVisibility(View.VISIBLE);
        typingDrawable.start();
    }

    private void hideTyping() {
        if (typingDrawable != null) typingDrawable.stop();
        if (typingBar != null) typingBar.setVisibility(View.GONE);
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
            loadAvatarPath(cached);
            return;
        }

        WeakReference<ImageView> refInfo = new WeakReference<>(ivChatAvatar);
        TdMediaRepository.get().getPathOrRequest(fid, p -> {
            ImageView iv = refInfo.get();
            if (iv == null) return;
            Object cur = iv.getTag();
            if (!(cur instanceof String) || !tag.equals(cur)) return;
            if (TextUtils.isEmpty(p)) return;
            iv.post(() -> loadAvatarPath(p));
        });
    }

    private void loadAvatarPath(String path) {
        if (!isAdded() || ivChatAvatar == null) return;
        Glide.with(ivChatAvatar)
                .load(path)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.bg_badge)
                .error(R.drawable.bg_badge)
                .into(ivChatAvatar);
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

        final int[] statusTop = {0};
        final int[] navBottom = {0};

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            statusTop[0] = status.top;
            navBottom[0] = nav.bottom;

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

    @Override
    public void onDestroyView() {
        closing = false;
        if (inputMode == InputMode.VOICE) {
            cancelVoiceRecording();
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void onSendClicked() {
        if (inputMode == InputMode.VOICE) finishAndSendVoice();
        else {
            String text = et.getText().toString();
            viewModel.sendMessage(text);
            et.setText("");
        }
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
                viewModel.sendVoice(m4a, durationSec, waveform);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "VoiceConvertSend").start();
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
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private static int durationSecFromWav(File wav, int sampleRate, int channels) {
        long bytes = Math.max(0, wav.length() - 44);
        double seconds = bytes / (double) (sampleRate * channels * 2);
        return Math.max(1, (int) Math.ceil(seconds));
    }

    private static void convertWavToM4a(File wav, File m4a, int sampleRate, int channels, int bitRate) throws IOException {
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(wav);
            skipFully(fis, 44);

            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
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
                } else if (outIndex >= 0) {
                    ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                    if (outBuf != null) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                        if (info.size > 0 && muxerStarted) {
                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            muxer.writeSampleData(trackIndex, outBuf, info);
                        }
                        codec.releaseOutputBuffer(outIndex, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    }
                }
            }
        } finally {
            if (codec != null) try { codec.stop(); codec.release(); } catch (Throwable ignored) {}
            if (muxer != null) try { muxer.stop(); muxer.release(); } catch (Throwable ignored) {}
            if (fis != null) try { fis.close(); } catch (Throwable ignored) {}
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

    private static byte[] buildTelegramWaveform5bit(List<Integer> levels, int targetPoints) {
        if (levels == null || levels.isEmpty() || targetPoints <= 0) return new byte[0];
        int n = levels.size();
        int[] p = new int[targetPoints];
        for (int i = 0; i < targetPoints; ++i) {
            int start = (int) ((long) i * n / targetPoints);
            int end = (int) ((long) (i + 1) * n / targetPoints);
            if (end <= start) end = Math.min(start + 1, n);
            long sum = 0;
            int cnt = 0;
            for (int j = start; j < end; ++j) {
                int v = levels.get(j);
                if (v < 0) v = 0; if (v > 100) v = 100;
                sum += v; cnt++;
            }
            int avg = (cnt == 0) ? 0 : (int) (sum / cnt);
            int v5 = Math.round(avg * 31f / 100f);
            if (v5 < 0) v5 = 0; if (v5 > 31) v5 = 31;
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
                    out[(bitPos + b) / 8] |= (byte) (1 << ((bitPos + b) % 8));
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