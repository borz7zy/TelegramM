package com.github.borz7zy.telegramm.ui.chat;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class UiContent {

    public enum Kind { TEXT, MEDIA, SYSTEM, UNKNOWN }

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

        public Text(String text) {
            this.text = text != null ? text : "";
        }

        @NonNull @Override
        public Kind kind() { return Kind.TEXT; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Text that)) return false;
            return Objects.equals(text, that.text);
        }

        @Override public int hashCode() { return Objects.hash(text); }
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
            if (!(o instanceof Media that)) return false;
            return Objects.equals(text, that.caption);
        }

        @Override public int hashCode() { return Objects.hash(text); }
    }

    public static final class Media extends UiContent {
        public final String caption;

        public Media(String caption) {
            this.caption = caption != null ? caption : "";
        }

        @NonNull @Override
        public Kind kind() { return Kind.MEDIA; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Media that)) return false;
            return Objects.equals(caption, that.caption);
        }

        @Override public int hashCode() { return Objects.hash(caption); }
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
}
