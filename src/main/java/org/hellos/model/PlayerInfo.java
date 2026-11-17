// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A HEOS player as returned by {@code player/get_players} and {@code player/get_player_info}.
 *
 * @param name          the name configured in the HEOS app
 * @param playerId      the player id ({@code pid}), may be negative
 * @param groupId       the id of the group the player belongs to, empty if not grouped
 * @param model         the model name, e.g. {@code HEOS 1}
 * @param version       the firmware version, if reported
 * @param ipAddress     the IP address, if reported (not part of the specification, but sent by
 *                      current firmware)
 * @param network       wired or wifi
 * @param lineOut       line-out level type
 * @param volumeControl volume control when the line-out level is fixed
 * @param serial        the serial number, if reported
 */
public record PlayerInfo(
        String name,
        long playerId,
        OptionalLong groupId,
        String model,
        Optional<String> version,
        Optional<String> ipAddress,
        NetworkType network,
        LineOutLevel lineOut,
        VolumeControl volumeControl,
        Optional<String> serial) {

    public PlayerInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(ipAddress, "ipAddress");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(lineOut, "lineOut");
        Objects.requireNonNull(volumeControl, "volumeControl");
        Objects.requireNonNull(serial, "serial");
    }

    public boolean isGrouped() {
        return groupId.isPresent();
    }

    /** Whether this player leads its group. The group id is the player id of the leader. */
    public boolean isGroupLeader() {
        return groupId.isPresent() && groupId.getAsLong() == playerId;
    }
}
