// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.hellos.cli.Commands;
import org.hellos.cli.HeosCommand;
import org.hellos.cli.HeosCommandException;
import org.hellos.cli.HeosConnectionException;
import org.hellos.cli.HeosException;
import org.hellos.cli.HeosMessage;
import org.hellos.cli.HeosProtocolException;
import org.hellos.cli.HeosResponseParser;
import org.hellos.cli.HeosTimeoutException;

/**
 * A single TCP connection to the CLI port of a HEOS device.
 *
 * <p>The protocol has no request ids, so commands are sent strictly one after another: the next
 * command is written only after the previous one was answered or timed out. Intermediate
 * "command under process" answers are skipped. Change events are handed to the event sink on the
 * reader thread, so the sink must not block.
 *
 * <p>Most applications use {@link HeosClient}, which adds heartbeat, reconnect and a separate event
 * connection on top of this class.
 */
public final class HeosConnection implements AutoCloseable {

    /** The CLI port of HEOS devices. */
    public static final int DEFAULT_PORT = 1255;

    static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    private static final System.Logger LOG = System.getLogger(HeosConnection.class.getName());

    private final String host;
    private final int port;
    private final Duration commandTimeout;
    private final Socket socket;
    private final OutputStream out;
    private final Consumer<HeosMessage> eventSink;
    private final Consumer<HeosException> closeHandler;
    private final BlockingQueue<PendingCommand> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread readerThread;
    private final Thread writerThread;
    private volatile PendingCommand inFlight;
    private volatile Instant lastActivity = Instant.now();

    private HeosConnection(String host, int port, Duration commandTimeout, Socket socket,
                           Consumer<HeosMessage> eventSink, Consumer<HeosException> closeHandler) throws IOException {
        this.host = host;
        this.port = port;
        this.commandTimeout = commandTimeout;
        this.socket = socket;
        this.out = socket.getOutputStream();
        this.eventSink = eventSink;
        this.closeHandler = closeHandler;
        String suffix = host + ":" + port;
        this.readerThread = Thread.ofVirtual().name("hellos-reader-" + suffix).unstarted(this::readLoop);
        this.writerThread = Thread.ofVirtual().name("hellos-writer-" + suffix).unstarted(this::writeLoop);
    }

    /**
     * Opens a connection. Blocks until the TCP connection is established.
     *
     * @param eventSink    receives change events; called on the reader thread, must not block
     * @param closeHandler called exactly once when the connection is closed; the argument is
     *                     {@code null} if {@link #close()} was called, otherwise the cause
     * @throws HeosConnectionException if the connection cannot be established
     */
    public static HeosConnection open(String host, int port, Duration connectTimeout, Duration commandTimeout,
                                      Consumer<HeosMessage> eventSink, Consumer<HeosException> closeHandler) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(eventSink, "eventSink");
        Objects.requireNonNull(closeHandler, "closeHandler");
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.connect(new InetSocketAddress(host, port), Math.toIntExact(connectTimeout.toMillis()));
            HeosConnection connection = new HeosConnection(host, port, commandTimeout, socket, eventSink, closeHandler);
            connection.readerThread.start();
            connection.writerThread.start();
            LOG.log(DEBUG, "Connected to {0}:{1,number,#}", host, port);
            return connection;
        } catch (IOException e) {
            closeQuietly(socket);
            throw new HeosConnectionException("Unable to connect to " + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    /**
     * Queues a command. The returned future completes with the response, or exceptionally with
     * {@link HeosCommandException} (device answered {@code fail}), {@link HeosTimeoutException} or
     * {@link HeosConnectionException}.
     */
    public CompletableFuture<HeosMessage> send(HeosCommand command) {
        Objects.requireNonNull(command, "command");
        CompletableFuture<HeosMessage> future = new CompletableFuture<>();
        if (Commands.SYSTEM_PRETTIFY_JSON_RESPONSE.equals(command.name()) && "on".equals(command.attribute("enable"))) {
            future.completeExceptionally(new IllegalArgumentException(
                    "prettify_json_response=on breaks the line-based protocol and is not supported"));
            return future;
        }
        if (closed.get()) {
            future.completeExceptionally(closedException());
            return future;
        }
        queue.add(new PendingCommand(command, future));
        if (closed.get()) {
            failQueued(closedException());
        }
        return future;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean isOpen() {
        return !closed.get();
    }

    /** The last time something was sent or received on this connection. */
    public Instant lastActivity() {
        return lastActivity;
    }

    /** Closes the connection. Outstanding commands fail with {@link HeosConnectionException}. */
    @Override
    public void close() {
        shutdown(null);
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                PendingCommand pending = queue.take();
                if (pending.future().isDone()) {
                    continue;
                }
                inFlight = pending;
                try {
                    out.write(pending.command().toWireFormat().getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    lastActivity = Instant.now();
                    LOG.log(DEBUG, "{0} -> {1}", host, pending.command());
                } catch (IOException e) {
                    HeosConnectionException failure = new HeosConnectionException(
                            "Failed to send " + pending.command().name() + " to " + host, e);
                    pending.future().completeExceptionally(failure);
                    inFlight = null;
                    shutdown(failure);
                    return;
                }
                if (Commands.SYSTEM_REBOOT.equals(pending.command().name())) {
                    pending.future().complete(HeosMessage.synthetic(Commands.SYSTEM_REBOOT));
                } else {
                    awaitResponse(pending);
                }
                inFlight = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitResponse(PendingCommand pending) throws InterruptedException {
        try {
            pending.future().get(commandTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.log(DEBUG, "{0}: no response to {1}", host, pending.command().name());
            pending.future().completeExceptionally(new HeosTimeoutException(pending.command().name(), commandTimeout));
        } catch (ExecutionException | CancellationException e) {
            // the outcome was already delivered to the caller
        }
    }

    private void readLoop() {
        HeosException cause;
        try {
            FrameReader reader = new FrameReader(new BufferedInputStream(socket.getInputStream()), MAX_FRAME_BYTES);
            while (true) {
                String frame = reader.readFrame();
                if (frame == null) {
                    cause = new HeosConnectionException("Connection closed by " + host);
                    break;
                }
                lastActivity = Instant.now();
                dispatch(frame);
            }
        } catch (IOException | HeosProtocolException e) {
            cause = new HeosConnectionException("Connection to " + host + " lost: " + e.getMessage(), e);
        }
        shutdown(cause);
    }

    private void dispatch(String frame) {
        HeosMessage message;
        try {
            message = HeosResponseParser.parse(frame);
        } catch (HeosProtocolException e) {
            LOG.log(WARNING, "{0}: ignoring malformed message: {1}", host, e.getMessage());
            return;
        }
        if (message.isUnderProcess()) {
            LOG.log(DEBUG, "{0}: command under process: {1}", host, message.command());
            return;
        }
        if (message.isEvent()) {
            LOG.log(DEBUG, "{0} <- event {1}", host, message);
            try {
                eventSink.accept(message);
            } catch (RuntimeException e) {
                LOG.log(WARNING, "Event sink failed for " + message.command(), e);
            }
            return;
        }
        PendingCommand pending = inFlight;
        if (pending != null && pending.command().name().equals(message.command())) {
            LOG.log(DEBUG, "{0} <- {1}", host, message);
            if (message.isFailure()) {
                pending.future().completeExceptionally(HeosCommandException.from(message));
            } else {
                pending.future().complete(message);
            }
        } else {
            LOG.log(DEBUG, "{0}: unexpected response {1}", host, message);
        }
    }

    private void shutdown(HeosException cause) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(socket);
        writerThread.interrupt();
        HeosException failure = cause != null ? cause : closedException();
        PendingCommand pending = inFlight;
        if (pending != null) {
            pending.future().completeExceptionally(failure);
        }
        failQueued(failure);
        if (cause != null) {
            LOG.log(DEBUG, "Connection to {0} closed: {1}", host, cause.getMessage());
        }
        try {
            closeHandler.accept(cause);
        } catch (RuntimeException e) {
            LOG.log(WARNING, "Close handler failed", e);
        }
    }

    private void failQueued(HeosException failure) {
        PendingCommand pending;
        while ((pending = queue.poll()) != null) {
            pending.future().completeExceptionally(failure);
        }
    }

    private HeosConnectionException closedException() {
        return new HeosConnectionException("Connection to " + host + " is closed");
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    private record PendingCommand(HeosCommand command, CompletableFuture<HeosMessage> future) {
    }
}
