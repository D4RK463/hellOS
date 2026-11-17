// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Masks sensitive attribute values (the HEOS account password) before anything is logged or put
 * into an exception message.
 */
final class Redaction {

    static final String MASK = "********";
    static final Set<String> SENSITIVE_KEYS = Set.of("pw");

    private static final Pattern RAW_PASSWORD = Pattern.compile("(^|[?&\"\\s])pw=[^&\"\\r\\n]*");
    private static final int MAX_RAW_LENGTH = 300;

    private Redaction() {
    }

    static boolean isSensitive(String key) {
        return SENSITIVE_KEYS.contains(key);
    }

    /** Masks passwords in an arbitrary raw protocol string. */
    static String maskRaw(String raw) {
        return RAW_PASSWORD.matcher(raw).replaceAll("$1pw=" + MASK);
    }

    /** Masks and shortens a raw protocol string so that it can be put into a log line. */
    static String forLog(String raw) {
        String masked = maskRaw(raw);
        return masked.length() <= MAX_RAW_LENGTH ? masked : masked.substring(0, MAX_RAW_LENGTH) + "…";
    }
}
