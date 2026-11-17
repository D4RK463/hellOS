// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A music source ({@code browse/get_music_sources}, {@code browse/get_source_info}).
 *
 * @param sourceId        the source id ({@code sid}), see {@link MusicSources}
 * @param name            display name
 * @param type            one of the source media types, e.g. {@link MediaType#MUSIC_SERVICE}
 * @param imageUrl        logo
 * @param available       for online services: whether the user has an account for it. This does
 *                        not mean the service can be browsed through the CLI.
 * @param serviceUsername user name of the service account, if available
 */
public record MusicSource(
        long sourceId,
        String name,
        MediaType type,
        Optional<String> imageUrl,
        boolean available,
        Optional<String> serviceUsername) {

    public MusicSource {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(imageUrl, "imageUrl");
        Objects.requireNonNull(serviceUsername, "serviceUsername");
    }
}
