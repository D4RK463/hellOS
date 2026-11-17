// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.List;
import java.util.Objects;

/**
 * The result of {@code browse/search}.
 *
 * @param sourceId   the searched source
 * @param criteriaId the search criteria
 * @param search     the search string
 * @param returned   number of items in this response
 * @param count      total number of results; {@code 0} means unknown
 * @param items      the results
 * @param options    options for the results
 */
public record SearchResult(
        long sourceId,
        int criteriaId,
        String search,
        int returned,
        int count,
        List<MediaItem> items,
        List<ServiceOption> options) {

    public SearchResult {
        Objects.requireNonNull(search, "search");
        items = List.copyOf(items);
        options = List.copyOf(options);
    }
}
