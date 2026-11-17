// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.hellos.cli.HeosMessage;
import org.hellos.cli.HeosResponseParser;

/** Loads the JSON responses in {@code src/test/resources/fixtures}. */
public final class Fixtures {

    private Fixtures() {
    }

    /** The fixture as a single JSON line, as a device would send it. */
    public static String line(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return HeosResponseParser.mapper().readTree(in).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static HeosMessage message(String name) {
        return HeosResponseParser.parse(line(name));
    }
}
