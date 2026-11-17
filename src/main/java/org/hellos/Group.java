// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static org.hellos.Requests.checkRange;
import static org.hellos.Requests.command;
import static org.hellos.Requests.onOff;

import java.util.concurrent.CompletableFuture;

import org.hellos.cli.Commands;
import org.hellos.internal.ModelParser;
import org.hellos.model.GroupInfo;

/**
 * A handle to control the volume of a group. Playback of a group is controlled through any of its
 * players, usually the leader: {@code heos.player(group.id())}. Obtain it with
 * {@link HeosSystem#group(long)}.
 */
public final class Group {

    private final long id;
    private final Requests requests;

    Group(long id, Requests requests) {
        this.id = id;
        this.requests = requests;
    }

    /** The group id ({@code gid}), which is the player id of the leader. */
    public long id() {
        return id;
    }

    public CompletableFuture<GroupInfo> info() {
        return requests.send(command(Commands.GROUP_GET_GROUP_INFO, "gid", id),
                response -> ModelParser.group(response.payload()));
    }

    public CompletableFuture<Integer> volume() {
        return requests.send(command(Commands.GROUP_GET_VOLUME, "gid", id),
                response -> response.message().requireInt("level"));
    }

    /** Sets the group volume; the device keeps the relative volume of the members. */
    public CompletableFuture<Void> setVolume(int level) {
        checkRange(level, 0, 100, "level");
        return requests.run(command(Commands.GROUP_SET_VOLUME, "gid", id, "level", level));
    }

    public CompletableFuture<Void> volumeUp() {
        return volumeUp(Player.DEFAULT_VOLUME_STEP);
    }

    public CompletableFuture<Void> volumeUp(int step) {
        checkRange(step, 1, 10, "step");
        return requests.run(command(Commands.GROUP_VOLUME_UP, "gid", id, "step", step));
    }

    public CompletableFuture<Void> volumeDown() {
        return volumeDown(Player.DEFAULT_VOLUME_STEP);
    }

    public CompletableFuture<Void> volumeDown(int step) {
        checkRange(step, 1, 10, "step");
        return requests.run(command(Commands.GROUP_VOLUME_DOWN, "gid", id, "step", step));
    }

    public CompletableFuture<Boolean> isMuted() {
        return requests.send(command(Commands.GROUP_GET_MUTE, "gid", id),
                response -> ModelParser.onOff(response.message().require("state")));
    }

    public CompletableFuture<Void> setMute(boolean muted) {
        return requests.run(command(Commands.GROUP_SET_MUTE, "gid", id, "state", onOff(muted)));
    }

    public CompletableFuture<Void> toggleMute() {
        return requests.run(command(Commands.GROUP_TOGGLE_MUTE, "gid", id));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Group other && other.id == id && other.requests == requests;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Group[" + id + "]";
    }
}
