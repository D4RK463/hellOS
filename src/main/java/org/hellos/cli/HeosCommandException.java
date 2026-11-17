// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import java.util.OptionalInt;

/**
 * Thrown when a HEOS device answers a command with {@code result=fail}.
 */
public class HeosCommandException extends HeosException {

    private static final long serialVersionUID = 1L;

    /** {@code syserrno} sent when an action requires a signed-in HEOS account. */
    public static final int SYSERRNO_USER_NOT_LOGGED_IN = -1063;
    /** {@code syserrno} sent when the user has not registered the requested music service. */
    public static final int SYSERRNO_SERVICE_NOT_REGISTERED = -1061;

    private final String commandName;
    private final HeosErrorCode errorCode;
    private final int errorId;
    private final String errorText;
    private final Integer systemErrorNumber;
    private final transient MessageParameters parameters;

    public HeosCommandException(String commandName, int errorId, String errorText, Integer systemErrorNumber,
                                MessageParameters parameters) {
        super(buildMessage(commandName, errorId, errorText, systemErrorNumber));
        this.commandName = commandName;
        this.errorId = errorId;
        this.errorCode = HeosErrorCode.fromCode(errorId);
        this.errorText = errorText;
        this.systemErrorNumber = systemErrorNumber;
        this.parameters = parameters;
    }

    /** Creates the exception from a failed response. */
    public static HeosCommandException from(HeosMessage response) {
        MessageParameters params = response.message();
        int eid = parseOptionalInt(params, "eid").orElse(HeosErrorCode.UNKNOWN.code());
        String text = params.get("text").orElse("");
        OptionalInt syserrno = parseOptionalInt(params, "syserrno");
        return new HeosCommandException(response.command(), eid, text,
                syserrno.isPresent() ? syserrno.getAsInt() : null, params);
    }

    private static OptionalInt parseOptionalInt(MessageParameters params, String key) {
        try {
            return params.getInt(key);
        } catch (HeosProtocolException e) {
            return OptionalInt.empty();
        }
    }

    private static String buildMessage(String commandName, int errorId, String errorText, Integer syserrno) {
        StringBuilder sb = new StringBuilder("HEOS command ").append(commandName).append(" failed: eid=")
                .append(errorId).append(" (").append(HeosErrorCode.fromCode(errorId)).append(')');
        if (errorText != null && !errorText.isEmpty()) {
            sb.append(' ').append(errorText);
        }
        if (syserrno != null) {
            sb.append(", syserrno=").append(syserrno);
        }
        return sb.toString();
    }

    public String commandName() {
        return commandName;
    }

    public HeosErrorCode errorCode() {
        return errorCode;
    }

    /** The raw {@code eid}, useful when {@link #errorCode()} is {@link HeosErrorCode#UNKNOWN}. */
    public int errorId() {
        return errorId;
    }

    public String errorText() {
        return errorText;
    }

    /** The {@code syserrno} attribute that accompanies {@link HeosErrorCode#SYSTEM_ERROR}. */
    public OptionalInt systemErrorNumber() {
        return systemErrorNumber == null ? OptionalInt.empty() : OptionalInt.of(systemErrorNumber);
    }

    /** All parameters of the failed response. May be {@code null} after deserialization. */
    public MessageParameters parameters() {
        return parameters;
    }

    /** Whether the command failed because no HEOS account is signed in. */
    public boolean isUserNotLoggedIn() {
        return errorCode == HeosErrorCode.USER_NOT_LOGGED_IN
                || (errorCode == HeosErrorCode.SYSTEM_ERROR
                && systemErrorNumber != null && systemErrorNumber == SYSERRNO_USER_NOT_LOGGED_IN);
    }
}
