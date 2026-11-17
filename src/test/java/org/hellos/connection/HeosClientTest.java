// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hellos.testing.Wait.waitUntil;
import static org.hellos.testing.FakeHeosServer.event;
import static org.hellos.testing.FakeHeosServer.success;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.hellos.cli.Commands;
import org.hellos.cli.Events;
import org.hellos.cli.HeosCommand;
import org.hellos.cli.HeosConnectionException;
import org.hellos.cli.HeosMessage;
import org.hellos.testing.FakeHeosServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeosClientTest {

    private FakeHeosServer server;
    private HeosClient client;
    private final List<ConnectionState> states = new CopyOnWriteArrayList<>();
    private final List<HeosMessage> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeHeosServer();
        server.reply(Commands.PLAYER_GET_PLAYERS, success(Commands.PLAYER_GET_PLAYERS, "", "[]"));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
        server.close();
    }

    private HeosClient.Builder builder() {
        return HeosClient.builder()
                .host(server.host())
                .port(server.port())
                .connectTimeout(Duration.ofSeconds(1))
                .commandTimeout(Duration.ofSeconds(1))
                .heartbeatInterval(Duration.ZERO)
                .reconnectDelay(Duration.ofMillis(50));
    }

    private HeosClient start(HeosClient.Builder builder) throws Exception {
        client = builder.build();
        client.addConnectionListener((state, host, cause) -> states.add(state));
        client.addEventListener(events::add);
        client.connect().get(3, TimeUnit.SECONDS);
        return client;
    }

    @Test
    void connectsWithSeparateEventConnection() throws Exception {
        start(builder());

        assertThat(client.state()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(client.currentHost()).hasValue(server.host());
        assertThat(server.connectionCount()).isEqualTo(2);
        assertThat(server.registeredConnectionCount()).isEqualTo(1);
        assertThat(client.send(HeosCommand.of(Commands.PLAYER_GET_PLAYERS)).get(2, TimeUnit.SECONDS).isSuccess())
                .isTrue();
        waitUntil(() -> states.equals(List.of(ConnectionState.CONNECTING, ConnectionState.CONNECTED)));
    }

    @Test
    void withoutChangeEventsOnlyOneConnectionIsOpened() throws Exception {
        start(builder().changeEvents(false));
        // a roundtrip guarantees that the fake server has accepted the connection
        client.send(HeosCommand.of(Commands.PLAYER_GET_PLAYERS)).get(2, TimeUnit.SECONDS);

        assertThat(server.connectionCount()).isEqualTo(1);
        assertThat(server.count(Commands.SYSTEM_REGISTER_FOR_CHANGE_EVENTS)).isZero();
    }

    @Test
    void deliversEventsToListeners() throws Exception {
        start(builder());
        List<HeosMessage> volumeEvents = new CopyOnWriteArrayList<>();
        client.addEventListener(Events.PLAYER_VOLUME_CHANGED, volumeEvents::add);

        server.pushEvent(event(Events.PLAYER_STATE_CHANGED, "pid=1&state=play"));
        server.pushEvent(event(Events.PLAYER_VOLUME_CHANGED, "pid=1&level=20&mute=off"));

        waitUntil(() -> events.size() == 2 && volumeEvents.size() == 1);
        assertThat(events).extracting(HeosMessage::command)
                .containsExactly(Events.PLAYER_STATE_CHANGED, Events.PLAYER_VOLUME_CHANGED);
    }

    @Test
    void removedListenerReceivesNothing() throws Exception {
        start(builder());
        List<HeosMessage> received = new CopyOnWriteArrayList<>();
        client.addEventListener(received::add).close();

        server.pushEvent(event(Events.PLAYERS_CHANGED, null));

        waitUntil(() -> events.size() == 1);
        assertThat(received).isEmpty();
    }

    @Test
    void sendFailsWhenNotConnected() {
        client = builder().build();

        assertThatThrownBy(() -> client.send(HeosCommand.of(Commands.PLAYER_GET_PLAYERS)).join())
                .cause().isInstanceOf(HeosConnectionException.class);
    }

    @Test
    void connectFailsWhenNoHostIsReachable() throws Exception {
        int unusedPort = unusedPort();
        client = HeosClient.builder().host("127.0.0.1").port(unusedPort)
                .connectTimeout(Duration.ofMillis(500)).build();

        assertThatThrownBy(() -> client.connect().get(3, TimeUnit.SECONDS))
                .cause().isInstanceOf(HeosConnectionException.class);
        assertThat(client.state()).isEqualTo(ConnectionState.DISCONNECTED);
    }

    @Test
    void failsOverToNextHost() throws Exception {
        // the fake server only listens on 127.0.0.1, so 127.0.0.2 refuses the connection
        client = HeosClient.builder()
                .host("127.0.0.2")
                .host(server.host())
                .port(server.port())
                .connectTimeout(Duration.ofMillis(500))
                .heartbeatInterval(Duration.ZERO)
                .build();

        client.connect().get(5, TimeUnit.SECONDS);

        assertThat(client.currentHost()).hasValue(server.host());
    }

    @Test
    void reconnectsAfterConnectionLoss() throws Exception {
        start(builder());

        server.dropConnections();

        waitUntil(() -> states.equals(List.of(ConnectionState.CONNECTING, ConnectionState.CONNECTED,
                ConnectionState.RECONNECTING, ConnectionState.CONNECTED)));
        assertThat(client.isConnected()).isTrue();
        waitUntil(() -> server.registeredConnectionCount() == 1);
        assertThat(client.send(HeosCommand.of(Commands.PLAYER_GET_PLAYERS)).get(2, TimeUnit.SECONDS).isSuccess())
                .isTrue();
        assertThat(server.count(Commands.SYSTEM_REGISTER_FOR_CHANGE_EVENTS)).isEqualTo(2);
    }

    @Test
    void givesUpAfterMaxReconnectAttempts() throws Exception {
        start(builder().maxReconnectAttempts(2));

        server.close();

        // listeners are notified asynchronously, so wait for the notification rather than the state
        waitUntil(() -> states.contains(ConnectionState.DISCONNECTED));
        assertThat(client.state()).isEqualTo(ConnectionState.DISCONNECTED);
        assertThat(states).containsSubsequence(ConnectionState.RECONNECTING, ConnectionState.DISCONNECTED);
    }

    @Test
    void doesNotReconnectWhenDisabled() throws Exception {
        start(builder().reconnect(false));

        server.dropConnections();

        waitUntil(() -> states.contains(ConnectionState.DISCONNECTED));
        assertThat(states).doesNotContain(ConnectionState.RECONNECTING);
    }

    @Test
    void sendsHeartbeatWhenIdle() throws Exception {
        start(builder().heartbeatInterval(Duration.ofMillis(100)));

        server.awaitRequest(Commands.SYSTEM_HEART_BEAT, Duration.ofSeconds(2));
        waitUntil(() -> server.count(Commands.SYSTEM_HEART_BEAT) >= 2);
    }

    @Test
    void heartbeatTimeoutTriggersReconnect() throws Exception {
        start(builder().heartbeatInterval(Duration.ofMillis(100)).commandTimeout(Duration.ofMillis(200)));
        server.reply(Commands.SYSTEM_HEART_BEAT); // device stops answering

        waitUntil(() -> states.contains(ConnectionState.RECONNECTING));
    }

    @Test
    void closeStopsEverything() throws Exception {
        start(builder());

        client.close();

        assertThat(client.state()).isEqualTo(ConnectionState.CLOSED);
        waitUntil(() -> server.connectionCount() == 0);
        assertThatThrownBy(() -> client.connect().join()).cause().isInstanceOf(IllegalStateException.class);
        waitUntil(() -> states.getLast() == ConnectionState.CLOSED);
    }

    @Test
    void builderValidatesInput() {
        assertThatThrownBy(() -> HeosClient.builder().build()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HeosClient.builder().port(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HeosClient.builder().commandTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HeosClient.builder().host(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
