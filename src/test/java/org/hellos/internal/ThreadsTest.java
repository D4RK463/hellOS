// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThreadsTest {

    @AfterEach
    void resetFallback() {
        Threads.virtualThreadsUnavailable = false;
    }

    @Test
    void usesVirtualThreadsOnTheJvm() {
        Thread thread = Threads.newThread(null, "hellos-test", () -> { });

        assertThat(Threads.isAndroid()).isFalse();
        assertThat(Threads.virtualThreadsAvailable()).isTrue();
        assertThat(thread.isVirtual()).isTrue();
        assertThat(thread.getName()).isEqualTo("hellos-test");
        assertThat(thread.getState()).isEqualTo(Thread.State.NEW);
    }

    @Test
    void fallsBackToNamedPlatformDaemonThreads() throws Exception {
        Threads.virtualThreadsUnavailable = true;
        CountDownLatch ran = new CountDownLatch(1);

        Thread thread = Threads.start(null, "hellos-reader-host:1255", ran::countDown);

        assertThat(Threads.virtualThreadsAvailable()).isFalse();
        assertThat(thread.isVirtual()).isFalse();
        assertThat(thread.isDaemon()).isTrue();
        assertThat(thread.getName()).isEqualTo("hellos-reader-host:1255");
        assertThat(ran.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void usesConfiguredFactoryAndKeepsThreadNames() throws Exception {
        List<Runnable> tasks = new CopyOnWriteArrayList<>();
        ThreadFactory custom = task -> {
            tasks.add(task);
            Thread thread = new Thread(task, "app-thread");
            thread.setDaemon(true);
            return thread;
        };
        CountDownLatch ran = new CountDownLatch(1);

        Thread thread = Threads.factory(custom, "hellos-callbacks").newThread(ran::countDown);
        thread.start();

        assertThat(tasks).hasSize(1);
        assertThat(thread.isVirtual()).isFalse();
        assertThat(thread.getName()).isEqualTo("hellos-callbacks");
        assertThat(ran.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void configuredFactoryWinsOverVirtualThreads() {
        Thread thread = Threads.newThread(task -> new Thread(task), "hellos-heartbeat", () -> { });

        assertThat(thread.isVirtual()).isFalse();
    }

    @Test
    void factoryRefusingAThreadIsReported() {
        assertThatThrownBy(() -> Threads.newThread(task -> null, "hellos-connect", () -> { }))
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessageContaining("hellos-connect");
    }
}
