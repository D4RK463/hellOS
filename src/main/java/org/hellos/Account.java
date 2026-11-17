// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos;

import static org.hellos.Requests.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.hellos.cli.Commands;
import org.hellos.internal.ModelParser;
import org.hellos.model.AccountStatus;

/**
 * The HEOS account of the system. A signed-in account is only needed for favorites, HEOS
 * playlists and streaming services; local control works without it. The sign-in is stored on the
 * device and survives reconnects.
 */
public final class Account {

    private final Requests requests;

    Account(Requests requests) {
        this.requests = requests;
    }

    public CompletableFuture<AccountStatus> status() {
        return requests.send(command(Commands.SYSTEM_CHECK_ACCOUNT),
                response -> ModelParser.accountStatus(response.message()));
    }

    /**
     * Signs in. Fails with {@link org.hellos.cli.HeosCommandException} (e.g.
     * {@link org.hellos.cli.HeosErrorCode#USER_NOT_FOUND} or
     * {@link org.hellos.cli.HeosErrorCode#INVALID_CREDENTIALS}) if the credentials are wrong.
     */
    public CompletableFuture<AccountStatus> signIn(Credentials credentials) {
        Objects.requireNonNull(credentials, "credentials");
        return requests.send(command(Commands.SYSTEM_SIGN_IN,
                        "un", credentials.username(), "pw", credentials.password()),
                response -> ModelParser.accountStatus(response.message()));
    }

    public CompletableFuture<AccountStatus> signIn(String username, String password) {
        return signIn(new Credentials(username, password));
    }

    public CompletableFuture<Void> signOut() {
        return requests.run(command(Commands.SYSTEM_SIGN_OUT));
    }

    /** Signs in unless the given account is already signed in. */
    public CompletableFuture<AccountStatus> ensureSignedIn(Credentials credentials) {
        Objects.requireNonNull(credentials, "credentials");
        return status().thenCompose(status ->
                status.signedIn() && status.username().filter(credentials.username()::equalsIgnoreCase).isPresent()
                        ? CompletableFuture.completedFuture(status)
                        : signIn(credentials));
    }
}
