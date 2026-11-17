// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hellos.testing.FakeHeosServer.success;

import java.util.List;
import java.util.Map;

import org.hellos.cli.Commands;
import org.hellos.model.BrowseResult;
import org.hellos.model.MediaItem;
import org.hellos.model.MusicSources;
import org.hellos.model.Range;
import org.hellos.testing.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrowseTest extends HeosSystemTestBase {

    private Browse browse;

    @BeforeEach
    void setUpBrowse() {
        browse = connect().browse();
    }

    @Test
    void musicSources() throws Exception {
        server.reply(Commands.BROWSE_GET_MUSIC_SOURCES, Fixtures.line("browse.get_music_sources"));

        assertThat(browse.musicSources().join()).hasSize(4);
        assertThat(lastRequest(Commands.BROWSE_GET_MUSIC_SOURCES).attributes()).isEmpty();
        browse.musicSources(true).join();
        assertThat(lastRequest(Commands.BROWSE_GET_MUSIC_SOURCES).attributes()).containsEntry("refresh", "on");
    }

    @Test
    void browseTopLevelAndIntoItems() throws Exception {
        server.on(Commands.BROWSE_BROWSE, request -> List.of(switch (request.attributes().get("sid")) {
            case "1027" -> Fixtures.line("browse.browse.aux");
            case "546978854" -> Fixtures.line("browse.browse.receiver_inputs");
            default -> Fixtures.line("browse.browse.album");
        }));

        BrowseResult aux = browse.browse(MusicSources.AUX_INPUTS).join();
        BrowseResult receiver = browse.browse(aux, aux.items().getFirst()).join();

        assertThat(lastRequest(Commands.BROWSE_BROWSE).attributes()).isEqualTo(Map.of("sid", "1027"));
        assertThat(lastRequest(Commands.BROWSE_BROWSE).attributes()).isEqualTo(Map.of("sid", "546978854"));
        assertThat(receiver.items()).allMatch(MediaItem::isInput);

        BrowseResult album = browse.browse(10, "LIBALBUM-1", Range.of(0, 49)).join();
        assertThat(lastRequest(Commands.BROWSE_BROWSE).attributes())
                .isEqualTo(Map.of("sid", "10", "cid", "LIBALBUM-1", "range", "0,49"));
        browse.browse(album, album.items().get(1)).join();
        assertThat(lastRequest(Commands.BROWSE_BROWSE).attributes())
                .containsEntry("sid", "10").containsEntry("cid", "LIBPLAYLIST-7");
        assertThatThrownBy(() -> browse.browse(album, album.items().get(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void browseAllFetchesPages() {
        server.on(Commands.BROWSE_BROWSE, request -> {
            String[] range = request.attributes().get("range").split(",");
            int start = Integer.parseInt(range[0]);
            int end = Math.min(Integer.parseInt(range[1]), 119);
            StringBuilder payload = new StringBuilder("[");
            for (int i = start; i <= end; i++) {
                payload.append(i > start ? "," : "").append("{\"name\":\"Track ").append(i)
                        .append("\",\"type\":\"song\",\"container\":\"no\",\"playable\":\"yes\",\"mid\":\"m").append(i).append("\"}");
            }
            payload.append(']');
            return List.of(success(request.name(), "sid=10&cid=BIG&range=" + start + "," + end
                    + "&returned=" + (end - start + 1) + "&count=120", payload.toString()));
        });

        BrowseResult all = browse.browseAll(10, "BIG").join();

        assertThat(all.items()).hasSize(120);
        assertThat(all.items().getLast().mediaId()).hasValue("m119");
        assertThat(server.count(Commands.BROWSE_BROWSE)).isEqualTo(3);
    }

    @Test
    void browseAllStopsOnEmptyPageWhenCountIsUnknown() {
        server.on(Commands.BROWSE_BROWSE, request -> List.of(request.attributes().get("range").startsWith("0,")
                ? success(request.name(), "sid=10&returned=1&count=0",
                "[{\"name\":\"Only\",\"type\":\"song\",\"mid\":\"m\"}]")
                : success(request.name(), "sid=10&returned=0&count=0", "[]")));

        assertThat(browse.browseAll(10, "X").join().items()).hasSize(1);
        assertThat(server.count(Commands.BROWSE_BROWSE)).isEqualTo(2);
    }

    @Test
    void searchAndCriteria() throws Exception {
        server.reply(Commands.BROWSE_GET_SEARCH_CRITERIA, Fixtures.line("browse.get_search_criteria"));
        server.reply(Commands.BROWSE_SEARCH, Fixtures.line("browse.search"));

        assertThat(browse.searchCriteria(10).join()).hasSize(3);
        assertThat(browse.search(10, 3, "Rays & More").join().count()).isEqualTo(15);
        assertThat(lastRequest(Commands.BROWSE_SEARCH).uri()).contains("search=Rays %26 More");
        assertThatThrownBy(() -> browse.search(10, 3, "x".repeat(129))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiSearch() throws Exception {
        server.reply(Commands.BROWSE_MULTI_SEARCH, Fixtures.line("browse.multi_search"));

        assertThat(browse.multiSearch("Rays", List.of(1L, 10L), List.of()).join().errors()).hasSize(2);
        assertThat(lastRequest(Commands.BROWSE_MULTI_SEARCH).attributes())
                .isEqualTo(Map.of("search", "Rays", "sid", "1,10"));
    }

    @Test
    void playlistsFavoritesAndMetadata() throws Exception {
        server.echo(Commands.BROWSE_RENAME_PLAYLIST, Commands.BROWSE_DELETE_PLAYLIST, Commands.BROWSE_SET_SERVICE_OPTION);
        server.reply(Commands.BROWSE_RETRIEVE_METADATA, Fixtures.line("browse.retrieve_metadata"));

        browse.renamePlaylist("171566", "New Name").join();
        assertThat(lastRequest(Commands.BROWSE_RENAME_PLAYLIST).attributes())
                .isEqualTo(Map.of("sid", "1025", "cid", "171566", "name", "New Name"));
        browse.deletePlaylist("171566").join();
        browse.addToFavorites(3, "s6513639", "Folk Radio").join();
        assertThat(lastRequest(Commands.BROWSE_SET_SERVICE_OPTION).attributes())
                .containsEntry("option", "19").containsEntry("mid", "s6513639");
        browse.removeFromFavorites("s6513639").join();
        assertThat(lastRequest(Commands.BROWSE_SET_SERVICE_OPTION).attributes())
                .isEqualTo(Map.of("option", "20", "mid", "s6513639"));
        assertThat(browse.albumMetadata(6, "123456").join()).isPresent();
    }
}
