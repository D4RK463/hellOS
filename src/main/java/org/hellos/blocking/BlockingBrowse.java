// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.blocking;

import static org.hellos.blocking.Await.result;
import static org.hellos.blocking.Await.run;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.hellos.Browse;
import org.hellos.cli.HeosMessage;
import org.hellos.model.AlbumMetadata;
import org.hellos.model.BrowseResult;
import org.hellos.model.MediaItem;
import org.hellos.model.MultiSearchResult;
import org.hellos.model.MusicSource;
import org.hellos.model.Range;
import org.hellos.model.SearchCriteria;
import org.hellos.model.SearchResult;

/** Blocking variant of {@link Browse}. */
public final class BlockingBrowse {

    private final Browse browse;

    private BlockingBrowse(Browse browse) {
        this.browse = browse;
    }

    public static BlockingBrowse of(Browse browse) {
        return new BlockingBrowse(Objects.requireNonNull(browse, "browse"));
    }

    public Browse async() {
        return browse;
    }

    public List<MusicSource> musicSources() {
        return result(browse.musicSources());
    }

    public List<MusicSource> musicSources(boolean refresh) {
        return result(browse.musicSources(refresh));
    }

    public MusicSource sourceInfo(long sourceId) {
        return result(browse.sourceInfo(sourceId));
    }

    public BrowseResult browse(long sourceId) {
        return result(browse.browse(sourceId));
    }

    public BrowseResult browse(long sourceId, String containerId) {
        return result(browse.browse(sourceId, containerId));
    }

    public BrowseResult browse(long sourceId, String containerId, Range range) {
        return result(browse.browse(sourceId, containerId, range));
    }

    public BrowseResult browse(BrowseResult parent, MediaItem item) {
        return result(browse.browse(parent, item));
    }

    public BrowseResult browseAll(long sourceId, String containerId) {
        return result(browse.browseAll(sourceId, containerId));
    }

    public List<SearchCriteria> searchCriteria(long sourceId) {
        return result(browse.searchCriteria(sourceId));
    }

    public SearchResult search(long sourceId, int criteriaId, String search) {
        return result(browse.search(sourceId, criteriaId, search));
    }

    public SearchResult search(long sourceId, int criteriaId, String search, Range range) {
        return result(browse.search(sourceId, criteriaId, search, range));
    }

    public MultiSearchResult multiSearch(String search, Collection<Long> sourceIds, Collection<Integer> criteriaIds) {
        return result(browse.multiSearch(search, sourceIds, criteriaIds));
    }

    public void renamePlaylist(String containerId, String newName) {
        run(browse.renamePlaylist(containerId, newName));
    }

    public void deletePlaylist(String containerId) {
        run(browse.deletePlaylist(containerId));
    }

    public Optional<AlbumMetadata> albumMetadata(long sourceId, String albumId) {
        return result(browse.albumMetadata(sourceId, albumId));
    }

    public void addToFavorites(long sourceId, String mediaId, String name) {
        run(browse.addToFavorites(sourceId, mediaId, name));
    }

    public void removeFromFavorites(String mediaId) {
        run(browse.removeFromFavorites(mediaId));
    }

    public BrowseResult createStation(long sourceId, String name, int criteriaId, Range range) {
        return result(browse.createStation(sourceId, name, criteriaId, range));
    }

    public HeosMessage setServiceOption(int optionId, Map<String, ?> attributes) {
        return result(browse.setServiceOption(optionId, attributes));
    }
}
