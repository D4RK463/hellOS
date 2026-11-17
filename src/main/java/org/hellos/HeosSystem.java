// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static java.lang.System.Logger.Level.WARNING;
import static org.hellos.Requests.command;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommand;
import org.hellos.cli.HeosMessage;
import org.hellos.connection.ConnectionListener;
import org.hellos.connection.ConnectionState;
import org.hellos.connection.HeosClient;
import org.hellos.discovery.DiscoveredDevice;
import org.hellos.discovery.HeosDiscovery;
import org.hellos.discovery.HeosDiscoveryException;
import org.hellos.event.HeosEvent;
import org.hellos.internal.EventParser;
import org.hellos.internal.ModelParser;
import org.hellos.model.GroupInfo;
import org.hellos.model.PlayerInfo;

/**
 * Entry point of the high-level API: a HEOS system reached through one of its devices.
 *
 * <pre>{@code
 * try (HeosSystem heos = HeosSystem.builder().host("192.168.1.20").build()) {
 *     heos.connect().join();
 *     for (PlayerInfo info : heos.players().join()) {
 *         System.out.println(info.name() + ": " + heos.player(info.playerId()).volume().join());
 *     }
 *     heos.addEventListener(HeosEvent.PlayerVolumeChanged.class,
 *             e -> System.out.println(e.playerId() + " -> " + e.level()));
 * }
 * }</pre>
 *
 * <p>All methods are asynchronous. For a blocking API wrap the system with
 * {@link org.hellos.blocking.BlockingHeosSystem#of(HeosSystem)}.
 *
 * <p>The API does not cache state; every call sends a command. Listen to {@link HeosEvent}s to
 * keep a local state up to date.
 */
public final class HeosSystem implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(HeosSystem.class.getName());

    private final HeosClient client;
    private final Credentials credentials;
    private final Requests requests;
    private final Account account;
    private final Browse browse;

    private HeosSystem(HeosClient client, Credentials credentials) {
        this.client = client;
        this.credentials = credentials;
        this.requests = new Requests(client);
        this.account = new Account(requests);
        this.browse = new Browse(requests);
        if (credentials != null) {
            client.addConnectionListener(new SignInAfterReconnect());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Wraps an existing client. {@link #close()} closes the client. */
    public static HeosSystem of(HeosClient client) {
        return new HeosSystem(Objects.requireNonNull(client, "client"), null);
    }

    /**
     * Discovers HEOS devices and connects to the system. All devices found are used for failover.
     * Fails with {@link HeosDiscoveryException} if no device answers within the timeout.
     */
    public static CompletableFuture<HeosSystem> discoverAndConnect(Duration timeout) {
        return HeosDiscovery.discover(timeout).thenCompose(devices -> {
            if (devices.isEmpty()) {
                throw new HeosDiscoveryException("No HEOS device found within " + timeout.toMillis() + " ms");
            }
            HeosSystem system = builder().hosts(devices.stream().map(DiscoveredDevice::host).toList()).build();
            return system.connect().whenComplete((connected, failure) -> {
                if (failure != null) {
                    system.close();
                }
            });
        });
    }

    /**
     * Connects and, if credentials were configured, signs in (unless that account is already
     * signed in). The future fails if the connection or the sign-in fails; in the latter case the
     * system stays connected.
     */
    public CompletableFuture<HeosSystem> connect() {
        CompletableFuture<Void> connected = client.connect();
        if (credentials != null) {
            return connected.thenCompose(v -> account.ensureSignedIn(credentials)).thenApply(status -> this);
        }
        return connected.thenApply(v -> this);
    }

    /** The low-level client, e.g. to send commands that are not covered by this API. */
    public HeosClient client() {
        return client;
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public Account account() {
        return account;
    }

    public Browse browse() {
        return browse;
    }

    // ---- players and groups ------------------------------------------------------------------

    public CompletableFuture<List<PlayerInfo>> players() {
        return requests.send(command(Commands.PLAYER_GET_PLAYERS), ModelParser::players);
    }

    /** A handle for the player with the given id. Does not check whether the player exists. */
    public Player player(long playerId) {
        return new Player(playerId, requests);
    }

    /** Finds a player by its name (case-insensitive). */
    public CompletableFuture<java.util.Optional<Player>> playerByName(String name) {
        Objects.requireNonNull(name, "name");
        return players().thenApply(players -> players.stream()
                .filter(info -> info.name().equalsIgnoreCase(name))
                .findFirst()
                .map(info -> player(info.playerId())));
    }

    public CompletableFuture<List<GroupInfo>> groups() {
        return requests.send(command(Commands.GROUP_GET_GROUPS), ModelParser::groups);
    }

    /** A handle for the group with the given id. Does not check whether the group exists. */
    public Group group(long groupId) {
        return new Group(groupId, requests);
    }

    /**
     * Creates a group or changes its members. The leader keeps playing, the members join.
     *
     * @return the group id, empty if only the leader was given (which ungroups)
     */
    public CompletableFuture<OptionalLong> setGroup(long leaderId, long... memberIds) {
        List<Long> ids = new ArrayList<>();
        ids.add(leaderId);
        Arrays.stream(memberIds).forEach(ids::add);
        return setGroup(ids);
    }

    /** @param playerIds leader first, then the members */
    public CompletableFuture<OptionalLong> setGroup(Collection<Long> playerIds) {
        return requests.send(command(Commands.GROUP_SET_GROUP, "pid", Requests.joinIds(playerIds, "playerIds")),
                response -> response.message().getLong("gid"));
    }

    /** Dissolves the group led by the given player. */
    public CompletableFuture<Void> ungroup(long leaderId) {
        return setGroup(leaderId).thenApply(gid -> null);
    }

    // ---- system ------------------------------------------------------------------------------

    /** Reboots the device the client is connected to. The connection is lost afterwards. */
    public CompletableFuture<Void> reboot() {
        return requests.run(command(Commands.SYSTEM_REBOOT));
    }

    /** Sends any command, for commands not covered by this API. */
    public CompletableFuture<HeosMessage> send(HeosCommand command) {
        return requests.send(command);
    }

    // ---- events ------------------------------------------------------------------------------

    /** Receives all change events, typed. */
    public HeosClient.Registration addEventListener(Consumer<HeosEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        return client.addEventListener(message -> listener.accept(EventParser.parse(message)));
    }

    /** Receives change events of one type, e.g. {@code HeosEvent.PlayerVolumeChanged.class}. */
    public <T extends HeosEvent> HeosClient.Registration addEventListener(Class<T> type, Consumer<? super T> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        return addEventListener(event -> {
            if (type.isInstance(event)) {
                listener.accept(type.cast(event));
            }
        });
    }

    public HeosClient.Registration addConnectionListener(ConnectionListener listener) {
        return client.addConnectionListener(listener);
    }

    /** Closes the connection. */
    @Override
    public void close() {
        client.close();
    }

    /** Re-checks the sign-in after a reconnect, the device may have been restarted. */
    private final class SignInAfterReconnect implements ConnectionListener {
        private ConnectionState previous = ConnectionState.DISCONNECTED;

        @Override
        public void onStateChanged(ConnectionState state, String host, org.hellos.cli.HeosException cause) {
            if (state == ConnectionState.CONNECTED && previous == ConnectionState.RECONNECTING) {
                account.ensureSignedIn(credentials).exceptionally(e -> {
                    LOG.log(WARNING, "Sign-in after reconnect failed: {0}", e.getMessage());
                    return null;
                });
            }
            previous = state;
        }
    }

    /** Builder for {@link HeosSystem}. */
    public static final class Builder {

        private final HeosClient.Builder clientBuilder = HeosClient.builder();
        private Credentials credentials;

        private Builder() {
        }

        /** Adds a host; further hosts are used for failover. */
        public Builder host(String host) {
            clientBuilder.host(host);
            return this;
        }

        public Builder hosts(Collection<String> hosts) {
            clientBuilder.hosts(hosts);
            return this;
        }

        /** Signs in with this account on connect and after reconnects. */
        public Builder credentials(String username, String password) {
            return credentials(new Credentials(username, password));
        }

        public Builder credentials(Credentials credentials) {
            this.credentials = Objects.requireNonNull(credentials, "credentials");
            return this;
        }

        /** Access to all connection settings like timeouts, heartbeat and reconnect. */
        public Builder configureClient(Consumer<HeosClient.Builder> configurer) {
            configurer.accept(clientBuilder);
            return this;
        }

        public HeosSystem build() {
            return new HeosSystem(clientBuilder.build(), credentials);
        }
    }
}
