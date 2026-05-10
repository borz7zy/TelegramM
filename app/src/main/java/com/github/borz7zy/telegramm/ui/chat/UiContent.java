package com.github.borz7zy.telegramm.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class UiContent {

    public enum Kind {
        TEXT,
        MEDIA,
        SYSTEM,
        UNKNOWN,
        STICKER
    }

    public enum StickerType {
        STATIC,
        ANIMATED_TGS,
        VIDEO_WEBM
    }

    public final List<List<UiButton>> buttons = new ArrayList<>();

    public static class UiButton {
        public final String text;
        public final String url;
        public final byte[] data;

        public UiButton(String text, String url, byte[] data) {
            this.text = text;
            this.url = url;
            this.data = data;
        }

        public boolean isUrl() { return url != null && !url.isEmpty(); }
    }

    @NonNull
    public abstract Kind kind();

    public static final class Text extends UiContent {
        public final String text;
        @Nullable public final TdApi.TextEntity[] entities;

        public Text(String text) {
            this(text, null);
        }

        public Text(@Nullable TdApi.FormattedText ft) {
            this(ft != null ? ft.text : "", ft != null ? ft.entities : null);
        }

        public Text(String text, @Nullable TdApi.TextEntity[] entities) {
            this.text = text != null ? text : "";
            this.entities = entities;
        }

        @NonNull @Override
        public Kind kind() { return Kind.TEXT; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Text that)) return false;
            return Objects.equals(text, that.text)
                    && Arrays.equals(entities, that.entities);
        }

        @Override public int hashCode() {
            return 31 * Objects.hashCode(text) + Arrays.hashCode(entities);
        }
    }

    public static final class Unknown extends UiContent {
        public final String text;
        public Unknown(){
            text = "Unknown content";
        }

        @NonNull
        @Override
        public Kind kind() {
            return Kind.UNKNOWN;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Unknown that)) return false;
            return Objects.equals(text, that.text);
        }

        @Override public int hashCode() { return Objects.hash(text); }
    }

    public static final class Media extends UiContent {
        public final String caption;
        @Nullable public final TdApi.TextEntity[] entities;

        public Media(String caption) {
            this(caption, null);
        }

        public Media(@Nullable TdApi.FormattedText ft) {
            this(ft != null ? ft.text : "", ft != null ? ft.entities : null);
        }

        public Media(String caption, @Nullable TdApi.TextEntity[] entities) {
            this.caption = caption != null ? caption : "";
            this.entities = entities;
        }

        @NonNull @Override
        public Kind kind() {
            return Kind.MEDIA;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Media that)) return false;
            return Objects.equals(caption, that.caption)
                    && Arrays.equals(entities, that.entities);
        }

        @Override public int hashCode() {
            return 31 * Objects.hashCode(caption) + Arrays.hashCode(entities);
        }
    }

    public static final class System extends UiContent {
        public final String text;
        public final Object messageType;

        public System(String text, Object messageType) {
            this.messageType = messageType;
            this.text = text != null ? text : "";
        }

        @NonNull @Override
        public Kind kind() { return Kind.SYSTEM; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof System that)) return false;
            return Objects.equals(text, that.text) &&
                    Objects.equals(messageType, that.messageType);
        }

        @Override public int hashCode() {
            return Objects.hash(text);
        }
    }

    public static final class Sticker extends UiContent {
        public final int fileId;
        public final int width;
        public final int height;
        public final StickerType type;
        public final boolean isAnimatedEmoji;

        public Sticker(int fileId, int w, int h, StickerType type) {
            this(fileId, w, h, type, false);
        }

        public Sticker(int fileId, int w, int h, StickerType type, boolean isAnimatedEmoji) {
            this.fileId = fileId;
            this.width = w;
            this.height = h;
            this.type = type;
            this.isAnimatedEmoji = isAnimatedEmoji;
        }


        @NonNull
        @Override
        public Kind kind() {
            return Kind.STICKER;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Sticker that)) return false;
            return fileId == that.fileId
                    && width == that.width
                    && height == that.height
                    && type == that.type
                    && isAnimatedEmoji == that.isAnimatedEmoji;
        }

        @Override public int hashCode() {
            return Objects.hash(fileId, width, height, type, isAnimatedEmoji);
        }
    }
}
