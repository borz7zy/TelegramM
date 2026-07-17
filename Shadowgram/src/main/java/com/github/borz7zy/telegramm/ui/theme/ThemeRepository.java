package com.github.borz7zy.telegramm.ui.theme;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Account-scoped theme cache.
 *
 * <p>Maintains the global list of {@link TdApi.EmojiChatTheme} (received via
 * {@code UpdateEmojiChatThemes}) and per-chat theme/background overrides
 * (received via {@code UpdateChatTheme} and {@code UpdateChatBackground}).
 * Consumers observe state through {@link #getEmojiThemes()} and
 * {@link #observeChat(long)}; the {@link ChatThemeState} they receive is the
 * raw TDLib data — Monet color derivation happens in the caller via
 * {@code ThemeEngine.Theme.forSeed} / {@code forBitmap}.
 */
public final class ThemeRepository {

    private static final ThemeRepository INSTANCE = new ThemeRepository();
    public static ThemeRepository get() { return INSTANCE; }

    private volatile int currentAccountId = -1;

    private final MutableLiveData<List<TdApi.EmojiChatTheme>> emojiThemes =
            new MutableLiveData<>(Collections.emptyList());
    private final Map<String, TdApi.EmojiChatTheme> emojiThemesByName = new ConcurrentHashMap<>();

    /** Per-chat resolved theme name (chatId → ChatTheme). Empty when the chat uses default. */
    private final ConcurrentHashMap<Long, TdApi.ChatTheme> chatThemes = new ConcurrentHashMap<>();
    /** Per-chat custom background; takes precedence over the theme background. */
    private final ConcurrentHashMap<Long, TdApi.ChatBackground> chatBackgrounds = new ConcurrentHashMap<>();

    /** Account-wide default chat backgrounds (delivered via UpdateDefaultBackground). */
    @Nullable private volatile TdApi.Background defaultBackgroundDark;
    @Nullable private volatile TdApi.Background defaultBackgroundLight;

    /**
     * LiveData per chat. Lazily created on first {@link #observeChat(long)}; held weakly enough
     * that the JVM can drop unused entries via the standard GC of the MutableLiveData itself.
     */
    private final ConcurrentHashMap<Long, MutableLiveData<ChatThemeState>> chatStates =
            new ConcurrentHashMap<>();

    private final Client.ResultHandler updateHandler = this::onUpdate;

    private ThemeRepository() {}

    public void setCurrentAccountId(int accountId) {
        if (this.currentAccountId == accountId) return;

        if (this.currentAccountId != -1) {
            AccountSession old = AccountManager.getInstance().getSession(this.currentAccountId);
            if (old != null) old.removeUpdateHandler(updateHandler);
        }

        // Wipe per-account caches; theme list and chat metadata are
        // account-specific and must not leak across logins.
        this.currentAccountId = accountId;
        emojiThemesByName.clear();
        emojiThemes.postValue(Collections.emptyList());
        chatThemes.clear();
        chatBackgrounds.clear();
        defaultBackgroundDark = null;
        defaultBackgroundLight = null;
        for (MutableLiveData<ChatThemeState> ld : chatStates.values()) {
            ld.postValue(ChatThemeState.empty());
        }

        AccountSession next = AccountManager.getInstance().getSession(accountId);
        if (next != null) {
            next.addUpdateHandler(updateHandler);
        }
    }

    public LiveData<List<TdApi.EmojiChatTheme>> getEmojiThemes() {
        return emojiThemes;
    }

    @Nullable
    public TdApi.EmojiChatTheme findThemeByName(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        return emojiThemesByName.get(name);
    }

    /**
     * Subscribe to per-chat theme + background state. The returned LiveData
     * starts with whatever has been seen so far (may be empty if the chat
     * hasn't been fetched yet — in which case the caller should also pass
     * the {@link TdApi.Chat} object via {@link #seedChat(TdApi.Chat)}).
     */
    @MainThread
    public LiveData<ChatThemeState> observeChat(long chatId) {
        return chatStates.computeIfAbsent(chatId, id ->
                new MutableLiveData<>(buildState(id)));
    }

    /**
     * Seed initial per-chat state from a freshly fetched {@link TdApi.Chat}.
     * Idempotent — only writes fields not already present.
     */
    public void seedChat(@Nullable TdApi.Chat chat) {
        if (chat == null) return;
        boolean changed = false;
        if (chat.theme != null && chatThemes.put(chat.id, chat.theme) == null) {
            changed = true;
        }
        if (chat.background != null && chatBackgrounds.put(chat.id, chat.background) == null) {
            changed = true;
        }
        if (changed) emitChatState(chat.id);
    }

    private void onUpdate(TdApi.Object object) {
        if (object instanceof TdApi.UpdateEmojiChatThemes) {
            TdApi.EmojiChatTheme[] arr = ((TdApi.UpdateEmojiChatThemes) object).chatThemes;
            Map<String, TdApi.EmojiChatTheme> ordered = new LinkedHashMap<>();
            List<TdApi.EmojiChatTheme> list = new ArrayList<>(arr == null ? 0 : arr.length);
            if (arr != null) {
                for (TdApi.EmojiChatTheme t : arr) {
                    if (t == null || t.name == null) continue;
                    ordered.put(t.name, t);
                    list.add(t);
                }
            }
            emojiThemesByName.clear();
            emojiThemesByName.putAll(ordered);
            emojiThemes.postValue(list);
            // A theme refresh might re-resolve every chat's settings, so notify all watchers.
            for (Long chatId : chatStates.keySet()) emitChatState(chatId);
        } else if (object instanceof TdApi.UpdateChatTheme) {
            TdApi.UpdateChatTheme u = (TdApi.UpdateChatTheme) object;
            if (u.theme == null) chatThemes.remove(u.chatId);
            else chatThemes.put(u.chatId, u.theme);
            emitChatState(u.chatId);
        } else if (object instanceof TdApi.UpdateChatBackground) {
            TdApi.UpdateChatBackground u = (TdApi.UpdateChatBackground) object;
            if (u.background == null) chatBackgrounds.remove(u.chatId);
            else chatBackgrounds.put(u.chatId, u.background);
            emitChatState(u.chatId);
        } else if (object instanceof TdApi.UpdateDefaultBackground) {
            TdApi.UpdateDefaultBackground u = (TdApi.UpdateDefaultBackground) object;
            if (u.forDarkTheme) defaultBackgroundDark = u.background;
            else defaultBackgroundLight = u.background;
            // Chats that don't have a per-chat override now have a default
            // wallpaper to fall back on — re-emit so observers repaint.
            for (Long chatId : chatStates.keySet()) emitChatState(chatId);
        }
    }

    /**
     * Returns the user's default chat background for the requested mode.
     * Comes from {@link TdApi.UpdateDefaultBackground} and is null until
     * TDLib delivers it (or the user has none configured).
     */
    @Nullable
    public TdApi.Background getDefaultBackground(boolean isDark) {
        return isDark ? defaultBackgroundDark : defaultBackgroundLight;
    }

    private void emitChatState(long chatId) {
        MutableLiveData<ChatThemeState> ld = chatStates.get(chatId);
        if (ld == null) return;
        ld.postValue(buildState(chatId));
    }

    private ChatThemeState buildState(long chatId) {
        TdApi.ChatTheme themeRef = chatThemes.get(chatId);
        TdApi.ChatBackground bg = chatBackgrounds.get(chatId);

        String name = null;
        if (themeRef instanceof TdApi.ChatThemeEmoji) {
            name = ((TdApi.ChatThemeEmoji) themeRef).name;
        }
        TdApi.EmojiChatTheme resolvedEmoji = (name != null) ? emojiThemesByName.get(name) : null;

        return new ChatThemeState(name, resolvedEmoji, bg);
    }

    /** Snapshot of one chat's theme overrides as exposed to UI. */
    public static final class ChatThemeState {
        /** Theme name set on the chat (null = use global). */
        @Nullable public final String themeName;
        /** Resolved EmojiChatTheme; may be null while {@link UpdateEmojiChatThemes} hasn't arrived yet. */
        @Nullable public final TdApi.EmojiChatTheme theme;
        /** Per-chat background override; null = use {@link #theme} background or app default. */
        @Nullable public final TdApi.ChatBackground background;

        ChatThemeState(@Nullable String themeName,
                       @Nullable TdApi.EmojiChatTheme theme,
                       @Nullable TdApi.ChatBackground background) {
            this.themeName = themeName;
            this.theme = theme;
            this.background = background;
        }

        static ChatThemeState empty() {
            return new ChatThemeState(null, null, null);
        }

        public boolean isEmpty() {
            return themeName == null && theme == null && background == null;
        }
    }
}