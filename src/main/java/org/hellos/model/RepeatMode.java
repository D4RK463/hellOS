// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import org.hellos.cli.HeosProtocolException;

/** Repeat mode of a player. */
public enum RepeatMode {
    ON_ALL("on_all"),
    ON_ONE("on_one"),
    OFF("off");

    private final String wireValue;

    RepeatMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static RepeatMode fromWire(String value) {
        for (RepeatMode mode : values()) {
            if (mode.wireValue.equals(value)) {
                return mode;
            }
        }
        throw new HeosProtocolException("Unknown repeat mode '" + value + "'");
    }
}
