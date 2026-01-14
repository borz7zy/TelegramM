package com.github.borz7zy.telegramm.ui;

import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
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

    private LayoutViewModel layoutViewModel;

    private BottomNavigationView bottomNav;
    private int currentFragmentId = 0;
    private BlurView header;
    private BlurView bottomNavView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bottomNav = view.findViewById(R.id.bottom_nav_view);

        layoutViewModel = new ViewModelProvider(requireActivity()).get(LayoutViewModel.class);

        setupBlur(view);

        bottomNav.setOnItemSelectedListener(item->{
            Fragment selectedFragment = null;

            int id = item.getItemId();

            if (id == R.id.nav_chats) {
                selectedFragment = new DialogsFragment();
            }else if(id == R.id.nav_contacts){
                selectedFragment = new ContactsFragment();
            }

            if(selectedFragment != null){
                loadFragment(currentFragmentId, id, selectedFragment);
                currentFragmentId = id;
                return true;
            }

            return false;
        });

        if(savedInstanceState == null){
            bottomNav.setSelectedItemId(R.id.nav_chats);
            currentFragmentId = R.id.nav_chats;
        }

        applyInsets(view);
    }

    private void loadFragment(int oldId, int id, Fragment fragment){
        if(oldId == id) return;
        if(oldId == R.id.nav_chats && id == R.id.nav_contacts){
            getChildFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.nav_pop_enter, R.anim.nav_pop_exit)
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }else if(oldId == R.id.nav_contacts && id == R.id.nav_chats) {
            getChildFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit)
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }else{
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    private void setupBlur(View view) {
        BlurTarget target = view.findViewById(R.id.blur_target);
        header = view.findViewById(R.id.header_blur);
        bottomNavView = view.findViewById(R.id.bottom_blur);

        Drawable bg = requireActivity().getWindow().getDecorView().getBackground();
        if (bg == null) {
            bg = view.getBackground();
        }

        header.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
        bottomNavView.setupWith(target).setFrameClearDrawable(bg).setBlurRadius(BLUR_RADIUS);
    }

    private void applyInsets(View view) {
        header = view.findViewById(R.id.header_blur);
        bottomNavView = view.findViewById(R.id.bottom_blur);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            header.setPadding(0, bars.top, 0, header.getPaddingBottom());
            bottomNavView.setPadding(0, bottomNavView.getPaddingTop(), 0, bars.bottom);

            header.addOnLayoutChangeListener((v1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                layoutViewModel.topInset.setValue(v1.getHeight());
            });

            bottomNavView.addOnLayoutChangeListener((v1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                layoutViewModel.bottomInset.setValue(v1.getHeight());
            });

            return insets;
        });
    }
}