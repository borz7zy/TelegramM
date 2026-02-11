package com.github.borz7zy.telegramm.ui;

import android.graphics.drawable.ColorDrawable;
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

import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.contacts.ContactsFragment;
import com.github.borz7zy.telegramm.ui.dialogs.DialogsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class MainFragment extends Fragment {

    private final float BLUR_RADIUS = 20.f;

    private BottomNavigationView bottomNav;
    private FragmentContainerView fragmentContainer;
    private BlurView header;
    private BlurView bottomNavView;
    private MainViewModel mainViewModel;
    private Fragment dialogsFragment;
    private Fragment contactsFragment;
    private Fragment currentFragment;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bottomNav = view.findViewById(R.id.bottom_nav_view);
        header = view.findViewById(R.id.header_blur);
        bottomNavView = view.findViewById(R.id.bottom_blur);
        fragmentContainer = view.findViewById(R.id.fragment_container);

        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupBlur(view);
        applyInsets(view);

        dialogsFragment = getChildFragmentManager().findFragmentByTag("dialogs");
        contactsFragment = getChildFragmentManager().findFragmentByTag("contacts");

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
        }

        if (target == null || target == currentFragment)
            return;

        var transaction = getChildFragmentManager().beginTransaction();

        if (currentFragment != null)
            transaction.hide(currentFragment);

        if (target.isAdded())
            transaction.show(target);
        else {
            String tag = (target instanceof DialogsFragment) ? "dialogs" : "contacts";
            transaction.add(R.id.fragment_container, target, tag);
        }

        transaction.commit();

        currentFragment = target;
    }

    private void setupBlur(View view) {
        BlurTarget target = view.findViewById(R.id.blur_target);

        Drawable bg = requireActivity().getWindow().getDecorView().getBackground();
        if (bg == null) {
            bg = view.getBackground();
        }

        if (bg == null) {
            bg = new ColorDrawable(0x00000000);
        }

        header.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);

        bottomNavView.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
    }

    private void applyInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            header.setPadding(0, bars.top, 0, header.getPaddingBottom());
            bottomNavView.setPadding(0, bottomNavView.getPaddingTop(), 0, bars.bottom);

            mainViewModel.getTopInset().setValue(header.getHeight() + bars.top);
            mainViewModel.getBottomInset().setValue(bottomNavView.getHeight() + bars.bottom);

            fragmentContainer.setPadding(
                    0,
                    mainViewModel.getTopInset().getValue() != null ? mainViewModel.getTopInset().getValue() : 0,
                    0,
                    mainViewModel.getBottomInset().getValue() != null ? mainViewModel.getBottomInset().getValue() : 0
            );

            return insets;
        });
        setupLayoutObservers();
    }

    private void setupLayoutObservers() {
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            mainViewModel.getTopInset().setValue(v.getHeight());
            fragmentContainer.setPadding(
                    0,
                    v.getHeight(),
                    0,
                    fragmentContainer.getPaddingBottom()
            );
        });

        bottomNavView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            mainViewModel.getBottomInset().setValue(v.getHeight());
            fragmentContainer.setPadding(
                    0,
                    fragmentContainer.getPaddingTop(),
                    0,
                    v.getHeight()
            );
        });
    }
}