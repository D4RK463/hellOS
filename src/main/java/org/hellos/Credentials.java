// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import java.util.Objects;

/**
 * HEOS account credentials. {@link #toString()} does not reveal the password.
 */
public record Credentials(String username, String password) {

    public Credentials {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    @Override
    public String toString() {
        return "Credentials[username=" + username + ", password=********]";
    }
}
