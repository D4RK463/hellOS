// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.hellos.cli.HeosProtocolException;
import org.hellos.cli.HeosText;

import tools.jackson.databind.JsonNode;

/**
 * Lenient accessors for payload fields. The specification shows ids as strings while devices send
 * numbers, booleans are sent as {@code yes}/{@code no} or {@code true}/{@code false}, and string
 * values are escaped with {@link HeosText}.
 */
public final class Json {

    private Json() {
    }

    /** The elements of an array; a single object is treated as an array with one element. */
    public static List<JsonNode> elements(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            return List.copyOf(node.values());
        }
        if (node.isObject()) {
            return node.isEmpty() ? List.of() : List.of(node);
        }
        return List.of();
    }

    /** A decoded, non-empty string value. */
    public static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode() || value.isContainer()) {
            return Optional.empty();
        }
        String text = value.asString();
        return text.isEmpty() ? Optional.empty() : Optional.of(HeosText.decode(text));
    }

    public static String requireText(JsonNode node, String field) {
        return text(node, field).orElseThrow(() -> missing(field));
    }

    /** A string value or {@code ""}, for fields that may legitimately be empty (e.g. names). */
    public static String textOrEmpty(JsonNode node, String field) {
        return text(node, field).orElse("");
    }

    public static OptionalLong longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return OptionalLong.empty();
        }
        if (value.isNumber()) {
            return OptionalLong.of(value.longValue());
        }
        if (value.isString()) {
            String text = value.asString().strip();
            if (text.isEmpty()) {
                return OptionalLong.empty();
            }
            try {
                return OptionalLong.of(new BigDecimal(text).longValue());
            } catch (NumberFormatException e) {
                throw new HeosProtocolException("Field '" + field + "' is not a number: '" + text + "'", e);
            }
        }
        throw new HeosProtocolException("Field '" + field + "' is not a number: " + value);
    }

    public static long requireLong(JsonNode node, String field) {
        return longValue(node, field).orElseThrow(() -> missing(field));
    }

    public static OptionalInt intValue(JsonNode node, String field) {
        OptionalLong value = longValue(node, field);
        return value.isPresent() ? OptionalInt.of(Math.toIntExact(value.getAsLong())) : OptionalInt.empty();
    }

    public static int requireInt(JsonNode node, String field) {
        return Math.toIntExact(requireLong(node, field));
    }

    /** {@code yes}, {@code true}, {@code on} and non-zero numbers are true; everything else is false. */
    public static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isNumber()) {
            return value.longValue() != 0;
        }
        return switch (value.asString().strip().toLowerCase(java.util.Locale.ROOT)) {
            case "yes", "true", "on" -> true;
            default -> false;
        };
    }

    private static HeosProtocolException missing(String field) {
        return new HeosProtocolException("Missing field '" + field + "'");
    }
}
