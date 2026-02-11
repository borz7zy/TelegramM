package com.github.borz7zy.telegramm.ui.chat;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.borz7zy.telegramm.App;
import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.ui.model.SystemMessages;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class MessageUiMapper {

    private final Context context = AppManager.getInstance().getContext().getApplicationContext();

    private final long accountId;
    private final Consumer<TdApi.User> onUserLoaded;

    private final Set<Long> pendingUserRequests = Collections.synchronizedSet(new HashSet<>());

    public interface Handler<T extends TdApi.MessageContent> {
        UiContent map(T content, long senderId);
    }

    public interface UserNameProvider {
        @Nullable String name(long userId);
    }

    private final Map<Class<?>, Handler<?>> handlers = new HashMap<>();
    private final UserNameProvider userNames;

    private final Handler<TdApi.MessageContent> fallback =
            (c, sid) -> new UiContent.Text("(unsupported: " + c.getClass().getSimpleName() + ")");

    public MessageUiMapper() {
        this(0, null, null);
    }

    public MessageUiMapper(long accountId, UserNameProvider provider, Consumer<TdApi.User> onUserLoaded) {
        this.accountId = accountId;
        this.userNames = (provider != null) ? provider : (id -> null);
        this.onUserLoaded = onUserLoaded;

        registerDefaults();
    }

    public <T extends TdApi.MessageContent> MessageUiMapper register(Class<T> cls, Handler<T> handler) {
        handlers.put(cls, handler);
        return this;
    }

    public UiContent map(TdApi.MessageContent content, long senderId) {
        if (content == null) return new UiContent.Text("");
        Handler<?> h = handlers.get(content.getClass());
        if (h != null) {
            //noinspection unchecked
            return ((Handler<TdApi.MessageContent>) h).map(content, senderId);
        }
        return fallback.map(content, senderId);
    }

    public UiContent map(TdApi.Message message) {
        if (message == null) return new UiContent.Text("");

        long senderId = 0;
        if (message.senderId instanceof TdApi.MessageSenderUser) {
            senderId = ((TdApi.MessageSenderUser) message.senderId).userId;
        } else if (message.senderId instanceof TdApi.MessageSenderChat) {
            senderId = ((TdApi.MessageSenderChat) message.senderId).chatId;
        }

        UiContent uiContent = map(message.content, senderId);

        if (message.replyMarkup != null) {
            injectButtons(uiContent, message.replyMarkup);
        }

        return uiContent;
    }

    public void destroy() {
        pendingUserRequests.clear();
    }

    private void requestUser(long userId) {
        if (userId == 0) return;

        if (pendingUserRequests.contains(userId)) return;

        AccountSession session = null;
        if (accountId != 0) {
            session = AccountManager.getInstance().getSession((int)accountId);
        } else {

        }

        if (session != null) {
            pendingUserRequests.add(userId);
            session.send(new TdApi.GetUser(userId), object -> {
                pendingUserRequests.remove(userId);
                if (object instanceof TdApi.User) {
                    if (onUserLoaded != null) {
                        onUserLoaded.accept((TdApi.User) object);
                    }
                }
            });
        }
    }

    private String getUserNameOrRequest(long userId) {
        String name = userNames.name(userId);
        if (TextUtils.isEmpty(name)) {
            name = App.getApplication().getString(R.string.user);
            requestUser(userId);
        }
        return name;
    }

    // --------------------
    // DEFAULT HANDLERS
    // --------------------

    private void registerDefaults() {
        register(TdApi.MessageText.class, (t, sid) -> {
            return new UiContent.Text(safeText(t.text));
        });
        register(TdApi.MessagePhoto.class, (p, sid) -> new UiContent.Media(safeText(p.caption)));
        register(TdApi.MessageVideo.class, (v, sid) -> new UiContent.Media(safeText(v.caption)));
        register(TdApi.MessageVoiceNote.class, (vn, sid) ->{
            return new UiContent.Text("[x] MessageVoiceNote"); // TODO: implement this
        });

        registerStubMedia(TdApi.MessageDocument.class);
        registerStubMedia(TdApi.MessageAudio.class);
        registerStubMedia(TdApi.MessageAnimation.class);
        registerStubMedia(TdApi.MessageAnimatedEmoji.class);
        register(TdApi.MessageChecklist.class, (a, sid) -> new UiContent.Media("[x] Checklist premium"));
        registerStubMedia(TdApi.MessagePoll.class);

        register(TdApi.MessageSticker.class, (s, sid) -> {
            String emoji = (s.sticker != null) ? s.sticker.emoji : "";
            return new UiContent.Text((TextUtils.isEmpty(emoji) ? "" : emoji + " ") + "sticker");
        });

        // ---------- SYSTEM EVENTS ----------
        register(TdApi.MessageChatChangeTitle.class, (x, sid) -> {
            String full = context.getString(R.string.changed_chat_title).replace("%0", x.title);
            return system(full);
        });

        register(TdApi.MessageChatChangePhoto.class, (x, sid) -> system(context.getString(R.string.chat_change_photo)));
        register(TdApi.MessageChatDeletePhoto.class, (x, sid) -> system(context.getString(R.string.chat_remove_photo)));

        register(TdApi.MessageChatAddMembers.class, (x, sid) ->
                system(buildAddedMembersText(x.memberUserIds)));

        registerSystem(TdApi.MessageChatDeleteMember.class, "removed a member");
        registerSystem(TdApi.MessagePinMessage.class, "pinned a message");
        registerSystem(TdApi.MessageChatSetTheme.class, "changed chat theme");
        registerSystem(TdApi.MessageChatSetBackground.class, "changed chat background");
        registerSystem(TdApi.MessageChatSetMessageAutoDeleteTime.class, "changed auto-delete timer");
        registerSystem(TdApi.MessageChatUpgradeFrom.class, "chat upgraded");
        registerSystem(TdApi.MessageVideoChatStarted.class, "video chat started");
        registerSystem(TdApi.MessageVideoChatEnded.class, "video chat ended");
        registerSystem(TdApi.MessageCall.class, "call");
        registerSystem(TdApi.MessageDice.class, "dice");
        registerSystem(TdApi.MessageForumTopicCreated.class, "topic created");
        registerSystem(TdApi.MessageSupergroupChatCreate.class, "supergroup created");
        registerSystem(TdApi.MessageBasicGroupChatCreate.class, "group created");
        registerSystem(TdApi.MessageChatJoinByLink.class, "joined via link");
        registerSystem(TdApi.MessageChatBoost.class, "chat boosted");
        registerSystem(TdApi.MessageGift.class, "sent a gift");
        registerSystem(TdApi.MessageGiftedStars.class, "gifted stars");
        registerSystem(TdApi.MessageGiftedTon.class, "gifted ton");
        registerSystem(TdApi.MessageGiveaway.class, "giveaway");
        registerSystem(TdApi.MessageGiveawayCreated.class, "giveaway created");
        registerSystem(TdApi.MessageGiveawayWinners.class, "giveaway winners");
        registerSystem(TdApi.MessageGiveawayCompleted.class, "giveaway completed");
        registerSystem(TdApi.MessageInvoice.class, "invoice");
        registerSystem(TdApi.MessagePaymentSuccessful.class, "payment successful");
        registerSystem(TdApi.MessagePaidMessagePriceChanged.class, "paid message price changed");
        registerSystem(TdApi.MessageDirectMessagePriceChanged.class, "direct message price changed");
        registerSystem(TdApi.MessagePaidMedia.class, "paid media");

        register(TdApi.MessageGiftedPremium.class, (x, senderId) -> {
            SystemMessages.PremiumGift gift = new SystemMessages.PremiumGift();
            gift.comment = (x.text != null && x.text.text != null && !x.text.text.isEmpty()) ? x.text.text : "";

            int stickerFileId = 0;
            if (x.sticker != null) {
                if (x.sticker.thumbnail != null && x.sticker.thumbnail.file != null) {
                    stickerFileId = x.sticker.thumbnail.file.id;
                } else if (x.sticker.sticker != null) {
                    stickerFileId = x.sticker.sticker.id;
                }
            }
            gift.stickerFileId = stickerFileId;
            gift.stickerPath = TdMediaRepository.get().getCachedPath(stickerFileId);

            String senderName = getUserNameOrRequest(senderId);
            if (TextUtils.isEmpty(senderName)) {
                senderName = App.getApplication().getString(R.string.user);
            }

            int months = x.dayCount/30;
            gift.complete_caption = context.getString(R.string.premium_gave) // TODO: implement verification from whom to whom
                    .replace("%0", senderName)
                    .replace("%1", String.valueOf(months));

            return new UiContent.System("Telegram Premium", gift);
        });
    }

    private <T extends TdApi.MessageContent> void registerSystem(Class<T> cls, String text) {
        register(cls, (x, sid) -> system(text));
    }

    private <T extends TdApi.MessageContent> void registerStubMedia(Class<T> cls) {
        register(cls, (x, sid) -> new UiContent.Media("[] " + cls.getSimpleName()));
    }

    private UiContent.System system(String text) {
        return new UiContent.System(text, SystemMessages.Default.class);
    }

    private String buildAddedMembersText(long[] memberUserIds) {
        if (memberUserIds == null || memberUserIds.length == 0) return "added members";

        List<Long> uniq = new ArrayList<>();
        for (long id : memberUserIds) {
            if (!uniq.contains(id)) uniq.add(id);
        }

        List<String> names = new ArrayList<>();
        for (long id : uniq) {
            names.add(getUserNameOrRequest(id));
        }

        if (names.isEmpty()) return "added " + uniq.size() + " member" + (uniq.size() == 1 ? "" : "s");

        int max = 3;
        int take = Math.min(max, names.size());
        String joined = TextUtils.join(", ", names.subList(0, take));
        if (names.size() > max) joined += " +" + (names.size() - max);
        return "added " + joined;
    }

    private static String safeText(TdApi.FormattedText ft) {
        return (ft == null || ft.text == null) ? "" : ft.text;
    }

    private void injectButtons(UiContent content, TdApi.ReplyMarkup replyMarkup) {
        if (replyMarkup instanceof TdApi.ReplyMarkupInlineKeyboard keyboard) {

            for (TdApi.InlineKeyboardButton[] row : keyboard.rows) {
                List<UiContent.UiButton> uiRow = new ArrayList<>();

                for (TdApi.InlineKeyboardButton btn : row) {
                    String url = null;
                    byte[] data = null;

                    if (btn.type instanceof TdApi.InlineKeyboardButtonTypeCallback) {
                        data = ((TdApi.InlineKeyboardButtonTypeCallback) btn.type).data;
                    } else if (btn.type instanceof TdApi.InlineKeyboardButtonTypeUrl) {
                        url = ((TdApi.InlineKeyboardButtonTypeUrl) btn.type).url;
                    }

                    uiRow.add(new UiContent.UiButton(btn.text, url, data));
                }

                if (!uiRow.isEmpty()) {
                    content.buttons.add(uiRow);
                }
            }

        } else if (replyMarkup instanceof TdApi.ReplyMarkupShowKeyboard keyboard) {
            for (TdApi.KeyboardButton[] row : keyboard.rows) {
                List<UiContent.UiButton> uiRow = new ArrayList<>();
                for (TdApi.KeyboardButton btn : row) {
                    uiRow.add(new UiContent.UiButton(btn.text, null, null));
                }
                if (!uiRow.isEmpty()) content.buttons.add(uiRow);
            }
        }
    }
}
