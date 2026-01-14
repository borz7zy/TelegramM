package com.github.borz7zy.telegramm.ui.model;

import java.util.Objects;

public class SystemMessages {
    public static class Default{}
    public static class PremiumGift {
        public String comment;
        public String complete_caption;
        public int stickerFileId;
        public String stickerPath;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PremiumGift that = (PremiumGift) o;
            return Objects.equals(comment, that.comment) &&
                    Objects.equals(complete_caption, that.complete_caption) &&
                    Objects.equals(stickerFileId, that.stickerFileId) &&
                    Objects.equals(stickerPath, that.stickerPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(comment, complete_caption, stickerFileId, stickerPath);
        }
    }
    public static class StarsGift{}
    public static class UniqueGift{}
    public static class DefaultGift{}
}
