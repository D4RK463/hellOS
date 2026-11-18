// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class HeosDiscoveryTest {

    private static final String HEOS_RESPONSE = """
            HTTP/1.1 200 OK\r
            CACHE-CONTROL: max-age=180\r
            EXT:\r
            LOCATION: http://%s:60006/upnp/desc/aios_device/aios_device.xml\r
            SERVER: LINUX UPnP/1.0 Denon-Heos/149200\r
            ST: urn:schemas-denon-com:device:ACT-Denon:1\r
            USN: uuid:00000000-0000-0000-0000-000000000001::urn:schemas-denon-com:device:ACT-Denon:1\r
            \r
            """;

    @Test
    void parsesHeosResponse() throws Exception {
        InetAddress sender = InetAddress.getByName("192.0.2.10");

        DiscoveredDevice device = HeosDiscovery.parseResponse(HEOS_RESPONSE.formatted("192.0.2.10"), sender).orElseThrow();

        assertThat(device.host()).isEqualTo("192.0.2.10");
        assertThat(device.locationUri()).hasValueSatisfying(uri -> assertThat(uri.getPort()).isEqualTo(60006));
        assertThat(device.usn()).contains(HeosDiscovery.SEARCH_TARGET);
    }

    @Test
    void ignoresOtherDevices() throws Exception {
        InetAddress sender = InetAddress.getByName("192.0.2.11");
        String other = "HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\nUSN: uuid:1::upnp:rootdevice\r\n\r\n";

        assertThat(HeosDiscovery.parseResponse(other, sender)).isEmpty();
        assertThat(HeosDiscovery.parseResponse("NOTIFY * HTTP/1.1\r\n\r\n", sender)).isEmpty();
    }

    @Test
    void searchRequestContainsSearchTarget() {
        String request = HeosDiscovery.builder().timeout(Duration.ofSeconds(2)).build().searchRequest();

        assertThat(request).startsWith("M-SEARCH * HTTP/1.1\r\n")
                .contains("ST: urn:schemas-denon-com:device:ACT-Denon:1\r\n", "MX: 2\r\n", "MAN: \"ssdp:discover\"")
                .endsWith("\r\n\r\n");
    }

    @Test
    void discoversDevicesAnsweringTheSearch() throws Exception {
        try (FakeResponder responder = new FakeResponder(HEOS_RESPONSE.formatted("127.0.0.1"))) {
            HeosDiscovery discovery = HeosDiscovery.builder()
                    .timeout(Duration.ofMillis(500))
                    .bindAddress(InetAddress.getLoopbackAddress())
                    .target(responder.address())
                    .build();
            List<DiscoveredDevice> found = new CopyOnWriteArrayList<>();

            List<DiscoveredDevice> devices = discovery.discoverAll(found::add).get(3, TimeUnit.SECONDS);

            assertThat(devices).hasSize(1).first().extracting(DiscoveredDevice::host).isEqualTo("127.0.0.1");
            assertThat(found).isEqualTo(devices);
            assertThat(responder.received()).contains("ST: " + HeosDiscovery.SEARCH_TARGET);
        }
    }

    @Test
    void discoverFirstFailsWhenNobodyAnswers() throws Exception {
        try (FakeResponder responder = new FakeResponder(null)) {
            HeosDiscovery discovery = HeosDiscovery.builder()
                    .timeout(Duration.ofMillis(200))
                    .bindAddress(InetAddress.getLoopbackAddress())
                    .target(responder.address())
                    .build();

            assertThatThrownBy(() -> discovery.discoverFirst().get(3, TimeUnit.SECONDS))
                    .cause().isInstanceOf(HeosDiscoveryException.class);
        }
    }

    @Test
    void searchesOnThreadOfConfiguredFactory() throws Exception {
        try (FakeResponder responder = new FakeResponder(HEOS_RESPONSE.formatted("127.0.0.1"))) {
            List<Thread> created = new CopyOnWriteArrayList<>();
            HeosDiscovery discovery = HeosDiscovery.builder()
                    .timeout(Duration.ofMillis(500))
                    .bindAddress(InetAddress.getLoopbackAddress())
                    .target(responder.address())
                    .threadFactory(task -> {
                        Thread thread = new Thread(task);
                        created.add(thread);
                        return thread;
                    })
                    .build();
            List<Thread> callbackThreads = new CopyOnWriteArrayList<>();

            DiscoveredDevice device = discovery.discoverFirst().get(3, TimeUnit.SECONDS);
            discovery.discoverAll(found -> callbackThreads.add(Thread.currentThread())).get(3, TimeUnit.SECONDS);

            assertThat(device.host()).isEqualTo("127.0.0.1");
            assertThat(created).hasSize(2).allSatisfy(thread -> assertThat(thread.getName()).isEqualTo("hellos-discovery"));
            assertThat(callbackThreads).hasSize(1).first().isSameAs(created.get(1));
        }
    }

    @Test
    void searchRequestLimitsMx() {
        assertThat(HeosDiscovery.builder().timeout(Duration.ofMillis(200)).build().searchRequest()).contains("MX: 1\r\n");
        assertThat(HeosDiscovery.builder().timeout(Duration.ofSeconds(30)).build().searchRequest()).contains("MX: 5\r\n");
    }

    /** Answers every UDP packet on localhost with a fixed response (or not at all). */
    private static final class FakeResponder implements AutoCloseable {
        private final DatagramSocket socket;
        private volatile String received = "";

        FakeResponder(String response) throws SocketException {
            socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            Thread.ofVirtual().start(() -> {
                byte[] buffer = new byte[4096];
                while (!socket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.US_ASCII);
                        if (response != null) {
                            byte[] data = response.getBytes(StandardCharsets.US_ASCII);
                            socket.send(new DatagramPacket(data, data.length, packet.getSocketAddress()));
                        }
                    } catch (Exception e) {
                        return;
                    }
                }
            });
        }

        InetSocketAddress address() {
            return (InetSocketAddress) socket.getLocalSocketAddress();
        }

        String received() {
            return received;
        }

        @Override
        public void close() {
            socket.close();
        }
    }
}
