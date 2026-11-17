// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

/** How media is added to the queue ({@code aid} attribute of {@code browse/add_to_queue}). */
public enum AddCriteria {
    PLAY_NOW(1),
    PLAY_NEXT(2),
    ADD_TO_END(3),
    REPLACE_AND_PLAY(4);

    private final int code;

    AddCriteria(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
