// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HeosCommandTest {

    @Test
    void commandWithoutAttributes() {
        HeosCommand command = HeosCommand.of(Commands.PLAYER_GET_PLAYERS);

        assertThat(command.toUri()).isEqualTo("heos://player/get_players");
        assertThat(command.toWireFormat()).isEqualTo("heos://player/get_players\r\n");
        assertThat(command.group()).isEqualTo("player");
        assertThat(command.command()).isEqualTo("get_players");
    }

    @Test
    void attributesKeepInsertionOrder() {
        HeosCommand command = HeosCommand.builder(Commands.PLAYER_SET_VOLUME)
                .attribute("pid", -409995282)
                .attribute("level", 30)
                .build();

        assertThat(command.toUri()).isEqualTo("heos://player/set_volume?pid=-409995282&level=30");
    }

    @Test
    void ofMapKeepsOrder() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("pid", 1);
        attributes.put("repeat", "on_all");
        attributes.put("shuffle", "off");

        assertThat(HeosCommand.of(Commands.PLAYER_SET_PLAY_MODE, attributes).toUri())
                .isEqualTo("heos://player/set_play_mode?pid=1&repeat=on_all&shuffle=off");
    }

    @Test
    void nullAttributesAreSkipped() {
        HeosCommand command = HeosCommand.builder(Commands.PLAYER_GET_QUEUE)
                .attribute("pid", 1)
                .attribute("range", null)
                .build();

        assertThat(command.toUri()).isEqualTo("heos://player/get_queue?pid=1");
    }

    @Test
    void specialCharactersAreEscaped() {
        HeosCommand command = HeosCommand.builder(Commands.PLAYER_SAVE_QUEUE)
                .attribute("pid", 1)
                .attribute("name", "Rock & Roll = 100%")
                .build();

        assertThat(command.toUri()).isEqualTo("heos://player/save_queue?pid=1&name=Rock %26 Roll %3D 100%25");
        assertThat(command.attribute("name")).isEqualTo("Rock & Roll = 100%");
    }

    @Test
    void urlIsLastAndNotEscaped() {
        HeosCommand command = HeosCommand.builder(Commands.BROWSE_PLAY_STREAM)
                .attribute("url", "http://example.com/stream.mp3?a=1&b=%20")
                .attribute("pid", 1)
                .build();

        assertThat(command.toUri())
                .isEqualTo("heos://browse/play_stream?pid=1&url=http://example.com/stream.mp3?a=1&b=%20");
    }

    @Test
    void passwordIsMasked() {
        HeosCommand command = HeosCommand.builder(Commands.SYSTEM_SIGN_IN)
                .attribute("un", "user@example.com")
                .attribute("pw", "secret&1")
                .build();

        assertThat(command.toUri()).isEqualTo("heos://system/sign_in?un=user@example.com&pw=secret%261");
        assertThat(command.toString()).isEqualTo("heos://system/sign_in?un=user@example.com&pw=********");
        assertThat(command.toMaskedUri()).doesNotContain("secret");
    }

    @Test
    void rejectsInvalidNames() {
        assertThatThrownBy(() -> HeosCommand.of("get_players")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HeosCommand.of("heos://player/get_players"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HeosCommand.of("player/get players")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLineBreaksToPreventCommandInjection() {
        assertThatThrownBy(() -> HeosCommand.builder(Commands.PLAYER_SAVE_QUEUE)
                .attribute("name", "x\r\nheos://system/reboot").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidAttributeNames() {
        assertThatThrownBy(() -> HeosCommand.builder(Commands.PLAYER_GET_VOLUME).attribute("p&id", 1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equality() {
        assertThat(HeosCommand.builder(Commands.PLAYER_GET_VOLUME).attribute("pid", 1).build())
                .isEqualTo(HeosCommand.of(Commands.PLAYER_GET_VOLUME, Map.of("pid", "1")))
                .hasSameHashCodeAs(HeosCommand.of(Commands.PLAYER_GET_VOLUME, Map.of("pid", 1)));
    }

    @Test
    void attributeMapKeepsOrderAndIsUnmodifiable() {
        HeosCommand command = HeosCommand.builder(Commands.PLAYER_SET_VOLUME).attribute("pid", 1).attribute("level", 20).build();

        assertThat(command.attributeMap()).containsExactly(
                org.assertj.core.api.Assertions.entry("pid", "1"),
                org.assertj.core.api.Assertions.entry("level", "20"));
        assertThat(command.attributes()).isEqualTo(command.attributeMap());
        assertThatThrownBy(() -> command.attributeMap().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> command.attributes().putFirst("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }
}
