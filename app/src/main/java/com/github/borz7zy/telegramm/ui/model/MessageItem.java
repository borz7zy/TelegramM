package com.github.borz7zy.telegramm.ui.model;

import android.text.TextUtils;

import com.github.borz7zy.telegramm.ui.chat.UiContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessageItem {
    public final long id;
    public final long chatId;
    public final boolean outgoing;
    public final String time;
    public final List<PhotoData> photos;
    public final long mediaAlbumId;
    public final UiContent ui;

    public MessageItem(long id, long chatId, boolean outgoing,
                       String time, List<PhotoData> photos,
                       long mediaAlbumId, UiContent ui) {
        this.id = id;
        this.chatId = chatId;
        this.outgoing = outgoing;
        this.time = time != null ? time : "";
        this.photos = photos != null ? photos : Collections.emptyList();
        this.mediaAlbumId = mediaAlbumId;
        this.ui = ui != null ? ui : new UiContent.Text("");
    }

    public MessageItem withAddedPhoto(PhotoData newPhoto, String newText) {
        List<PhotoData> newPhotos = new ArrayList<>(this.photos);
        if (newPhoto != null) newPhotos.add(newPhoto);

        UiContent newUi = this.ui;

        if (this.ui instanceof UiContent.Media media) {
            String caption = media.caption;
            if (TextUtils.isEmpty(caption) && !TextUtils.isEmpty(newText)) {
                caption = newText;
            }
            newUi = new UiContent.Media(caption);
        }

        return new MessageItem(
                id, chatId, outgoing,
                time, newPhotos,
                mediaAlbumId,
                newUi
        );
    }
}
