// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

/**
 * Names of the change events (unsolicited responses, spec section 5) a HEOS device sends after
 * {@code system/register_for_change_events?enable=on}.
 */
public final class Events {

    private Events() {
    }

    /** 5.1. */
    public static final String SOURCES_CHANGED = "event/sources_changed";
    /** 5.2. */
    public static final String PLAYERS_CHANGED = "event/players_changed";
    /** 5.3. */
    public static final String GROUPS_CHANGED = "event/groups_changed";
    /** 5.4 {@code pid}, {@code state}. */
    public static final String PLAYER_STATE_CHANGED = "event/player_state_changed";
    /** 5.5 {@code pid}. */
    public static final String PLAYER_NOW_PLAYING_CHANGED = "event/player_now_playing_changed";
    /** 5.6 {@code pid}, {@code cur_pos}, {@code duration} (milliseconds). */
    public static final String PLAYER_NOW_PLAYING_PROGRESS = "event/player_now_playing_progress";
    /** 5.7 {@code pid}, {@code error}. */
    public static final String PLAYER_PLAYBACK_ERROR = "event/player_playback_error";
    /** 5.8 {@code pid}. */
    public static final String PLAYER_QUEUE_CHANGED = "event/player_queue_changed";
    /** 5.9 {@code pid}, {@code level}, {@code mute}. */
    public static final String PLAYER_VOLUME_CHANGED = "event/player_volume_changed";
    /** 5.10 {@code pid}, {@code repeat}. */
    public static final String REPEAT_MODE_CHANGED = "event/repeat_mode_changed";
    /** 5.11 {@code pid}, {@code shuffle}. */
    public static final String SHUFFLE_MODE_CHANGED = "event/shuffle_mode_changed";
    /** 5.12 {@code gid}, {@code level}, {@code mute}. */
    public static final String GROUP_VOLUME_CHANGED = "event/group_volume_changed";
    /** 5.13 {@code signed_out} or {@code signed_in&un=...}. */
    public static final String USER_CHANGED = "event/user_changed";
}
