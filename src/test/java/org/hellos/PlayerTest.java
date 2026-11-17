// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hellos.testing.FakeHeosServer.success;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommandException;
import org.hellos.cli.HeosErrorCode;
import org.hellos.internal.ModelParser;
import org.hellos.model.AddCriteria;
import org.hellos.model.BrowseResult;
import org.hellos.model.Input;
import org.hellos.model.MediaItem;
import org.hellos.model.MediaType;
import org.hellos.model.PlayMode;
import org.hellos.model.PlayState;
import org.hellos.model.QueueItem;
import org.hellos.model.Range;
import org.hellos.model.RepeatMode;
import org.hellos.model.SearchCriteria;
import org.hellos.testing.FakeHeosServer;
import org.hellos.testing.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerTest extends HeosSystemTestBase {

    private static final long PID = -409995282L;

    private Player player;

    @BeforeEach
    void setUpPlayer() {
        server.echo(Commands.PLAYER_SET_PLAY_STATE, Commands.PLAYER_SET_VOLUME, Commands.PLAYER_VOLUME_UP,
                Commands.PLAYER_VOLUME_DOWN, Commands.PLAYER_SET_MUTE, Commands.PLAYER_TOGGLE_MUTE,
                Commands.PLAYER_SET_PLAY_MODE, Commands.PLAYER_PLAY_NEXT, Commands.PLAYER_PLAY_PREVIOUS,
                Commands.PLAYER_PLAY_QUEUE, Commands.PLAYER_REMOVE_FROM_QUEUE, Commands.PLAYER_MOVE_QUEUE_ITEM,
                Commands.PLAYER_CLEAR_QUEUE, Commands.PLAYER_SAVE_QUEUE, Commands.PLAYER_SET_QUICKSELECT,
                Commands.PLAYER_PLAY_QUICKSELECT, Commands.BROWSE_PLAY_INPUT, Commands.BROWSE_PLAY_STREAM,
                Commands.BROWSE_PLAY_PRESET, Commands.BROWSE_ADD_TO_QUEUE, Commands.BROWSE_SET_SERVICE_OPTION);
        player = connect().player(PID);
    }

    @Test
    void playStateCommands() throws Exception {
        server.reply(Commands.PLAYER_GET_PLAY_STATE, success(Commands.PLAYER_GET_PLAY_STATE, "pid=" + PID + "&state=pause"));

        assertThat(player.playState().join()).isEqualTo(PlayState.PAUSE);
        player.play().join();
        assertThat(lastRequest(Commands.PLAYER_SET_PLAY_STATE).uri())
                .isEqualTo("heos://player/set_play_state?pid=-409995282&state=play");
        player.stop().join();
        assertThat(lastRequest(Commands.PLAYER_SET_PLAY_STATE).attributes()).containsEntry("state", "stop");
        player.next().join();
        player.previous().join();
        assertThat(server.count(Commands.PLAYER_PLAY_NEXT)).isEqualTo(1);
        assertThat(server.count(Commands.PLAYER_PLAY_PREVIOUS)).isEqualTo(1);
    }

    @Test
    void volume() throws Exception {
        server.reply(Commands.PLAYER_GET_VOLUME, success(Commands.PLAYER_GET_VOLUME, "pid=" + PID + "&level=42"));
        server.reply(Commands.PLAYER_GET_MUTE, success(Commands.PLAYER_GET_MUTE, "pid=" + PID + "&state=on"));

        assertThat(player.volume().join()).isEqualTo(42);
        assertThat(player.isMuted().join()).isTrue();
        player.setVolume(30).join();
        assertThat(lastRequest(Commands.PLAYER_SET_VOLUME).attributes()).containsEntry("level", "30");
        player.volumeUp().join();
        assertThat(lastRequest(Commands.PLAYER_VOLUME_UP).attributes()).containsEntry("step", "5");
        player.volumeDown(2).join();
        assertThat(lastRequest(Commands.PLAYER_VOLUME_DOWN).attributes()).containsEntry("step", "2");
        player.setMute(false).join();
        assertThat(lastRequest(Commands.PLAYER_SET_MUTE).attributes()).containsEntry("state", "off");
    }

    @Test
    void validatesArgumentsBeforeSending() {
        assertThatThrownBy(() -> player.setVolume(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.volumeUp(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.setQuickSelect(7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.playPreset(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.saveQueue("x".repeat(129))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.removeFromQueue(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(server.count(Commands.PLAYER_SET_VOLUME)).isZero();
    }

    @Test
    void deviceErrorsAreReported() {
        server.reply(Commands.PLAYER_GET_VOLUME, FakeHeosServer.failure(Commands.PLAYER_GET_VOLUME, 2, "ID Not Valid"));

        assertThatThrownBy(() -> player.volume().join()).cause()
                .isInstanceOfSatisfying(HeosCommandException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(HeosErrorCode.INVALID_ID));
    }

    @Test
    void playModeReadModifyWrite() throws Exception {
        server.reply(Commands.PLAYER_GET_PLAY_MODE,
                success(Commands.PLAYER_GET_PLAY_MODE, "pid=" + PID + "&repeat=on_all&shuffle=on"));

        assertThat(player.playMode().join()).isEqualTo(new PlayMode(RepeatMode.ON_ALL, true));
        player.setRepeat(RepeatMode.OFF).join();

        assertThat(lastRequest(Commands.PLAYER_SET_PLAY_MODE).attributes())
                .containsEntry("repeat", "off").containsEntry("shuffle", "on");
    }

    @Test
    void queueIsFetchedInPages() {
        server.on(Commands.PLAYER_GET_QUEUE, request -> {
            String[] range = request.attributes().get("range").split(",");
            int start = Integer.parseInt(range[0]);
            int end = Math.min(Integer.parseInt(range[1]), 149);
            StringBuilder payload = new StringBuilder("[");
            for (int i = start; i <= end; i++) {
                payload.append(i > start ? "," : "")
                        .append("{\"song\":\"Song ").append(i).append("\",\"qid\":").append(i + 1).append('}');
            }
            payload.append(']');
            return List.of(success(request.name(), "pid=" + PID + "&range=" + request.attributes().get("range")
                    + "&returned=" + (end - start + 1) + "&count=150", payload.toString()));
        });

        List<QueueItem> queue = player.queue().join();

        assertThat(queue).hasSize(150);
        assertThat(queue.getLast().queueId()).isEqualTo(150);
        assertThat(server.count(Commands.PLAYER_GET_QUEUE)).isEqualTo(2);
        assertThat(player.queue(Range.of(0, 9)).join()).hasSize(10);
    }

    @Test
    void queueCommands() throws Exception {
        player.playQueueItem(3).join();
        assertThat(lastRequest(Commands.PLAYER_PLAY_QUEUE).attributes()).containsEntry("qid", "3");
        player.removeFromQueue(4, 5, 6).join();
        assertThat(lastRequest(Commands.PLAYER_REMOVE_FROM_QUEUE).attributes()).containsEntry("qid", "4,5,6");
        player.moveQueueItems(List.of(2, 4), 6).join();
        assertThat(lastRequest(Commands.PLAYER_MOVE_QUEUE_ITEM).attributes())
                .containsEntry("sqid", "2,4").containsEntry("dqid", "6");
        player.saveQueue("Road & Trip").join();
        assertThat(lastRequest(Commands.PLAYER_SAVE_QUEUE).uri()).endsWith("name=Road %26 Trip");
        player.clearQueue().join();
        assertThat(server.count(Commands.PLAYER_CLEAR_QUEUE)).isEqualTo(1);
    }

    @Test
    void quickSelectsAndUpdate() throws Exception {
        server.reply(Commands.PLAYER_GET_QUICKSELECTS, Fixtures.line("player.get_quickselects"));
        server.reply(Commands.PLAYER_CHECK_UPDATE, Fixtures.line("player.check_update"));

        assertThat(player.quickSelects().join()).hasSize(2);
        assertThat(player.checkForUpdate().join()).isTrue();
        player.playQuickSelect(2).join();
        assertThat(lastRequest(Commands.PLAYER_PLAY_QUICKSELECT).attributes()).containsEntry("id", "2");
    }

    @Test
    void nowPlaying() {
        server.reply(Commands.PLAYER_GET_NOW_PLAYING_MEDIA, Fixtures.line("player.get_now_playing_media.station"));

        assertThat(player.nowPlaying().join().station()).hasValue("Example Radio");
    }

    @Test
    void playInputsUrlsAndPresets() throws Exception {
        player.playInput(Input.HDMI_ARC_1).join();
        assertThat(lastRequest(Commands.BROWSE_PLAY_INPUT).uri())
                .isEqualTo("heos://browse/play_input?pid=-409995282&input=inputs/hdmi_arc_1");

        player.playInputFrom(546978854L, Input.DVD).join();
        assertThat(lastRequest(Commands.BROWSE_PLAY_INPUT).attributes())
                .containsEntry("spid", "546978854").containsEntry("input", "inputs/dvd");

        player.playUrl("http://192.0.2.50:8000/stream.mp3?token=a&b=c").join();
        assertThat(lastRequest(Commands.BROWSE_PLAY_STREAM).uri())
                .isEqualTo("heos://browse/play_stream?pid=-409995282&url=http://192.0.2.50:8000/stream.mp3?token=a&b=c");

        player.playPreset(2).join();
        assertThat(lastRequest(Commands.BROWSE_PLAY_PRESET).attributes()).containsEntry("preset", "2");
    }

    @Test
    void playDispatchesByItemType() throws Exception {
        BrowseResult favorites = ModelParser.browseResult(Fixtures.message("browse.browse.favorites"), 1028, null);
        player.play(favorites, favorites.items().getFirst()).join();
        assertThat(lastRequest(Commands.BROWSE_PLAY_STREAM).attributes())
                .containsEntry("sid", "1028").containsEntry("mid", "s7332").containsEntry("name", "Classical & Jazz")
                .doesNotContainKey("cid");

        BrowseResult inputs = ModelParser.browseResult(Fixtures.message("browse.browse.receiver_inputs"), 546978854, null);
        player.play(inputs, inputs.items().get(1)).join();
        assertThat(lastRequest(Commands.BROWSE_PLAY_INPUT).attributes())
                .containsEntry("spid", "546978854").containsEntry("input", "inputs/dvd");

        BrowseResult album = ModelParser.browseResult(Fixtures.message("browse.browse.album"), 10, "LIBALBUM-1");
        player.play(album, album.items().get(0)).join();
        assertThat(lastRequest(Commands.BROWSE_ADD_TO_QUEUE).attributes())
                .containsEntry("sid", "10").containsEntry("cid", "LIBALBUM-1").containsEntry("mid", "t-1")
                .containsEntry("aid", "1");

        player.play(10, "LIBALBUM-1", album.items().get(1), AddCriteria.ADD_TO_END).join();
        assertThat(lastRequest(Commands.BROWSE_ADD_TO_QUEUE).attributes())
                .containsEntry("cid", "LIBPLAYLIST-7").containsEntry("aid", "3").doesNotContainKey("mid");
    }

    @Test
    void notPlayableItemsAreRejected() {
        MediaItem artist = new MediaItem("Artist", MediaType.ARTIST, true, false, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of("ART-1"), Optional.empty(), OptionalLong.empty());

        assertThatThrownBy(() -> player.play(10, null, artist, AddCriteria.PLAY_NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addSearchResultsToQueue() throws Exception {
        SearchCriteria tracks = new SearchCriteria("Track", 3, true, true, Optional.of("SEARCHED_TRACKS-"));

        player.addSearchResultsToQueue(2, tracks, "earth", AddCriteria.PLAY_NOW).join();

        assertThat(lastRequest(Commands.BROWSE_ADD_TO_QUEUE).attributes())
                .containsEntry("cid", "SEARCHED_TRACKS-earth").containsEntry("aid", "1");
        assertThatThrownBy(() -> player.addSearchResultsToQueue(2,
                new SearchCriteria("Artist", 1, false, false, Optional.empty()), "x", AddCriteria.PLAY_NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serviceOptions() throws Exception {
        player.thumbsUp(1).join();
        assertThat(lastRequest(Commands.BROWSE_SET_SERVICE_OPTION).attributes())
                .containsEntry("sid", "1").containsEntry("option", "11").containsEntry("pid", String.valueOf(PID));
        player.addNowPlayingToFavorites().join();
        assertThat(lastRequest(Commands.BROWSE_SET_SERVICE_OPTION).attributes())
                .containsEntry("option", "19").doesNotContainKey("sid");
    }

    @Test
    void rawSendAddsPlayerId() throws Exception {
        server.echo("player/some_new_command");

        player.send("player/some_new_command", "foo", "bar").join();

        assertThat(lastRequest("player/some_new_command").attributes())
                .isEqualTo(Map.of("pid", String.valueOf(PID), "foo", "bar"));
    }
}
