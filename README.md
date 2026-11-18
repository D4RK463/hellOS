# hellOS

A Java library for the HEOS CLI protocol of Denon and Marantz devices.

> Status: early development. Discovery, connecting and read-only queries (players, groups,
> volume, play state, now playing), volume changes (`setVolume`, `volumeUp`, `volumeDown`) and
> volume change events have been tested against a HEOS AVR; other commands that change state are
> not yet tested against real devices. Android 14 (API 34) and newer is supported since 0.2.0: the
> build checks the library against the Android API, but it has not yet been run in an app on a real
> Android device.

## Requirements

- Java 21 or newer (built and tested with Java 25), or Android 14 (API 34) or newer, see
  [Android](#android)
- A HEOS device reachable on TCP port 1255 in the local network

A HEOS account is **not** required for local control (players, groups, volume, playback, queue,
inputs, URLs, local media servers). Favorites, HEOS playlists and streaming services need a
signed-in account.

## Usage

### Blocking

```java
try (BlockingHeosSystem heos = BlockingHeosSystem.connect("192.168.1.20")) {
    for (PlayerInfo info : heos.players()) {
        System.out.println(info.name() + " (" + info.model() + ")");
    }
    BlockingPlayer kitchen = heos.playerByName("Kitchen").orElseThrow();
    kitchen.setVolume(25);
    kitchen.playInput(Input.AUX_IN_1);
}
```

### Asynchronous

```java
HeosSystem heos = HeosSystem.builder()
        .host("192.168.1.20")
        .host("192.168.1.21")                 // failover
        .credentials("user@example.com", "…") // optional, signs in on connect
        .build();

heos.addEventListener(HeosEvent.PlayerVolumeChanged.class,
        e -> System.out.println(e.playerId() + " volume " + e.level()));

heos.connect()
        .thenCompose(h -> h.browse().browse(MusicSources.FAVORITES))
        .thenCompose(favorites -> heos.player(-409995282L).play(favorites, favorites.items().get(0)))
        .join();
```

Devices can also be found automatically: `HeosSystem.discoverAndConnect(Duration.ofSeconds(3))`.

> **Firewall note:** discovery sends an SSDP search to `239.255.255.250:1900`. HEOS devices answer
> with a unicast UDP packet from a **random source port**, not from port 1900. A host firewall that
> only allows replies from port 1900 drops these answers, and discovery finds nothing while a direct
> connection to the device's IP still works. Allow incoming UDP from the HEOS devices, for example
> with ufw:
>
> ```bash
> sudo ufw allow proto udp from 192.168.1.20               # one device
> sudo ufw allow proto udp from 192.168.1.0/24             # whole local network (broader)
> ```
>
> If no firewall rule is possible, connect to the device's IP directly.

### Events

```java
heos.addEventListener(event -> {
    switch (event) {
        case HeosEvent.PlayerStateChanged(long pid, PlayState state) -> System.out.println(pid + " " + state);
        case HeosEvent.PlayerNowPlayingChanged(long pid) -> heos.player(pid).nowPlaying().thenAccept(System.out::println);
        default -> { }
    }
});
```

Devices may send the same change event more than once (a HEOS AVR sends every
`player_volume_changed` twice). The library passes events on as received, so listeners should be
idempotent: treat an event as "this is the current state" or "something changed, query again",
not as "exactly one change happened".

## Android

The library runs on Android 14 (API 34) and newer. It only uses JDK APIs that exist on Android
API 34; the build checks this (see [Build](#build)).

**Dependencies.** If the app uses the plain jar from the GitHub release instead of a Maven
dependency, Jackson has to be added as well, since the jar does not contain it:

```kotlin
dependencies {
    implementation(files("libs/hellOS-0.2.0.jar"))
    implementation("tools.jackson.core:jackson-databind:3.3.0") // brings jackson-core and jackson-annotations
}
```

**Permissions** in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

**Discovery** needs a `WifiManager.MulticastLock`; without it many devices drop the SSDP answers.
Hold the lock only while searching:

```java
WifiManager wifi = context.getSystemService(WifiManager.class);
WifiManager.MulticastLock lock = wifi.createMulticastLock("hellos-discovery");
lock.setReferenceCounted(false);
lock.acquire();
HeosDiscovery.discover(Duration.ofSeconds(3))
        .whenComplete((devices, failure) -> lock.release());
```

Connecting to a known IP address directly needs neither the lock nor the two Wi-Fi permissions.

**Threads.** On Android the library uses platform daemon threads (`hellos-reader-…`,
`hellos-callbacks`, …); on the JVM it uses virtual threads. A custom `ThreadFactory` can be set
with `HeosClient.Builder.threadFactory(…)` (for `HeosSystem` via
`configureClient(c -> c.threadFactory(…))`) and `HeosDiscovery.Builder.threadFactory(…)`.
Listeners are called on the library's callback thread, not on the main thread: post UI updates to
the main thread. Do not use the blocking API on the main thread.

**Logging** goes through `java.util.logging` and ends up in Logcat. Debug output (level `FINE`) is
not shown by default.

**JVM-only methods.** `HeosCommand.attributes()` and `MessageParameters.asMap()` return a
`SequencedMap`, which Android API 34 does not have. On Android use `HeosCommand.attributeMap()` and
`MessageParameters.parameterMap()` instead.

## Architecture

| Package | Content |
|---|---|
| `org.hellos` | High-level API: `HeosSystem`, `Player`, `Group`, `Browse`, `Account` (asynchronous) |
| `org.hellos.blocking` | Blocking variants of the high-level API |
| `org.hellos.model` | Records and enums: `PlayerInfo`, `NowPlayingMedia`, `BrowseResult`, `Input`, `MusicSources`, … |
| `org.hellos.event` | Typed change events (`HeosEvent`, sealed) |
| `org.hellos.cli` | Low level: commands, escaping, response parsing, error codes, command and event names |
| `org.hellos.connection` | `HeosConnection` (one socket) and `HeosClient` (heartbeat, reconnect, failover, events) |
| `org.hellos.discovery` | SSDP discovery |

`HeosClient` keeps a command connection and a separate change event connection to one device
(one device controls the whole HEOS system). It sends heartbeats on idle connections, reconnects
automatically and fails over to the other configured hosts. Commands are sent one after another
because the protocol has no request ids.

The high-level API does not cache state: every call sends a command. Use the events to keep a
local state up to date.

## Protocol reference

The implementation follows the *HEOS CLI Protocol Specification* version 1.17 published by Denon,
plus `browse/multi_search` from version 1.21. Known differences between the specification and real
devices (numeric and negative ids, whitespace in command names, decimal numbers, escaped payload
strings) are handled leniently.

## Build

```sh
./gradlew build
```

The build includes an Android API check ([Animal Sniffer](https://github.com/xvik/gradle-animalsniffer-plugin)
with the API 34 signatures of [gummy-bears](https://github.com/open-toast/gummy-bears)): it fails
if the library uses a JDK API that is missing on Android 14, e.g. `System.Logger`,
`Thread.ofVirtual()` or `List.getFirst()`. Methods that are deliberately JVM-only are annotated with
`org.hellos.internal.JvmOnly`.

Releases are created by the release workflow when a pull request is merged into `main`; the
version bump comes from the pull request label (`major`, `minor`, none for a patch, `no-release`).

## License

GNU General Public License v3.0 or later, see [LICENSE](LICENSE).
