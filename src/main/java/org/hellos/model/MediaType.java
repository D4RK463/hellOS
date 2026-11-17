// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

/** The {@code type} of a browse/search item, of now playing media or of a music source. */
public enum MediaType {
    SONG("song"),
    STATION("station"),
    GENRE("genre"),
    ARTIST("artist"),
    ALBUM("album"),
    CONTAINER("container"),
    PLAYLIST("playlist"),
    MUSIC_SERVICE("music_service"),
    HEOS_SERVICE("heos_service"),
    HEOS_SERVER("heos_server"),
    DLNA_SERVER("dlna_server"),
    /** Missing or not documented. */
    UNKNOWN("");

    private final String wireValue;

    MediaType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    /** Whether this is a music source (browsable with its own source id) rather than a media item. */
    public boolean isSource() {
        return this == MUSIC_SERVICE || this == HEOS_SERVICE || this == HEOS_SERVER || this == DLNA_SERVER;
    }

    public static MediaType fromWire(String value) {
        if (value != null) {
            for (MediaType type : values()) {
                if (type != UNKNOWN && type.wireValue.equals(value)) {
                    return type;
                }
            }
        }
        return UNKNOWN;
    }
}
