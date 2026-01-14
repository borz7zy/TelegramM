package com.github.borz7zy.telegramm.actors;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import com.github.borz7zy.telegramm.App;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.core.TdMessages;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TdClientActor extends AbstractActor {
    private final Context context;
    private final int accountId;
    private Client client;

    private final Set<ActorRef> subscribers = new HashSet<>();

    private TdApi.AuthorizationState lastAuthState;
    private boolean tdlibParametersSent = false;
    private boolean encryptionKeySent = false;

    private final Map<Long, TdApi.Chat> chatCache = new HashMap<>();

    private boolean loadChatsInProgress = false;
    private int newChatsSinceLoadRequest = 0;
    private int loadChatsLoopGuard = 0;

    public TdClientActor(Context context, int accountId) {
        this.context = context;
        this.accountId = accountId;
    }

    @Override
    public void preStart() {
        client = Client.create(object -> self().tell(new TdApiUpdate(object)), null, null);

        client.send(new TdApi.GetAuthorizationState(), result -> self().tell(new TdApiUpdate(result)));
    }

    @Override
    public void postStop() {
        if (client != null) {
            client.send(new TdApi.Close(), null);
        }
    }

    @Override
    public void onReceive(Object message) {

        if (message instanceof TdMessages.Subscribe) {
            ActorRef sub = ((TdMessages.Subscribe) message).subscriber;
            subscribers.add(sub);

            if (lastAuthState != null) {
                sub.tell(new TdMessages.AuthStateChanged(lastAuthState));
            } else {
                client.send(new TdApi.GetAuthorizationState(), r -> self().tell(new TdApiUpdate(r)));
            }

            List<TdApi.Chat> snapshot = buildMainChatListSnapshot();
            if (!snapshot.isEmpty()) {
                sub.tell(new TdMessages.ChatListUpdated(snapshot));
            }

            return;
        }
        else if (message instanceof TdMessages.Unsubscribe) {
            subscribers.remove(((TdMessages.Unsubscribe) message).subscriber);
            return;
        }

        if (message instanceof TdMessages.SendPhone) {
            String phone = ((TdMessages.SendPhone) message).phoneNumber;
            client.send(new TdApi.SetAuthenticationPhoneNumber(phone, null),
                    r -> { if (r instanceof TdApi.Error) self().tell(new TdApiUpdate(r)); });
            return;
        }
        if (message instanceof TdMessages.SendCode) {
            String code = ((TdMessages.SendCode) message).code;
            client.send(new TdApi.CheckAuthenticationCode(code),
                    r -> { if (r instanceof TdApi.Error) self().tell(new TdApiUpdate(r)); });
            return;
        }
        if (message instanceof TdMessages.SendPassword) {
            String pass = ((TdMessages.SendPassword) message).password;
            Log.d("pass", pass);
            client.send(new TdApi.CheckAuthenticationPassword(pass),
                    r -> { if (r instanceof TdApi.Error) self().tell(new TdApiUpdate(r)); });
            return;
        }

        if (message instanceof TdApiUpdate) {
            handleTdObject(((TdApiUpdate) message).object);
            return;
        }

        if (message instanceof TdMessages.LoadChats) {
            if (loadChatsInProgress) return;

            loadChatsInProgress = true;
            newChatsSinceLoadRequest = 0;

            if (loadChatsLoopGuard <= 0) loadChatsLoopGuard = 20;
            loadChatsLoopGuard--;

            client.send(new TdApi.LoadChats(new TdApi.ChatListMain(), 200), result -> {
                self().tell(new LoadChatsDone(result));
            });

            return;
        }

        if (message instanceof LoadChatsDone done) {
            loadChatsInProgress = false;

            if (done.result instanceof TdApi.Error e) {
                if (e.code == 404){
                    return;
                }
                Log.e("TdClient", "LoadChats error: " + ((TdApi.Error) done.result).message);
                return;
            }

            if (newChatsSinceLoadRequest > 0 && loadChatsLoopGuard > 0) {
                self().tell(new TdMessages.LoadChats());
            }
            return;
        }

        if (message instanceof TdMessages.DownloadFile) {
            int fileId = ((TdMessages.DownloadFile) message).fileId;
            int priority = ((TdMessages.DownloadFile) message).priority;
            client.send(new TdApi.DownloadFile(fileId, priority, 0, 0, false), null);
            return;
        }

        if (message instanceof TdMessages.GetChatHistory req) {

            final ActorRef replyTo = (req.replyTo != null) ? req.replyTo : sender();

            client.send(new TdApi.GetChatHistory(req.chatId, req.fromMessageId, req.offset, req.limit, false), result -> {
                if (result instanceof TdApi.Messages) {
                    if (replyTo != null) {
                        replyTo.tell(new TdMessages.ChatHistoryLoaded((TdApi.Messages) result));
                    } else {
                        Log.e("TdClient", "GetChatHistory: replyTo is null, dropped");
                    }
                } else if (result instanceof TdApi.Error) {
                    Log.e("TdClient", "Error loading history: " + ((TdApi.Error) result).message);
                }
            });
        }

        if (message instanceof TdMessages.Send req) {
            client.send(req.query, null);
            return;
        }

        if (message instanceof TdMessages.SendWithId req) {

            final ActorRef replyTo = (req.replyTo != null) ? req.replyTo : sender();

            client.send(req.function, result -> {
                if (replyTo != null) {
                    replyTo.tell(new TdMessages.ResultWithId(req.requestId, result));
                } else {
                    Log.e("TdClient", "SendWithId: replyTo is null, dropped");
                }
            });
        }
    }

    private void handleTdObject(TdApi.Object object) {

        if (object instanceof TdApi.AuthorizationState) {
            processAuthState((TdApi.AuthorizationState) object);
            return;
        }

        if (object instanceof TdApi.UpdateAuthorizationState) {
            processAuthState(((TdApi.UpdateAuthorizationState) object).authorizationState);
            return;
        }

        if (object instanceof TdApi.Error) {
            notifySubscribers(new TdMessages.TdError(((TdApi.Error) object).message));
            return;
        }

        if (object instanceof TdApi.UpdateNewChat) {
            TdApi.Chat chat = ((TdApi.UpdateNewChat) object).chat;
            if (chat != null) {
                chatCache.put(chat.id, chat);
                if (loadChatsInProgress) newChatsSinceLoadRequest++;
            }
        }
        else if (object instanceof TdApi.UpdateChatLastMessage u) {
            TdApi.Chat chat = chatCache.get(u.chatId);
            if (chat != null) {
                chat.lastMessage = u.lastMessage;
                chat.positions = u.positions;
            }
        }
        else if (object instanceof TdApi.UpdateChatPosition u) {
            TdApi.Chat chat = chatCache.get(u.chatId);
            if (chat != null) {
                applyChatPosition(chat, u.position);
            }
        }
        else if (object instanceof TdApi.UpdateChatTitle u) {
            TdApi.Chat chat = chatCache.get(u.chatId);
            if (chat != null) chat.title = u.title;
        }
        else if (object instanceof TdApi.UpdateChatPhoto u) {
            TdApi.Chat chat = chatCache.get(u.chatId);
            if (chat != null) chat.photo = u.photo;
        }

        notifySubscribers(new TdMessages.TdUpdate(object));
    }

    private void processAuthState(TdApi.AuthorizationState state) {
        lastAuthState = state;

        int c = state.getConstructor();

        if (c == TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR) {
            if (!tdlibParametersSent) {
                tdlibParametersSent = true;
                sendTdlibParameters();
            }
            return;
        }

        notifySubscribers(new TdMessages.AuthStateChanged(state));
    }

    private void notifySubscribers(Object msg) {
        for (ActorRef sub : subscribers) sub.tell(msg);
    }

    private void sendTdlibParameters() {
        File rootDir = new File(context.getFilesDir(), "user" + accountId);
        if (!rootDir.exists()) rootDir.mkdirs();

        TdApi.SetTdlibParameters request = new TdApi.SetTdlibParameters();
        request.databaseDirectory = rootDir.getAbsolutePath();

        setIfPresent(request, "filesDirectory", new File(rootDir, "files").getAbsolutePath());
        setIfPresent(request, "useFileDatabase", true);
        setIfPresent(request, "useChatInfoDatabase", true);

        request.useMessageDatabase = true;
        request.useSecretChats = true;
        request.apiId = Integer.parseInt(App.getApplication().getString(R.string.api_id));
        request.apiHash = App.getApplication().getString(R.string.api_hash);
        request.systemLanguageCode = Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        request.deviceModel = Build.MODEL != null ? Build.MANUFACTURER + " " + Build.MODEL : "Virtual Machine on Android";
        final String system = Build.VERSION.BASE_OS.isEmpty() ? "Android " : Build.VERSION.BASE_OS + " ";
        request.systemVersion = system + Build.VERSION.RELEASE + " (SDK: " +Build.VERSION.SDK_INT + ")";
        request.applicationVersion =
                App.getApplication().getString(R.string.version_name) +
                " (" +
                App.getApplication().getString(R.string.version_code) +
                ")";
        request.useTestDc = true;

        client.send(request, null);
    }

    private static void setIfPresent(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getField(fieldName);
            f.set(target, value);
        } catch (Throwable t) {
            Log.i("TdClientActor", "error", t);
        }
    }

    private static class TdApiUpdate {
        final TdApi.Object object;
        TdApiUpdate(TdApi.Object object) { this.object = object; }
    }

    private static class LoadChatsDone {
        final TdApi.Object result;
        LoadChatsDone(TdApi.Object result) { this.result = result; }
    }

    private static long getMainOrder(TdApi.Chat chat) {
        if (chat == null || chat.positions == null) return 0;
        for (TdApi.ChatPosition p : chat.positions) {
            if (p != null && p.list instanceof TdApi.ChatListMain) {
                return p.order;
            }
        }
        return 0;
    }

    private static void applyChatPosition(TdApi.Chat chat, TdApi.ChatPosition newPos) {

        if (chat.positions == null) {
            chat.positions = new TdApi.ChatPosition[]{ newPos };
            return;
        }

        for (int i = 0; i < chat.positions.length; i++) {
            TdApi.ChatPosition p = chat.positions[i];
            if (p != null && p.list != null && newPos != null
                    && p.list.getConstructor() == newPos.list.getConstructor()) {
                chat.positions[i] = newPos;
                return;
            }
        }

        TdApi.ChatPosition[] old = chat.positions;
        TdApi.ChatPosition[] neu = new TdApi.ChatPosition[old.length + 1];
        System.arraycopy(old, 0, neu, 0, old.length);
        neu[old.length] = newPos;
        chat.positions = neu;
    }

    private List<TdApi.Chat> buildMainChatListSnapshot() {
        if (chatCache.isEmpty()) return new ArrayList<>();

        ArrayList<TdApi.Chat> list = new ArrayList<>(chatCache.values());

        list.removeIf(c -> getMainOrder(c) == 0);

        list.sort(Comparator.comparingLong(TdClientActor::getMainOrder).reversed());
        return list;
    }
}
