// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.testing;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Polls a condition in tests that depend on asynchronous callbacks. */
public final class Wait {

    private Wait() {
    }

    public static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met within 3 s");
            }
            Thread.sleep(10);
        }
    }
}
