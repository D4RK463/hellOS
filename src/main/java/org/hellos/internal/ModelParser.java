// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hellos.cli.HeosMessage;
import org.hellos.cli.HeosProtocolException;
import org.hellos.cli.MessageParameters;
import org.hellos.model.AccountStatus;
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
import org.hellos.model.PlayMode;
import org.hellos.model.PlayerInfo;
import org.hellos.model.QueueItem;
import org.hellos.model.QuickSelect;
import org.hellos.model.RepeatMode;
import org.hellos.model.SearchCriteria;
import org.hellos.model.SearchResult;
import org.hellos.model.ServiceOption;
import org.hellos.model.VolumeControl;

import tools.jackson.databind.JsonNode;

/** Converts responses into model records. */
public final class ModelParser {

    private static final Pattern TUPLE = Pattern.compile("\\(([^)]*)\\)");

    private ModelParser() {
    }

    // ---- system ------------------------------------------------------------------------------

    /** Parses {@code signed_out} or {@code signed_in&un=...} (check_account, sign_in, user_changed). */
    public static AccountStatus accountStatus(MessageParameters message) {
        if (message.has("signed_in")) {
            return new AccountStatus(true, message.get("un").filter(s -> !s.isEmpty()));
        }
        return AccountStatus.signedOut();
    }

    // ---- players -----------------------------------------------------------------------------

    public static List<PlayerInfo> players(HeosMessage response) {
        return Json.elements(response.payload()).stream().map(ModelParser::player).toList();
    }

    public static PlayerInfo player(JsonNode node) {
        return new PlayerInfo(
                Json.textOrEmpty(node, "name"),
                Json.requireLong(node, "pid"),
                Json.longValue(node, "gid"),
                Json.textOrEmpty(node, "model"),
                Json.text(node, "version"),
                Json.text(node, "ip"),
                NetworkType.fromWire(Json.text(node, "network").orElse("")),
                LineOutLevel.fromCode(boxed(Json.intValue(node, "lineout"))),
                VolumeControl.fromCode(boxed(Json.intValue(node, "control"))),
                Json.text(node, "serial"));
    }

    public static PlayMode playMode(MessageParameters message) {
        return new PlayMode(
                RepeatMode.fromWire(message.require("repeat")),
                onOff(message.require("shuffle")));
    }

    public static NowPlayingMedia nowPlaying(HeosMessage response) {
        JsonNode node = response.payload();
        List<ServiceOption> options = serviceOptions(response.options());
        if (!node.isObject()) {
            return new NowPlayingMedia(MediaType.UNKNOWN, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), OptionalInt.empty(), OptionalLong.empty(),
                    Optional.empty(), options);
        }
        return new NowPlayingMedia(
                MediaType.fromWire(Json.text(node, "type").orElse(null)),
                Json.text(node, "song"),
                Json.text(node, "album"),
                Json.text(node, "artist"),
                Json.text(node, "station"),
                Json.text(node, "image_url"),
                Json.text(node, "mid"),
                Json.intValue(node, "qid"),
                Json.longValue(node, "sid"),
                Json.text(node, "album_id"),
                options);
    }

    public static List<QueueItem> queue(HeosMessage response) {
        return Json.elements(response.payload()).stream().map(node -> new QueueItem(
                Json.requireInt(node, "qid"),
                Json.textOrEmpty(node, "song"),
                Json.text(node, "album"),
                Json.text(node, "artist"),
                Json.text(node, "image_url"),
                Json.text(node, "mid"),
                Json.text(node, "album_id"))).toList();
    }

    public static List<QuickSelect> quickSelects(HeosMessage response) {
        return Json.elements(response.payload()).stream()
                .map(node -> new QuickSelect(Json.requireInt(node, "id"), Json.textOrEmpty(node, "name")))
                .toList();
    }

    public static boolean updateAvailable(HeosMessage response) {
        return Json.text(response.payload(), "update").map("update_exist"::equals).orElse(false);
    }

    // ---- groups ------------------------------------------------------------------------------

    public static List<GroupInfo> groups(HeosMessage response) {
        return Json.elements(response.payload()).stream().map(ModelParser::group).toList();
    }

    public static GroupInfo group(JsonNode node) {
        List<GroupInfo.Member> members = Json.elements(node.get("players")).stream()
                .map(p -> new GroupInfo.Member(
                        Json.textOrEmpty(p, "name"),
                        Json.requireLong(p, "pid"),
                        GroupInfo.Role.fromWire(Json.text(p, "role").orElse(""))))
                .toList();
        return new GroupInfo(Json.textOrEmpty(node, "name"), Json.requireLong(node, "gid"), members);
    }

    // ---- browse ------------------------------------------------------------------------------

    public static List<MusicSource> musicSources(HeosMessage response) {
        return Json.elements(response.payload()).stream().map(ModelParser::musicSource).toList();
    }

    public static MusicSource musicSource(JsonNode node) {
        return new MusicSource(
                Json.requireLong(node, "sid"),
                Json.textOrEmpty(node, "name"),
                MediaType.fromWire(Json.text(node, "type").orElse(null)),
                Json.text(node, "image_url"),
                Json.bool(node, "available"),
                Json.text(node, "service_username"));
    }

    public static BrowseResult browseResult(HeosMessage response, long sourceId, String containerId) {
        MessageParameters message = response.message();
        List<MediaItem> items = mediaItems(response.payload());
        return new BrowseResult(
                message.getLong("sid").orElse(sourceId),
                Optional.ofNullable(message.get("cid").filter(s -> !s.isEmpty()).orElse(containerId)),
                message.getInt("returned").orElse(items.size()),
                message.getInt("count").orElse(0),
                items,
                serviceOptions(response.options()));
    }

    public static SearchResult searchResult(HeosMessage response, long sourceId, int criteriaId, String search) {
        MessageParameters message = response.message();
        List<MediaItem> items = mediaItems(response.payload());
        return new SearchResult(
                message.getLong("sid").orElse(sourceId),
                message.getInt("scid").orElse(criteriaId),
                message.get("search").orElse(search),
                message.getInt("returned").orElse(items.size()),
                message.getInt("count").orElse(0),
                items,
                serviceOptions(response.options()));
    }

    public static MultiSearchResult multiSearchResult(HeosMessage response, String search) {
        MessageParameters message = response.message();
        List<MediaItem> items = mediaItems(response.payload());
        List<MultiSearchResult.Statistic> statistics = new ArrayList<>();
        for (long[] t : tuples(message.get("stats").orElse(""), 4)) {
            statistics.add(new MultiSearchResult.Statistic(t[0], (int) t[1], (int) t[2], (int) t[3]));
        }
        List<MultiSearchResult.SourceError> errors = new ArrayList<>();
        for (long[] t : tuples(message.get("errno").orElse(""), 3)) {
            errors.add(new MultiSearchResult.SourceError(t[0], (int) t[1], (int) t[2]));
        }
        return new MultiSearchResult(
                message.get("search").orElse(search),
                numbers(message.get("sid").orElse("")),
                numbers(message.get("scid").orElse("")).stream().map(Long::intValue).toList(),
                message.getInt("returned").orElse(items.size()),
                message.getInt("count").orElse(0),
                items,
                statistics,
                errors);
    }

    public static List<MediaItem> mediaItems(JsonNode payload) {
        return Json.elements(payload).stream().map(ModelParser::mediaItem).toList();
    }

    public static MediaItem mediaItem(JsonNode node) {
        return new MediaItem(
                Json.textOrEmpty(node, "name"),
                MediaType.fromWire(Json.text(node, "type").orElse(null)),
                Json.bool(node, "container"),
                Json.bool(node, "playable"),
                Json.text(node, "image_url"),
                Json.text(node, "artist"),
                Json.text(node, "album"),
                Json.text(node, "album_id"),
                Json.text(node, "cid"),
                Json.text(node, "mid"),
                Json.longValue(node, "sid"));
    }

    public static List<SearchCriteria> searchCriteria(HeosMessage response) {
        return Json.elements(response.payload()).stream().map(node -> new SearchCriteria(
                Json.textOrEmpty(node, "name"),
                Json.requireInt(node, "scid"),
                Json.bool(node, "wildcard"),
                Json.bool(node, "playable"),
                Json.text(node, "cid"))).toList();
    }

    public static List<AlbumMetadata> albumMetadata(HeosMessage response) {
        return Json.elements(response.payload()).stream().map(node -> new AlbumMetadata(
                Json.textOrEmpty(node, "album_id"),
                Json.elements(node.get("images")).stream()
                        .filter(image -> Json.text(image, "image_url").isPresent())
                        .map(image -> new AlbumMetadata.Image(
                                Json.requireText(image, "image_url"),
                                Json.intValue(image, "width").orElse(0)))
                        .toList())).toList();
    }

    /** Parses {@code [{"play":[{"id":11,"name":"Thumbs Up"}]}, {"browse":[...]}]}. */
    public static List<ServiceOption> serviceOptions(JsonNode options) {
        List<ServiceOption> result = new ArrayList<>();
        for (JsonNode group : Json.elements(options)) {
            for (Map.Entry<String, JsonNode> entry : group.properties()) {
                for (JsonNode option : Json.elements(entry.getValue())) {
                    result.add(new ServiceOption(
                            entry.getKey(),
                            Json.requireInt(option, "id"),
                            Json.textOrEmpty(option, "name"),
                            Json.intValue(option, "scid")));
                }
            }
        }
        return result;
    }

    // ---- helpers -----------------------------------------------------------------------------

    public static boolean onOff(String value) {
        return switch (value.strip()) {
            case "on" -> true;
            case "off" -> false;
            default -> throw new HeosProtocolException("Expected on/off but was '" + value + "'");
        };
    }

    private static Integer boxed(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static List<Long> numbers(String commaSeparated) {
        if (commaSeparated.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(commaSeparated.split(",")).map(String::strip).map(Long::valueOf).toList();
        } catch (NumberFormatException e) {
            throw new HeosProtocolException("Invalid number list '" + commaSeparated + "'", e);
        }
    }

    private static List<long[]> tuples(String value, int size) {
        List<long[]> result = new ArrayList<>();
        Matcher matcher = TUPLE.matcher(value);
        while (matcher.find()) {
            List<Long> numbers = numbers(matcher.group(1));
            if (numbers.size() == size) {
                result.add(numbers.stream().mapToLong(Long::longValue).toArray());
            }
        }
        return result;
    }
}
