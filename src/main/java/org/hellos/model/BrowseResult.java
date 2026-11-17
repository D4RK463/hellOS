// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of {@code browse/browse}.
 *
 * @param sourceId    the browsed source
 * @param containerId the browsed container, empty at the top level of a source
 * @param returned    number of items in this response
 * @param count       total number of items; {@code 0} means unknown, keep paging until an empty
 *                    result is returned
 * @param items       the items
 * @param options     options for the current browse level
 */
public record BrowseResult(
        long sourceId,
        Optional<String> containerId,
        int returned,
        int count,
        List<MediaItem> items,
        List<ServiceOption> options) {

    public BrowseResult {
        Objects.requireNonNull(containerId, "containerId");
        items = List.copyOf(items);
        options = List.copyOf(options);
    }

    /** Whether the option with the given id is offered, e.g. {@link ServiceOptions#PLAYABLE_CONTAINER}. */
    public boolean hasOption(int optionId) {
        return options.stream().anyMatch(o -> o.id() == optionId);
    }
}
