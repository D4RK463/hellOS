// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import java.time.Duration;

/**
 * Thrown when a HEOS device does not answer a command within the configured timeout.
 */
public class HeosTimeoutException extends HeosException {

    private static final long serialVersionUID = 1L;

    private final String commandName;
    private final Duration timeout;

    public HeosTimeoutException(String commandName, Duration timeout) {
        super("No response to " + commandName + " within " + timeout.toMillis() + " ms");
        this.commandName = commandName;
        this.timeout = timeout;
    }

    /** The name of the command that timed out, e.g. {@code player/get_volume}. */
    public String commandName() {
        return commandName;
    }

    public Duration timeout() {
        return timeout;
    }
}
