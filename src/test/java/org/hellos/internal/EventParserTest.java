// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import org.hellos.cli.HeosMessage;
import org.hellos.cli.HeosResponseParser;
import org.hellos.event.HeosEvent;
import org.hellos.model.PlayState;
import org.hellos.model.RepeatMode;
import org.hellos.testing.FakeHeosServer;
import org.junit.jupiter.api.Test;

class EventParserTest {

    private static HeosEvent parse(String command, String message) {
        HeosMessage raw = HeosResponseParser.parse(FakeHeosServer.event(command, message));
        return EventParser.parse(raw);
    }

    @Test
    void parsesAllDocumentedEvents() {
        assertThat(parse("event/sources_changed", null)).isEqualTo(new HeosEvent.SourcesChanged());
        assertThat(parse("event/players_changed", null)).isEqualTo(new HeosEvent.PlayersChanged());
        assertThat(parse("event/groups_changed", null)).isEqualTo(new HeosEvent.GroupsChanged());
        assertThat(parse("event/player_state_changed", "pid=-1&state=pause"))
                .isEqualTo(new HeosEvent.PlayerStateChanged(-1, PlayState.PAUSE));
        assertThat(parse("event/player_now_playing_changed", "pid=5"))
                .isEqualTo(new HeosEvent.PlayerNowPlayingChanged(5));
        assertThat(parse("event/player_now_playing_progress", "pid=5&cur_pos=61000&duration=240000"))
                .isEqualTo(new HeosEvent.PlayerNowPlayingProgress(5, Duration.ofSeconds(61), Duration.ofMinutes(4)));
        assertThat(parse("event/player_playback_error", "pid=5&error=Could Not Download"))
                .isEqualTo(new HeosEvent.PlayerPlaybackError(5, "Could Not Download"));
        assertThat(parse("event/player_queue_changed", "pid=5")).isEqualTo(new HeosEvent.PlayerQueueChanged(5));
        assertThat(parse("event/player_volume_changed", "pid=5&level=30&mute=on"))
                .isEqualTo(new HeosEvent.PlayerVolumeChanged(5, 30, true));
        assertThat(parse("event/repeat_mode_changed", "pid=5&repeat=on_all"))
                .isEqualTo(new HeosEvent.RepeatModeChanged(5, RepeatMode.ON_ALL));
        assertThat(parse("event/shuffle_mode_changed", "pid=5&shuffle=off"))
                .isEqualTo(new HeosEvent.ShuffleModeChanged(5, false));
        assertThat(parse("event/group_volume_changed", "gid=7&level=12&mute=off"))
                .isEqualTo(new HeosEvent.GroupVolumeChanged(7, 12, false));
        assertThat(parse("event/user_changed", "signed_in&un=user@example.com"))
                .isEqualTo(new HeosEvent.UserChanged(true, Optional.of("user@example.com")));
        assertThat(parse("event/user_changed", "signed_out"))
                .isEqualTo(new HeosEvent.UserChanged(false, Optional.empty()));
    }

    @Test
    void progressWithoutDurationForStreams() {
        assertThat(parse("event/player_now_playing_progress", "pid=5&cur_pos=1000&duration="))
                .isEqualTo(new HeosEvent.PlayerNowPlayingProgress(5, Duration.ofSeconds(1), Duration.ZERO));
    }

    @Test
    void unknownAndMalformedEventsAreKept() {
        assertThat(parse("event/some_future_event", "pid=1")).isInstanceOf(HeosEvent.UnknownEvent.class);
        HeosEvent malformed = parse("event/player_volume_changed", "pid=abc&level=x");
        assertThat(malformed).isInstanceOfSatisfying(HeosEvent.UnknownEvent.class,
                e -> assertThat(e.message().command()).isEqualTo("event/player_volume_changed"));
    }
}
