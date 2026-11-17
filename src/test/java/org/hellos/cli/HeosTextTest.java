// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HeosTextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "plain|plain",
            "a&b|a%26b",
            "a=b|a%3Db",
            "100%|100%25",
            "%26|%2526",
            "Ferrari Simmons & RaySean|Ferrari Simmons %26 RaySean"
    })
    void encodeAndDecode(String decoded, String encoded) {
        assertThat(HeosText.encode(decoded)).isEqualTo(encoded);
        assertThat(HeosText.decode(encoded)).isEqualTo(decoded);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "%20|%20",
            "50%|50%",
            "%2|%2",
            "%3d|="
    })
    void decodeIsLenient(String input, String expected) {
        assertThat(HeosText.decode(input)).isEqualTo(expected);
    }
}
