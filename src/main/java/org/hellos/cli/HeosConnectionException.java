// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

/**
 * Thrown when a connection to a HEOS device cannot be established, is not available or was lost.
 */
public class HeosConnectionException extends HeosException {

    private static final long serialVersionUID = 1L;

    public HeosConnectionException(String message) {
        super(message);
    }

    public HeosConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
