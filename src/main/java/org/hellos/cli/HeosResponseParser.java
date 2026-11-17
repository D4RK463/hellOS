// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses single JSON lines received from a HEOS device into {@link HeosMessage}s.
 *
 * <p>The parser is lenient where real devices and the specification differ: whitespace inside the
 * command name (e.g. {@code " player/ get_volume "}) is removed and a missing {@code result} or
 * {@code message} is accepted.
 */
public final class HeosResponseParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private HeosResponseParser() {
    }

    /** The shared Jackson mapper used for HEOS JSON. */
    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static HeosMessage parse(String line) {
        JsonNode root;
        try {
            root = MAPPER.readTree(line);
        } catch (JacksonException e) {
            throw new HeosProtocolException("Invalid JSON received: " + Redaction.forLog(line), e);
        }
        if (root == null || !root.isObject()) {
            throw new HeosProtocolException("Expected a JSON object: " + Redaction.forLog(line));
        }
        JsonNode heos = root.get("heos");
        if (heos == null || !heos.isObject()) {
            throw new HeosProtocolException("Missing 'heos' object: " + Redaction.forLog(line));
        }
        JsonNode command = heos.get("command");
        if (command == null || !command.isString() || command.asString().isBlank()) {
            throw new HeosProtocolException("Missing 'heos.command': " + Redaction.forLog(line));
        }
        String name = WHITESPACE.matcher(command.asString()).replaceAll("");
        return new HeosMessage(
                name,
                parseResult(heos.get("result")),
                MessageParameters.parse(textOrEmpty(heos.get("message"))),
                root.path("payload"),
                root.path("options"),
                line);
    }

    private static HeosMessage.Result parseResult(JsonNode result) {
        if (result == null || result.isNull()) {
            return HeosMessage.Result.NONE;
        }
        return switch (result.asString().strip()) {
            case "success" -> HeosMessage.Result.SUCCESS;
            case "fail" -> HeosMessage.Result.FAIL;
            default -> throw new HeosProtocolException("Unknown result '" + result.asString() + "'");
        };
    }

    private static String textOrEmpty(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asString();
    }
}
