// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import java.util.Objects;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * A message received from a HEOS device: either the response to a command or an unsolicited
 * change event.
 *
 * @param command the normalized command name, e.g. {@code player/get_volume} or
 *                {@code event/player_volume_changed}
 * @param result  the result of a command response, {@link Result#NONE} for events
 * @param message the parsed {@code message} field
 * @param payload the {@code payload} element or a {@link MissingNode} if absent. String values in
 *                the payload are still escaped, see {@link HeosText#decode(String)}
 * @param options the {@code options} element or a {@link MissingNode} if absent
 * @param raw     the JSON line exactly as received; may contain sensitive data, do not log it
 */
public record HeosMessage(
        String command,
        Result result,
        MessageParameters message,
        JsonNode payload,
        JsonNode options,
        String raw) {

    /** The prefix of all change event names. */
    public static final String EVENT_PREFIX = "event/";
    /** Flag the device sends while it is still processing a command. */
    public static final String UNDER_PROCESS_FLAG = "command under process";

    public HeosMessage {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(message, "message");
        payload = payload == null ? MissingNode.getInstance() : payload;
        options = options == null ? MissingNode.getInstance() : options;
        raw = raw == null ? "" : raw;
    }

    /**
     * Creates a successful message without payload. Used for commands the device never answers,
     * like {@code system/reboot}.
     */
    public static HeosMessage synthetic(String command) {
        return new HeosMessage(command, Result.SUCCESS, MessageParameters.empty(), null, null, "");
    }

    public boolean isEvent() {
        return command.startsWith(EVENT_PREFIX);
    }

    /** Whether this is the intermediate "command under process" answer to a long-running command. */
    public boolean isUnderProcess() {
        return message.has(UNDER_PROCESS_FLAG);
    }

    public boolean isSuccess() {
        return result == Result.SUCCESS;
    }

    public boolean isFailure() {
        return result == Result.FAIL;
    }

    public boolean hasPayload() {
        return !payload.isMissingNode() && !payload.isNull();
    }

    public boolean hasOptions() {
        return !options.isMissingNode() && !options.isNull();
    }

    /** A short description with the password masked, safe to log. Does not contain the payload. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(command);
        if (result != Result.NONE) {
            sb.append(" [").append(result.name().toLowerCase(java.util.Locale.ROOT)).append(']');
        }
        if (!message.isEmpty()) {
            sb.append(' ').append(message);
        }
        if (hasPayload()) {
            sb.append(" (payload)");
        }
        return sb.toString();
    }

    /** The {@code result} field of a response. */
    public enum Result {
        SUCCESS,
        FAIL,
        /** The message has no result, which is the case for change events. */
        NONE
    }
}
