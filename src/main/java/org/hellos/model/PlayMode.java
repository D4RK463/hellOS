// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;

/** Repeat and shuffle setting of a player. */
public record PlayMode(RepeatMode repeat, boolean shuffle) {

    public PlayMode {
        Objects.requireNonNull(repeat, "repeat");
    }

    public PlayMode withRepeat(RepeatMode newRepeat) {
        return new PlayMode(newRepeat, shuffle);
    }

    public PlayMode withShuffle(boolean newShuffle) {
        return new PlayMode(repeat, newShuffle);
    }
}
