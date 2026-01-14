package com.github.borz7zy.telegramm.ui.chat;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.borz7zy.telegramm.App;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.actor.Props;
import com.github.borz7zy.telegramm.core.StopActor;
import com.github.borz7zy.telegramm.core.TdMessages;
import com.github.borz7zy.telegramm.ui.model.SystemMessages;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class MessageUiMapper {

    private final Context context = App.getApplication().getApplicationContext();
    private final App app = App.getApplication();

    private final ActorRef uiActorRef;
    private final Consumer<TdApi.User> onUserLoaded;

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
        this.userNames = (provider != null) ? provider : (id -> null);
        this.onUserLoaded = onUserLoaded;

        this.uiActorRef = app.getActorSystem().actorOf(
                "message-mapper-" + UUID.randomUUID(),
                Props.of(() -> new UiActor(accountId)).dispatcher("ui")
        );

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

    public void destroy() {
        if (uiActorRef != null) uiActorRef.tell(new StopActor());
    }

    // --------------------
    // DEFAULT HANDLERS
    // --------------------

    private void registerDefaults() {
        register(TdApi.MessageText.class, (t, sid) -> new UiContent.Text(safeText(t.text)));
        register(TdApi.MessagePhoto.class, (p, sid) -> new UiContent.Media(safeText(p.caption)));
        register(TdApi.MessageVideo.class, (v, sid) -> new UiContent.Media(safeText(v.caption)));

        registerStubMedia(TdApi.MessageDocument.class);
        registerStubMedia(TdApi.MessageAudio.class);
        registerStubMedia(TdApi.MessageVoiceNote.class);
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

            String senderName = userNames.name(senderId);
            if (TextUtils.isEmpty(senderName)) {
                senderName = "User";
                uiActorRef.tell(new TdMessages.RequestUserName(senderId));
            }

            int months = x.dayCount/30;
            gift.complete_caption = context.getString(R.string.premium_gave)
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

        LinkedHashSet<Long> uniq = new LinkedHashSet<>();
        for (long id : memberUserIds) uniq.add(id);

        List<String> names = new ArrayList<>();
        for (long id : uniq) {
            String n = userNames.name(id);
            if (TextUtils.isEmpty(n)) {
                uiActorRef.tell(new TdMessages.RequestUserName(id));
            } else {
                names.add(n);
            }
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

    // --------------------
    // INTERNAL ACTOR
    // --------------------

    private final class UiActor extends AbstractActor {

        private final long accountId;
        private ActorRef clientActorRef;

        private final Set<Long> pendingUserIds = new LinkedHashSet<>();

        private final Deque<TdMessages.Send> pendingClientCommands = new ArrayDeque<>();

        UiActor(long accountId) {
            this.accountId = accountId;
        }

        @Override
        public void preStart() throws Exception {
            super.preStart();
            App.getApplication().getAccountManager()
                    .tell(new TdMessages.GetAccount((int) accountId), self());
        }

        @Override
        public void postStop() throws Exception {
            if (clientActorRef != null) clientActorRef.tell(new TdMessages.Unsubscribe(self()));
            super.postStop();
        }

        @Override
        public void onReceive(Object message) throws Exception {
            if (message instanceof StopActor) {
                context().stop(self());
                return;
            }

            if (message instanceof ActorRef) {
                onClientReady((ActorRef) message);
                return;
            }

            if (message instanceof TdMessages.RequestUserName req) {
                requestUser(req.userId);
                return;
            }

            if (message instanceof TdMessages.ResultWithId r) {
                handleResult(r);
                return;
            }

            if (message instanceof TdMessages.TdUpdate tu) {
                handleUpdate(tu.object);
                return;
            }

            if (message instanceof TdMessages.Send s) {
                sendToClient(s);
            }
        }

        private void onClientReady(ActorRef ref) {
            clientActorRef = ref;
            clientActorRef.tell(new TdMessages.Subscribe(self()));

            while (!pendingClientCommands.isEmpty()) {
                clientActorRef.tell(pendingClientCommands.pollFirst());
            }

            for (Long uid : pendingUserIds) {
                clientActorRef.tell(new TdMessages.SendWithId(uid, new TdApi.GetUser(uid), self()));
            }
        }

        private void requestUser(long uid) {
            if (uid == 0) return;

            if (!pendingUserIds.add(uid)) return;

            if (clientActorRef != null) {
                clientActorRef.tell(new TdMessages.SendWithId(uid, new TdApi.GetUser(uid), self()));
            }
        }

        private void handleResult(TdMessages.ResultWithId r) {
            if (r.result instanceof TdApi.User user) {
                onUser(user);
                return;
            }
            if (r.result instanceof TdApi.Error) {
                // requestId == uid
                pendingUserIds.remove(r.requestId);
            }
        }

        private void handleUpdate(TdApi.Object obj) {
            if (obj instanceof TdApi.UpdateUser uu) {
                onUser(uu.user);
            }
        }

        private void onUser(TdApi.User user) {
            pendingUserIds.remove(user.id);
            if (onUserLoaded != null) onUserLoaded.accept(user);
        }

        private void sendToClient(TdMessages.Send msg) {
            if (clientActorRef != null) {
                clientActorRef.tell(msg);
            } else {
                pendingClientCommands.addLast(msg);
            }
        }
    }
}
