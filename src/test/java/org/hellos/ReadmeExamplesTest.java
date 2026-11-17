// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hellos.testing.FakeHeosServer.event;
import static org.hellos.testing.Wait.waitUntil;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.hellos.blocking.BlockingHeosSystem;
import org.hellos.blocking.BlockingPlayer;
import org.hellos.cli.Commands;
import org.hellos.event.HeosEvent;
import org.hellos.model.Input;
import org.hellos.model.MusicSources;
import org.hellos.model.PlayState;
import org.hellos.model.PlayerInfo;
import org.hellos.testing.Fixtures;
import org.junit.jupiter.api.Test;

/** Keeps the examples of the README compiling and working. */
class ReadmeExamplesTest extends HeosSystemTestBase {

    @Test
    void blockingExample() throws Exception {
        server.reply(Commands.PLAYER_GET_PLAYERS, Fixtures.line("player.get_players"));
        server.echo(Commands.PLAYER_SET_VOLUME, Commands.BROWSE_PLAY_INPUT);
        List<String> output = new CopyOnWriteArrayList<>();

        try (BlockingHeosSystem heos = BlockingHeosSystem.of(builder().build()).connect()) {
            for (PlayerInfo info : heos.players()) {
                output.add(info.name() + " (" + info.model() + ")");
            }
            BlockingPlayer kitchen = heos.playerByName("Kitchen").orElseThrow();
            kitchen.setVolume(25);
            kitchen.playInput(Input.AUX_IN_1);
        }

        assertThat(output).contains("Kitchen (HEOS 1)");
        assertThat(lastRequest(Commands.BROWSE_PLAY_INPUT).uri())
                .isEqualTo("heos://browse/play_input?pid=1083491543&input=inputs/aux_in_1");
    }

    @Test
    void asyncExample() throws Exception {
        server.reply(Commands.BROWSE_BROWSE, Fixtures.line("browse.browse.favorites"));
        server.echo(Commands.BROWSE_PLAY_STREAM);
        heos = builder().configureClient(c -> c.connectTimeout(Duration.ofSeconds(1))).build();

        heos.connect()
                .thenCompose(h -> h.browse().browse(MusicSources.FAVORITES))
                .thenCompose(favorites -> heos.player(-409995282L).play(favorites, favorites.items().getFirst()))
                .join();

        assertThat(lastRequest(Commands.BROWSE_PLAY_STREAM).attributes()).containsEntry("mid", "s7332");
    }

    @Test
    void eventExample() throws Exception {
        connect();
        List<String> output = new CopyOnWriteArrayList<>();

        heos.addEventListener(event -> {
            switch (event) {
                case HeosEvent.PlayerStateChanged(long pid, PlayState state) -> output.add(pid + " " + state);
                case HeosEvent.PlayerNowPlayingChanged(long pid) -> output.add("now playing " + pid);
                default -> { }
            }
        });
        server.pushEvent(event("event/player_state_changed", "pid=7&state=play"));
        server.pushEvent(event("event/player_now_playing_changed", "pid=7"));

        waitUntil(() -> output.size() == 2);
        assertThat(output).containsExactly("7 PLAY", "now playing 7");
    }
}
