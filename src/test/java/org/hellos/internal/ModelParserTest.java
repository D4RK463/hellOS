// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hellos.testing.Fixtures.message;

import java.util.List;

import org.hellos.cli.HeosProtocolException;
import org.hellos.cli.HeosResponseParser;
import org.hellos.cli.MessageParameters;
import org.hellos.model.AlbumMetadata;
import org.hellos.model.BrowseResult;
import org.hellos.model.GroupInfo;
import org.hellos.model.LineOutLevel;
import org.hellos.model.MediaItem;
import org.hellos.model.MediaType;
import org.hellos.model.MultiSearchResult;
import org.hellos.model.MusicSource;
import org.hellos.model.NetworkType;
import org.hellos.model.NowPlayingMedia;
import org.hellos.model.PlayerInfo;
import org.hellos.model.RepeatMode;
import org.hellos.model.SearchCriteria;
import org.hellos.model.SearchResult;
import org.hellos.model.ServiceOptions;
import org.hellos.model.VolumeControl;
import org.junit.jupiter.api.Test;

class ModelParserTest {

    @Test
    void players() {
        List<PlayerInfo> players = ModelParser.players(message("player.get_players"));

        assertThat(players).hasSize(3);
        PlayerInfo leader = players.get(0);
        assertThat(leader.playerId()).isEqualTo(-409995282L);
        assertThat(leader.isGroupLeader()).isTrue();
        assertThat(leader.network()).isEqualTo(NetworkType.WIRED);
        assertThat(leader.lineOut()).isEqualTo(LineOutLevel.VARIABLE);
        assertThat(leader.ipAddress()).hasValue("192.0.2.21");
        assertThat(leader.serial()).hasValue("SN0000001");

        PlayerInfo member = players.get(1);
        assertThat(member.playerId()).as("ids sent as strings like in the spec").isEqualTo(1083491543L);
        assertThat(member.groupId()).hasValue(-409995282L);
        assertThat(member.isGroupLeader()).isFalse();
        assertThat(member.lineOut()).isEqualTo(LineOutLevel.FIXED);
        assertThat(member.volumeControl()).isEqualTo(VolumeControl.NETWORK);

        PlayerInfo old = players.get(2);
        assertThat(old.isGrouped()).isFalse();
        assertThat(old.version()).isEmpty();
        assertThat(old.network()).isEqualTo(NetworkType.UNKNOWN);
        assertThat(old.lineOut()).isEqualTo(LineOutLevel.UNKNOWN);
    }

    @Test
    void playerInfo() {
        PlayerInfo info = ModelParser.player(message("player.get_player_info").payload());

        assertThat(info.name()).isEqualTo("Kitchen");
        assertThat(info.model()).isEqualTo("HEOS 1");
    }

    @Test
    void nowPlayingSong() {
        NowPlayingMedia media = ModelParser.nowPlaying(message("player.get_now_playing_media.song"));

        assertThat(media.type()).isEqualTo(MediaType.SONG);
        assertThat(media.song()).as("escaped characters are decoded").hasValue("Rock & Roll");
        assertThat(media.queueId()).hasValue(3);
        assertThat(media.sourceId()).hasValue(1024L);
        assertThat(media.albumId()).hasValue("a-9");
        assertThat(media.options()).isEmpty();
        assertThat(media.isEmpty()).isFalse();
    }

    @Test
    void nowPlayingStationWithOptions() {
        NowPlayingMedia media = ModelParser.nowPlaying(message("player.get_now_playing_media.station"));

        assertThat(media.isStation()).isTrue();
        assertThat(media.station()).hasValue("Example Radio");
        assertThat(media.album()).as("empty strings are absent").isEmpty();
        assertThat(media.imageUrl()).isEmpty();
        assertThat(media.hasOption(ServiceOptions.THUMBS_UP)).isTrue();
        assertThat(media.options()).extracting(o -> o.context()).containsOnly("play");
    }

    @Test
    void nowPlayingNothing() {
        assertThat(ModelParser.nowPlaying(message("player.get_now_playing_media.empty")).isEmpty()).isTrue();
    }

    @Test
    void quickSelectsAndUpdate() {
        assertThat(ModelParser.quickSelects(message("player.get_quickselects")))
                .extracting(q -> q.name()).containsExactly("Quick Select 1", "TV");
        assertThat(ModelParser.updateAvailable(message("player.check_update"))).isTrue();
    }

    @Test
    void playModeAndOnOff() {
        assertThat(ModelParser.playMode(MessageParameters.parse("pid=1&repeat=on_one&shuffle=on")))
                .satisfies(mode -> {
                    assertThat(mode.repeat()).isEqualTo(RepeatMode.ON_ONE);
                    assertThat(mode.shuffle()).isTrue();
                });
        assertThatThrownBy(() -> ModelParser.onOff("maybe")).isInstanceOf(HeosProtocolException.class);
    }

    @Test
    void groups() {
        GroupInfo group = ModelParser.groups(message("group.get_groups")).getFirst();

        assertThat(group.groupId()).isEqualTo(-409995282L);
        assertThat(group.leader()).hasValueSatisfying(l -> assertThat(l.name()).isEqualTo("Living Room"));
        assertThat(group.playerIds()).containsExactly(-409995282L, 1083491543L);
    }

    @Test
    void musicSources() {
        List<MusicSource> sources = ModelParser.musicSources(message("browse.get_music_sources"));

        assertThat(sources).extracting(MusicSource::sourceId).containsExactly(1L, 3L, 1024L, 1028L);
        assertThat(sources.get(0).available()).isTrue();
        assertThat(sources.get(0).serviceUsername()).hasValue("listener");
        assertThat(sources.get(1).available()).isFalse();
        assertThat(sources.get(2).type()).isEqualTo(MediaType.HEOS_SERVER);
    }

    @Test
    void browseFavorites() {
        BrowseResult result = ModelParser.browseResult(message("browse.browse.favorites"), 1028, null);

        assertThat(result.sourceId()).isEqualTo(1028);
        assertThat(result.containerId()).isEmpty();
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.items().getFirst().name()).isEqualTo("Classical & Jazz");
        assertThat(result.hasOption(ServiceOptions.REMOVE_FROM_FAVORITES)).isTrue();
    }

    @Test
    void browseAuxSourcesAndInputs() {
        BrowseResult aux = ModelParser.browseResult(message("browse.browse.aux"), 1027, null);
        MediaItem receiver = aux.items().getFirst();
        assertThat(receiver.isSource()).isTrue();
        assertThat(receiver.sourceId()).hasValue(546978854L);
        assertThat(aux.items().get(1).sourceId()).hasValue(-263109739L);

        BrowseResult inputs = ModelParser.browseResult(message("browse.browse.receiver_inputs"), 546978854, null);
        assertThat(inputs.items()).allMatch(MediaItem::isInput);
    }

    @Test
    void browseContainer() {
        BrowseResult result = ModelParser.browseResult(message("browse.browse.album"), 10, "LIBALBUM-1");

        assertThat(result.containerId()).hasValue("LIBALBUM-1");
        MediaItem track = result.items().get(0);
        assertThat(track.type()).isEqualTo(MediaType.SONG);
        assertThat(track.container()).isFalse();
        assertThat(track.playable()).isTrue();
        assertThat(track.mediaId()).hasValue("t-1");
        MediaItem playlist = result.items().get(1);
        assertThat(playlist.type()).isEqualTo(MediaType.PLAYLIST);
        assertThat(playlist.containerId()).hasValue("LIBPLAYLIST-7");
        assertThat(result.options()).anySatisfy(o -> {
            assertThat(o.id()).isEqualTo(ServiceOptions.CREATE_NEW_STATION);
            assertThat(o.searchCriteriaId()).hasValue(1);
        });
        assertThat(result.hasOption(ServiceOptions.PLAYABLE_CONTAINER)).isTrue();
    }

    @Test
    void searchCriteria() {
        List<SearchCriteria> criteria = ModelParser.searchCriteria(message("browse.get_search_criteria"));

        assertThat(criteria).hasSize(3);
        SearchCriteria track = criteria.get(2);
        assertThat(track.wildcard()).isTrue();
        assertThat(track.playable()).isTrue();
        assertThat(track.containerIdPrefix()).hasValue("SEARCHED_TRACKS-");
        assertThat(criteria.get(0).playable()).isFalse();
    }

    @Test
    void search() {
        SearchResult result = ModelParser.searchResult(message("browse.search"), 10, 3, "Rays");

        assertThat(result.criteriaId()).isEqualTo(3);
        assertThat(result.returned()).isEqualTo(1);
        assertThat(result.count()).isEqualTo(15);
        assertThat(result.items().getFirst().albumId()).hasValue("401192835");
    }

    @Test
    void multiSearch() {
        MultiSearchResult result = ModelParser.multiSearchResult(message("browse.multi_search"), "Rays");

        assertThat(result.sourceIds()).containsExactly(1L, 10L, 13L);
        assertThat(result.criteriaIds()).containsExactly(0, 1, 2, 3);
        assertThat(result.count()).isEqualTo(74);
        assertThat(result.items()).hasSize(2);
        assertThat(result.statistics()).containsExactly(
                new MultiSearchResult.Statistic(10, 2, 2, 2), new MultiSearchResult.Statistic(1, 0, 57, 57));
        assertThat(result.errors()).containsExactly(
                new MultiSearchResult.SourceError(13, 0, 2), new MultiSearchResult.SourceError(8, 0, -1061));
    }

    @Test
    void albumMetadata() {
        AlbumMetadata metadata = ModelParser.albumMetadata(message("browse.retrieve_metadata")).getFirst();

        assertThat(metadata.albumId()).isEqualTo("7890");
        assertThat(metadata.largestImage()).hasValueSatisfying(i -> assertThat(i.width()).isEqualTo(640));
    }

    @Test
    void accountStatus() {
        assertThat(ModelParser.accountStatus(MessageParameters.parse("signed_in&un=user@example.com")).username())
                .hasValue("user@example.com");
        assertThat(ModelParser.accountStatus(MessageParameters.parse("signed_out")).signedIn()).isFalse();
    }

    @Test
    void missingRequiredFieldsFail() {
        assertThatThrownBy(() -> ModelParser.players(HeosResponseParser.parse(
                "{\"heos\":{\"command\":\"player/get_players\",\"result\":\"success\",\"message\":\"\"},"
                        + "\"payload\":[{\"name\":\"No id\"}]}")))
                .isInstanceOf(HeosProtocolException.class);
    }
}
