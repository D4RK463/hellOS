// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

/**
 * Creates the threads of the library.
 *
 * <p>Uses virtual threads where the runtime offers them and named platform daemon threads
 * otherwise. Virtual threads are looked up by reflection, so that no class of the library refers to
 * {@code Thread.ofVirtual()} or {@code Thread.Builder} directly: both do not exist on Android, and
 * a direct reference would fail there at run time. On Android platform threads are always used,
 * even if the runtime has a hidden {@code ofVirtual} method.
 *
 * <p>A {@link ThreadFactory} configured by the application takes precedence. Threads created by it
 * are renamed to the library's thread names ({@code hellos-reader-…}, {@code hellos-callbacks}, …).
 */
public final class Threads {

    /** Only for tests: forces the fallback to platform threads. */
    static volatile boolean virtualThreadsUnavailable;

    private static final VirtualThreads VIRTUAL = VirtualThreads.lookup();

    private Threads() {
    }

    /**
     * Creates an unstarted thread.
     *
     * @param custom the factory configured by the application, or {@code null} for the default
     */
    public static Thread newThread(ThreadFactory custom, String name, Runnable task) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(task, "task");
        if (custom != null) {
            Thread thread = custom.newThread(task);
            if (thread == null) {
                throw new RejectedExecutionException("Thread factory refused to create thread " + name);
            }
            thread.setName(name);
            return thread;
        }
        if (!virtualThreadsUnavailable && VIRTUAL != null) {
            Thread thread = VIRTUAL.newThread(name, task);
            if (thread != null) {
                return thread;
            }
        }
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    /** Creates and starts a thread. */
    public static Thread start(ThreadFactory custom, String name, Runnable task) {
        Thread thread = newThread(custom, name, task);
        thread.start();
        return thread;
    }

    /** A factory whose threads are all named {@code name}. */
    public static ThreadFactory factory(ThreadFactory custom, String name) {
        Objects.requireNonNull(name, "name");
        return task -> newThread(custom, name, task);
    }

    /** Whether the default threads are virtual threads. */
    static boolean virtualThreadsAvailable() {
        return !virtualThreadsUnavailable && VIRTUAL != null;
    }

    static boolean isAndroid() {
        String vmName = System.getProperty("java.vm.name", "");
        if (vmName.contains("Dalvik")) {
            return true;
        }
        try {
            Class.forName("android.os.Build", false, Threads.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Reflective access to {@code Thread.ofVirtual().name(name).unstarted(task)}. */
    private record VirtualThreads(Method ofVirtual, Method name, Method unstarted) {

        static VirtualThreads lookup() {
            if (isAndroid()) {
                return null;
            }
            try {
                Method ofVirtual = Thread.class.getMethod("ofVirtual");
                Class<?> builderType = ofVirtual.getReturnType();
                Method name = builderType.getMethod("name", String.class);
                Method unstarted = builderType.getMethod("unstarted", Runnable.class);
                VirtualThreads virtualThreads = new VirtualThreads(ofVirtual, name, unstarted);
                return virtualThreads.newThread("hellos-probe", () -> { }) != null ? virtualThreads : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
        }

        /** Returns {@code null} if the thread cannot be created. */
        Thread newThread(String threadName, Runnable task) {
            try {
                Object builder = name.invoke(ofVirtual.invoke(null), threadName);
                return (Thread) unstarted.invoke(builder, task);
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
                return null;
            }
        }
    }
}
