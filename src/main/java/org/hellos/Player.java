// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static org.hellos.Requests.checkLength;
import static org.hellos.Requests.checkRange;
import static org.hellos.Requests.command;
import static org.hellos.Requests.joinIds;
import static org.hellos.Requests.onOff;
import static org.hellos.Requests.requireText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosMessage;
import org.hellos.internal.ModelParser;
import org.hellos.model.AddCriteria;
import org.hellos.model.BrowseResult;
import org.hellos.model.Input;
import org.hellos.model.MediaItem;
import org.hellos.model.MediaType;
import org.hellos.model.NowPlayingMedia;
import org.hellos.model.PlayMode;
import org.hellos.model.PlayState;
import org.hellos.model.PlayerInfo;
import org.hellos.model.QueueItem;
import org.hellos.model.QuickSelect;
import org.hellos.model.Range;
import org.hellos.model.RepeatMode;
import org.hellos.model.SearchCriteria;
import org.hellos.model.ServiceOptions;

/**
 * A handle to control one player. It only holds the player id and does not cache any state; every
 * method sends a command. Obtain it with {@link HeosSystem#player(long)}.
 *
 * <p>Commands sent to a player that is part of a group affect playback of the whole group
 * (play state, queue, media), but volume and mute only affect this player. Use {@link Group} for
 * the group volume.
 */
public final class Player {

    /** Default volume step of {@link #volumeUp()} and {@link #volumeDown()}. */
    public static final int DEFAULT_VOLUME_STEP = 5;
    /** Records per page when fetching the whole queue. */
    static final int QUEUE_PAGE_SIZE = 100;
    /** Safety limit for {@link #queue()} in case a device ignores the requested range. */
    static final int MAX_QUEUE_ITEMS = 10_000;

    private final long id;
    private final Requests requests;

    Player(long id, Requests requests) {
        this.id = id;
        this.requests = requests;
    }

    /** The player id ({@code pid}). */
    public long id() {
        return id;
    }

    public CompletableFuture<PlayerInfo> info() {
        return requests.send(command(Commands.PLAYER_GET_PLAYER_INFO, "pid", id),
                response -> ModelParser.player(response.payload()));
    }

    // ---- playback ----------------------------------------------------------------------------

    public CompletableFuture<PlayState> playState() {
        return requests.send(command(Commands.PLAYER_GET_PLAY_STATE, "pid", id),
                response -> PlayState.fromWire(response.message().require("state")));
    }

    public CompletableFuture<Void> setPlayState(PlayState state) {
        Objects.requireNonNull(state, "state");
        return requests.run(command(Commands.PLAYER_SET_PLAY_STATE, "pid", id, "state", state.wireValue()));
    }

    public CompletableFuture<Void> play() {
        return setPlayState(PlayState.PLAY);
    }

    public CompletableFuture<Void> pause() {
        return setPlayState(PlayState.PAUSE);
    }

    public CompletableFuture<Void> stop() {
        return setPlayState(PlayState.STOP);
    }

    public CompletableFuture<Void> next() {
        return requests.run(command(Commands.PLAYER_PLAY_NEXT, "pid", id));
    }

    public CompletableFuture<Void> previous() {
        return requests.run(command(Commands.PLAYER_PLAY_PREVIOUS, "pid", id));
    }

    public CompletableFuture<NowPlayingMedia> nowPlaying() {
        return requests.send(command(Commands.PLAYER_GET_NOW_PLAYING_MEDIA, "pid", id), ModelParser::nowPlaying);
    }

    // ---- volume ------------------------------------------------------------------------------

    /** The volume level, 0 to 100. */
    public CompletableFuture<Integer> volume() {
        return requests.send(command(Commands.PLAYER_GET_VOLUME, "pid", id),
                response -> response.message().requireInt("level"));
    }

    /** @param level 0 to 100 */
    public CompletableFuture<Void> setVolume(int level) {
        checkRange(level, 0, 100, "level");
        return requests.run(command(Commands.PLAYER_SET_VOLUME, "pid", id, "level", level));
    }

    public CompletableFuture<Void> volumeUp() {
        return volumeUp(DEFAULT_VOLUME_STEP);
    }

    /** @param step 1 to 10 */
    public CompletableFuture<Void> volumeUp(int step) {
        checkRange(step, 1, 10, "step");
        return requests.run(command(Commands.PLAYER_VOLUME_UP, "pid", id, "step", step));
    }

    public CompletableFuture<Void> volumeDown() {
        return volumeDown(DEFAULT_VOLUME_STEP);
    }

    /** @param step 1 to 10 */
    public CompletableFuture<Void> volumeDown(int step) {
        checkRange(step, 1, 10, "step");
        return requests.run(command(Commands.PLAYER_VOLUME_DOWN, "pid", id, "step", step));
    }

    public CompletableFuture<Boolean> isMuted() {
        return requests.send(command(Commands.PLAYER_GET_MUTE, "pid", id),
                response -> ModelParser.onOff(response.message().require("state")));
    }

    public CompletableFuture<Void> setMute(boolean muted) {
        return requests.run(command(Commands.PLAYER_SET_MUTE, "pid", id, "state", onOff(muted)));
    }

    public CompletableFuture<Void> toggleMute() {
        return requests.run(command(Commands.PLAYER_TOGGLE_MUTE, "pid", id));
    }

    // ---- play mode ---------------------------------------------------------------------------

    public CompletableFuture<PlayMode> playMode() {
        return requests.send(command(Commands.PLAYER_GET_PLAY_MODE, "pid", id),
                response -> ModelParser.playMode(response.message()));
    }

    public CompletableFuture<Void> setPlayMode(PlayMode mode) {
        Objects.requireNonNull(mode, "mode");
        return requests.run(command(Commands.PLAYER_SET_PLAY_MODE, "pid", id,
                "repeat", mode.repeat().wireValue(), "shuffle", onOff(mode.shuffle())));
    }

    /** Changes only the repeat mode; reads the current play mode first. */
    public CompletableFuture<Void> setRepeat(RepeatMode repeat) {
        Objects.requireNonNull(repeat, "repeat");
        return playMode().thenCompose(mode -> setPlayMode(mode.withRepeat(repeat)));
    }

    /** Changes only shuffle; reads the current play mode first. */
    public CompletableFuture<Void> setShuffle(boolean shuffle) {
        return playMode().thenCompose(mode -> setPlayMode(mode.withShuffle(shuffle)));
    }

    // ---- queue -------------------------------------------------------------------------------

    /** The whole queue. Large queues are fetched in pages of {@value #QUEUE_PAGE_SIZE} entries. */
    public CompletableFuture<List<QueueItem>> queue() {
        return fetchQueue(Range.ofSize(0, QUEUE_PAGE_SIZE), new ArrayList<>());
    }

    private CompletableFuture<List<QueueItem>> fetchQueue(Range range, List<QueueItem> collected) {
        return requests.send(command(Commands.PLAYER_GET_QUEUE, "pid", id, "range", range.wireValue()))
                .thenCompose(response -> {
                    List<QueueItem> page = ModelParser.queue(response);
                    collected.addAll(page);
                    int count = response.message().getInt("count").orElse(0);
                    boolean done = page.size() < range.size()
                            || (count > 0 && collected.size() >= count)
                            || collected.size() >= MAX_QUEUE_ITEMS;
                    return done
                            ? CompletableFuture.completedFuture(List.copyOf(collected))
                            : fetchQueue(range.next(), collected);
                });
    }

    /** A part of the queue; the device returns at most 100 entries per request. */
    public CompletableFuture<List<QueueItem>> queue(Range range) {
        Objects.requireNonNull(range, "range");
        return requests.send(command(Commands.PLAYER_GET_QUEUE, "pid", id, "range", range.wireValue()),
                ModelParser::queue);
    }

    /** Plays the queue entry with the given queue id ({@code qid}, starting at 1). */
    public CompletableFuture<Void> playQueueItem(int queueId) {
        return requests.run(command(Commands.PLAYER_PLAY_QUEUE, "pid", id, "qid", queueId));
    }

    public CompletableFuture<Void> removeFromQueue(Integer... queueIds) {
        return removeFromQueue(Arrays.asList(queueIds));
    }

    public CompletableFuture<Void> removeFromQueue(Collection<Integer> queueIds) {
        return requests.run(command(Commands.PLAYER_REMOVE_FROM_QUEUE, "pid", id,
                "qid", joinIds(queueIds, "queueIds")));
    }

    /** Moves the given queue entries in front of the entry {@code destinationQueueId}. */
    public CompletableFuture<Void> moveQueueItems(Collection<Integer> queueIds, int destinationQueueId) {
        return requests.run(command(Commands.PLAYER_MOVE_QUEUE_ITEM, "pid", id,
                "sqid", joinIds(queueIds, "queueIds"), "dqid", destinationQueueId));
    }

    public CompletableFuture<Void> clearQueue() {
        return requests.run(command(Commands.PLAYER_CLEAR_QUEUE, "pid", id));
    }

    /** Saves the queue as HEOS playlist; requires a signed-in account. */
    public CompletableFuture<Void> saveQueue(String playlistName) {
        checkLength(playlistName, 128, "playlistName");
        return requests.run(command(Commands.PLAYER_SAVE_QUEUE, "pid", id, "name", playlistName));
    }

    // ---- quick selects (AVR / soundbar) ------------------------------------------------------

    public CompletableFuture<List<QuickSelect>> quickSelects() {
        return requests.send(command(Commands.PLAYER_GET_QUICKSELECTS, "pid", id), ModelParser::quickSelects);
    }

    /** Stores the current source in quick select slot 1 to 6. */
    public CompletableFuture<Void> setQuickSelect(int slot) {
        checkRange(slot, 1, 6, "slot");
        return requests.run(command(Commands.PLAYER_SET_QUICKSELECT, "pid", id, "id", slot));
    }

    public CompletableFuture<Void> playQuickSelect(int slot) {
        checkRange(slot, 1, 6, "slot");
        return requests.run(command(Commands.PLAYER_PLAY_QUICKSELECT, "pid", id, "id", slot));
    }

    // ---- firmware ----------------------------------------------------------------------------

    /** Whether a firmware update is available for this player. */
    public CompletableFuture<Boolean> checkForUpdate() {
        return requests.send(command(Commands.PLAYER_CHECK_UPDATE, "pid", id), ModelParser::updateAvailable);
    }

    // ---- play media --------------------------------------------------------------------------

    /** Plays an input of this player. */
    public CompletableFuture<Void> playInput(Input input) {
        return playInput(Objects.requireNonNull(input, "input").wireValue());
    }

    /** Plays an input of this player by its protocol name, e.g. {@code inputs/aux_in_1}. */
    public CompletableFuture<Void> playInput(String input) {
        requireText(input, "input");
        return requests.run(command(Commands.BROWSE_PLAY_INPUT, "pid", id, "input", input));
    }

    /** Plays an input of another player ({@code sourcePlayerId}) on this player. */
    public CompletableFuture<Void> playInputFrom(long sourcePlayerId, Input input) {
        return playInputFrom(sourcePlayerId, Objects.requireNonNull(input, "input").wireValue());
    }

    public CompletableFuture<Void> playInputFrom(long sourcePlayerId, String input) {
        requireText(input, "input");
        if (sourcePlayerId == id) {
            return playInput(input);
        }
        return requests.run(command(Commands.BROWSE_PLAY_INPUT, "pid", id, "spid", sourcePlayerId, "input", input));
    }

    /** Plays a stream URL. Current firmware limits URLs to about 255 characters. */
    public CompletableFuture<Void> playUrl(String url) {
        requireText(url, "url");
        return requests.run(command(Commands.BROWSE_PLAY_STREAM, "pid", id, "url", url));
    }

    /** Plays the HEOS favorite at the given position (starting at 1); requires a signed-in account. */
    public CompletableFuture<Void> playPreset(int preset) {
        if (preset < 1) {
            throw new IllegalArgumentException("preset must be 1 or greater");
        }
        return requests.run(command(Commands.BROWSE_PLAY_PRESET, "pid", id, "preset", preset));
    }

    /**
     * Plays a station from a browse or search result.
     *
     * @param containerId the container the station was browsed from, {@code null} for search results
     */
    public CompletableFuture<Void> playStation(long sourceId, String containerId, String mediaId, String name) {
        requireText(mediaId, "mediaId");
        return requests.run(command(Commands.BROWSE_PLAY_STREAM, "pid", id, "sid", sourceId,
                "cid", containerId, "mid", mediaId, "name", name));
    }

    /** Adds a whole playable container (album, playlist, ...) to the queue. */
    public CompletableFuture<Void> addContainerToQueue(long sourceId, String containerId, AddCriteria criteria) {
        requireText(containerId, "containerId");
        Objects.requireNonNull(criteria, "criteria");
        return requests.run(command(Commands.BROWSE_ADD_TO_QUEUE, "pid", id, "sid", sourceId,
                "cid", containerId, "aid", criteria.code()));
    }

    /** Adds a single track to the queue. */
    public CompletableFuture<Void> addTrackToQueue(long sourceId, String containerId, String mediaId,
                                                   AddCriteria criteria) {
        requireText(mediaId, "mediaId");
        Objects.requireNonNull(criteria, "criteria");
        return requests.run(command(Commands.BROWSE_ADD_TO_QUEUE, "pid", id, "sid", sourceId,
                "cid", containerId, "mid", mediaId, "aid", criteria.code()));
    }

    /**
     * Adds all tracks found by a search to the queue. Only possible if the criteria is
     * {@link SearchCriteria#playable()}.
     */
    public CompletableFuture<Void> addSearchResultsToQueue(long sourceId, SearchCriteria criteria, String search,
                                                           AddCriteria addCriteria) {
        Objects.requireNonNull(criteria, "criteria");
        if (!criteria.playable() || criteria.containerIdPrefix().isEmpty()) {
            throw new IllegalArgumentException("Search criteria '" + criteria.name() + "' is not playable");
        }
        return addContainerToQueue(sourceId, criteria.containerIdPrefix().get() + requireText(search, "search"),
                addCriteria);
    }

    /** Plays an item of a browse result, see {@link #play(long, String, MediaItem, AddCriteria)}. */
    public CompletableFuture<Void> play(BrowseResult parent, MediaItem item) {
        Objects.requireNonNull(parent, "parent");
        return play(parent.sourceId(), parent.containerId().orElse(null), item, AddCriteria.PLAY_NOW);
    }

    /**
     * Plays an item of a browse or search result. Depending on the item it plays the input, the
     * station, the track or the whole container.
     *
     * @param sourceId          the source the item was found in
     * @param parentContainerId the container the item was found in, {@code null} for top level or search
     * @param criteria          how tracks and containers are added to the queue
     * @throws IllegalArgumentException if the item cannot be played
     */
    public CompletableFuture<Void> play(long sourceId, String parentContainerId, MediaItem item,
                                        AddCriteria criteria) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(criteria, "criteria");
        if (item.isInput()) {
            // AUX inputs: the source id is the id of the player that owns the input
            return playInputFrom(sourceId, item.mediaId().orElseThrow());
        }
        if (item.type() == MediaType.STATION && item.mediaId().isPresent()) {
            return playStation(sourceId, parentContainerId, item.mediaId().get(), item.name());
        }
        if (item.container() && item.playable() && item.containerId().isPresent()) {
            return addContainerToQueue(sourceId, item.containerId().get(), criteria);
        }
        if (!item.container() && item.playable() && item.mediaId().isPresent()) {
            return addTrackToQueue(sourceId, parentContainerId, item.mediaId().get(), criteria);
        }
        throw new IllegalArgumentException("Item '" + item.name() + "' (" + item.type() + ") is not playable");
    }

    // ---- service options on the now playing media -------------------------------------------

    /** Rates the playing track positively (Pandora and other services offering the option). */
    public CompletableFuture<Void> thumbsUp(long sourceId) {
        return serviceOption(ServiceOptions.THUMBS_UP, sourceId);
    }

    public CompletableFuture<Void> thumbsDown(long sourceId) {
        return serviceOption(ServiceOptions.THUMBS_DOWN, sourceId);
    }

    /** Adds the playing station to the HEOS favorites; requires a signed-in account. */
    public CompletableFuture<Void> addNowPlayingToFavorites() {
        return requests.run(command(Commands.BROWSE_SET_SERVICE_OPTION,
                "option", ServiceOptions.ADD_TO_FAVORITES, "pid", id));
    }

    private CompletableFuture<Void> serviceOption(int option, long sourceId) {
        return requests.run(command(Commands.BROWSE_SET_SERVICE_OPTION, "sid", sourceId, "option", option, "pid", id));
    }

    /** Sends any command with this player's id; for commands not covered by this class. */
    public CompletableFuture<HeosMessage> send(String commandName, Object... attributes) {
        Object[] all = new Object[attributes.length + 2];
        all[0] = "pid";
        all[1] = id;
        System.arraycopy(attributes, 0, all, 2, attributes.length);
        return requests.send(command(commandName, all));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Player other && other.id == id && other.requests == requests;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Player[" + id + "]";
    }
}
