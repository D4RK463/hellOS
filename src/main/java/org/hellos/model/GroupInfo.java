// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A group of players as returned by {@code group/get_groups} and {@code group/get_group_info}.
 *
 * @param name    the group name
 * @param groupId the group id ({@code gid}), equal to the player id of the leader
 * @param members all players of the group, including the leader
 */
public record GroupInfo(String name, long groupId, List<Member> members) {

    public GroupInfo {
        Objects.requireNonNull(name, "name");
        members = List.copyOf(members);
    }

    public Optional<Member> leader() {
        return members.stream().filter(m -> m.role() == Role.LEADER).findFirst();
    }

    /** The player ids of all members, leader first. */
    public List<Long> playerIds() {
        return members.stream()
                .sorted((a, b) -> Boolean.compare(b.role() == Role.LEADER, a.role() == Role.LEADER))
                .map(Member::playerId)
                .toList();
    }

    /** A player in a group. */
    public record Member(String name, long playerId, Role role) {
        public Member {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(role, "role");
        }
    }

    /** Role of a player in a group. */
    public enum Role {
        LEADER,
        MEMBER;

        public static Role fromWire(String value) {
            return "leader".equals(value) ? LEADER : MEMBER;
        }
    }
}
