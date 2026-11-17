// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

/** Ids of service options ({@code option} attribute of {@code browse/set_service_option}, spec section 4.4.19). */
public final class ServiceOptions {

    private ServiceOptions() {
    }

    public static final int ADD_TRACK_TO_LIBRARY = 1;
    public static final int ADD_ALBUM_TO_LIBRARY = 2;
    public static final int ADD_STATION_TO_LIBRARY = 3;
    public static final int ADD_PLAYLIST_TO_LIBRARY = 4;
    public static final int REMOVE_TRACK_FROM_LIBRARY = 5;
    public static final int REMOVE_ALBUM_FROM_LIBRARY = 6;
    public static final int REMOVE_STATION_FROM_LIBRARY = 7;
    public static final int REMOVE_PLAYLIST_FROM_LIBRARY = 8;
    public static final int THUMBS_UP = 11;
    public static final int THUMBS_DOWN = 12;
    public static final int CREATE_NEW_STATION = 13;
    public static final int ADD_TO_FAVORITES = 19;
    public static final int REMOVE_FROM_FAVORITES = 20;
    /** Only appears in browse results: the container is playable although it did not say so. */
    public static final int PLAYABLE_CONTAINER = 21;
}
