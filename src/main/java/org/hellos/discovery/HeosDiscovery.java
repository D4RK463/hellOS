// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.discovery;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hellos.cli.HeosConnectionException;
import org.hellos.internal.Threads;

/**
 * Finds HEOS devices in the local network with UPnP SSDP (spec section 2).
 *
 * <pre>{@code
 * List<DiscoveredDevice> devices = HeosDiscovery.discover(Duration.ofSeconds(3)).join();
 * }</pre>
 */
public final class HeosDiscovery {

    /** The SSDP search target of HEOS devices. */
    public static final String SEARCH_TARGET = "urn:schemas-denon-com:device:ACT-Denon:1";

    static final InetSocketAddress SSDP_MULTICAST = new InetSocketAddress("239.255.255.250", 1900);

    private static final Logger LOG = Logger.getLogger(HeosDiscovery.class.getName());
    private static final int SEND_COUNT = 2;

    private final Duration timeout;
    private final InetAddress bindAddress;
    private final InetSocketAddress target;
    private final ThreadFactory threadFactory;

    private HeosDiscovery(Builder builder) {
        this.timeout = builder.timeout;
        this.bindAddress = builder.bindAddress;
        this.target = builder.target;
        this.threadFactory = builder.threadFactory;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Searches for {@code timeout} and returns all devices found. */
    public static CompletableFuture<List<DiscoveredDevice>> discover(Duration timeout) {
        return builder().timeout(timeout).build().discoverAll(device -> { });
    }

    /**
     * Searches for the configured timeout. {@code onFound} is called for every new device as soon as
     * it answers; the future completes with all devices when the timeout has elapsed.
     */
    public CompletableFuture<List<DiscoveredDevice>> discoverAll(Consumer<DiscoveredDevice> onFound) {
        Objects.requireNonNull(onFound, "onFound");
        CompletableFuture<List<DiscoveredDevice>> result = new CompletableFuture<>();
        Threads.start(threadFactory, "hellos-discovery", () -> {
            try {
                result.complete(search(onFound, false));
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /**
     * Completes with the first device that answers, or exceptionally with
     * {@link HeosDiscoveryException} if none answers within the timeout.
     */
    public CompletableFuture<DiscoveredDevice> discoverFirst() {
        CompletableFuture<DiscoveredDevice> result = new CompletableFuture<>();
        Threads.start(threadFactory, "hellos-discovery", () -> {
            try {
                List<DiscoveredDevice> devices = search(device -> { }, true);
                if (devices.isEmpty()) {
                    result.completeExceptionally(new HeosDiscoveryException(
                            "No HEOS device answered within " + timeout.toMillis() + " ms"));
                } else {
                    result.complete(devices.get(0));
                }
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    private List<DiscoveredDevice> search(Consumer<DiscoveredDevice> onFound, boolean stopAfterFirst) {
        Map<InetAddress, DiscoveredDevice> found = new LinkedHashMap<>();
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(bindAddress, 0))) {
            byte[] request = searchRequest().getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < SEND_COUNT; i++) {
                socket.send(new DatagramPacket(request, request.length, target));
            }
            long deadline = System.nanoTime() + timeout.toNanos();
            byte[] buffer = new byte[4096];
            while (true) {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
                if (remainingMs <= 0) {
                    break;
                }
                socket.setSoTimeout((int) Math.max(1, Math.min(remainingMs, Integer.MAX_VALUE)));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String text = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8);
                Optional<DiscoveredDevice> device = parseResponse(text, packet.getAddress());
                if (device.isPresent() && !found.containsKey(device.get().address())) {
                    found.put(device.get().address(), device.get());
                    LOG.log(Level.FINE, "Found HEOS device {0}", device.get().host());
                    onFound.accept(device.get());
                    if (stopAfterFirst) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new HeosConnectionException("SSDP discovery failed: " + e.getMessage(), e);
        }
        return new ArrayList<>(found.values());
    }

    String searchRequest() {
        long mx = Math.max(1, Math.min(timeout.toSeconds(), 5));
        return "M-SEARCH * HTTP/1.1\r\n"
                + "HOST: 239.255.255.250:1900\r\n"
                + "MAN: \"ssdp:discover\"\r\n"
                + "MX: " + mx + "\r\n"
                + "ST: " + SEARCH_TARGET + "\r\n"
                + "\r\n";
    }

    /** Parses an SSDP response; returns empty if it is not a HEOS device. */
    static Optional<DiscoveredDevice> parseResponse(String response, InetAddress sender) {
        String[] lines = response.split("\r?\n");
        if (lines.length == 0 || !lines[0].toUpperCase(Locale.ROOT).startsWith("HTTP/1.1 200")) {
            return Optional.empty();
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).strip().toUpperCase(Locale.ROOT),
                        lines[i].substring(colon + 1).strip());
            }
        }
        String st = headers.getOrDefault("ST", "");
        String usn = headers.getOrDefault("USN", "");
        if (!st.equalsIgnoreCase(SEARCH_TARGET) && !usn.contains(SEARCH_TARGET)) {
            return Optional.empty();
        }
        return Optional.of(new DiscoveredDevice(sender, parseUri(headers.get("LOCATION")), usn));
    }

    private static URI parseUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Builder for {@link HeosDiscovery}. */
    public static final class Builder {

        private Duration timeout = Duration.ofSeconds(5);
        private InetAddress bindAddress;
        private InetSocketAddress target = SSDP_MULTICAST;
        private ThreadFactory threadFactory;

        private Builder() {
        }

        /** How long to wait for answers. Default 5 s. */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        /** Local address to search from, to select a network interface. Default: any. */
        public Builder bindAddress(InetAddress bindAddress) {
            this.bindAddress = bindAddress;
            return this;
        }

        /**
         * Factory for the search thread, which is renamed to {@code hellos-discovery}. Default
         * {@code null}: a virtual thread where available, a platform daemon thread otherwise
         * (always on Android).
         *
         * @since 0.1.1
         */
        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        /** Where to send the search request. Only meant for tests. */
        Builder target(InetSocketAddress target) {
            this.target = Objects.requireNonNull(target, "target");
            return this;
        }

        public HeosDiscovery build() {
            return new HeosDiscovery(this);
        }
    }
}
