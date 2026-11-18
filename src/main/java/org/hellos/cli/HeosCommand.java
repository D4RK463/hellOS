// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.regex.Pattern;

import org.hellos.internal.JvmOnly;

/**
 * A command that is sent to a HEOS device, e.g. {@code heos://player/set_volume?pid=1&level=20}.
 *
 * <p>Instances are immutable. Attribute values are escaped as described in the specification when
 * the command is turned into its wire format. The {@code url} attribute is the only exception: as
 * required by the specification it is always written last and is not escaped.
 *
 * <p>{@link #toString()} masks the HEOS account password and is safe to log.
 */
public final class HeosCommand {

    /** The scheme prefix of every HEOS command. */
    public static final String PREFIX = "heos://";
    /** Delimiter that terminates every command on the wire. */
    public static final String DELIMITER = "\r\n";
    /** The attribute that has to be the last one and must not be escaped. */
    public static final String URL_ATTRIBUTE = "url";

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+/[a-z0-9_]+");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_]+");

    private final String name;
    /** Never modified after construction; kept as LinkedHashMap for {@link #attributes()}. */
    private final LinkedHashMap<String, String> attributes;
    private final Map<String, String> attributeView;

    private HeosCommand(String name, LinkedHashMap<String, String> attributes) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid command name '" + name + "', expected 'group/command'");
        }
        attributes.forEach(HeosCommand::validateAttribute);
        this.name = name;
        this.attributes = attributes;
        this.attributeView = Collections.unmodifiableMap(attributes);
    }

    /** Creates a command without attributes, e.g. {@code HeosCommand.of("player/get_players")}. */
    public static HeosCommand of(String name) {
        return new HeosCommand(name, new LinkedHashMap<>());
    }

    /**
     * Creates a command with attributes. Values are converted with {@link String#valueOf(Object)}.
     * The iteration order of the given map is kept.
     */
    public static HeosCommand of(String name, Map<String, ?> attributes) {
        Builder builder = builder(name);
        attributes.forEach(builder::attribute);
        return builder.build();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** The full command name, e.g. {@code player/get_volume}. */
    public String name() {
        return name;
    }

    /** The command group, e.g. {@code player}. */
    public String group() {
        return name.substring(0, name.indexOf('/'));
    }

    /** The command without its group, e.g. {@code get_volume}. */
    public String command() {
        return name.substring(name.indexOf('/') + 1);
    }

    /**
     * The unescaped attributes in insertion order.
     *
     * <p>Not available on Android (API 34 has no {@code SequencedMap}); use {@link #attributeMap()}
     * there.
     */
    @JvmOnly
    public SequencedMap<String, String> attributes() {
        return Collections.unmodifiableSequencedMap(attributes);
    }

    /**
     * The unescaped attributes in insertion order, as unmodifiable map. Same content as
     * {@link #attributes()}, also available on Android.
     *
     * @since 0.2.0
     */
    public Map<String, String> attributeMap() {
        return attributeView;
    }

    /** Returns the attribute value or {@code null} if absent. */
    public String attribute(String key) {
        return attributes.get(key);
    }

    /** The command as it is sent to the device, without the trailing delimiter. */
    public String toUri() {
        return buildUri(false);
    }

    /** The command including the trailing {@code \r\n}. */
    public String toWireFormat() {
        return toUri() + DELIMITER;
    }

    /** Like {@link #toUri()}, but with the password masked. */
    public String toMaskedUri() {
        return buildUri(true);
    }

    private String buildUri(boolean mask) {
        StringBuilder sb = new StringBuilder(PREFIX).append(name);
        char separator = '?';
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey().equals(URL_ATTRIBUTE)) {
                continue;
            }
            String value = mask && Redaction.isSensitive(entry.getKey())
                    ? Redaction.MASK
                    : HeosText.encode(entry.getValue());
            sb.append(separator).append(entry.getKey()).append('=').append(value);
            separator = '&';
        }
        String url = attributes.get(URL_ATTRIBUTE);
        if (url != null) {
            sb.append(separator).append(URL_ATTRIBUTE).append('=').append(url);
        }
        return sb.toString();
    }

    private static void validateAttribute(String key, String value) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid attribute name '" + key + "'");
        }
        Objects.requireNonNull(value, () -> "Attribute '" + key + "' must not be null");
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Attribute '" + key + "' must not contain line breaks");
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HeosCommand other && name.equals(other.name) && attributes.equals(other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, attributes);
    }

    /** The masked URI, safe to log. */
    @Override
    public String toString() {
        return toMaskedUri();
    }

    /** Builder for {@link HeosCommand}. */
    public static final class Builder {

        private final String name;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        /** Adds an attribute; {@code null} values are ignored so optional attributes can be passed directly. */
        public Builder attribute(String key, Object value) {
            Objects.requireNonNull(key, "key");
            if (value != null) {
                attributes.put(key, String.valueOf(value));
            }
            return this;
        }

        public HeosCommand build() {
            return new HeosCommand(name, new LinkedHashMap<>(attributes));
        }
    }
}
