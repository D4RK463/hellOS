// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.discovery;

import org.hellos.cli.HeosException;

/** Thrown when no HEOS device could be discovered. */
public class HeosDiscoveryException extends HeosException {

    private static final long serialVersionUID = 1L;

    public HeosDiscoveryException(String message) {
        super(message);
    }
}
