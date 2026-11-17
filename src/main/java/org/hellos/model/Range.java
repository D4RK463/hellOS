// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

/**
 * A range of record indexes for queue, browse and search results. Both ends are inclusive and the
 * first record has index 0.
 */
public record Range(int start, int end) {

    public Range {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range " + start + "," + end);
        }
    }

    public static Range of(int start, int end) {
        return new Range(start, end);
    }

    /** {@code size} records beginning at {@code start}. */
    public static Range ofSize(int start, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        return new Range(start, start + size - 1);
    }

    public int size() {
        return end - start + 1;
    }

    /** The next range of the same size. */
    public Range next() {
        return ofSize(end + 1, size());
    }

    /** The protocol format {@code start,end}. */
    public String wireValue() {
        return start + "," + end;
    }
}
