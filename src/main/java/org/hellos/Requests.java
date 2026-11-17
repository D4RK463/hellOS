// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hellos.cli.HeosCommand;
import org.hellos.cli.HeosMessage;
import org.hellos.connection.HeosClient;

/** Sends commands and maps responses; shared by the high-level handles. */
final class Requests {

    private final HeosClient client;

    Requests(HeosClient client) {
        this.client = client;
    }

    HeosClient client() {
        return client;
    }

    /** Builds a command from alternating attribute names and values; {@code null} values are skipped. */
    static HeosCommand command(String name, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Attributes must be key/value pairs");
        }
        HeosCommand.Builder builder = HeosCommand.builder(name);
        for (int i = 0; i < keyValues.length; i += 2) {
            builder.attribute((String) keyValues[i], keyValues[i + 1]);
        }
        return builder.build();
    }

    CompletableFuture<HeosMessage> send(HeosCommand command) {
        return client.send(command);
    }

    <T> CompletableFuture<T> send(HeosCommand command, Function<HeosMessage, T> mapper) {
        return client.send(command).thenApply(mapper);
    }

    CompletableFuture<Void> run(HeosCommand command) {
        return client.send(command).thenApply(response -> null);
    }

    // ---- validation --------------------------------------------------------------------------

    static int checkRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + " but was " + value);
        }
        return value;
    }

    static String checkLength(String value, int maxLength, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static String joinIds(Collection<? extends Number> ids, String name) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    static String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
