# Changelog

## 0.2.0

Android support: the library runs unchanged on Android 14 (API 34) and newer. The public API is
unchanged; on the JVM the library behaves as before.

### Added

- Android 14 (API 34) support, see the Android section in the README.
- `HeosClient.Builder.threadFactory(ThreadFactory)` and `HeosDiscovery.Builder.threadFactory(ThreadFactory)`
  to create the library's threads with an own factory. For `HeosSystem` use
  `configureClient(c -> c.threadFactory(…))`.
- `HeosCommand.attributeMap()` and `MessageParameters.parameterMap()`: the same content as
  `attributes()` and `asMap()`, as `Map`, also available on Android.
- Build check against the Android API 34 signatures (Animal Sniffer with gummy-bears), part of
  `./gradlew build`.

### Changed

- Logging uses `java.util.logging` instead of `System.Logger`. Logger names, messages and levels
  are unchanged (`DEBUG` is now `FINE`); on the JVM `System.Logger` was backed by
  `java.util.logging` anyway. Passwords are still masked.
- Threads are created centrally: virtual threads where available, named platform daemon threads
  otherwise and always on Android.
- The jar is compiled for Java 21 (class file version 65) instead of 25, so older Android build
  tools can dex it as well. Java 21 or newer is enough on the JVM.
- `HeosCommand.attributes()` and `MessageParameters.asMap()` are JVM-only, because Android API 34
  has no `SequencedMap`.

### Fixed

- No more references to APIs that are missing on Android: `System.Logger`, `Thread.ofVirtual()`,
  `List.getFirst()`/`List.addFirst()`, `SequencedMap`, `Thread.sleep(Duration)` and `Math.clamp`.

## 0.1.0

First release.
