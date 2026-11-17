// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

/** How a player is connected to the network. */
public enum NetworkType {
    WIRED("wired"),
    WIFI("wifi"),
    /** Reported as {@code unknown} or not reported at all. */
    UNKNOWN("unknown");

    private final String wireValue;

    NetworkType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static NetworkType fromWire(String value) {
        for (NetworkType type : values()) {
            if (type.wireValue.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
