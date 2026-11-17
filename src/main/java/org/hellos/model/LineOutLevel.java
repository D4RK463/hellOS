// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

/** Line-out level type of a player ({@code lineout} field). */
public enum LineOutLevel {
    VARIABLE(1),
    FIXED(2),
    /** Not reported, {@code 0} or an undocumented value. */
    UNKNOWN(0);

    private final int code;

    LineOutLevel(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static LineOutLevel fromCode(Integer code) {
        if (code != null) {
            for (LineOutLevel level : values()) {
                if (level.code == code) {
                    return level;
                }
            }
        }
        return UNKNOWN;
    }
}
