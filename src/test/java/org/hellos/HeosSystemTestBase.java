// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import java.io.IOException;
import java.time.Duration;

import org.hellos.testing.FakeHeosServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Starts a fake HEOS device and a connected {@link HeosSystem} for every test. */
abstract class HeosSystemTestBase {

    static final Duration TIMEOUT = Duration.ofSeconds(2);

    FakeHeosServer server;
    HeosSystem heos;

    @BeforeEach
    void startServer() throws IOException {
        server = new FakeHeosServer();
    }

    @AfterEach
    void stop() throws IOException {
        if (heos != null) {
            heos.close();
        }
        server.close();
    }

    HeosSystem.Builder builder() {
        return HeosSystem.builder()
                .host(server.host())
                .configureClient(client -> client
                        .port(server.port())
                        .commandTimeout(Duration.ofSeconds(1))
                        .heartbeatInterval(Duration.ZERO)
                        .reconnectDelay(Duration.ofMillis(50)));
    }

    HeosSystem connect() {
        heos = builder().build();
        heos.connect().orTimeout(3, java.util.concurrent.TimeUnit.SECONDS).join();
        return heos;
    }

    FakeHeosServer.Request lastRequest(String name) throws InterruptedException {
        return server.awaitRequest(name, TIMEOUT);
    }
}
