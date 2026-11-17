// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A search criteria of a source ({@code browse/get_search_criteria}).
 *
 * @param name                  e.g. Artist, Album, Track, Station
 * @param criteriaId            the {@code scid} to pass to search
 * @param wildcard              whether {@code *} is supported in the search string
 * @param playable              whether all results can be added to the queue at once
 * @param containerIdPrefix     prefix for that, e.g. {@code SEARCHED_TRACKS-}; append the search
 *                              string and use it as container id for add to queue
 */
public record SearchCriteria(
        String name,
        int criteriaId,
        boolean wildcard,
        boolean playable,
        Optional<String> containerIdPrefix) {

    public SearchCriteria {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(containerIdPrefix, "containerIdPrefix");
    }
}
