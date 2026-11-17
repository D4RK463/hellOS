// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeosCommandExceptionTest {

    private static HeosCommandException fromMessage(String command, String message) {
        return HeosCommandException.from(HeosResponseParser.parse(
                "{\"heos\":{\"command\":\"" + command + "\",\"result\":\"fail\",\"message\":\"" + message + "\"}}"));
    }

    @Test
    void mapsErrorCode() {
        HeosCommandException e = fromMessage("player/get_volume", "eid=2&text=ID Not Valid&pid=99");

        assertThat(e.errorCode()).isEqualTo(HeosErrorCode.INVALID_ID);
        assertThat(e.errorId()).isEqualTo(2);
        assertThat(e.errorText()).isEqualTo("ID Not Valid");
        assertThat(e.commandName()).isEqualTo("player/get_volume");
        assertThat(e.systemErrorNumber()).isEmpty();
        assertThat(e.getMessage()).contains("player/get_volume", "eid=2", "INVALID_ID");
    }

    @Test
    void systemErrorWithSyserrno() {
        HeosCommandException e = fromMessage("browse/browse", "eid=12&text=System error&syserrno=-1063&sid=1028");

        assertThat(e.errorCode()).isEqualTo(HeosErrorCode.SYSTEM_ERROR);
        assertThat(e.systemErrorNumber()).hasValue(-1063);
        assertThat(e.isUserNotLoggedIn()).isTrue();
    }

    @Test
    void userNotLoggedIn() {
        assertThat(fromMessage("browse/play_preset", "eid=8&text=User not logged in").isUserNotLoggedIn()).isTrue();
        assertThat(fromMessage("player/get_volume", "eid=2&text=x").isUserNotLoggedIn()).isFalse();
    }

    @Test
    void unknownErrorIds() {
        HeosCommandException e = fromMessage("player/get_volume", "eid=42&text=new");

        assertThat(e.errorCode()).isEqualTo(HeosErrorCode.UNKNOWN);
        assertThat(e.errorId()).isEqualTo(42);
        assertThat(fromMessage("player/get_volume", "text=no eid").errorCode()).isEqualTo(HeosErrorCode.UNKNOWN);
    }

    @Test
    void messageDoesNotContainPassword() {
        HeosCommandException e = fromMessage("system/sign_in", "eid=6&text=Invalid Credentials&un=a&pw=secret");

        assertThat(e.getMessage()).doesNotContain("secret");
        assertThat(e.parameters().toString()).doesNotContain("secret");
    }
}
