// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;

/** A quick select slot of an AVR or soundbar ({@code player/get_quickselects}). */
public record QuickSelect(int id, String name) {

    public QuickSelect {
        Objects.requireNonNull(name, "name");
    }
}
