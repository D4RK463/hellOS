// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;

/** The state of a {@link HeosClient}. */
public enum ConnectionState {
    /** Not connected, {@link HeosClient#connect()} has not been called or all reconnect attempts failed. */
    DISCONNECTED,
    /** The initial connect is in progress. */
    CONNECTING,
    /** Connected; commands can be sent. */
    CONNECTED,
    /** The connection was lost and the client is trying to reconnect. */
    RECONNECTING,
    /** {@link HeosClient#close()} was called; the client cannot be used anymore. */
    CLOSED
}
