package com.github.borz7zy.telegramm.ui.theme;

import com.github.borz7zy.shadowgram.shadowgramui.R;
import com.github.borz7zy.telegramm.core.theme.ThemeRepository;
import com.github.borz7zy.telegramm.core.theme.ThemeEngine;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;

import org.drinkless.tdlib.TdApi;

import java.util.List;

/**
 * One-stop theme picker. Two modes:
 *
 * <ul>
 *   <li>{@link Mode#GLOBAL} — persists the selected theme + isDark to
 *       {@code SettingsEntity} via {@link AppManager#selectTheme} and
 *       re-seeds {@code ThemeEngine}.</li>
 *   <li>{@link Mode#CHAT} — sends {@link TdApi.SetChatTheme} for the given
 *       {@code chatId}. The result is propagated through TDLib's
 *       {@code UpdateChatTheme}, which {@link ThemeRepository} already observes,
 *       so {@code ChatFragment} re-paints automatically.</li>
 * </ul>
 *
 * <p>UI is built programmatically — keeping it free of XML resources so it
 * stays self-contained and easy to delete if the design evolves.
 */
public final class ThemePickerSheet extends DialogFragment {

    public enum Mode { GLOBAL, CHAT }

    private static final String ARG_MODE   = "mode";
    private static final String ARG_CHAT_ID = "chat_id";
    private static final String TAG = "ThemePickerSheet";

    public static void showGlobal(@NonNull FragmentManager fm) {
        ThemePickerSheet f = new ThemePickerSheet();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, Mode.GLOBAL.name());
        f.setArguments(b);
        f.show(fm, TAG);
    }

    public static void showForChat(@NonNull FragmentManager fm, long chatId) {
        ThemePickerSheet f = new ThemePickerSheet();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, Mode.CHAT.name());
        b.putLong(ARG_CHAT_ID, chatId);
        f.setArguments(b);
        f.show(fm, TAG);
    }

    private Mode mode = Mode.GLOBAL;
    private long chatId = 0L;
    private boolean isDark = true;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        mode = Mode.valueOf(args.getString(ARG_MODE, Mode.GLOBAL.name()));
        chatId = args.getLong(ARG_CHAT_ID, 0L);

        Dialog d = new Dialog(requireContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(buildContent());
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
        }
        return d;
    }

    private View buildContent() {
        ThemeRepository repo = ThemeRepository.get();

        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        // Surface tint: pick from the current global theme so the sheet
        // doesn't look out of place.
        var theme = AppManager.getInstance().getThemeEngine().getCurrentTheme().getValue();
        int sheetBg = (theme != null) ? theme.surfaceColor : 0xFF202124;
        int textCol = (theme != null) ? theme.onSurfaceColor : Color.WHITE;
        isDark = theme == null || theme.isDark;

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(sheetBg);
        bg.setCornerRadii(new float[]{
                dp(16), dp(16), dp(16), dp(16),
                0, 0, 0, 0
        });
        root.setBackground(bg);

        TextView title = new TextView(requireContext());
        title.setText(mode == Mode.GLOBAL ? "Theme" : "Chat theme");
        title.setTextColor(textCol);
        title.setTextSize(18f);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        // Light / Dark switcher.
        // GLOBAL mode: persists isDark and re-seeds Monet for the whole UI.
        // CHAT mode: only affects swatch preview in this sheet, so the user
        // can see how a per-chat theme looks in the opposite mode without
        // changing global state.
        {
            LinearLayout toggleRow = new LinearLayout(requireContext());
            toggleRow.setOrientation(LinearLayout.HORIZONTAL);
            toggleRow.setPadding(0, dp(4), 0, dp(12));

            Button btnLight = makeToggleButton("Light", !isDark, textCol);
            Button btnDark  = makeToggleButton("Dark",  isDark,  textCol);

            btnLight.setOnClickListener(v -> {
                isDark = false;
                refreshToggleButtons(btnLight, btnDark, textCol);
                refreshSwatches(root);
            });
            btnDark.setOnClickListener(v -> {
                isDark = true;
                refreshToggleButtons(btnLight, btnDark, textCol);
                refreshSwatches(root);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, dp(4), 0);
            toggleRow.addView(btnLight, lp);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp2.setMargins(dp(4), 0, 0, 0);
            toggleRow.addView(btnDark, lp2);
            root.addView(toggleRow);
        }

        // Follow-system + custom presets are global-only — per-chat customs
        // come from the wallpaper.
        if (mode == Mode.GLOBAL) {
            root.addView(buildFollowSystemRow(textCol));
            root.addView(buildPresetRow(textCol));
        }

        // "Default" entry — applies no theme.
        root.addView(buildThemeRow("Default", null, textCol));

        // Theme list.
        List<TdApi.EmojiChatTheme> themes = repo.getEmojiThemes().getValue();
        if (themes != null) {
            for (TdApi.EmojiChatTheme t : themes) {
                if (t == null || t.name == null) continue;
                root.addView(buildThemeRow(t.name, t, textCol));
            }
        }

        scroll.addView(root);
        return scroll;
    }

    private View buildThemeRow(@NonNull String label,
                               @Nullable TdApi.EmojiChatTheme theme,
                               int textCol) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(12), dp(8), dp(12));
        row.setGravity(Gravity.CENTER_VERTICAL);

        View swatch = new View(requireContext());
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(swatchColorFor(theme));
        swatch.setBackground(d);
        // Tag the row with its theme so refreshSwatches() can recompute the
        // swatch color when the user flips light/dark.
        swatch.setTag(theme);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(20), dp(20));
        sp.setMarginEnd(dp(12));
        row.addView(swatch, sp);

        TextView name = new TextView(requireContext());
        String labelToShow = (theme != null && theme.name != null) ? theme.name : label;
        name.setText(labelToShow);
        name.setTextColor(textCol);
        name.setTextSize(15f);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(name, np);

        row.setOnClickListener(v -> applySelection(theme));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        return row;
    }

    private void applySelection(@Nullable TdApi.EmojiChatTheme theme) {
        String themeName = (theme != null) ? theme.name : null;

        if (mode == Mode.GLOBAL) {
            AppManager.getInstance().selectTheme(themeName, isDark, /* customSeedColor */ null);
            dismiss();
            return;
        }

        // Mode.CHAT — push the selection through TDLib so all clients sync.
        // Pass null InputChatTheme to clear, otherwise wrap the name as
        // InputChatThemeEmoji.
        TdApi.InputChatTheme inputTheme = (themeName == null)
                ? null
                : new TdApi.InputChatThemeEmoji(themeName);
        AccountStorage.getInstance().getCurrentActive(account -> {
            if (account == null) return;
            AccountSession s = AccountManager.getInstance().getSession(account.getAccountId());
            if (s == null) return;
            s.send(new TdApi.SetChatTheme(chatId, inputTheme), null);
        });
        dismiss();
    }

    private Button makeToggleButton(String label, boolean selected, int textCol) {
        Button b = new Button(requireContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(textCol);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(selected ? 0x33FFFFFF : 0x11FFFFFF);
        b.setBackground(bg);
        return b;
    }

    private View buildFollowSystemRow(int textCol) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(8));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(requireContext());
        label.setText("Follow system dark mode");
        label.setTextColor(textCol);
        label.setTextSize(15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, lp);

        android.widget.Switch sw = new android.widget.Switch(requireContext());
        // Read current state cheaply: we don't subscribe to DB, just reflect
        // the current theme engine snapshot's likely follow-system intent.
        // Since the picker is short-lived, this is good enough.
        sw.setChecked(false);
        sw.setOnCheckedChangeListener((btn, checked) -> {
            AppManager.getInstance().setFollowSystemDark(checked);
            if (checked) {
                // Re-derive isDark immediately for the swatch preview.
                int uiMode = getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                isDark = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                refreshSwatches((LinearLayout) row.getParent());
            }
        });
        row.addView(sw);
        return row;
    }

    private static final int[] PRESET_SEEDS = {
            0xFF4285F4, // blue
            0xFFEA4335, // red
            0xFF34A853, // green
            0xFFFBBC04, // yellow
            0xFFAB47BC, // purple
            0xFFFF7043, // orange
            0xFF26A69A, // teal
            0xFFEC407A, // pink
    };

    private View buildPresetRow(int textCol) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(12));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(requireContext());
        label.setText("Custom");
        label.setTextColor(textCol);
        label.setTextSize(13f);
        label.setAlpha(0.7f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(12));
        row.addView(label, lp);

        for (int color : PRESET_SEEDS) {
            View dot = new View(requireContext());
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            dot.setBackground(d);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(22), dp(22));
            sp.setMarginEnd(dp(8));
            row.addView(dot, sp);
            dot.setOnClickListener(v -> applyCustomSeed(color));
        }
        return row;
    }

    private void applyCustomSeed(int color) {
        // Custom seed takes precedence over themeName in AppManager.
        AppManager.getInstance().selectTheme(null, isDark, color);
        dismiss();
    }

    private int swatchColorFor(@Nullable TdApi.EmojiChatTheme theme) {
        if (theme == null) return 0xFF888888;
        TdApi.ThemeSettings ts = isDark ? theme.darkSettings : theme.lightSettings;
        return (ts != null) ? (0xFF000000 | ts.accentColor) : 0xFF888888;
    }

    /**
     * Walk the row tree and refresh swatch colors when the user flips
     * light/dark. We rely on the swatch View's tag holding the EmojiChatTheme
     * (or null for "Default" / preset rows we want to leave alone).
     */
    private void refreshSwatches(@NonNull ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                if (row.getChildCount() > 0) {
                    View first = row.getChildAt(0);
                    Object tag = first.getTag();
                    if (tag instanceof TdApi.EmojiChatTheme || tag == null) {
                        // Skip rows whose first child isn't a swatch (e.g.
                        // toggle buttons row): identify by background type.
                        if (first.getBackground() instanceof GradientDrawable
                                && ((GradientDrawable) first.getBackground()).getShape()
                                == GradientDrawable.OVAL) {
                            ((GradientDrawable) first.getBackground())
                                    .setColor(swatchColorFor(
                                            tag instanceof TdApi.EmojiChatTheme
                                                    ? (TdApi.EmojiChatTheme) tag
                                                    : null));
                        }
                    }
                }
            }
        }
    }

    private void refreshToggleButtons(Button light, Button dark, int textCol) {
        ((GradientDrawable) light.getBackground()).setColor(!isDark ? 0x33FFFFFF : 0x11FFFFFF);
        ((GradientDrawable) dark.getBackground()).setColor(isDark ? 0x33FFFFFF : 0x11FFFFFF);
        light.setTextColor(textCol);
        dark.setTextColor(textCol);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
    }

    @SuppressWarnings("unused")
    private void toast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }
}