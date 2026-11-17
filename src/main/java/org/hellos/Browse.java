// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static org.hellos.Requests.checkLength;
import static org.hellos.Requests.command;
import static org.hellos.Requests.requireText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommand;
import org.hellos.cli.HeosMessage;
import org.hellos.internal.ModelParser;
import org.hellos.model.AlbumMetadata;
import org.hellos.model.BrowseResult;
import org.hellos.model.MediaItem;
import org.hellos.model.MultiSearchResult;
import org.hellos.model.MusicSource;
import org.hellos.model.MusicSources;
import org.hellos.model.Range;
import org.hellos.model.SearchCriteria;
import org.hellos.model.SearchResult;
import org.hellos.model.ServiceOptions;

/**
 * Browsing and searching music sources, managing HEOS playlists and favorites. Use
 * {@link Player#play(BrowseResult, MediaItem)} to play what you found.
 *
 * <pre>{@code
 * BrowseResult favorites = heos.browse().browse(MusicSources.FAVORITES).join();
 * heos.player(pid).play(favorites, favorites.items().getFirst()).join();
 * }</pre>
 */
public final class Browse {

    /** Records per page when fetching a whole container. */
    static final int PAGE_SIZE = 50;
    /** Safety limit for {@link #browseAll(long, String)}. */
    static final int MAX_ITEMS = 10_000;

    private final Requests requests;

    Browse(Requests requests) {
        this.requests = requests;
    }

    // ---- sources -----------------------------------------------------------------------------

    public CompletableFuture<List<MusicSource>> musicSources() {
        return musicSources(false);
    }

    /**
     * @param refresh ask the device to refresh the list, e.g. after services were added in the
     *                HEOS app. Causes a burst of {@code sources_changed} events.
     */
    public CompletableFuture<List<MusicSource>> musicSources(boolean refresh) {
        return requests.send(command(Commands.BROWSE_GET_MUSIC_SOURCES, "refresh", refresh ? "on" : null),
                ModelParser::musicSources);
    }

    public CompletableFuture<MusicSource> sourceInfo(long sourceId) {
        return requests.send(command(Commands.BROWSE_GET_SOURCE_INFO, "sid", sourceId), response -> {
            List<MusicSource> sources = ModelParser.musicSources(response);
            if (sources.isEmpty()) {
                throw new org.hellos.cli.HeosProtocolException("No source info for sid " + sourceId);
            }
            return sources.getFirst();
        });
    }

    // ---- browse ------------------------------------------------------------------------------

    /** The top level of a source, e.g. {@link MusicSources#FAVORITES}. */
    public CompletableFuture<BrowseResult> browse(long sourceId) {
        return browse(sourceId, null, null);
    }

    /** A container of a source. */
    public CompletableFuture<BrowseResult> browse(long sourceId, String containerId) {
        return browse(sourceId, containerId, null);
    }

    /**
     * A part of a container. Ranges are not supported at the top level of most sources (only for
     * favorites).
     *
     * @param containerId the container, {@code null} for the top level
     * @param range       the records to return, {@code null} for the device default (50 or 100)
     */
    public CompletableFuture<BrowseResult> browse(long sourceId, String containerId, Range range) {
        HeosCommand command = command(Commands.BROWSE_BROWSE, "sid", sourceId, "cid", containerId,
                "range", range == null ? null : range.wireValue());
        return requests.send(command, response -> ModelParser.browseResult(response, sourceId, containerId));
    }

    /**
     * Browses into an item of a previous result: a source item is browsed by its own source id, a
     * container within the parent's source.
     */
    public CompletableFuture<BrowseResult> browse(BrowseResult parent, MediaItem item) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(item, "item");
        if (item.isSource() && item.sourceId().isPresent()) {
            return browse(item.sourceId().getAsLong());
        }
        if (item.containerId().isPresent()) {
            return browse(parent.sourceId(), item.containerId().get());
        }
        throw new IllegalArgumentException("Item '" + item.name() + "' cannot be browsed");
    }

    /** All items of a container, fetched in pages. Stops at {@value #MAX_ITEMS} items. */
    public CompletableFuture<BrowseResult> browseAll(long sourceId, String containerId) {
        requireText(containerId, "containerId");
        return fetchAll(sourceId, containerId, Range.ofSize(0, PAGE_SIZE), new ArrayList<>());
    }

    private CompletableFuture<BrowseResult> fetchAll(long sourceId, String containerId, Range range,
                                                     List<MediaItem> collected) {
        return browse(sourceId, containerId, range).thenCompose(page -> {
            collected.addAll(page.items());
            boolean done = page.items().isEmpty()
                    || (page.count() > 0 && collected.size() >= page.count())
                    || collected.size() >= MAX_ITEMS;
            if (done) {
                return CompletableFuture.completedFuture(new BrowseResult(page.sourceId(), page.containerId(),
                        collected.size(), Math.max(page.count(), collected.size()), collected, page.options()));
            }
            return fetchAll(sourceId, containerId, Range.ofSize(range.start() + page.items().size(), PAGE_SIZE),
                    collected);
        });
    }

    // ---- search ------------------------------------------------------------------------------

    public CompletableFuture<List<SearchCriteria>> searchCriteria(long sourceId) {
        return requests.send(command(Commands.BROWSE_GET_SEARCH_CRITERIA, "sid", sourceId),
                ModelParser::searchCriteria);
    }

    public CompletableFuture<SearchResult> search(long sourceId, int criteriaId, String search) {
        return search(sourceId, criteriaId, search, null);
    }

    /**
     * @param search up to 128 characters; {@code *} is a wildcard if the criteria supports it
     * @param range  the records to return, {@code null} for the device default
     */
    public CompletableFuture<SearchResult> search(long sourceId, int criteriaId, String search, Range range) {
        checkLength(search, 128, "search");
        return requests.send(command(Commands.BROWSE_SEARCH, "sid", sourceId, "search", search,
                        "scid", criteriaId, "range", range == null ? null : range.wireValue()),
                response -> ModelParser.searchResult(response, sourceId, criteriaId, search));
    }

    /**
     * Searches several sources and criteria at once (spec v1.21, HEOS firmware 3.40 or newer).
     *
     * @param sourceIds   sources to search, empty for all
     * @param criteriaIds criteria to search, empty for all
     */
    public CompletableFuture<MultiSearchResult> multiSearch(String search, Collection<Long> sourceIds,
                                                            Collection<Integer> criteriaIds) {
        checkLength(search, 128, "search");
        return requests.send(command(Commands.BROWSE_MULTI_SEARCH, "search", search,
                        "sid", joinOrNull(sourceIds), "scid", joinOrNull(criteriaIds)),
                response -> ModelParser.multiSearchResult(response, search));
    }

    private static String joinOrNull(Collection<? extends Number> values) {
        return values == null || values.isEmpty()
                ? null
                : values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    // ---- playlists ---------------------------------------------------------------------------

    /** Renames a HEOS playlist (container of {@link MusicSources#PLAYLISTS}). */
    public CompletableFuture<Void> renamePlaylist(String containerId, String newName) {
        requireText(containerId, "containerId");
        checkLength(newName, 128, "newName");
        return requests.run(command(Commands.BROWSE_RENAME_PLAYLIST, "sid", MusicSources.PLAYLISTS,
                "cid", containerId, "name", newName));
    }

    public CompletableFuture<Void> deletePlaylist(String containerId) {
        requireText(containerId, "containerId");
        return requests.run(command(Commands.BROWSE_DELETE_PLAYLIST, "sid", MusicSources.PLAYLISTS,
                "cid", containerId));
    }

    // ---- metadata ----------------------------------------------------------------------------

    /** Album art for services that do not send it while browsing (Rhapsody, Napster). */
    public CompletableFuture<Optional<AlbumMetadata>> albumMetadata(long sourceId, String albumId) {
        requireText(albumId, "albumId");
        return requests.send(command(Commands.BROWSE_RETRIEVE_METADATA, "sid", sourceId, "cid", albumId),
                response -> ModelParser.albumMetadata(response).stream().findFirst());
    }

    // ---- service options ---------------------------------------------------------------------

    /** Adds a station from a browse result to the HEOS favorites. */
    public CompletableFuture<Void> addToFavorites(long sourceId, String mediaId, String name) {
        requireText(mediaId, "mediaId");
        requireText(name, "name");
        return requests.run(command(Commands.BROWSE_SET_SERVICE_OPTION, "sid", sourceId,
                "option", ServiceOptions.ADD_TO_FAVORITES, "mid", mediaId, "name", name));
    }

    /** Removes an entry (media id from browsing {@link MusicSources#FAVORITES}) from the favorites. */
    public CompletableFuture<Void> removeFromFavorites(String mediaId) {
        requireText(mediaId, "mediaId");
        return requests.run(command(Commands.BROWSE_SET_SERVICE_OPTION,
                "option", ServiceOptions.REMOVE_FROM_FAVORITES, "mid", mediaId));
    }

    /**
     * Creates a new station (Pandora, iHeartRadio). The result contains the stations; play one
     * with {@link Player#play(BrowseResult, MediaItem)}.
     *
     * @param criteriaId the {@code scid} from the browse options, e.g. 1 = artist
     * @param range      optional limit of returned stations
     */
    public CompletableFuture<BrowseResult> createStation(long sourceId, String name, int criteriaId, Range range) {
        checkLength(name, 128, "name");
        return requests.send(command(Commands.BROWSE_SET_SERVICE_OPTION, "sid", sourceId,
                        "option", ServiceOptions.CREATE_NEW_STATION, "name", name, "scid", criteriaId,
                        "range", range == null ? null : range.wireValue()),
                response -> ModelParser.browseResult(response, sourceId, null));
    }

    /**
     * Applies any service option, e.g. adding an album to the library of a service.
     *
     * @param attributes option specific attributes like {@code sid}, {@code mid}, {@code cid}
     */
    public CompletableFuture<HeosMessage> setServiceOption(int optionId, Map<String, ?> attributes) {
        HeosCommand.Builder builder = HeosCommand.builder(Commands.BROWSE_SET_SERVICE_OPTION).attribute("option", optionId);
        attributes.forEach(builder::attribute);
        return requests.send(builder.build());
    }
}
