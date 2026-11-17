// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

/** Well-known source ids ({@code sid}, spec section 1.1). */
public final class MusicSources {

    private MusicSources() {
    }

    public static final long PANDORA = 1;
    public static final long RHAPSODY = 2;
    public static final long TUNEIN = 3;
    /** Not browsable through the CLI. */
    public static final long SPOTIFY = 4;
    public static final long DEEZER = 5;
    public static final long NAPSTER = 6;
    public static final long IHEARTRADIO = 7;
    public static final long SIRIUSXM = 8;
    public static final long SOUNDCLOUD = 9;
    public static final long TIDAL = 10;
    public static final long AMAZON_MUSIC = 13;
    public static final long MOODMIX = 15;
    public static final long QQMUSIC = 18;

    /** Local USB media and DLNA servers. */
    public static final long LOCAL_MUSIC = 1024;
    /** HEOS playlists; requires a signed-in account. */
    public static final long PLAYLISTS = 1025;
    public static final long HISTORY = 1026;
    /** Auxiliary inputs of all players. */
    public static final long AUX_INPUTS = 1027;
    /** HEOS favorites; requires a signed-in account. */
    public static final long FAVORITES = 1028;
}
