// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

/**
 * Names of all commands of the HEOS CLI protocol (specification v1.17 plus {@code browse/multi_search}
 * from v1.21). The section number of the specification is given for each command.
 */
public final class Commands {

    private Commands() {
    }

    // 4.1 System commands
    /** 4.1.1 {@code enable=on|off}. */
    public static final String SYSTEM_REGISTER_FOR_CHANGE_EVENTS = "system/register_for_change_events";
    /** 4.1.2. */
    public static final String SYSTEM_CHECK_ACCOUNT = "system/check_account";
    /** 4.1.3 {@code un}, {@code pw}. */
    public static final String SYSTEM_SIGN_IN = "system/sign_in";
    /** 4.1.4. */
    public static final String SYSTEM_SIGN_OUT = "system/sign_out";
    /** 4.1.5. */
    public static final String SYSTEM_HEART_BEAT = "system/heart_beat";
    /** 4.1.6 The device does not answer this command. */
    public static final String SYSTEM_REBOOT = "system/reboot";
    /**
     * 4.1.7 Only meant for manual telnet sessions. Enabling it breaks the line-based framing, so
     * hellOS refuses to send {@code enable=on}.
     */
    public static final String SYSTEM_PRETTIFY_JSON_RESPONSE = "system/prettify_json_response";

    // 4.2 Player commands
    /** 4.2.1. */
    public static final String PLAYER_GET_PLAYERS = "player/get_players";
    /** 4.2.2 {@code pid}. */
    public static final String PLAYER_GET_PLAYER_INFO = "player/get_player_info";
    /** 4.2.3 {@code pid}. */
    public static final String PLAYER_GET_PLAY_STATE = "player/get_play_state";
    /** 4.2.4 {@code pid}, {@code state=play|pause|stop}. */
    public static final String PLAYER_SET_PLAY_STATE = "player/set_play_state";
    /** 4.2.5 {@code pid}. */
    public static final String PLAYER_GET_NOW_PLAYING_MEDIA = "player/get_now_playing_media";
    /** 4.2.6 {@code pid}. */
    public static final String PLAYER_GET_VOLUME = "player/get_volume";
    /** 4.2.7 {@code pid}, {@code level=0..100}. */
    public static final String PLAYER_SET_VOLUME = "player/set_volume";
    /** 4.2.8 {@code pid}, {@code step=1..10}. */
    public static final String PLAYER_VOLUME_UP = "player/volume_up";
    /** 4.2.9 {@code pid}, {@code step=1..10}. */
    public static final String PLAYER_VOLUME_DOWN = "player/volume_down";
    /** 4.2.10 {@code pid}. */
    public static final String PLAYER_GET_MUTE = "player/get_mute";
    /** 4.2.11 {@code pid}, {@code state=on|off}. */
    public static final String PLAYER_SET_MUTE = "player/set_mute";
    /** 4.2.12 {@code pid}. */
    public static final String PLAYER_TOGGLE_MUTE = "player/toggle_mute";
    /** 4.2.13 {@code pid}. */
    public static final String PLAYER_GET_PLAY_MODE = "player/get_play_mode";
    /** 4.2.14 {@code pid}, {@code repeat=on_all|on_one|off}, {@code shuffle=on|off}. */
    public static final String PLAYER_SET_PLAY_MODE = "player/set_play_mode";
    /** 4.2.15 {@code pid}, optional {@code range=start,end}. */
    public static final String PLAYER_GET_QUEUE = "player/get_queue";
    /** 4.2.16 {@code pid}, {@code qid}. */
    public static final String PLAYER_PLAY_QUEUE = "player/play_queue";
    /** 4.2.17 {@code pid}, {@code qid=1,2,3}. */
    public static final String PLAYER_REMOVE_FROM_QUEUE = "player/remove_from_queue";
    /** 4.2.18 {@code pid}, {@code name}. */
    public static final String PLAYER_SAVE_QUEUE = "player/save_queue";
    /** 4.2.19 {@code pid}. */
    public static final String PLAYER_CLEAR_QUEUE = "player/clear_queue";
    /** 4.2.20 {@code pid}, {@code sqid=1,2}, {@code dqid}. */
    public static final String PLAYER_MOVE_QUEUE_ITEM = "player/move_queue_item";
    /** 4.2.21 {@code pid}. */
    public static final String PLAYER_PLAY_NEXT = "player/play_next";
    /** 4.2.22 {@code pid}. */
    public static final String PLAYER_PLAY_PREVIOUS = "player/play_previous";
    /** 4.2.23 {@code pid}, {@code id=1..6}. AVR/soundbar only. */
    public static final String PLAYER_SET_QUICKSELECT = "player/set_quickselect";
    /** 4.2.24 {@code pid}, {@code id=1..6}. AVR/soundbar only. */
    public static final String PLAYER_PLAY_QUICKSELECT = "player/play_quickselect";
    /** 4.2.25 {@code pid}, optional {@code id}. AVR/soundbar only. */
    public static final String PLAYER_GET_QUICKSELECTS = "player/get_quickselects";
    /** 4.2.26 {@code pid}. */
    public static final String PLAYER_CHECK_UPDATE = "player/check_update";

    // 4.3 Group commands
    /** 4.3.1. */
    public static final String GROUP_GET_GROUPS = "group/get_groups";
    /** 4.3.2 {@code gid}. */
    public static final String GROUP_GET_GROUP_INFO = "group/get_group_info";
    /** 4.3.3 {@code pid=leader,member1,...}. */
    public static final String GROUP_SET_GROUP = "group/set_group";
    /** 4.3.4 {@code gid}. */
    public static final String GROUP_GET_VOLUME = "group/get_volume";
    /** 4.3.5 {@code gid}, {@code level=0..100}. */
    public static final String GROUP_SET_VOLUME = "group/set_volume";
    /** 4.3.6 {@code gid}, {@code step=1..10}. */
    public static final String GROUP_VOLUME_UP = "group/volume_up";
    /** 4.3.7 {@code gid}, {@code step=1..10}. */
    public static final String GROUP_VOLUME_DOWN = "group/volume_down";
    /** 4.3.8 {@code gid}. */
    public static final String GROUP_GET_MUTE = "group/get_mute";
    /** 4.3.9 {@code gid}, {@code state=on|off}. */
    public static final String GROUP_SET_MUTE = "group/set_mute";
    /** 4.3.10 {@code gid}. */
    public static final String GROUP_TOGGLE_MUTE = "group/toggle_mute";

    // 4.4 Browse commands
    /** 4.4.1 optional {@code refresh=on}. */
    public static final String BROWSE_GET_MUSIC_SOURCES = "browse/get_music_sources";
    /** 4.4.2 {@code sid}. */
    public static final String BROWSE_GET_SOURCE_INFO = "browse/get_source_info";
    /** 4.4.3 / 4.4.4 {@code sid}, optional {@code cid}, {@code range}. */
    public static final String BROWSE_BROWSE = "browse/browse";
    /** 4.4.5 {@code sid}. */
    public static final String BROWSE_GET_SEARCH_CRITERIA = "browse/get_search_criteria";
    /** 4.4.6 {@code sid}, {@code search}, {@code scid}, optional {@code range}. */
    public static final String BROWSE_SEARCH = "browse/search";
    /** 4.4.7 / 4.4.10 Play station ({@code pid, sid, cid, mid, name}) or URL ({@code pid, url}). */
    public static final String BROWSE_PLAY_STREAM = "browse/play_stream";
    /** 4.4.8 {@code pid}, {@code preset=1..}. Requires a signed-in account. */
    public static final String BROWSE_PLAY_PRESET = "browse/play_preset";
    /** 4.4.9 {@code pid}, optional {@code spid}, {@code input}. */
    public static final String BROWSE_PLAY_INPUT = "browse/play_input";
    /** 4.4.11 / 4.4.12 {@code pid, sid, cid}, optional {@code mid}, {@code aid=1..4}. */
    public static final String BROWSE_ADD_TO_QUEUE = "browse/add_to_queue";
    /** 4.4.14 {@code sid}, {@code cid}, {@code name}. */
    public static final String BROWSE_RENAME_PLAYLIST = "browse/rename_playlist";
    /** 4.4.15 {@code sid}, {@code cid}. */
    public static final String BROWSE_DELETE_PLAYLIST = "browse/delete_playlist";
    /** 4.4.17 {@code sid}, {@code cid}. */
    public static final String BROWSE_RETRIEVE_METADATA = "browse/retrieve_metadata";
    /**
     * 4.4.18 {@code sid}.
     *
     * @deprecated obsolete according to the specification, {@code get_now_playing_media} returns the options
     */
    @Deprecated
    public static final String BROWSE_GET_SERVICE_OPTIONS = "browse/get_service_options";
    /** 4.4.19 {@code option} plus option-specific attributes. */
    public static final String BROWSE_SET_SERVICE_OPTION = "browse/set_service_option";
    /** 4.4.20 (spec v1.21) {@code search}, optional {@code sid=1,2}, {@code scid=1,2}. */
    public static final String BROWSE_MULTI_SEARCH = "browse/multi_search";
}
