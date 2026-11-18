// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommand;
import org.hellos.cli.HeosCommandException;
import org.hellos.cli.HeosConnectionException;
import org.hellos.cli.HeosException;
import org.hellos.cli.HeosMessage;
import org.hellos.internal.Threads;

/**
 * Low-level client for the HEOS CLI protocol.
 *
 * <p>The client keeps two connections to one HEOS device, as recommended by the specification: one
 * for commands and one that is registered for change events. One device is enough to control the
 * whole HEOS system. On top of that the client
 * <ul>
 *   <li>sends {@code system/heart_beat} on idle connections to detect broken connections,</li>
 *   <li>reconnects automatically after a connection loss and</li>
 *   <li>fails over to the next configured host if the current one is not reachable.</li>
 * </ul>
 *
 * <p>Commands are sent with {@link #send(HeosCommand)}; responses arrive as {@link HeosMessage}.
 * Change events and connection state changes are delivered to listeners on a single callback
 * thread, in order.
 *
 * <pre>{@code
 * try (HeosClient client = HeosClient.builder().host("192.168.1.20").build()) {
 *     client.addEventListener(event -> System.out.println(event));
 *     client.connect().join();
 *     HeosMessage players = client.send(HeosCommand.of(Commands.PLAYER_GET_PLAYERS)).join();
 * }
 * }</pre>
 */
public final class HeosClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(HeosClient.class.getName());

    private final List<String> hosts;
    private final int port;
    private final Duration connectTimeout;
    private final Duration commandTimeout;
    private final Duration heartbeatInterval;
    private final boolean reconnect;
    private final Duration reconnectDelay;
    private final int maxReconnectAttempts;
    private final boolean changeEvents;
    private final ThreadFactory threadFactory;

    private final List<Consumer<HeosMessage>> eventListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService callbacks;

    private final Object lock = new Object();
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private CompletableFuture<Void> connectedSignal = new CompletableFuture<>();
    private Session session;
    private String lastHost;
    private Thread heartbeatThread;
    private Thread reconnectThread;

    private HeosClient(Builder builder) {
        this.hosts = List.copyOf(builder.hosts);
        this.port = builder.port;
        this.connectTimeout = builder.connectTimeout;
        this.commandTimeout = builder.commandTimeout;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.reconnect = builder.reconnect;
        this.reconnectDelay = builder.reconnectDelay;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.changeEvents = builder.changeEvents;
        this.threadFactory = builder.threadFactory;
        this.callbacks = Executors.newSingleThreadExecutor(Threads.factory(threadFactory, "hellos-callbacks"));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Creates a client for one host with default settings. */
    public static HeosClient create(String host) {
        return builder().host(host).build();
    }

    /**
     * Connects to the first reachable host. The future completes once the client is
     * {@link ConnectionState#CONNECTED}. If the client is already connecting or reconnecting, the
     * future of that attempt is returned.
     */
    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> signal;
        synchronized (lock) {
            switch (state) {
                case CLOSED -> {
                    return CompletableFuture.failedFuture(new IllegalStateException("Client is closed"));
                }
                case CONNECTED, CONNECTING, RECONNECTING -> {
                    return connectedSignal.copy();
                }
                case DISCONNECTED -> setStateLocked(ConnectionState.CONNECTING, null, null);
            }
            signal = connectedSignal.copy();
        }
        Threads.start(threadFactory, "hellos-connect", () -> {
            try {
                activate(openFirstAvailable(null));
            } catch (HeosException e) {
                synchronized (lock) {
                    if (state == ConnectionState.CONNECTING) {
                        setStateLocked(ConnectionState.DISCONNECTED, null, e);
                    }
                }
            }
        });
        return signal;
    }

    /**
     * Sends a command on the command connection.
     *
     * @return a future that completes with the response, or exceptionally with
     *         {@link HeosCommandException}, {@link org.hellos.cli.HeosTimeoutException} or
     *         {@link HeosConnectionException} (also if the client is not connected)
     */
    public CompletableFuture<HeosMessage> send(HeosCommand command) {
        Objects.requireNonNull(command, "command");
        Session current;
        ConnectionState currentState;
        synchronized (lock) {
            current = session;
            currentState = state;
        }
        if (current == null) {
            return CompletableFuture.failedFuture(
                    new HeosConnectionException("Not connected to a HEOS device (state " + currentState + ")"));
        }
        return current.commands.send(command);
    }

    /** Registers a listener for all change events. */
    public Registration addEventListener(Consumer<HeosMessage> listener) {
        Objects.requireNonNull(listener, "listener");
        eventListeners.add(listener);
        return () -> eventListeners.remove(listener);
    }

    /** Registers a listener for one change event, e.g. {@link org.hellos.cli.Events#PLAYER_VOLUME_CHANGED}. */
    public Registration addEventListener(String eventName, Consumer<HeosMessage> listener) {
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(listener, "listener");
        return addEventListener(event -> {
            if (event.command().equals(eventName)) {
                listener.accept(event);
            }
        });
    }

    public Registration addConnectionListener(ConnectionListener listener) {
        Objects.requireNonNull(listener, "listener");
        connectionListeners.add(listener);
        return () -> connectionListeners.remove(listener);
    }

    public ConnectionState state() {
        synchronized (lock) {
            return state;
        }
    }

    public boolean isConnected() {
        return state() == ConnectionState.CONNECTED;
    }

    /** The host the client is currently connected to. */
    public Optional<String> currentHost() {
        synchronized (lock) {
            return session == null ? Optional.empty() : Optional.of(session.host);
        }
    }

    /** The configured hosts in failover order. */
    public List<String> hosts() {
        return hosts;
    }

    /** Closes all connections and stops reconnecting. The client cannot be reused. */
    @Override
    public void close() {
        Session current;
        Thread heartbeat;
        Thread reconnecting;
        synchronized (lock) {
            if (state == ConnectionState.CLOSED) {
                return;
            }
            current = session;
            session = null;
            heartbeat = heartbeatThread;
            reconnecting = reconnectThread;
            heartbeatThread = null;
            reconnectThread = null;
            setStateLocked(ConnectionState.CLOSED, lastHost, null);
        }
        if (current != null) {
            current.close();
        }
        if (heartbeat != null) {
            heartbeat.interrupt();
        }
        if (reconnecting != null) {
            reconnecting.interrupt();
        }
        callbacks.shutdown();
    }

    // ---- connection handling ------------------------------------------------------------------

    private Session openFirstAvailable(String preferredHost) {
        List<String> order = new ArrayList<>(hosts);
        if (preferredHost != null && order.remove(preferredHost)) {
            order.add(0, preferredHost);
        }
        HeosConnectionException failure = null;
        for (String host : order) {
            if (state() == ConnectionState.CLOSED) {
                throw new HeosConnectionException("Client is closed");
            }
            try {
                return openSession(host);
            } catch (HeosException e) {
                LOG.log(Level.FINE, "Connecting to {0} failed: {1}", new Object[] {host, e.getMessage()});
                if (failure == null) {
                    failure = new HeosConnectionException("Unable to connect to any HEOS host of " + hosts);
                }
                failure.addSuppressed(e);
            }
        }
        throw failure;
    }

    private Session openSession(String host) {
        Session newSession = new Session(host);
        try {
            newSession.commands = HeosConnection.open(host, port, connectTimeout, commandTimeout,
                    this::dispatchEvent, cause -> connectionLost(newSession, cause), threadFactory);
            if (changeEvents) {
                newSession.events = HeosConnection.open(host, port, connectTimeout, commandTimeout,
                        this::dispatchEvent, cause -> connectionLost(newSession, cause), threadFactory);
                await(newSession.events.send(HeosCommand.builder(Commands.SYSTEM_REGISTER_FOR_CHANGE_EVENTS)
                        .attribute("enable", "on").build()));
            }
            return newSession;
        } catch (HeosException e) {
            newSession.close();
            throw e;
        }
    }

    private void activate(Session newSession) {
        synchronized (lock) {
            if (state != ConnectionState.CLOSED && newSession.isOpen()) {
                session = newSession;
                lastHost = newSession.host;
                setStateLocked(ConnectionState.CONNECTED, newSession.host, null);
                startHeartbeatLocked();
                LOG.log(Level.INFO, "Connected to HEOS device {0}", newSession.host);
                return;
            }
        }
        newSession.close();
        throw new HeosConnectionException("Connection to " + newSession.host + " was closed during setup");
    }

    private void connectionLost(Session lostSession, HeosException cause) {
        if (cause == null) {
            return; // closed on purpose
        }
        synchronized (lock) {
            if (session != lostSession) {
                return;
            }
            session = null;
            LOG.log(Level.WARNING, "Connection to HEOS device {0} lost: {1}",
                    new Object[] {lostSession.host, cause.getMessage()});
            if (reconnect) {
                setStateLocked(ConnectionState.RECONNECTING, lostSession.host, cause);
                startReconnectLocked(lostSession.host);
            } else {
                setStateLocked(ConnectionState.DISCONNECTED, lostSession.host, cause);
            }
        }
        lostSession.close();
    }

    private void startReconnectLocked(String preferredHost) {
        if (reconnectThread != null && reconnectThread.isAlive()) {
            return;
        }
        reconnectThread = Threads.start(threadFactory, "hellos-reconnect", () -> reconnectLoop(preferredHost));
    }

    private void reconnectLoop(String preferredHost) {
        int attempt = 0;
        try {
            while (true) {
                Thread.sleep(reconnectDelay.toMillis());
                synchronized (lock) {
                    if (state != ConnectionState.RECONNECTING) {
                        return;
                    }
                }
                attempt++;
                try {
                    activate(openFirstAvailable(preferredHost));
                    return;
                } catch (HeosException e) {
                    LOG.log(Level.FINE, "Reconnect attempt {0} failed: {1}", new Object[] {attempt, e.getMessage()});
                    if (maxReconnectAttempts > 0 && attempt >= maxReconnectAttempts) {
                        synchronized (lock) {
                            if (state == ConnectionState.RECONNECTING) {
                                setStateLocked(ConnectionState.DISCONNECTED, preferredHost, e);
                            }
                        }
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (lock) {
                if (reconnectThread == Thread.currentThread()) {
                    reconnectThread = null;
                }
            }
        }
    }

    private void startHeartbeatLocked() {
        if (heartbeatInterval.isZero() || (heartbeatThread != null && heartbeatThread.isAlive())) {
            return;
        }
        heartbeatThread = Threads.start(threadFactory, "hellos-heartbeat", this::heartbeatLoop);
    }

    private void heartbeatLoop() {
        Duration period = heartbeatInterval.dividedBy(2);
        if (period.toMillis() < 20) {
            period = Duration.ofMillis(20);
        }
        try {
            while (true) {
                Thread.sleep(period.toMillis());
                Session current;
                synchronized (lock) {
                    if (state == ConnectionState.CLOSED) {
                        return;
                    }
                    current = session;
                }
                if (current == null) {
                    continue;
                }
                try {
                    heartbeatIfIdle(current.commands);
                    if (current.events != null) {
                        heartbeatIfIdle(current.events);
                    }
                } catch (HeosCommandException e) {
                    LOG.log(Level.FINE, "Heartbeat answered with failure: {0}", e.getMessage());
                } catch (HeosException e) {
                    connectionLost(current, e instanceof HeosConnectionException hce
                            ? hce
                            : new HeosConnectionException("Heartbeat failed: " + e.getMessage(), e));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void heartbeatIfIdle(HeosConnection connection) {
        if (Duration.between(connection.lastActivity(), Instant.now()).compareTo(heartbeatInterval) >= 0) {
            await(connection.send(HeosCommand.of(Commands.SYSTEM_HEART_BEAT)));
        }
    }

    // ---- callbacks ----------------------------------------------------------------------------

    private void dispatchEvent(HeosMessage event) {
        runCallback(() -> {
            for (Consumer<HeosMessage> listener : eventListeners) {
                try {
                    listener.accept(event);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Event listener failed for " + event.command(), e);
                }
            }
        });
    }

    private void setStateLocked(ConnectionState newState, String host, HeosException cause) {
        if (state == newState) {
            return;
        }
        state = newState;
        switch (newState) {
            case CONNECTED -> connectedSignal.complete(null);
            case CONNECTING, RECONNECTING -> {
                if (connectedSignal.isDone()) {
                    connectedSignal = new CompletableFuture<>();
                }
            }
            case DISCONNECTED -> {
                connectedSignal.completeExceptionally(
                        cause != null ? cause : new HeosConnectionException("Disconnected"));
                connectedSignal = new CompletableFuture<>();
            }
            case CLOSED -> connectedSignal.completeExceptionally(new HeosConnectionException("Client is closed"));
        }
        runCallback(() -> {
            for (ConnectionListener listener : connectionListeners) {
                try {
                    listener.onStateChanged(newState, host, cause);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Connection listener failed", e);
                }
            }
        });
    }

    private void runCallback(Runnable task) {
        try {
            callbacks.execute(task);
        } catch (RejectedExecutionException e) {
            LOG.log(Level.FINE, "Client closed, dropping callback");
        }
    }

    private static HeosMessage await(CompletableFuture<HeosMessage> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof HeosException heosException) {
                throw heosException;
            }
            throw new HeosConnectionException("Command failed: " + e.getCause(), e.getCause());
        }
    }

    // ---- types --------------------------------------------------------------------------------

    /** Handle to remove a registered listener. */
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        /** Removes the listener. */
        @Override
        void close();
    }

    private static final class Session {
        final String host;
        volatile HeosConnection commands;
        volatile HeosConnection events;

        Session(String host) {
            this.host = host;
        }

        boolean isOpen() {
            HeosConnection c = commands;
            HeosConnection e = events;
            return c != null && c.isOpen() && (e == null || e.isOpen());
        }

        void close() {
            HeosConnection c = commands;
            HeosConnection e = events;
            if (c != null) {
                c.close();
            }
            if (e != null) {
                e.close();
            }
        }
    }

    /** Builder for {@link HeosClient}. */
    public static final class Builder {

        private final LinkedHashSet<String> hosts = new LinkedHashSet<>();
        private int port = HeosConnection.DEFAULT_PORT;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration commandTimeout = Duration.ofSeconds(15);
        private Duration heartbeatInterval = Duration.ofSeconds(10);
        private boolean reconnect = true;
        private Duration reconnectDelay = Duration.ofSeconds(2);
        private int maxReconnectAttempts;
        private boolean changeEvents = true;
        private ThreadFactory threadFactory;

        private Builder() {
        }

        /** Adds a host. The first host is preferred, further hosts are used for failover. */
        public Builder host(String host) {
            Objects.requireNonNull(host, "host");
            if (host.isBlank()) {
                throw new IllegalArgumentException("host must not be blank");
            }
            hosts.add(host.strip());
            return this;
        }

        public Builder hosts(Collection<String> hosts) {
            hosts.forEach(this::host);
            return this;
        }

        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid port " + port);
            }
            this.port = port;
            return this;
        }

        /** Timeout for establishing a TCP connection. Default 5 s. */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = positive(timeout, "connectTimeout");
            return this;
        }

        /** Time to wait for the response to a command. Default 15 s. */
        public Builder commandTimeout(Duration timeout) {
            this.commandTimeout = positive(timeout, "commandTimeout");
            return this;
        }

        /** Idle time after which a heartbeat is sent. Default 10 s, {@link Duration#ZERO} disables it. */
        public Builder heartbeatInterval(Duration interval) {
            Objects.requireNonNull(interval, "heartbeatInterval");
            if (interval.isNegative()) {
                throw new IllegalArgumentException("heartbeatInterval must not be negative");
            }
            this.heartbeatInterval = interval;
            return this;
        }

        /** Whether to reconnect after a connection loss. Default {@code true}. */
        public Builder reconnect(boolean reconnect) {
            this.reconnect = reconnect;
            return this;
        }

        /** Delay before each reconnect attempt. Default 2 s. */
        public Builder reconnectDelay(Duration delay) {
            this.reconnectDelay = positive(delay, "reconnectDelay");
            return this;
        }

        /** Maximum number of reconnect rounds over all hosts; 0 (default) means unlimited. */
        public Builder maxReconnectAttempts(int attempts) {
            if (attempts < 0) {
                throw new IllegalArgumentException("maxReconnectAttempts must not be negative");
            }
            this.maxReconnectAttempts = attempts;
            return this;
        }

        /** Whether to open the event connection and register for change events. Default {@code true}. */
        public Builder changeEvents(boolean enabled) {
            this.changeEvents = enabled;
            return this;
        }

        /**
         * Factory for the threads of the client and its connections (reader, writer, heartbeat,
         * reconnect, callbacks). The threads are renamed to {@code hellos-…}. Default
         * {@code null}: virtual threads where available, named platform daemon threads otherwise
         * (always on Android).
         *
         * @since 0.1.1
         */
        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public HeosClient build() {
            if (hosts.isEmpty()) {
                throw new IllegalStateException("At least one host is required");
            }
            return new HeosClient(this);
        }

        private static Duration positive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}
