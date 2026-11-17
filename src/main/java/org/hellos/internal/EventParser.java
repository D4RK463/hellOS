// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

import static java.lang.System.Logger.Level.DEBUG;

import java.time.Duration;

import org.hellos.cli.Events;
import org.hellos.cli.HeosMessage;
import org.hellos.cli.HeosProtocolException;
import org.hellos.cli.MessageParameters;
import org.hellos.event.HeosEvent;
import org.hellos.model.AccountStatus;
import org.hellos.model.PlayState;
import org.hellos.model.RepeatMode;

/** Converts raw change events into {@link HeosEvent}s. */
public final class EventParser {

    private static final System.Logger LOG = System.getLogger(EventParser.class.getName());

    private EventParser() {
    }

    /** Never throws; events that cannot be parsed become {@link HeosEvent.UnknownEvent}. */
    public static HeosEvent parse(HeosMessage message) {
        try {
            return parseStrict(message);
        } catch (HeosProtocolException | ArithmeticException e) {
            LOG.log(DEBUG, "Cannot parse event {0}: {1}", message, e.getMessage());
            return new HeosEvent.UnknownEvent(message);
        }
    }

    private static HeosEvent parseStrict(HeosMessage message) {
        MessageParameters m = message.message();
        return switch (message.command()) {
            case Events.SOURCES_CHANGED -> new HeosEvent.SourcesChanged();
            case Events.PLAYERS_CHANGED -> new HeosEvent.PlayersChanged();
            case Events.GROUPS_CHANGED -> new HeosEvent.GroupsChanged();
            case Events.PLAYER_STATE_CHANGED ->
                    new HeosEvent.PlayerStateChanged(m.requireLong("pid"), PlayState.fromWire(m.require("state")));
            case Events.PLAYER_NOW_PLAYING_CHANGED -> new HeosEvent.PlayerNowPlayingChanged(m.requireLong("pid"));
            case Events.PLAYER_NOW_PLAYING_PROGRESS -> new HeosEvent.PlayerNowPlayingProgress(
                    m.requireLong("pid"),
                    Duration.ofMillis(m.getLong("cur_pos").orElse(0)),
                    Duration.ofMillis(m.getLong("duration").orElse(0)));
            case Events.PLAYER_PLAYBACK_ERROR ->
                    new HeosEvent.PlayerPlaybackError(m.requireLong("pid"), m.get("error").orElse(""));
            case Events.PLAYER_QUEUE_CHANGED -> new HeosEvent.PlayerQueueChanged(m.requireLong("pid"));
            case Events.PLAYER_VOLUME_CHANGED -> new HeosEvent.PlayerVolumeChanged(
                    m.requireLong("pid"), m.requireInt("level"), ModelParser.onOff(m.require("mute")));
            case Events.REPEAT_MODE_CHANGED ->
                    new HeosEvent.RepeatModeChanged(m.requireLong("pid"), RepeatMode.fromWire(m.require("repeat")));
            case Events.SHUFFLE_MODE_CHANGED ->
                    new HeosEvent.ShuffleModeChanged(m.requireLong("pid"), ModelParser.onOff(m.require("shuffle")));
            case Events.GROUP_VOLUME_CHANGED -> new HeosEvent.GroupVolumeChanged(
                    m.requireLong("gid"), m.requireInt("level"), ModelParser.onOff(m.require("mute")));
            case Events.USER_CHANGED -> {
                AccountStatus status = ModelParser.accountStatus(m);
                yield new HeosEvent.UserChanged(status.signedIn(), status.username());
            }
            default -> new HeosEvent.UnknownEvent(message);
        };
    }
}
