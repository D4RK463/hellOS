// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hellos.testing.FakeHeosServer.success;

import java.io.IOException;
import java.time.Duration;

import org.hellos.HeosSystem;
import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommandException;
import org.hellos.cli.HeosConnectionException;
import org.hellos.cli.HeosTimeoutException;
import org.hellos.testing.FakeHeosServer;
import org.hellos.testing.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockingHeosSystemTest {

    private FakeHeosServer server;
    private BlockingHeosSystem heos;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeHeosServer();
        server.reply(Commands.PLAYER_GET_PLAYERS, Fixtures.line("player.get_players"));
        server.reply(Commands.PLAYER_GET_VOLUME, success(Commands.PLAYER_GET_VOLUME, "pid=1083491543&level=12"));
        server.echo(Commands.PLAYER_SET_VOLUME);
        HeosSystem system = HeosSystem.builder().host(server.host())
                .configureClient(c -> c.port(server.port()).heartbeatInterval(Duration.ZERO)
                        .commandTimeout(Duration.ofMillis(500)))
                .build();
        heos = BlockingHeosSystem.of(system).connect();
    }

    @AfterEach
    void tearDown() throws IOException {
        heos.close();
        server.close();
    }

    @Test
    void returnsPlainValues() {
        BlockingPlayer kitchen = heos.playerByName("Kitchen").orElseThrow();

        assertThat(heos.players()).hasSize(3);
        assertThat(kitchen.volume()).isEqualTo(12);
        kitchen.setVolume(30);
        assertThat(server.count(Commands.PLAYER_SET_VOLUME)).isEqualTo(1);
    }

    @Test
    void throwsUnwrappedExceptions() {
        server.reply(Commands.PLAYER_GET_MUTE, FakeHeosServer.failure(Commands.PLAYER_GET_MUTE, 2, "ID Not Valid"));
        server.reply(Commands.PLAYER_GET_PLAY_STATE); // no answer

        assertThatThrownBy(() -> heos.player(99).isMuted()).isInstanceOf(HeosCommandException.class);
        assertThatThrownBy(() -> heos.player(99).playState()).isInstanceOf(HeosTimeoutException.class);
        assertThatThrownBy(() -> heos.player(1).setVolume(200)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectFailureIsThrown() throws IOException {
        int port = server.port();
        server.close();

        assertThatThrownBy(() -> BlockingHeosSystem.of(HeosSystem.builder().host("127.0.0.1")
                .configureClient(c -> c.port(port).connectTimeout(Duration.ofMillis(300))).build()).connect())
                .isInstanceOf(HeosConnectionException.class);
    }
}
