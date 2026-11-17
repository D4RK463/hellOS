// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Objects;
import java.util.Optional;

/** Whether a HEOS account is signed in on the system, and which one. */
public record AccountStatus(boolean signedIn, Optional<String> username) {

    public AccountStatus {
        Objects.requireNonNull(username, "username");
    }

    public static AccountStatus signedOut() {
        return new AccountStatus(false, Optional.empty());
    }

    public static AccountStatus signedIn(String username) {
        return new AccountStatus(true, Optional.of(username));
    }
}
