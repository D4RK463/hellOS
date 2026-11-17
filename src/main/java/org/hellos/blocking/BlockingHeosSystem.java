// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.blocking;

import static org.hellos.blocking.Await.result;
import static org.hellos.blocking.Await.run;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

import org.hellos.HeosSystem;
import org.hellos.cli.HeosCommand;
import org.hellos.cli.HeosMessage;
import org.hellos.connection.ConnectionListener;
import org.hellos.connection.HeosClient;
import org.hellos.event.HeosEvent;
import org.hellos.model.GroupInfo;
import org.hellos.model.PlayerInfo;

/**
 * Blocking variant of {@link HeosSystem}. Every method waits for the device; failures are thrown
 * as {@link org.hellos.cli.HeosException} (no {@code CompletionException} wrapping). Blocking is
 * cheap on virtual threads.
 *
 * <pre>{@code
 * try (BlockingHeosSystem heos = BlockingHeosSystem.connect("192.168.1.20")) {
 *     BlockingPlayer kitchen = heos.playerByName("Kitchen").orElseThrow();
 *     kitchen.setVolume(25);
 *     kitchen.play();
 * }
 * }</pre>
 */
public final class BlockingHeosSystem implements AutoCloseable {

    private final HeosSystem system;

    private BlockingHeosSystem(HeosSystem system) {
        this.system = system;
    }

    public static BlockingHeosSystem of(HeosSystem system) {
        return new BlockingHeosSystem(Objects.requireNonNull(system, "system"));
    }

    /** Connects to a host with default settings. */
    public static BlockingHeosSystem connect(String host) {
        HeosSystem system = HeosSystem.builder().host(host).build();
        try {
            result(system.connect());
        } catch (RuntimeException e) {
            system.close();
            throw e;
        }
        return of(system);
    }

    /** Discovers devices and connects, see {@link HeosSystem#discoverAndConnect(Duration)}. */
    public static BlockingHeosSystem discoverAndConnect(Duration timeout) {
        return of(result(HeosSystem.discoverAndConnect(timeout)));
    }

    /** Connects (and signs in if credentials are configured). */
    public BlockingHeosSystem connect() {
        result(system.connect());
        return this;
    }

    /** The asynchronous system. */
    public HeosSystem async() {
        return system;
    }

    public HeosClient client() {
        return system.client();
    }

    public boolean isConnected() {
        return system.isConnected();
    }

    public BlockingAccount account() {
        return BlockingAccount.of(system.account());
    }

    public BlockingBrowse browse() {
        return BlockingBrowse.of(system.browse());
    }

    public List<PlayerInfo> players() {
        return result(system.players());
    }

    public BlockingPlayer player(long playerId) {
        return BlockingPlayer.of(system.player(playerId));
    }

    public Optional<BlockingPlayer> playerByName(String name) {
        return result(system.playerByName(name)).map(BlockingPlayer::of);
    }

    public List<GroupInfo> groups() {
        return result(system.groups());
    }

    public BlockingGroup group(long groupId) {
        return BlockingGroup.of(system.group(groupId));
    }

    public OptionalLong setGroup(long leaderId, long... memberIds) {
        return result(system.setGroup(leaderId, memberIds));
    }

    public OptionalLong setGroup(Collection<Long> playerIds) {
        return result(system.setGroup(playerIds));
    }

    public void ungroup(long leaderId) {
        run(system.ungroup(leaderId));
    }

    public void reboot() {
        run(system.reboot());
    }

    public HeosMessage send(HeosCommand command) {
        return result(system.send(command));
    }

    public HeosClient.Registration addEventListener(Consumer<HeosEvent> listener) {
        return system.addEventListener(listener);
    }

    public <T extends HeosEvent> HeosClient.Registration addEventListener(Class<T> type, Consumer<? super T> listener) {
        return system.addEventListener(type, listener);
    }

    public HeosClient.Registration addConnectionListener(ConnectionListener listener) {
        return system.addConnectionListener(listener);
    }

    @Override
    public void close() {
        system.close();
    }
}
