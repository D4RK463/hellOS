// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.blocking;

import static org.hellos.blocking.Await.result;
import static org.hellos.blocking.Await.run;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.hellos.Player;
import org.hellos.model.AddCriteria;
import org.hellos.model.BrowseResult;
import org.hellos.model.Input;
import org.hellos.model.MediaItem;
import org.hellos.model.NowPlayingMedia;
import org.hellos.model.PlayMode;
import org.hellos.model.PlayState;
import org.hellos.model.PlayerInfo;
import org.hellos.model.QueueItem;
import org.hellos.model.QuickSelect;
import org.hellos.model.Range;
import org.hellos.model.RepeatMode;
import org.hellos.model.SearchCriteria;

/** Blocking variant of {@link Player}. Failures are thrown as {@link org.hellos.cli.HeosException}. */
public final class BlockingPlayer {

    private final Player player;

    private BlockingPlayer(Player player) {
        this.player = player;
    }

    public static BlockingPlayer of(Player player) {
        return new BlockingPlayer(Objects.requireNonNull(player, "player"));
    }

    /** The asynchronous player. */
    public Player async() {
        return player;
    }

    public long id() {
        return player.id();
    }

    public PlayerInfo info() {
        return result(player.info());
    }

    public PlayState playState() {
        return result(player.playState());
    }

    public void setPlayState(PlayState state) {
        run(player.setPlayState(state));
    }

    public void play() {
        run(player.play());
    }

    public void pause() {
        run(player.pause());
    }

    public void stop() {
        run(player.stop());
    }

    public void next() {
        run(player.next());
    }

    public void previous() {
        run(player.previous());
    }

    public NowPlayingMedia nowPlaying() {
        return result(player.nowPlaying());
    }

    public int volume() {
        return result(player.volume());
    }

    public void setVolume(int level) {
        run(player.setVolume(level));
    }

    public void volumeUp() {
        run(player.volumeUp());
    }

    public void volumeUp(int step) {
        run(player.volumeUp(step));
    }

    public void volumeDown() {
        run(player.volumeDown());
    }

    public void volumeDown(int step) {
        run(player.volumeDown(step));
    }

    public boolean isMuted() {
        return result(player.isMuted());
    }

    public void setMute(boolean muted) {
        run(player.setMute(muted));
    }

    public void toggleMute() {
        run(player.toggleMute());
    }

    public PlayMode playMode() {
        return result(player.playMode());
    }

    public void setPlayMode(PlayMode mode) {
        run(player.setPlayMode(mode));
    }

    public void setRepeat(RepeatMode repeat) {
        run(player.setRepeat(repeat));
    }

    public void setShuffle(boolean shuffle) {
        run(player.setShuffle(shuffle));
    }

    public List<QueueItem> queue() {
        return result(player.queue());
    }

    public List<QueueItem> queue(Range range) {
        return result(player.queue(range));
    }

    public void playQueueItem(int queueId) {
        run(player.playQueueItem(queueId));
    }

    public void removeFromQueue(Integer... queueIds) {
        run(player.removeFromQueue(queueIds));
    }

    public void removeFromQueue(Collection<Integer> queueIds) {
        run(player.removeFromQueue(queueIds));
    }

    public void moveQueueItems(Collection<Integer> queueIds, int destinationQueueId) {
        run(player.moveQueueItems(queueIds, destinationQueueId));
    }

    public void clearQueue() {
        run(player.clearQueue());
    }

    public void saveQueue(String playlistName) {
        run(player.saveQueue(playlistName));
    }

    public List<QuickSelect> quickSelects() {
        return result(player.quickSelects());
    }

    public void setQuickSelect(int slot) {
        run(player.setQuickSelect(slot));
    }

    public void playQuickSelect(int slot) {
        run(player.playQuickSelect(slot));
    }

    public boolean checkForUpdate() {
        return result(player.checkForUpdate());
    }

    public void playInput(Input input) {
        run(player.playInput(input));
    }

    public void playInput(String input) {
        run(player.playInput(input));
    }

    public void playInputFrom(long sourcePlayerId, Input input) {
        run(player.playInputFrom(sourcePlayerId, input));
    }

    public void playInputFrom(long sourcePlayerId, String input) {
        run(player.playInputFrom(sourcePlayerId, input));
    }

    public void playUrl(String url) {
        run(player.playUrl(url));
    }

    public void playPreset(int preset) {
        run(player.playPreset(preset));
    }

    public void playStation(long sourceId, String containerId, String mediaId, String name) {
        run(player.playStation(sourceId, containerId, mediaId, name));
    }

    public void addContainerToQueue(long sourceId, String containerId, AddCriteria criteria) {
        run(player.addContainerToQueue(sourceId, containerId, criteria));
    }

    public void addTrackToQueue(long sourceId, String containerId, String mediaId, AddCriteria criteria) {
        run(player.addTrackToQueue(sourceId, containerId, mediaId, criteria));
    }

    public void addSearchResultsToQueue(long sourceId, SearchCriteria criteria, String search,
                                        AddCriteria addCriteria) {
        run(player.addSearchResultsToQueue(sourceId, criteria, search, addCriteria));
    }

    public void play(BrowseResult parent, MediaItem item) {
        run(player.play(parent, item));
    }

    public void play(long sourceId, String parentContainerId, MediaItem item, AddCriteria criteria) {
        run(player.play(sourceId, parentContainerId, item, criteria));
    }

    public void thumbsUp(long sourceId) {
        run(player.thumbsUp(sourceId));
    }

    public void thumbsDown(long sourceId) {
        run(player.thumbsDown(sourceId));
    }

    public void addNowPlayingToFavorites() {
        run(player.addNowPlayingToFavorites());
    }

    @Override
    public String toString() {
        return player.toString();
    }
}
