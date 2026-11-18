// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hellos.testing.FakeHeosServer.success;
import static org.hellos.testing.Wait.waitUntil;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommand;
import org.hellos.testing.FakeHeosServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The library logs through java.util.logging, which also exists on Android. */
class LoggingTest {

    private static final Logger CONNECTION_LOG = Logger.getLogger(HeosConnection.class.getName());
    private static final Logger CLIENT_LOG = Logger.getLogger(HeosClient.class.getName());

    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            record.getSourceClassName(); // inferred lazily from the calling thread's stack
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Level connectionLevel;
    private FakeHeosServer server;
    private HeosClient client;

    @BeforeEach
    void setUp() throws Exception {
        connectionLevel = CONNECTION_LOG.getLevel();
        CONNECTION_LOG.setLevel(Level.FINE);
        CONNECTION_LOG.addHandler(handler);
        CLIENT_LOG.addHandler(handler);
        server = new FakeHeosServer();
        server.reply(Commands.SYSTEM_SIGN_IN, success(Commands.SYSTEM_SIGN_IN, "signed_in&un=user@example.com"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        server.close();
        CONNECTION_LOG.removeHandler(handler);
        CLIENT_LOG.removeHandler(handler);
        CONNECTION_LOG.setLevel(connectionLevel);
    }

    @Test
    void logsThroughJavaUtilLoggingWithMaskedPassword() throws Exception {
        client = HeosClient.builder().host(server.host()).port(server.port())
                .heartbeatInterval(Duration.ZERO).changeEvents(false).build();
        client.connect().get(3, TimeUnit.SECONDS);
        client.send(HeosCommand.builder(Commands.SYSTEM_SIGN_IN)
                .attribute("un", "user@example.com").attribute("pw", "s3cr3t").build()).get(3, TimeUnit.SECONDS);

        waitUntil(() -> messages().stream().anyMatch(m -> m.contains("<-") && m.contains("signed_in")));
        List<String> messages = messages();
        assertThat(messages).anyMatch(m -> m.equals("Connected to HEOS device " + server.host()));
        assertThat(messages).anyMatch(m -> m.equals("Connected to " + server.host() + ":" + server.port()));
        assertThat(messages).anyMatch(m -> m.contains("-> heos://system/sign_in?un=user@example.com&pw=********"));
        assertThat(messages).noneMatch(m -> m.contains("s3cr3t"));
        assertThat(records).anyMatch(r -> r.getLevel() == Level.INFO && r.getLoggerName().equals(HeosClient.class.getName()));
        assertThat(records).anyMatch(r -> r.getLevel() == Level.FINE
                && r.getLoggerName().equals(HeosConnection.class.getName())
                && HeosConnection.class.getName().equals(r.getSourceClassName()));
    }

    private List<String> messages() {
        SimpleFormatter formatter = new SimpleFormatter();
        return records.stream().map(formatter::formatMessage).toList();
    }
}
