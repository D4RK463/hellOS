// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.hellos.cli.HeosText;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A minimal HEOS device emulation for tests. It answers commands with configurable JSON lines,
 * sends change events only to connections that registered for them and can drop connections.
 *
 * <p>Built-in behavior: {@code system/heart_beat} and {@code system/register_for_change_events}
 * succeed, {@code system/reboot} is not answered, everything else fails with {@code eid=1}.
 */
public final class FakeHeosServer implements AutoCloseable {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ServerSocket serverSocket;
    private final Map<String, Function<Request, List<String>>> handlers = new ConcurrentHashMap<>();
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Request> requestQueue = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    public FakeHeosServer() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().name("fake-heos-accept").start(this::acceptLoop);
    }

    public String host() {
        return serverSocket.getInetAddress().getHostAddress();
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    /** Answers the command with the lines returned by the handler (may be empty for no answer). */
    public FakeHeosServer on(String commandName, Function<Request, List<String>> handler) {
        handlers.put(commandName, handler);
        return this;
    }

    /** Answers the command with fixed lines. */
    public FakeHeosServer reply(String commandName, String... lines) {
        return on(commandName, request -> List.of(lines));
    }

    /** Sends an event to every connection that registered for change events. */
    public void pushEvent(String json) {
        for (ClientHandler client : clients) {
            if (client.registered) {
                client.write(json);
            }
        }
    }

    /** Sends a raw line to all connections, regardless of registration. */
    public void pushToAll(String line) {
        clients.forEach(client -> client.write(line));
    }

    /** Closes all client connections, the server keeps accepting new ones. */
    public void dropConnections() {
        clients.forEach(ClientHandler::close);
    }

    public int connectionCount() {
        return (int) clients.stream().filter(c -> !c.socket.isClosed()).count();
    }

    public int registeredConnectionCount() {
        return (int) clients.stream().filter(c -> !c.socket.isClosed() && c.registered).count();
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public long count(String commandName) {
        return requests.stream().filter(r -> r.name().equals(commandName)).count();
    }

    /** Waits for the next received request with the given command name. */
    public Request awaitRequest(String commandName, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new AssertionError("No request " + commandName + " within " + timeout);
            }
            Request request = requestQueue.poll(remaining, TimeUnit.NANOSECONDS);
            if (request != null && request.name().equals(commandName)) {
                return request;
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        serverSocket.close();
        dropConnections();
    }

    // ---- JSON helpers -----------------------------------------------------------------------

    public static String success(String command, String message) {
        return response(command, "success", message, null);
    }

    public static String success(String command, String message, String payloadJson) {
        return response(command, "success", message, payloadJson);
    }

    /** A success response that echoes the request attributes in the message, like real devices do. */
    public static List<String> echo(Request request) {
        StringBuilder message = new StringBuilder();
        request.attributes().forEach((key, value) -> {
            if (!message.isEmpty()) {
                message.append('&');
            }
            message.append(key).append('=').append(key.equals("url") ? value : HeosText.encode(value));
        });
        return List.of(success(request.name(), message.toString()));
    }

    /** Answers the given commands with {@link #echo(Request)}. */
    public FakeHeosServer echo(String... commandNames) {
        for (String name : commandNames) {
            on(name, FakeHeosServer::echo);
        }
        return this;
    }

    public static String failure(String command, int eid, String text) {
        return response(command, "fail", "eid=" + eid + "&text=" + text, null);
    }

    public static String event(String command, String message) {
        ObjectNode heos = MAPPER.createObjectNode().put("command", command);
        if (message != null) {
            heos.put("message", message);
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set("heos", heos);
        return MAPPER.writeValueAsString(root);
    }

    private static String response(String command, String result, String message, String payloadJson) {
        ObjectNode heos = MAPPER.createObjectNode()
                .put("command", command)
                .put("result", result)
                .put("message", message == null ? "" : message);
        ObjectNode root = MAPPER.createObjectNode();
        root.set("heos", heos);
        if (payloadJson != null) {
            root.set("payload", MAPPER.readTree(payloadJson));
        }
        return MAPPER.writeValueAsString(root);
    }

    // ---- internals ----------------------------------------------------------------------------

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler client = new ClientHandler(socket);
                clients.add(client);
                Thread.ofVirtual().name("fake-heos-client").start(client::run);
            } catch (IOException e) {
                return;
            }
        }
    }

    private List<String> handle(ClientHandler client, Request request) {
        Function<Request, List<String>> handler = handlers.get(request.name());
        if (handler != null) {
            return handler.apply(request);
        }
        return switch (request.name()) {
            case "system/heart_beat" -> List.of(success(request.name(), ""));
            case "system/register_for_change_events" -> {
                client.registered = "on".equals(request.attributes().get("enable"));
                yield List.of(success(request.name(), "enable=" + request.attributes().get("enable")));
            }
            case "system/reboot" -> List.of();
            default -> List.of(failure(request.name(), 1, "Command not registered: heos://" + request.name()));
        };
    }

    /** A request received by the fake server. */
    public record Request(String uri, String name, Map<String, String> attributes) {

        static Request parse(String line) {
            String uri = line.strip();
            String rest = uri.startsWith("heos://") ? uri.substring(7) : uri;
            int question = rest.indexOf('?');
            String name = question < 0 ? rest : rest.substring(0, question);
            Map<String, String> attributes = new LinkedHashMap<>();
            if (question >= 0) {
                String query = rest.substring(question + 1);
                int start = 0;
                while (start < query.length()) {
                    if (query.startsWith("url=", start)) {
                        attributes.put("url", query.substring(start + 4));
                        break;
                    }
                    int end = query.indexOf('&', start);
                    end = end < 0 ? query.length() : end;
                    String pair = query.substring(start, end);
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        attributes.put(pair.substring(0, eq), HeosText.decode(pair.substring(eq + 1)));
                    }
                    start = end + 1;
                }
            }
            return new Request(uri, name, attributes);
        }
    }

    private final class ClientHandler {
        private final Socket socket;
        private final OutputStream out;
        volatile boolean registered;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = socket.getOutputStream();
        }

        void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    Request request = Request.parse(line);
                    requests.add(request);
                    requestQueue.add(request);
                    handle(this, request).forEach(this::write);
                }
            } catch (IOException e) {
                // connection closed
            } finally {
                close();
            }
        }

        synchronized void write(String line) {
            try {
                out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                close();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }
}
