// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that uses JDK APIs missing on Android API 34, kept for compatibility on the JVM.
 * The Android API check of the build skips methods with this annotation; library code must not
 * call them.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface JvmOnly {
}
