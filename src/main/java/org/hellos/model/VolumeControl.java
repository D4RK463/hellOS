// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

/** How the volume is controlled when the line-out level is {@link LineOutLevel#FIXED} ({@code control} field). */
public enum VolumeControl {
    NONE(1),
    IR(2),
    TRIGGER(3),
    NETWORK(4),
    /** Not reported (line-out is variable) or an undocumented value. */
    UNKNOWN(0);

    private final int code;

    VolumeControl(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static VolumeControl fromCode(Integer code) {
        if (code != null) {
            for (VolumeControl control : values()) {
                if (control.code == code) {
                    return control;
                }
            }
        }
        return UNKNOWN;
    }
}
