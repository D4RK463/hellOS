// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.blocking;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.hellos.cli.HeosException;

/** Waits for a future and rethrows its failure unwrapped. */
final class Await {

    private Await() {
    }

    static <T> T result(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw new HeosException("Interrupted while waiting for the HEOS device", e);
        } catch (CancellationException e) {
            throw new HeosException("Command was cancelled", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new HeosException(String.valueOf(cause.getMessage()), cause);
        }
    }

    static void run(CompletableFuture<Void> future) {
        result(future);
    }
}
