package com.github.borz7zy.telegramm.core;

import com.github.borz7zy.telegramm.actor.ActorRef;
import org.drinkless.tdlib.TdApi;

import java.util.List;

public class TdMessages {

    public static class ChatListUpdated {
        public final List<TdApi.Chat> chats;
        public ChatListUpdated(List<TdApi.Chat> chats) { this.chats = chats; }
    }

    public static class LoadChats {}

    public static class DownloadFile {
        public final int fileId;
        public final int priority;
        public DownloadFile(int fileId) {
            this.fileId = fileId;
            this.priority = 1;
        }
    }

    public static class GetChatHistory {
        public final long chatId;
        public final long fromMessageId;
        public final int offset;
        public final int limit;
        public final ActorRef replyTo;

        public GetChatHistory(long chatId, long fromMessageId, int offset, int limit, ActorRef replyTo) {
            this.chatId = chatId;
            this.fromMessageId = fromMessageId;
            this.offset = offset;
            this.limit = limit;
            this.replyTo = replyTo;
        }
    }

    public static class ChatHistoryLoaded {
        public final TdApi.Messages messages;
        public ChatHistoryLoaded(TdApi.Messages messages) { this.messages = messages; }
    }

    public static class TdUpdate {
        public final TdApi.Object object;
        public TdUpdate(TdApi.Object object) { this.object = object; }
    }

    public static class Send {
        public final TdApi.Function query;
        public Send(TdApi.Function query) { this.query = query; }
    }

    public static class GetAccount {
        public final int accountId;
        public GetAccount(int accountId) { this.accountId = accountId; }
    }

    public static class Subscribe {
        public final ActorRef subscriber;
        public Subscribe(ActorRef subscriber) { this.subscriber = subscriber; }
    }

    public static class Unsubscribe {
        public final ActorRef subscriber;
        public Unsubscribe(ActorRef subscriber) { this.subscriber = subscriber; }
    }

    public static class SendPhone {
        public final String phoneNumber;
        public SendPhone(String phoneNumber) { this.phoneNumber = phoneNumber; }
    }

    public static class SendBotToken {
        public final String botToken;
        public SendBotToken(String botToken) { this.botToken = botToken; }
    }

    public static class SendCode {
        public final String code;
        public SendCode(String code) { this.code = code; }
    }

    public static class SendPassword {
        public final String password;
        public SendPassword(String password) { this.password = password; }
    }

    public static class AuthStateChanged {
        public final TdApi.AuthorizationState state;
        public AuthStateChanged(TdApi.AuthorizationState state) { this.state = state; }
    }

    public static class TdError {
        public final String message;
        public TdError(String message) { this.message = message; }
    }

    public static class SendWithId {
        public final long requestId;
        public final TdApi.Function function;
        public final ActorRef replyTo;

        public SendWithId(long requestId, TdApi.Function function, ActorRef replyTo) {
            this.requestId = requestId;
            this.function = function;
            this.replyTo = replyTo;
        }
    }

    public static class ResultWithId {
        public final long requestId;
        public final TdApi.Object result;
        public ResultWithId(long requestId, TdApi.Object result) {
            this.requestId = requestId;
            this.result = result;
        }
    }

    public static class RequestUserName{
        public final long userId;
        public RequestUserName(long userId){
            this.userId = userId;
        }
    }
}
