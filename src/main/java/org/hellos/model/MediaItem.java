// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An entry of a browse or search result. Depending on the {@link #type()} it is
 * <ul>
 *   <li>a source (e.g. a DLNA server or an AUX input device) that can be browsed with its
 *       {@link #sourceId()},</li>
 *   <li>a container (artist, album, playlist, ...) that can be browsed with its
 *       {@link #containerId()} and possibly be queued as a whole if {@link #playable()},</li>
 *   <li>or a playable song or station identified by its {@link #mediaId()}.</li>
 * </ul>
 */
public record MediaItem(
        String name,
        MediaType type,
        boolean container,
        boolean playable,
        Optional<String> imageUrl,
        Optional<String> artist,
        Optional<String> album,
        Optional<String> albumId,
        Optional<String> containerId,
        Optional<String> mediaId,
        OptionalLong sourceId) {

    public MediaItem {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(imageUrl, "imageUrl");
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(album, "album");
        Objects.requireNonNull(albumId, "albumId");
        Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(mediaId, "mediaId");
        Objects.requireNonNull(sourceId, "sourceId");
    }

    /** Whether this item is a source that is browsed by its own source id. */
    public boolean isSource() {
        return type.isSource() || (sourceId.isPresent() && containerId.isEmpty() && mediaId.isEmpty());
    }

    /** Whether this item is an input of an AVR or other HEOS device ({@code mid} starts with {@code inputs/}). */
    public boolean isInput() {
        return mediaId.map(mid -> mid.startsWith(Input.PREFIX)).orElse(false);
    }
}
