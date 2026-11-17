// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

/**
 * Base class of all exceptions thrown by hellOS.
 */
public class HeosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HeosException(String message) {
        super(message);
    }

    public HeosException(String message, Throwable cause) {
        super(message, cause);
    }
}
