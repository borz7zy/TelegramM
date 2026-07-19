package com.github.borz7zy.telegramm.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.borz7zy.shadowgram.shadowgramui.R;
import com.github.borz7zy.telegramm.ui.MainViewModel;
import com.github.borz7zy.telegramm.ui.theme.ThemePickerSheet;

public class SettingsFragment extends Fragment {

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

//    private SettingsViewModel viewModel;

    private MainViewModel mainViewModel;
    private LinearLayout settingsRoot;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: Use the ViewModel
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        applyInsetsPadding();

        FrameLayout rootFrame = view.findViewById(R.id.root_container);
        settingsRoot = createSettingsGroup(requireContext());
        rootFrame.addView(settingsRoot);
    }

    private void applyInsetsPadding() {
        mainViewModel.getTopInset().observe(getViewLifecycleOwner(), topInset -> {
            if (topInset != null) {
                settingsRoot.setPadding(
                        settingsRoot.getPaddingLeft(),
                        topInset,
                        settingsRoot.getPaddingRight(),
                        settingsRoot.getPaddingBottom()
                );
            }
        });

        mainViewModel.getBottomInset().observe(getViewLifecycleOwner(), bottomInset -> {
            if (bottomInset != null) {
                settingsRoot.setPadding(
                        settingsRoot.getPaddingLeft(),
                        settingsRoot.getPaddingTop(),
                        settingsRoot.getPaddingRight(),
                        bottomInset
                );
            }
        });
    }

    private LinearLayout createSettingsGroup(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.setId(R.id.root_linear_settings);

        TextView title = new TextView(context);
        title.setText("Настройки"); // TODO
        title.setTextSize(14f);
        title.setAlpha(0.6f);
        root.addView(title);

        // TODO:
        root.addView(createSettingsItem(context, R.drawable.ic_message_outline, "Настройки чатов", R.id.chat_settings));
        root.addView(createSettingsItem(context, R.drawable.ic_lock_outline, "Конфиденциальность", R.id.confidentiality_settings));
        root.addView(createSettingsItem(context, R.drawable.ic_notification_outline, "Уведомления и звук", R.id.notifications_settings));
        root.addView(createSettingsItem(context, R.drawable.ic_document_outline, "Данные и память", R.id.datastorage_settings));
        root.addView(createSettingsItem(context, R.drawable.ic_lightbulb_outline, "Энергосбережение", R.id.energy_settings));
        root.addView(createSettingsItem(context, R.drawable.ic_folder_outline, "Папки с чатами", R.id.folders_settings));
        root.addView(createSettingsItem(context, R.drawable.ic_devices_outline, "Устройства", R.id.devices_settings));
        root.addView(createSettingsItem(context, R.drawable.ic_hieroglyph_character_outline, "Язык", R.id.lang_settings));

        LinearLayout themeItem = createSettingsItem(context, R.drawable.ic_lightbulb_outline, "Тема оформления", View.NO_ID);
        themeItem.setOnClickListener(v -> ThemePickerSheet.showGlobal(getParentFragmentManager()));
        root.addView(themeItem);

        return root;
    }

    private LinearLayout createSettingsItem(
            Context context,
            @DrawableRes int iconRes,
            String textValue,
            int idInt
    ) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        item.setPadding(0, 24, 0, 24);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setId(idInt);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(48, 48);
        iconParams.setMarginEnd(32);
        icon.setLayoutParams(iconParams);

        TextView text = new TextView(context);
        text.setText(textValue);
        text.setTextSize(16f);

        item.addView(icon);
        item.addView(text);

        return item;
    }
}
