// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.connection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.hellos.cli.HeosProtocolException;

/**
 * Splits the byte stream of a HEOS connection into frames. Frames are terminated by
 * {@code \r\n}; a bare {@code \n} is accepted as well. Empty frames are skipped.
 */
final class FrameReader {

    private final InputStream in;
    private final int maxFrameBytes;
    private final ByteArrayOutputStream frame = new ByteArrayOutputStream(1024);

    FrameReader(InputStream in, int maxFrameBytes) {
        this.in = in;
        this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * Blocks until the next frame is complete.
     *
     * @return the decoded frame, or {@code null} at the end of the stream
     * @throws HeosProtocolException if a frame exceeds the maximum size
     */
    String readFrame() throws IOException {
        while (true) {
            int b = in.read();
            if (b < 0) {
                return null;
            }
            if (b == '\n') {
                byte[] bytes = frame.toByteArray();
                frame.reset();
                int length = bytes.length;
                if (length > 0 && bytes[length - 1] == '\r') {
                    length--;
                }
                if (length == 0) {
                    continue;
                }
                return new String(bytes, 0, length, StandardCharsets.UTF_8);
            }
            frame.write(b);
            if (frame.size() > maxFrameBytes) {
                frame.reset();
                throw new HeosProtocolException("Frame exceeds " + maxFrameBytes + " bytes");
            }
        }
    }
}
