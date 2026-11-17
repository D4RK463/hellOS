// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * What a player is currently playing ({@code player/get_now_playing_media}). All fields are empty
 * when nothing is playing, see {@link #isEmpty()}.
 *
 * @param type     song or station; {@link MediaType#UNKNOWN} if nothing is playing
 * @param song     song title
 * @param album    album name
 * @param artist   artist name
 * @param station  station name, only for stations
 * @param imageUrl cover art
 * @param mediaId  media id ({@code mid})
 * @param queueId  queue id ({@code qid}) of the playing queue entry
 * @param sourceId source id ({@code sid})
 * @param albumId  album id, usable with {@code browse/retrieve_metadata}
 * @param options  options available for the playing media, e.g. thumbs up
 */
public record NowPlayingMedia(
        MediaType type,
        Optional<String> song,
        Optional<String> album,
        Optional<String> artist,
        Optional<String> station,
        Optional<String> imageUrl,
        Optional<String> mediaId,
        OptionalInt queueId,
        OptionalLong sourceId,
        Optional<String> albumId,
        List<ServiceOption> options) {

    public NowPlayingMedia {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(song, "song");
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(imageUrl, "imageUrl");
        Objects.requireNonNull(mediaId, "mediaId");
        Objects.requireNonNull(queueId, "queueId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(albumId, "albumId");
        options = List.copyOf(options);
    }

    public boolean isEmpty() {
        return type == MediaType.UNKNOWN && song.isEmpty() && station.isEmpty() && mediaId.isEmpty();
    }

    public boolean isStation() {
        return type == MediaType.STATION;
    }

    /** Whether the option with the given id is offered, e.g. {@link ServiceOptions#THUMBS_UP}. */
    public boolean hasOption(int optionId) {
        return options.stream().anyMatch(o -> o.id() == optionId);
    }
}
