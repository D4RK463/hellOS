// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hellos.testing.FakeHeosServer.event;
import static org.hellos.testing.FakeHeosServer.success;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommand;
import org.hellos.cli.HeosCommandException;
import org.hellos.cli.HeosConnectionException;
import org.hellos.cli.HeosErrorCode;
import org.hellos.cli.HeosException;
import org.hellos.cli.HeosMessage;
import org.hellos.cli.HeosTimeoutException;
import org.hellos.testing.FakeHeosServer;
import static org.hellos.testing.Wait.waitUntil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeosConnectionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private FakeHeosServer server;
    private HeosConnection connection;
    private final List<HeosMessage> events = new CopyOnWriteArrayList<>();
    private final AtomicReference<HeosException> closeCause = new AtomicReference<>();
    private final CountDownLatch closed = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeHeosServer();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (connection != null) {
            connection.close();
        }
        server.close();
    }

    private HeosConnection open(Duration commandTimeout) {
        connection = HeosConnection.open(server.host(), server.port(), TIMEOUT, commandTimeout, events::add,
                cause -> {
                    closeCause.set(cause);
                    closed.countDown();
                });
        return connection;
    }

    private static HeosCommand getVolume(int pid) {
        return HeosCommand.builder(Commands.PLAYER_GET_VOLUME).attribute("pid", pid).build();
    }

    @Test
    void sendsCommandAndReceivesResponse() throws Exception {
        server.on(Commands.PLAYER_GET_VOLUME, r -> List.of(
                success(r.name(), "pid=" + r.attributes().get("pid") + "&level=25")));
        open(TIMEOUT);

        HeosMessage response = connection.send(getVolume(7)).get(2, TimeUnit.SECONDS);

        assertThat(response.message().requireInt("level")).isEqualTo(25);
        assertThat(server.requests().getFirst().uri()).isEqualTo("heos://player/get_volume?pid=7");
    }

    @Test
    void skipsCommandUnderProcess() throws Exception {
        server.reply(Commands.BROWSE_BROWSE,
                success(Commands.BROWSE_BROWSE, "command under process&sid=1028"),
                success(Commands.BROWSE_BROWSE, "sid=1028&returned=1&count=1", "[{\"name\":\"Radio\"}]"));
        open(TIMEOUT);

        HeosMessage response = connection.send(HeosCommand.builder(Commands.BROWSE_BROWSE)
                .attribute("sid", 1028).build()).get(2, TimeUnit.SECONDS);

        assertThat(response.isUnderProcess()).isFalse();
        assertThat(response.payload().get(0).get("name").asString()).isEqualTo("Radio");
    }

    @Test
    void failedResponseCompletesExceptionally() {
        server.reply(Commands.PLAYER_GET_VOLUME, FakeHeosServer.failure(Commands.PLAYER_GET_VOLUME, 2, "ID Not Valid"));
        open(TIMEOUT);

        assertThatThrownBy(() -> connection.send(getVolume(99)).join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(HeosCommandException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(HeosErrorCode.INVALID_ID));
    }

    @Test
    void timesOutAndContinuesWithNextCommand() throws Exception {
        server.reply(Commands.PLAYER_GET_VOLUME);
        server.reply(Commands.PLAYER_GET_MUTE, success(Commands.PLAYER_GET_MUTE, "pid=1&state=off"));
        open(Duration.ofMillis(200));

        CompletableFuture<HeosMessage> volume = connection.send(getVolume(1));
        CompletableFuture<HeosMessage> mute = connection.send(
                HeosCommand.builder(Commands.PLAYER_GET_MUTE).attribute("pid", 1).build());

        assertThatThrownBy(volume::join).cause().isInstanceOf(HeosTimeoutException.class);
        assertThat(mute.get(2, TimeUnit.SECONDS).message().require("state")).isEqualTo("off");
        assertThat(connection.isOpen()).isTrue();
    }

    @Test
    void commandsAreSentStrictlyOneAfterAnother() throws Exception {
        // the first command is answered later; the server keeps reading, so additional commands would be visible
        server.on(Commands.PLAYER_GET_VOLUME, r -> "1".equals(r.attributes().get("pid"))
                ? List.of()
                : List.of(success(r.name(), "pid=" + r.attributes().get("pid") + "&level=1")));
        open(TIMEOUT);

        List<CompletableFuture<HeosMessage>> futures = new ArrayList<>();
        for (int pid = 1; pid <= 5; pid++) {
            futures.add(connection.send(getVolume(pid)));
        }
        server.awaitRequest(Commands.PLAYER_GET_VOLUME, TIMEOUT);
        Thread.sleep(100);
        assertThat(server.count(Commands.PLAYER_GET_VOLUME)).as("only the first command is in flight").isEqualTo(1);

        server.pushToAll(success(Commands.PLAYER_GET_VOLUME, "pid=1&level=1"));
        for (int i = 0; i < futures.size(); i++) {
            assertThat(futures.get(i).get(2, TimeUnit.SECONDS).message().requireInt("pid")).isEqualTo(i + 1);
        }
    }

    @Test
    void deliversEvents() throws Exception {
        server.reply(Commands.SYSTEM_HEART_BEAT, success(Commands.SYSTEM_HEART_BEAT, ""));
        open(TIMEOUT);
        connection.send(HeosCommand.of(Commands.SYSTEM_HEART_BEAT)).get(2, TimeUnit.SECONDS);

        server.pushToAll(event("event/player_state_changed", "pid=1&state=play"));

        waitUntil(() -> !events.isEmpty());
        assertThat(events.getFirst().command()).isEqualTo("event/player_state_changed");
    }

    @Test
    void ignoresMalformedLinesAndUnexpectedResponses() throws Exception {
        server.on(Commands.PLAYER_GET_VOLUME, r -> List.of(
                "this is not json",
                success(Commands.PLAYER_GET_MUTE, "pid=1&state=on"),
                success(r.name(), "pid=1&level=3")));
        open(TIMEOUT);

        assertThat(connection.send(getVolume(1)).get(2, TimeUnit.SECONDS).message().requireInt("level")).isEqualTo(3);
    }

    @Test
    void rebootIsNotAnswered() throws Exception {
        open(Duration.ofMillis(200));

        HeosMessage response = connection.send(HeosCommand.of(Commands.SYSTEM_REBOOT)).get(2, TimeUnit.SECONDS);

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void refusesPrettifyJson() {
        open(TIMEOUT);

        assertThatThrownBy(() -> connection.send(HeosCommand.builder(Commands.SYSTEM_PRETTIFY_JSON_RESPONSE)
                .attribute("enable", "on").build()).join())
                .cause().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectionLossFailsPendingCommandsAndNotifies() throws Exception {
        server.reply(Commands.PLAYER_GET_VOLUME);
        open(Duration.ofSeconds(10));
        CompletableFuture<HeosMessage> pending = connection.send(getVolume(1));
        CompletableFuture<HeosMessage> queued = connection.send(getVolume(2));
        server.awaitRequest(Commands.PLAYER_GET_VOLUME, TIMEOUT);

        server.dropConnections();

        assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(closeCause.get()).isInstanceOf(HeosConnectionException.class);
        assertThatThrownBy(pending::join).cause().isInstanceOf(HeosConnectionException.class);
        assertThatThrownBy(queued::join).cause().isInstanceOf(HeosConnectionException.class);
        assertThat(connection.isOpen()).isFalse();
        assertThatThrownBy(() -> connection.send(getVolume(3)).join()).cause()
                .isInstanceOf(HeosConnectionException.class);
    }

    @Test
    void closeNotifiesWithoutCause() throws Exception {
        open(TIMEOUT);

        connection.close();

        assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(closeCause.get()).isNull();
    }

    @Test
    void connectFailsForUnreachablePort() throws IOException {
        int port = server.port();
        server.close();

        assertThatThrownBy(() -> HeosConnection.open("127.0.0.1", port, TIMEOUT, TIMEOUT, e -> { }, e -> { }))
                .isInstanceOf(HeosConnectionException.class);
    }
}
