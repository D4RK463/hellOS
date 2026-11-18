// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MessageParametersTest {

    @Test
    void parsesKeyValuePairs() {
        MessageParameters params = MessageParameters.parse("pid=-409995282&level=30");

        assertThat(params.requireLong("pid")).isEqualTo(-409995282L);
        assertThat(params.requireInt("level")).isEqualTo(30);
        assertThat(params.asMap()).containsExactly(
                org.assertj.core.api.Assertions.entry("pid", "-409995282"),
                org.assertj.core.api.Assertions.entry("level", "30"));
        assertThat(params.parameterMap()).containsExactly(
                org.assertj.core.api.Assertions.entry("pid", "-409995282"),
                org.assertj.core.api.Assertions.entry("level", "30"));
        assertThatThrownBy(() -> params.parameterMap().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> params.asMap().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyMessage() {
        assertThat(MessageParameters.parse("").isEmpty()).isTrue();
        assertThat(MessageParameters.parse(null).isEmpty()).isTrue();
    }

    @Test
    void flagsWithoutValue() {
        MessageParameters signedIn = MessageParameters.parse("signed_in&un=user@example.com");

        assertThat(signedIn.has("signed_in")).isTrue();
        assertThat(signedIn.require("un")).isEqualTo("user@example.com");
        assertThat(MessageParameters.parse("signed_out").has("signed_out")).isTrue();
    }

    @Test
    void commandUnderProcessFlag() {
        MessageParameters params = MessageParameters.parse("command under process&sid=2&search=U2");

        assertThat(params.has(HeosMessage.UNDER_PROCESS_FLAG)).isTrue();
        assertThat(params.require("search")).isEqualTo("U2");
    }

    @Test
    void toleratesEmptySegmentsLikeTheSpecExample() {
        MessageParameters params = MessageParameters.parse("pid=1&&repeat=on_all&shuffle=off");

        assertThat(params.require("repeat")).isEqualTo("on_all");
        assertThat(params.require("shuffle")).isEqualTo("off");
    }

    @Test
    void valuesAreDecoded() {
        MessageParameters params = MessageParameters.parse("pid=1&name=Rock %26 Roll %3D 100%25");

        assertThat(params.require("name")).isEqualTo("Rock & Roll = 100%");
        assertThat(params.raw()).isEqualTo("pid=1&name=Rock %26 Roll %3D 100%25");
    }

    @Test
    void urlConsumesTheRest() {
        MessageParameters params = MessageParameters.parse("pid=1&url=http://host:49152/web/1.mp3?a=b&c=d");

        assertThat(params.require("url")).isEqualTo("http://host:49152/web/1.mp3?a=b&c=d");
        assertThat(params.has("c")).isFalse();
    }

    @Test
    void decimalNumbersAreTruncated() {
        assertThat(MessageParameters.parse("level=30.0").requireInt("level")).isEqualTo(30);
        assertThat(MessageParameters.parse("cur_pos=1234.9").requireLong("cur_pos")).isEqualTo(1234L);
    }

    @Test
    void optionalNumbers() {
        MessageParameters params = MessageParameters.parse("pid=1&gid=");

        assertThat(params.getInt("pid")).hasValue(1);
        assertThat(params.getInt("gid")).isEmpty();
        assertThat(params.getLong("missing")).isEmpty();
    }

    @Test
    void missingOrInvalidValuesFail() {
        MessageParameters params = MessageParameters.parse("pid=abc");

        assertThatThrownBy(() -> params.require("level")).isInstanceOf(HeosProtocolException.class);
        assertThatThrownBy(() -> params.requireInt("pid")).isInstanceOf(HeosProtocolException.class);
    }

    @Test
    void multiSearchStatisticsStayIntact() {
        MessageParameters params = MessageParameters.parse(
                "search=Tangerine Rays&sid=1,4,8&returned=74&count=74"
                        + "&stats=(10,2,2,2),(1,0,57,57)&errno=(13,0,2),(8,0,-1061)");

        assertThat(params.require("stats")).isEqualTo("(10,2,2,2),(1,0,57,57)");
        assertThat(params.require("errno")).isEqualTo("(13,0,2),(8,0,-1061)");
    }

    @Test
    void toStringMasksPassword() {
        MessageParameters params = MessageParameters.parse("eid=6&text=Invalid Credentials&un=user@example.com&pw=secret");

        assertThat(params.toString()).doesNotContain("secret").contains("pw=********");
    }
}
