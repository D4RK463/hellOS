// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * An option offered in the {@code options} element of a response, e.g. "Add to HEOS Favorites".
 * Apply it with {@code browse/set_service_option}. Known ids are in {@link ServiceOptions}.
 *
 * @param context          where the option applies: {@code play} (now playing screen) or
 *                         {@code browse} (current browse level)
 * @param id               the option id
 * @param name             the display name
 * @param searchCriteriaId the criteria id for "create new station" options
 */
public record ServiceOption(String context, int id, String name, OptionalInt searchCriteriaId) {

    public ServiceOption {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(searchCriteriaId, "searchCriteriaId");
    }
}
