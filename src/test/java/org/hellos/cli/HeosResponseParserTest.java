// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HeosResponseParserTest {

    @Test
    void parsesSuccessfulResponseWithPayload() {
        HeosMessage message = HeosResponseParser.parse("""
                {"heos":{"command":"player/get_players","result":"success","message":""},
                 "payload":[{"name":"Kitchen","pid":-409995282,"model":"HEOS 1","version":"3.34.620","network":"wifi"}]}""");

        assertThat(message.command()).isEqualTo(Commands.PLAYER_GET_PLAYERS);
        assertThat(message.isSuccess()).isTrue();
        assertThat(message.isEvent()).isFalse();
        assertThat(message.hasPayload()).isTrue();
        assertThat(message.hasOptions()).isFalse();
        assertThat(message.payload().get(0).get("pid").asLong()).isEqualTo(-409995282L);
    }

    @Test
    void parsesFailure() {
        HeosMessage message = HeosResponseParser.parse(
                "{\"heos\":{\"command\":\"player/get_volume\",\"result\":\"fail\",\"message\":\"eid=2&text=ID Not Valid&pid=5\"}}");

        assertThat(message.isFailure()).isTrue();
        assertThat(message.message().requireInt("eid")).isEqualTo(2);
    }

    @Test
    void parsesEventWithoutResult() {
        HeosMessage message = HeosResponseParser.parse(
                "{\"heos\":{\"command\":\"event/player_volume_changed\",\"message\":\"pid=1&level=20&mute=off\"}}");

        assertThat(message.isEvent()).isTrue();
        assertThat(message.result()).isEqualTo(HeosMessage.Result.NONE);
        assertThat(message.message().requireInt("level")).isEqualTo(20);
    }

    @Test
    void parsesEventWithoutMessage() {
        HeosMessage message = HeosResponseParser.parse("{\"heos\":{\"command\":\"event/players_changed\"}}");

        assertThat(message.command()).isEqualTo("event/players_changed");
        assertThat(message.message().isEmpty()).isTrue();
    }

    @Test
    void normalizesWhitespaceInCommandLikeTheSpecExamples() {
        HeosMessage message = HeosResponseParser.parse(
                "{\"heos\":{\"command\":\" player/ get_volume \",\"result\":\"success\",\"message\":\"pid=1&level=5\"}}");

        assertThat(message.command()).isEqualTo(Commands.PLAYER_GET_VOLUME);
    }

    @Test
    void detectsCommandUnderProcess() {
        HeosMessage message = HeosResponseParser.parse(
                "{\"heos\":{\"command\":\"browse/browse\",\"result\":\"success\",\"message\":\"command under process&sid=1028\"}}");

        assertThat(message.isUnderProcess()).isTrue();
    }

    @Test
    void parsesOptions() {
        HeosMessage message = HeosResponseParser.parse("""
                {"heos":{"command":"player/get_now_playing_media","result":"success","message":"pid=1"},
                 "payload":{"type":"station","song":"Song"},
                 "options":[{"play":[{"id":19,"name":"Add to HEOS Favorites"}]}]}""");

        assertThat(message.hasOptions()).isTrue();
        assertThat(message.options().get(0).get("play").get(0).get("id").asInt()).isEqualTo(19);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> HeosResponseParser.parse("not json")).isInstanceOf(HeosProtocolException.class);
        assertThatThrownBy(() -> HeosResponseParser.parse("[]")).isInstanceOf(HeosProtocolException.class);
        assertThatThrownBy(() -> HeosResponseParser.parse("{\"foo\":1}")).isInstanceOf(HeosProtocolException.class);
        assertThatThrownBy(() -> HeosResponseParser.parse("{\"heos\":{\"result\":\"success\"}}"))
                .isInstanceOf(HeosProtocolException.class);
        assertThatThrownBy(() -> HeosResponseParser.parse(
                "{\"heos\":{\"command\":\"player/get_volume\",\"result\":\"maybe\"}}"))
                .isInstanceOf(HeosProtocolException.class);
    }

    @Test
    void errorMessagesDoNotLeakPasswords() {
        assertThatThrownBy(() -> HeosResponseParser.parse("{broken heos://system/sign_in?un=a&pw=secret"))
                .isInstanceOf(HeosProtocolException.class)
                .message().doesNotContain("secret");
    }

    @Test
    void toStringIsSafeToLog() {
        HeosMessage message = HeosResponseParser.parse(
                "{\"heos\":{\"command\":\"system/sign_in\",\"result\":\"fail\",\"message\":\"eid=6&text=Invalid Credentials&un=a&pw=secret\"}}");

        assertThat(message.toString()).doesNotContain("secret");
    }
}
