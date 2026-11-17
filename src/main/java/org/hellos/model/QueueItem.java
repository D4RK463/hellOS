// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;
import java.util.Optional;

/**
 * An entry of a player's queue ({@code player/get_queue}).
 *
 * @param queueId  the queue id ({@code qid}), starting at 1
 * @param song     song title
 * @param album    album name
 * @param artist   artist name
 * @param imageUrl cover art
 * @param mediaId  media id ({@code mid})
 * @param albumId  album id
 */
public record QueueItem(
        int queueId,
        String song,
        Optional<String> album,
        Optional<String> artist,
        Optional<String> imageUrl,
        Optional<String> mediaId,
        Optional<String> albumId) {

    public QueueItem {
        Objects.requireNonNull(song, "song");
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(imageUrl, "imageUrl");
        Objects.requireNonNull(mediaId, "mediaId");
        Objects.requireNonNull(albumId, "albumId");
    }
}
