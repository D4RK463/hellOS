// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.cli;

import java.util.Locale;

/**
 * The HEOS-specific escaping of {@code &}, {@code =} and {@code %} (spec section 3.1).
 *
 * <p>HEOS does not use full URL encoding. Only these three characters are replaced by {@code %26},
 * {@code %3D} and {@code %25}. This applies to command attributes as well as to string fields in
 * responses (for example names in browse results).
 */
public final class HeosText {

    private HeosText() {
    }

    /** Escapes {@code &}, {@code =} and {@code %}. */
    public static String encode(String value) {
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            String replacement = switch (c) {
                case '&' -> "%26";
                case '=' -> "%3D";
                case '%' -> "%25";
                default -> null;
            };
            if (replacement != null && sb == null) {
                sb = new StringBuilder(value.length() + 8).append(value, 0, i);
            }
            if (sb != null) {
                if (replacement != null) {
                    sb.append(replacement);
                } else {
                    sb.append(c);
                }
            }
        }
        return sb == null ? value : sb.toString();
    }

    /** Reverses {@link #encode(String)}. Other percent sequences are left untouched. */
    public static String decode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                String code = value.substring(i + 1, i + 3).toUpperCase(Locale.ROOT);
                String replacement = switch (code) {
                    case "26" -> "&";
                    case "3D" -> "=";
                    case "25" -> "%";
                    default -> null;
                };
                if (replacement != null) {
                    sb.append(replacement);
                    i += 3;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
