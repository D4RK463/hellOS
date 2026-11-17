// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.blocking;

import static org.hellos.blocking.Await.result;
import static org.hellos.blocking.Await.run;

import java.util.Objects;

import org.hellos.Group;
import org.hellos.model.GroupInfo;

/** Blocking variant of {@link Group}. */
public final class BlockingGroup {

    private final Group group;

    private BlockingGroup(Group group) {
        this.group = group;
    }

    public static BlockingGroup of(Group group) {
        return new BlockingGroup(Objects.requireNonNull(group, "group"));
    }

    public Group async() {
        return group;
    }

    public long id() {
        return group.id();
    }

    public GroupInfo info() {
        return result(group.info());
    }

    public int volume() {
        return result(group.volume());
    }

    public void setVolume(int level) {
        run(group.setVolume(level));
    }

    public void volumeUp() {
        run(group.volumeUp());
    }

    public void volumeUp(int step) {
        run(group.volumeUp(step));
    }

    public void volumeDown() {
        run(group.volumeDown());
    }

    public void volumeDown(int step) {
        run(group.volumeDown(step));
    }

    public boolean isMuted() {
        return result(group.isMuted());
    }

    public void setMute(boolean muted) {
        run(group.setMute(muted));
    }

    public void toggleMute() {
        run(group.toggleMute());
    }

    @Override
    public String toString() {
        return group.toString();
    }
}
