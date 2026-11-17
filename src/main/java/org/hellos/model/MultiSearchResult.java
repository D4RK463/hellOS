// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.List;
import java.util.Objects;

/**
 * The result of {@code browse/multi_search} (spec v1.21), a search over several sources and
 * criteria at once.
 *
 * @param search      the search string
 * @param sourceIds   the searched sources
 * @param criteriaIds the searched criteria
 * @param returned    number of items in this response
 * @param count       total number of results
 * @param items       the results of all sources
 * @param statistics  results per source and criteria
 * @param errors      sources and criteria that failed
 */
public record MultiSearchResult(
        String search,
        List<Long> sourceIds,
        List<Integer> criteriaIds,
        int returned,
        int count,
        List<MediaItem> items,
        List<Statistic> statistics,
        List<SourceError> errors) {

    public MultiSearchResult {
        Objects.requireNonNull(search, "search");
        sourceIds = List.copyOf(sourceIds);
        criteriaIds = List.copyOf(criteriaIds);
        items = List.copyOf(items);
        statistics = List.copyOf(statistics);
        errors = List.copyOf(errors);
    }

    /** Number of results of one source and criteria. */
    public record Statistic(long sourceId, int criteriaId, int returned, int count) {
    }

    /**
     * A source and criteria that could not be searched.
     *
     * @param errorNumber the system error number, e.g. {@code -1061} (service not registered)
     */
    public record SourceError(long sourceId, int criteriaId, int errorNumber) {
    }
}
