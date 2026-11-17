// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.hellos.cli.HeosProtocolException;
import org.junit.jupiter.api.Test;

class FrameReaderTest {

    private static FrameReader reader(String data, int max) {
        return new FrameReader(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), max);
    }

    @Test
    void splitsOnCrLfAndSkipsEmptyFrames() throws IOException {
        FrameReader reader = reader("{\"a\":1}\r\n\r\n{\"b\":2}\n{\"c\":\"ä€\"}\r\nincomplete", 1024);

        assertThat(reader.readFrame()).isEqualTo("{\"a\":1}");
        assertThat(reader.readFrame()).isEqualTo("{\"b\":2}");
        assertThat(reader.readFrame()).isEqualTo("{\"c\":\"ä€\"}");
        assertThat(reader.readFrame()).isNull();
    }

    @Test
    void rejectsOversizedFrames() {
        FrameReader reader = reader("x".repeat(20) + "\r\n", 10);

        assertThatThrownBy(reader::readFrame).isInstanceOf(HeosProtocolException.class);
    }
}
