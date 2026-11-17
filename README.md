# hellOS

A Java library for the HEOS CLI protocol of Denon and Marantz devices.

> Status: early development. Discovery, connecting and read-only queries (players, groups,
> volume, play state, now playing), volume changes (`setVolume`, `volumeUp`, `volumeDown`) and
> volume change events have been tested against a HEOS AVR; other commands that change state are
> not yet tested against real devices.

## Requirements

- Java 25
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
        .thenCompose(favorites -> heos.player(-409995282L).play(favorites, favorites.items().getFirst()))
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

## License

GNU General Public License v3.0 or later, see [LICENSE](LICENSE).
