// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

/**
 * Thrown when data received from a HEOS device does not match the expected protocol structure.
 */
public class HeosProtocolException extends HeosException {

    private static final long serialVersionUID = 1L;

    public HeosProtocolException(String message) {
        super(message);
    }

    public HeosProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
