// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import org.hellos.cli.HeosProtocolException;

/** Play state of a player. */
public enum PlayState {
    PLAY("play"),
    PAUSE("pause"),
    STOP("stop");

    private final String wireValue;

    PlayState(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static PlayState fromWire(String value) {
        for (PlayState state : values()) {
            if (state.wireValue.equals(value)) {
                return state;
            }
        }
        throw new HeosProtocolException("Unknown play state '" + value + "'");
    }
}
