package com.github.borz7zy.telegramm.ui;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.ui.chat.ChatViewModel;
import com.github.borz7zy.telegramm.ui.contacts.ContactsFragment;
import com.github.borz7zy.telegramm.ui.dialogs.DialogsFragment;
import com.github.borz7zy.telegramm.ui.settings.SettingsFragment;
import com.github.borz7zy.telegramm.ui.widget.EmojiStatusView;
import com.github.borz7zy.telegramm.ui.widget.LiquidTabBarView;
import com.github.borz7zy.telegramm.utils.EmojiStatusRepository;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class MainFragment extends Fragment {

    private final float BLUR_RADIUS = 20.f;

    private FragmentContainerView fragmentContainer;
    private BlurView header;
    private TextView headerTitle;
    private EmojiStatusView headerEmojiStatus;
    private BlurView bottomNavView;
    private BlurView hiddenPanelBottomView;
    private LiquidTabBarView tabBar;
    private ImageView buttonSearch;
    private MainViewModel mainViewModel;
    private Fragment dialogsFragment;
    private Fragment contactsFragment;
    private Fragment settingsFragment;
    private Fragment currentFragment;

    private enum Screen {
        DIALOGS,
        CONTACTS,
        SETTINGS
    }

    private int[] getAnimation(Screen from, Screen to) {
        if (from == null || to == null) return null;

        int fromIndex = getIndex(from);
        int toIndex = getIndex(to);

        if (toIndex > fromIndex) {
            return new int[]{R.anim.nav_enter, R.anim.nav_exit};
        } else {
            return new int[]{R.anim.nav_enter_left, R.anim.nav_exit};
        }
    }

    private int getIndex(Screen screen) {
        switch (screen) {
            case CONTACTS: return 0;
            case DIALOGS: return 1;
            case SETTINGS: return 2;
            default: return 1;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View root = view.findViewById(R.id.dialogs_root);

        fragmentContainer = view.findViewById(R.id.fragment_container);

        header = view.findViewById(R.id.header_blur);
        bottomNavView = view.findViewById(R.id.bottom_blur);
        hiddenPanelBottomView = view.findViewById(R.id.hidden_panel_blur);

        tabBar = view.findViewById(R.id.liquidTabBar);
        headerTitle = view.findViewById(R.id.header_title);
        headerEmojiStatus = view.findViewById(R.id.header_emoji_status);

        buttonSearch = view.findViewById(R.id.btn_search);

        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupBlur(view);
        observeCurrentUserEmojiStatus();

        dialogsFragment = getChildFragmentManager().findFragmentByTag("dialogs");
        contactsFragment = getChildFragmentManager().findFragmentByTag("contacts");
        settingsFragment = getChildFragmentManager().findFragmentByTag("settings");

        if(savedInstanceState != null){
            int restoredId = savedInstanceState.getInt("current_tab", R.id.nav_chats);
            if (restoredId == R.id.nav_chats) currentFragment = dialogsFragment;
            else if (restoredId == R.id.nav_contacts) currentFragment = contactsFragment;
            else if (restoredId == R.id.nav_settings) currentFragment = settingsFragment;
        }

        tabBar.setOnTabSelectedListener(index->{
            mainViewModel.setCurrentTab(indexToMenuId(index));
        });

        mainViewModel.getCurrentTab().observe(getViewLifecycleOwner(), id->{
            switchToFragment(id);
            fragmentContainer.post(() ->
                    tabBar.setSelectedIndex(menuIdToIndex(id))
            );
        });

        if (savedInstanceState == null) {
            mainViewModel.setCurrentTab(R.id.nav_chats);
        }

        applyInsets(view);
        setupInsetListeners();

        AppManager.getInstance().getThemeEngine().getCurrentTheme().observe(getViewLifecycleOwner(), theme->{
            root.setBackgroundColor(theme.surfaceColor);

            headerTitle.setTextColor(theme.onSurfaceColor);

            buttonSearch.setColorFilter(theme.onSurfaceColor);

            int activeColor = theme.onPrimaryColor;
            int activeTextColor = theme.onSurfaceColor;
            int inactiveColor = theme.onSurfaceColor;

            ColorStateList iconColor = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{
                            activeColor,
                            inactiveColor
                    }
            );

            ColorStateList textColor = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{
                            activeTextColor,
                            inactiveColor
                    }
            );

        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Integer tab = mainViewModel.getCurrentTab().getValue();
        outState.putInt("current_tab", tab != null ? tab : R.id.nav_chats);
    }

    private int indexToMenuId(int index) {
        switch (index) {
            case 0: return R.id.nav_contacts;
            case 1: return R.id.nav_chats;
            case 2: return R.id.nav_settings;
            default: return R.id.nav_chats;
        }
    }

    private int menuIdToIndex(int id) {
        if (id == R.id.nav_contacts) return 0;
        if (id == R.id.nav_chats) return 1;
        if (id == R.id.nav_settings) return 2;
        return 1;
    }

    private void switchToFragment(int id) {
        Fragment target;
        if (id == R.id.nav_chats) {
            if (dialogsFragment == null) dialogsFragment = new DialogsFragment();
            target = dialogsFragment;
        } else if (id == R.id.nav_contacts) {
            if (contactsFragment == null) contactsFragment = new ContactsFragment();
            target = contactsFragment;
        } else if (id == R.id.nav_settings) {
            if (settingsFragment == null) settingsFragment = new SettingsFragment();
            target = settingsFragment;
        } else {
            target = null;
        }

        if (target == null || target == currentFragment) return;

        var transaction = getChildFragmentManager().beginTransaction();
        transaction.setReorderingAllowed(true);

        final Screen from = getScreen(currentFragment);
        final Screen to = getScreen(target);
        final int[] anim = getAnimation(from, to);
        if (anim != null) transaction.setCustomAnimations(anim[0], anim[1]);

        for (Fragment f : new Fragment[]{dialogsFragment, contactsFragment, settingsFragment}) {
            if (f != null && f.isAdded() && f != target) {
                transaction.hide(f);
            }
        }

        if (target.isAdded()) {
            if (target.isHidden()) transaction.show(target);
        } else {
            String tag = getTagForFragment(target);
            transaction.add(R.id.fragment_container, target, tag);
        }

        transaction.commit();

        fragmentContainer.post(() -> {
            View targetView = target.getView();
            if (targetView != null) targetView.bringToFront();
        });

        currentFragment = target;
    }

    private String getTagForFragment(Fragment f) {
        if (f instanceof DialogsFragment) return "dialogs";
        if (f instanceof ContactsFragment) return "contacts";
        if (f instanceof SettingsFragment) return "settings";
        return "unknown";
    }

    private Screen getScreen(Fragment f) {
        if (f instanceof DialogsFragment) return Screen.DIALOGS;
        if (f instanceof ContactsFragment) return Screen.CONTACTS;
        if (f instanceof SettingsFragment) return Screen.SETTINGS;
        return null;
    }

    private void observeCurrentUserEmojiStatus() {
        AccountStorage.getInstance().getCurrentActive(account -> {
            if (account == null) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                AccountSession session = AccountManager.getInstance().getSession(account.getAccountId());
                if (session == null) return;

                TdMediaRepository.get().setCurrentAccountId(account.getAccountId());
                EmojiStatusRepository.get().setCurrentAccountId(account.getAccountId());

                session.getCurrentUserLiveData().observe(getViewLifecycleOwner(), user -> {
                    if (headerEmojiStatus == null) return;
                    long id = user == null ? 0L : ChatViewModel.customEmojiIdFromStatus(user.emojiStatus);
                    headerEmojiStatus.setEmojiStatus(id);
                });
            });
        });
    }

    private void setupBlur(View view) {
        BlurTarget target = view.findViewById(R.id.blur_target);

        Drawable bg = requireActivity().getWindow().getDecorView().getBackground();
        if (bg == null) {
            bg = view.getBackground();
        }

        header.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
        bottomNavView.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
        hiddenPanelBottomView.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
    }

    private void applyInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            header.setPadding(0, bars.top, 0, header.getPaddingBottom());
            bottomNavView.setPadding(0, bottomNavView.getPaddingTop(), 0, bars.bottom);

            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.systemBars(),
                            Insets.of(bars.left, bars.top, bars.right, 0))
                    .build();
        });
    }

    private void setupInsetListeners() {
        header.addOnLayoutChangeListener((v1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            mainViewModel.setTopInset(v1.getHeight());
        });

        bottomNavView.addOnLayoutChangeListener((v1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            mainViewModel.setBottomInset(v1.getHeight());
        });
    }
}