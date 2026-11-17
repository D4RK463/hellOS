// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.SequencedMap;
import java.util.StringJoiner;

/**
 * The parsed {@code message} field of a HEOS response, e.g. {@code pid=1&level=20}.
 *
 * <p>Segments without {@code =} (like {@code signed_out} or {@code command under process}) are
 * stored as flags with an empty value, see {@link #has(String)}. Values are decoded with
 * {@link HeosText#decode(String)}. A {@code url} attribute consumes the remainder of the message,
 * because URLs are not escaped by the device.
 *
 * <p>{@link #toString()} masks the HEOS account password.
 */
public final class MessageParameters {

    private static final MessageParameters EMPTY = new MessageParameters("", new LinkedHashMap<>());

    private final String raw;
    private final SequencedMap<String, String> values;

    private MessageParameters(String raw, SequencedMap<String, String> values) {
        this.raw = raw;
        this.values = Collections.unmodifiableSequencedMap(values);
    }

    public static MessageParameters empty() {
        return EMPTY;
    }

    public static MessageParameters parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY;
        }
        SequencedMap<String, String> values = new LinkedHashMap<>();
        int start = 0;
        while (start <= raw.length()) {
            if (raw.startsWith(HeosCommand.URL_ATTRIBUTE + "=", start)) {
                values.put(HeosCommand.URL_ATTRIBUTE, raw.substring(start + 4));
                break;
            }
            int end = raw.indexOf('&', start);
            if (end < 0) {
                end = raw.length();
            }
            String segment = raw.substring(start, end);
            if (!segment.isBlank()) {
                int eq = segment.indexOf('=');
                if (eq < 0) {
                    values.putIfAbsent(segment.strip(), "");
                } else {
                    values.put(segment.substring(0, eq).strip(), HeosText.decode(segment.substring(eq + 1)));
                }
            }
            start = end + 1;
        }
        return new MessageParameters(raw, values);
    }

    /** The message exactly as received. May contain the password of a sign-in request. */
    public String raw() {
        return raw;
    }

    /** All decoded values in received order. */
    public SequencedMap<String, String> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Whether the key is present, either as attribute or as flag. */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /** Returns the value or throws {@link HeosProtocolException} if absent. */
    public String require(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new HeosProtocolException("Missing attribute '" + key + "' in message '" + this + "'");
        }
        return value;
    }

    /**
     * Returns the value as int. Values with a decimal point (sent by some firmware versions) are
     * truncated.
     */
    public int requireInt(String key) {
        return Math.toIntExact(requireLong(key));
    }

    public long requireLong(String key) {
        String value = require(key);
        try {
            return new BigDecimal(value.strip()).longValue();
        } catch (NumberFormatException e) {
            throw new HeosProtocolException("Attribute '" + key + "' is not a number: '" + value + "'", e);
        }
    }

    public OptionalInt getInt(String key) {
        return has(key) && !values.get(key).isBlank() ? OptionalInt.of(requireInt(key)) : OptionalInt.empty();
    }

    public OptionalLong getLong(String key) {
        return has(key) && !values.get(key).isBlank() ? OptionalLong.of(requireLong(key)) : OptionalLong.empty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MessageParameters other && raw.equals(other.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    /** The decoded parameters with the password masked, safe to log. */
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (Redaction.isSensitive(entry.getKey())) {
                joiner.add(entry.getKey() + "=" + Redaction.MASK);
            } else if (entry.getValue().isEmpty()) {
                joiner.add(entry.getKey());
            } else {
                joiner.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return joiner.toString();
    }
}
