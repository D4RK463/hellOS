// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hellos.testing.Wait.waitUntil;
import static org.hellos.testing.FakeHeosServer.event;
import static org.hellos.testing.FakeHeosServer.success;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommandException;
import org.hellos.cli.HeosErrorCode;
import org.hellos.event.HeosEvent;
import org.hellos.model.AccountStatus;
import org.hellos.model.PlayerInfo;
import org.hellos.testing.FakeHeosServer;
import org.hellos.testing.Fixtures;
import org.junit.jupiter.api.Test;

class HeosSystemTest extends HeosSystemTestBase {

    @Test
    void playersAndGroups() throws Exception {
        server.reply(Commands.PLAYER_GET_PLAYERS, Fixtures.line("player.get_players"));
        server.reply(Commands.GROUP_GET_GROUPS, Fixtures.line("group.get_groups"));
        connect();

        assertThat(heos.players().join()).extracting(PlayerInfo::name)
                .containsExactly("Living Room", "Kitchen", "Old Link");
        assertThat(heos.playerByName("kitchen").join()).hasValueSatisfying(
                p -> assertThat(p.id()).isEqualTo(1083491543L));
        assertThat(heos.playerByName("Garage").join()).isEmpty();
        assertThat(heos.groups().join().getFirst().members()).hasSize(2);
    }

    @Test
    void setGroupAndUngroup() throws Exception {
        server.on(Commands.GROUP_SET_GROUP, request -> {
            String pids = request.attributes().get("pid");
            return List.of(pids.contains(",")
                    ? success(request.name(), "gid=3&name=Kitchen + Office&pid=" + pids)
                    : success(request.name(), "pid=" + pids));
        });
        connect();

        assertThat(heos.setGroup(3, 1, 4).join()).hasValue(3L);
        assertThat(lastRequest(Commands.GROUP_SET_GROUP).attributes()).containsEntry("pid", "3,1,4");
        assertThat(heos.setGroup(3).join()).isEmpty();
        heos.ungroup(3).join();
        assertThat(lastRequest(Commands.GROUP_SET_GROUP).attributes()).containsEntry("pid", "3");
    }

    @Test
    void groupVolume() throws Exception {
        server.reply(Commands.GROUP_GET_VOLUME, success(Commands.GROUP_GET_VOLUME, "gid=3&level=17"));
        server.reply(Commands.GROUP_GET_MUTE, success(Commands.GROUP_GET_MUTE, "gid=3&state=off"));
        server.echo(Commands.GROUP_SET_VOLUME, Commands.GROUP_VOLUME_UP, Commands.GROUP_TOGGLE_MUTE);
        connect();
        Group group = heos.group(3);

        assertThat(group.volume().join()).isEqualTo(17);
        assertThat(group.isMuted().join()).isFalse();
        group.setVolume(20).join();
        assertThat(lastRequest(Commands.GROUP_SET_VOLUME).attributes()).containsEntry("gid", "3").containsEntry("level", "20");
        group.volumeUp(3).join();
        assertThat(lastRequest(Commands.GROUP_VOLUME_UP).attributes()).containsEntry("step", "3");
        group.toggleMute().join();
    }

    @Test
    void typedEvents() throws Exception {
        connect();
        List<HeosEvent> all = new CopyOnWriteArrayList<>();
        List<HeosEvent.PlayerVolumeChanged> volume = new CopyOnWriteArrayList<>();
        heos.addEventListener(all::add);
        heos.addEventListener(HeosEvent.PlayerVolumeChanged.class, volume::add);

        server.pushEvent(event("event/player_state_changed", "pid=1&state=play"));
        server.pushEvent(event("event/player_volume_changed", "pid=1&level=20&mute=off"));
        server.pushEvent(event("event/brand_new_event", "x=1"));

        waitUntil(() -> all.size() == 3);
        assertThat(volume).containsExactly(new HeosEvent.PlayerVolumeChanged(1, 20, false));
        assertThat(all.get(2)).isInstanceOf(HeosEvent.UnknownEvent.class);
    }

    @Test
    void connectSignsInWithCredentials() throws Exception {
        server.reply(Commands.SYSTEM_CHECK_ACCOUNT, success(Commands.SYSTEM_CHECK_ACCOUNT, "signed_out"));
        server.on(Commands.SYSTEM_SIGN_IN, r -> List.of(
                success(r.name(), "signed_in&un=" + r.attributes().get("un"))));
        heos = builder().credentials("user@example.com", "s3cr&t").build();

        heos.connect().get(3, TimeUnit.SECONDS);

        FakeHeosServer.Request signIn = lastRequest(Commands.SYSTEM_SIGN_IN);
        assertThat(signIn.attributes()).containsEntry("un", "user@example.com").containsEntry("pw", "s3cr&t");
        assertThat(signIn.uri()).contains("pw=s3cr%26t");
    }

    @Test
    void connectSkipsSignInWhenAlreadySignedIn() throws Exception {
        server.reply(Commands.SYSTEM_CHECK_ACCOUNT, success(Commands.SYSTEM_CHECK_ACCOUNT, "signed_in&un=User@Example.com"));
        heos = builder().credentials("user@example.com", "secret").build();

        heos.connect().get(3, TimeUnit.SECONDS);

        assertThat(server.count(Commands.SYSTEM_CHECK_ACCOUNT)).isEqualTo(1);
        assertThat(server.count(Commands.SYSTEM_SIGN_IN)).isZero();
    }

    @Test
    void connectFailsWhenSignInFails() {
        server.reply(Commands.SYSTEM_CHECK_ACCOUNT, success(Commands.SYSTEM_CHECK_ACCOUNT, "signed_out"));
        server.reply(Commands.SYSTEM_SIGN_IN, FakeHeosServer.failure(Commands.SYSTEM_SIGN_IN, 10, "User not found"));
        heos = builder().credentials("nobody@example.com", "secret").build();

        assertThatThrownBy(() -> heos.connect().get(3, TimeUnit.SECONDS))
                .cause().isInstanceOfSatisfying(HeosCommandException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(HeosErrorCode.USER_NOT_FOUND));
        assertThat(heos.isConnected()).as("the connection itself stays up").isTrue();
    }

    @Test
    void signInIsCheckedAgainAfterReconnect() throws Exception {
        server.reply(Commands.SYSTEM_CHECK_ACCOUNT, success(Commands.SYSTEM_CHECK_ACCOUNT, "signed_in&un=user@example.com"));
        heos = builder().credentials("user@example.com", "secret").build();
        heos.connect().get(3, TimeUnit.SECONDS);

        server.dropConnections();

        waitUntil(() -> server.count(Commands.SYSTEM_CHECK_ACCOUNT) == 2);
    }

    @Test
    void accountCommands() throws Exception {
        server.reply(Commands.SYSTEM_CHECK_ACCOUNT, success(Commands.SYSTEM_CHECK_ACCOUNT, "signed_in&un=user@example.com"));
        server.reply(Commands.SYSTEM_SIGN_OUT, success(Commands.SYSTEM_SIGN_OUT, "signed_out"));
        connect();

        assertThat(heos.account().status().join()).isEqualTo(AccountStatus.signedIn("user@example.com"));
        heos.account().signOut().join();
        assertThat(server.count(Commands.SYSTEM_SIGN_OUT)).isEqualTo(1);
    }

    @Test
    void credentialsDoNotRevealPassword() {
        assertThat(new Credentials("user@example.com", "secret").toString()).doesNotContain("secret");
    }

    @Test
    void rebootIsSent() throws Exception {
        connect();

        // completes as soon as the command is written, the device does not answer
        heos.reboot().get(2, TimeUnit.SECONDS);

        assertThat(lastRequest(Commands.SYSTEM_REBOOT).uri()).isEqualTo("heos://system/reboot");
    }
}
