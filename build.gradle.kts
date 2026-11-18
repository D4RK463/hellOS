plugins {
    `java-library`
    id("ru.vyarus.animalsniffer") version "2.0.1"
}

group = "org.hellos"
version = providers.gradleProperty("releaseVersion").getOrElse("0.2.0-SNAPSHOT")
description = "Java library for the Denon/Marantz HEOS CLI protocol"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api("tools.jackson.core:jackson-databind:3.3.0")

    // Android API 34 signatures: the library must not use JDK APIs that are missing on Android.
    signature("com.toasttab.android:gummy-bears-api-34:0.16.0@signature")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

animalsniffer {
    // Only the library itself has to run on Android, not the tests.
    sourceSets = listOf(project.sourceSets.main.get())
    toolVersion = "1.29.1"
    // JVM-only methods kept for API compatibility (e.g. ones returning SequencedMap).
    annotation = "org.hellos.internal.JvmOnly"
}

configurations.named("animalsniffer") {
    // animal-sniffer 1.x brings an old ASM. If ASM cannot read a class file, the check logs the
    // exception but the build stays green, so keep ASM current for the toolchain's class files.
    resolutionStrategy.force("org.ow2.asm:asm:9.11.1")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-requires-transitive-automatic"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
