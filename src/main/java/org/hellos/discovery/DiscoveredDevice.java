// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.discovery;

import java.net.InetAddress;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * A HEOS device found by {@link HeosDiscovery}.
 *
 * @param address  the IP address of the device; connect to it with
 *                 {@link org.hellos.connection.HeosClient}
 * @param location the UPnP description URL from the {@code LOCATION} header, or {@code null}
 * @param usn      the unique service name from the {@code USN} header, or an empty string
 */
public record DiscoveredDevice(InetAddress address, URI location, String usn) {

    public DiscoveredDevice {
        Objects.requireNonNull(address, "address");
        usn = usn == null ? "" : usn;
    }

    /** The IP address as string, ready to be passed to {@code HeosClient.Builder#host}. */
    public String host() {
        return address.getHostAddress();
    }

    public Optional<URI> locationUri() {
        return Optional.ofNullable(location);
    }
}
