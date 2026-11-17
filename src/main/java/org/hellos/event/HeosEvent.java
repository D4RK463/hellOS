// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.event;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.hellos.cli.HeosMessage;
import org.hellos.model.PlayState;
import org.hellos.model.RepeatMode;

/**
 * A typed change event (spec section 5). Use pattern matching to handle the events you are
 * interested in:
 *
 * <pre>{@code
 * heos.addEventListener(event -> {
 *     switch (event) {
 *         case HeosEvent.PlayerVolumeChanged(long pid, int level, boolean muted) -> ...
 *         case HeosEvent.PlayerStateChanged e -> ...
 *         default -> { }
 *     }
 * });
 * }</pre>
 */
public sealed interface HeosEvent {

    /** The list of music sources or their availability changed. */
    record SourcesChanged() implements HeosEvent {
    }

    /** Players were added, removed or renamed. */
    record PlayersChanged() implements HeosEvent {
    }

    /** Groups were created, changed or removed. */
    record GroupsChanged() implements HeosEvent {
    }

    record PlayerStateChanged(long playerId, PlayState state) implements HeosEvent {
        public PlayerStateChanged {
            Objects.requireNonNull(state, "state");
        }
    }

    /** The playing media changed; fetch it with {@code Player#nowPlaying()}. */
    record PlayerNowPlayingChanged(long playerId) implements HeosEvent {
    }

    /** Playback progress, sent about once per second while playing. */
    record PlayerNowPlayingProgress(long playerId, Duration position, Duration duration) implements HeosEvent {
        public PlayerNowPlayingProgress {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(duration, "duration");
        }
    }

    /** @param error a message that can be shown to the user, e.g. "Could Not Download" */
    record PlayerPlaybackError(long playerId, String error) implements HeosEvent {
        public PlayerPlaybackError {
            Objects.requireNonNull(error, "error");
        }
    }

    record PlayerQueueChanged(long playerId) implements HeosEvent {
    }

    record PlayerVolumeChanged(long playerId, int level, boolean muted) implements HeosEvent {
    }

    record RepeatModeChanged(long playerId, RepeatMode repeat) implements HeosEvent {
        public RepeatModeChanged {
            Objects.requireNonNull(repeat, "repeat");
        }
    }

    record ShuffleModeChanged(long playerId, boolean shuffle) implements HeosEvent {
    }

    record GroupVolumeChanged(long groupId, int level, boolean muted) implements HeosEvent {
    }

    /** The HEOS account was signed in or out. */
    record UserChanged(boolean signedIn, Optional<String> username) implements HeosEvent {
        public UserChanged {
            Objects.requireNonNull(username, "username");
        }
    }

    /**
     * An event that is not documented or could not be parsed. The raw message is kept so that new
     * firmware events can still be handled.
     */
    record UnknownEvent(HeosMessage message) implements HeosEvent {
        public UnknownEvent {
            Objects.requireNonNull(message, "message");
        }
    }
}
