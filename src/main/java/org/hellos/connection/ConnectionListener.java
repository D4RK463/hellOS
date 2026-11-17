// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;

import org.hellos.cli.HeosException;

/**
 * Receives state changes of a {@link HeosClient}. Listeners are called one after another on a
 * single callback thread, in the order the changes happened.
 */
@FunctionalInterface
public interface ConnectionListener {

    /**
     * @param state the new state
     * @param host  the host the client is or was connected to, {@code null} if unknown
     * @param cause the reason for {@link ConnectionState#RECONNECTING} or
     *              {@link ConnectionState#DISCONNECTED}, otherwise {@code null}
     */
    void onStateChanged(ConnectionState state, String host, HeosException cause);
}
