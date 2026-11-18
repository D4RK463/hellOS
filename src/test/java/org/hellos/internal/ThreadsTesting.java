// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

/** Gives tests in other packages access to the fallback switch of {@link Threads}. */
public final class ThreadsTesting {

    private ThreadsTesting() {
    }

    /** Behaves as if the runtime had no virtual threads until the returned handle is closed. */
    public static AutoCloseable simulateVirtualThreadsUnavailable() {
        Threads.virtualThreadsUnavailable = true;
        return () -> Threads.virtualThreadsUnavailable = false;
    }
}
