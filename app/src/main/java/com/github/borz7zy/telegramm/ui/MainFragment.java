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
import com.github.borz7zy.telegramm.ui.contacts.ContactsFragment;
import com.github.borz7zy.telegramm.ui.dialogs.DialogsFragment;
import com.github.borz7zy.telegramm.ui.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class MainFragment extends Fragment {

    private final float BLUR_RADIUS = 20.f;

    private BottomNavigationView bottomNav;
    private FragmentContainerView fragmentContainer;
    private BlurView header;
    private TextView headerTitle;
    private BlurView bottomNavView;
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
        switch (from) {
            case DIALOGS:
                if (to == Screen.CONTACTS)
                    return new int[]{R.anim.nav_pop_enter, R.anim.nav_pop_exit};
                if (to == Screen.SETTINGS)
                    return new int[]{R.anim.nav_enter, R.anim.nav_exit};
                break;

            case CONTACTS:
                if (to == Screen.DIALOGS)
                    return new int[]{R.anim.nav_enter, R.anim.nav_exit};
                if (to == Screen.SETTINGS)
                    return new int[]{R.anim.nav_enter, R.anim.nav_exit};
                break;

            case SETTINGS:
                if (to == Screen.DIALOGS)
                    return new int[]{R.anim.nav_pop_enter, R.anim.nav_pop_exit};
                if (to == Screen.CONTACTS)
                    return new int[]{R.anim.nav_pop_enter, R.anim.nav_pop_exit};
                break;
        }
        return null;
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

        bottomNav = view.findViewById(R.id.bottom_nav_view);
        headerTitle = view.findViewById(R.id.header_title);

        buttonSearch = view.findViewById(R.id.btn_search);

        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupBlur(view);

        dialogsFragment = getChildFragmentManager().findFragmentByTag("dialogs");
        contactsFragment = getChildFragmentManager().findFragmentByTag("contacts");
        settingsFragment = getChildFragmentManager().findFragmentByTag("settings");

        bottomNav.setOnItemSelectedListener(item->{
            mainViewModel.setCurrentTab(item.getItemId());
            return true;
        });

        mainViewModel.getCurrentTab().observe(getViewLifecycleOwner(), this::switchToFragment);

        if(savedInstanceState == null){
            mainViewModel.setCurrentTab(R.id.nav_chats);
            bottomNav.setSelectedItemId(R.id.nav_chats);
        }else{
            switchToFragment(mainViewModel.getCurrentTab().getValue() != null ?
                    mainViewModel.getCurrentTab().getValue() : R.id.nav_chats
            );
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

            bottomNav.setItemIconTintList(iconColor);
            bottomNav.setItemTextColor(textColor);
        });
    }

    private void switchToFragment(int id){
        Fragment target = null;
        if (id == R.id.nav_chats) {
            if (dialogsFragment == null)
                dialogsFragment = new DialogsFragment();
            target = dialogsFragment;
        }else if (id == R.id.nav_contacts) {
            if (contactsFragment == null)
                contactsFragment = new ContactsFragment();
            target = contactsFragment;
        }else if (id == R.id.nav_settings){
            if(settingsFragment == null)
                settingsFragment = new SettingsFragment();
            target = settingsFragment;
        }

        if (target == null || target == currentFragment)
            return;

        var transaction = getChildFragmentManager().beginTransaction();

        final Screen from = getScreen(currentFragment);
        final Screen to = getScreen(target);

        final int[] anim = getAnimation(from, to);

        if(anim != null)
            transaction.setCustomAnimations(anim[0], anim[1]);

        if (currentFragment != null)
            transaction.hide(currentFragment);

        if (target.isAdded())
            transaction.show(target);
        else {
            String tag = "dialogs";
            if(target instanceof DialogsFragment){
                tag = "dialogs";
            }else if (target instanceof ContactsFragment){
                tag = "contacts";
            }else if (target instanceof SettingsFragment){
                tag = "settings";
            }
            transaction.add(R.id.fragment_container, target, tag);
        }

        transaction.commit();

        currentFragment = target;
    }

    private Screen getScreen(Fragment f) {
        if (f instanceof DialogsFragment) return Screen.DIALOGS;
        if (f instanceof ContactsFragment) return Screen.CONTACTS;
        if (f instanceof SettingsFragment) return Screen.SETTINGS;
        return null;
    }

    private void setupBlur(View view) {
        BlurTarget target = view.findViewById(R.id.blur_target);

        Drawable bg = requireActivity().getWindow().getDecorView().getBackground();
        if (bg == null) {
            bg = view.getBackground();
        }

        header.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
        bottomNavView.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
    }

    private void applyInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            header.setPadding(0, bars.top, 0, header.getPaddingBottom());
            bottomNavView.setPadding(0, bottomNavView.getPaddingTop(), 0, bars.bottom);

            return insets;
        });
    }

    private void setupInsetListeners() {
        header.addOnLayoutChangeListener((v1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            mainViewModel.getTopInset().setValue(v1.getHeight());
        });

        bottomNavView.addOnLayoutChangeListener((v1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            mainViewModel.getBottomInset().setValue(v1.getHeight());
        });
    }
}