// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

/**
 * Error ids ({@code eid}) returned by HEOS devices in failed command responses (spec section 6.2).
 */
public enum HeosErrorCode {
    UNRECOGNIZED_COMMAND(1),
    INVALID_ID(2),
    WRONG_NUMBER_OF_ARGUMENTS(3),
    REQUESTED_DATA_NOT_AVAILABLE(4),
    RESOURCE_CURRENTLY_NOT_AVAILABLE(5),
    INVALID_CREDENTIALS(6),
    COMMAND_NOT_EXECUTED(7),
    USER_NOT_LOGGED_IN(8),
    PARAMETER_OUT_OF_RANGE(9),
    USER_NOT_FOUND(10),
    INTERNAL_ERROR(11),
    /** Details are in the {@code syserrno} attribute, see {@link HeosCommandException#systemErrorNumber()}. */
    SYSTEM_ERROR(12),
    PROCESSING_PREVIOUS_COMMAND(13),
    MEDIA_CANNOT_BE_PLAYED(14),
    OPTION_NOT_SUPPORTED(15),
    TOO_MANY_COMMANDS_IN_QUEUE(16),
    REACHED_SKIP_LIMIT(17),
    /** An error id that is not documented in the specification. */
    UNKNOWN(-1);

    private final int code;

    HeosErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static HeosErrorCode fromCode(int code) {
        for (HeosErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
