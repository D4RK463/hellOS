// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.blocking;

import static org.hellos.blocking.Await.result;
import static org.hellos.blocking.Await.run;

import java.util.Objects;

import org.hellos.Account;
import org.hellos.Credentials;
import org.hellos.model.AccountStatus;

/** Blocking variant of {@link Account}. */
public final class BlockingAccount {

    private final Account account;

    private BlockingAccount(Account account) {
        this.account = account;
    }

    public static BlockingAccount of(Account account) {
        return new BlockingAccount(Objects.requireNonNull(account, "account"));
    }

    public Account async() {
        return account;
    }

    public AccountStatus status() {
        return result(account.status());
    }

    public AccountStatus signIn(Credentials credentials) {
        return result(account.signIn(credentials));
    }

    public AccountStatus signIn(String username, String password) {
        return result(account.signIn(username, password));
    }

    public void signOut() {
        run(account.signOut());
    }

    public AccountStatus ensureSignedIn(Credentials credentials) {
        return result(account.ensureSignedIn(credentials));
    }
}
