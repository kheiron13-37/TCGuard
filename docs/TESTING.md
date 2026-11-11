# Testing

TCGuard is tested against real servers. Nothing is mocked: the suites start a server, connect
protocol-level clients (mineflayer / minecraft-protocol) and send the actual hostile packets. Every
group runs on a freshly reset server.

There are two suites:

- **Full suite** (`suite.js`, `gate.js`): every check of the plugin and the gate, on Spigot 1.8.8.
- **Core suite** (`core.js`): the checks that exist on every version. The same file runs against
  1.8.8, 1.12.2, 1.16.5, 1.20.4 and 1.21.8.

## Results for plugin 6.0.0 and gate 1.2.0

Run on 11 November 2026 on Linux. Servers: Spigot/TacoSpigot 1.8.8 (Java 25), Paper 1.12.2 and
1.16.5 (Java 8), Paper 1.20.4 (Java 17), Paper 1.21.8 (Java 25).

After this run the comments were removed from the source. Compiled without debug information, the
classes before and after are byte-identical (Go: identical syntax trees), and the rebuilt jar and
gate passed the core suite again on 1.8.8 and on 1.21.8 through the gate.

| run | result |
| --- | --- |
| 1.8.8, full suite, clients connect directly | 35/35 |
| 1.8.8, full suite behind the gate, plus the gate checks | 44/44 |
| 1.8.8, full suite behind the gate with the origin lock, plus gate and lock checks | 46/46 |
| plugin unit tests | 74/74 |
| frame inspector fuzzer, 200,000 frames | 0 valid frames rejected, no exception, slowest mutated frame 5.3 ms |
| gate unit tests (`go test`) | 12/12 |

### Core suite, every version

| server | direct | through the gate, origin lock on |
| --- | --- | --- |
| 1.8.8 | 10/10 | 13/13 |
| 1.12.2 | 10/10 | 13/13 |
| 1.16.5 | 10/10 | 13/13 |
| 1.20.4 | 10/10 | 13/13 |
| 1.21.8 | 10/10 | 13/13 |

"Direct" is M01 - M09 plus the log check (10). Through the gate the three origin lock checks are
added (13).

Detail of one run (1.21.8 through the gate); the other versions have the same rows:

| id | check | result | detail |
| --- | --- | --- | --- |
| M01 | join and chat | pass | chat=true packetVls=0.0 guarded=1 |
| M02 | normal play for 15 seconds raises nothing | pass | disconnected=false alerts=0 |
| M03 | packet flood -> dropped, then kicked | pass | disconnected=true alerts=2 |
| M04 | frame above the size limit is refused | pass | disconnected=true plugin=false gate=true bytes=1200000 |
| M05 | compressed frame announcing a huge size is refused before inflating | pass | disconnected=true logged=true wire=1963B |
| M06 | exploit command is blocked, three in a row -> kick | pass | disconnected=true reason=Disconnected: invalid or excessive packets. socketClosed |
| M07 | op outside op-guard.allowed is removed, a listed one stays | pass | removed=true kicked=true opsFile="name": "Sahip" |
| M08 | a log4j lookup is refused in chat and never reaches the log | pass | kicked=true refused=true lookupLogged=false plainLineLogged=true |
| M09 | 20 players online, nobody is kicked | pass | online=20/20 tps=20.0 |
| L01 | origin lock: player joins through the gate with the real address | pass | joined=true realAddress=true |
| L02 | origin lock: direct, unsigned and forged connections are refused | pass | answered: direct=false unsigned=false forged=false |
| L03 | origin lock: a valid signature works once; replay and stale are refused | pass | answered: valid=true replay=false stale=false |
| H01 | no TCGuard exception in the server log | pass | errors=0 |

M04 behind the gate: a frame that large is already cut by the gate's upload budget, so either the
gate or the plugin may be the one that refuses it.

### 1.8.8 with ViaVersion

The 1.8 guard parses frames in the 1.8 format, so it has to sit behind ViaVersion's translation.
The core suite was run against 1.8.8 with ViaVersion 5.12.2, with newer clients:

| client version | result |
| --- | --- |
| 1.12.2 | 10/10 |
| 1.16.5 | 10/10 |
| 1.20.4 | 10/10 |
| 1.21.8 | 10/10 |

### Full suite, 1.8.8, direct

| id | check | result | detail |
| --- | --- | --- | --- |
| C01 | join and chat | pass | delivered |
| C02 | a colon in command arguments is not a namespace | pass | command ran |
| C04 | three exploit commands in a row -> kick | pass | "§cDisconnected: invalid or excessive packets." socketClosed |
| C05 | Log4Shell in chat -> kick, never logged | pass | disconnected=true logged=false |
| P01 | vehicle input of NaN -> kick | pass | disconnected=true logged=true |
| P02 | sign line with a translate component -> kick | pass | disconnected=true logged=true |
| P03 | written book page with a translate component -> kick | pass | disconnected=true logged=true |
| P04 | window click on slot 30000 -> kick | pass | disconnected=true logged=true |
| P05 | packet flood -> kick with at most 3 alerts | pass | disconnected=true alerts=2 |
| P06 | 250 swings at once -> notification, no kick | pass | notified=true disconnected=false |
| C03 | exploit commands are blocked, also behind a namespace | pass | refused=3/3 vls=2.6 |
| F01 | mining (a swing every tick) raises nothing | pass | disconnected=false alerts=0 |
| F02 | 60 packets a second for 3 seconds is fine | pass | disconnected=false |
| F03 | chat still works on the death screen | pass | delivered |
| F04 | clicking a full 50-page book is fine | pass | disconnected=false alerts=0 |
| N01 | 9 KB chat frame is dropped before decoding | pass | disconnected=true logged=true |
| N02 | 40-level NBT is dropped before decoding | pass | disconnected=true logged=true |
| N03 | NBT list of 20000 entries is dropped before decoding | pass | disconnected=true logged=true |
| N04 | book page of 3000 bytes is dropped before decoding | pass | disconnected=true logged=true |
| N05 | sign line of 2000 bytes is dropped before decoding | pass | disconnected=true logged=true |
| N06 | tab-complete of 5000 bytes is dropped before decoding | pass | disconnected=true logged=true |
| N07 | compressed frame announcing 2 MB is dropped before inflating | pass | disconnected=true logged=true wire=1963B |
| I01 | creative item is rebuilt: 32k enchant and attributes gone | pass | DIAMOND_SWORD {ench:[0:{lvl:5s,id:16s}],display:{Name:"Kilic"}} |
| I02 | blacklisted creative item (command block) is refused | pass | EMPTY |
| I03 | skull texture does not survive the rebuild | pass | SKULL_ITEM {SkullOwner:{Name:"Notch"}} |
| I04 | chest with packed contents arrives empty in creative | pass | CHEST null |
| I05 | survival: Sharpness 50 is reset on click, no exception | pass | exception=false item=ItemStack{STICK x 1, UNSPECIFIC_META:{meta-type=UNSPECIFIC, enchants={DAMAGE_ALL=5}}} |
| W01 | a dispenser pouring water onto an end portal is cancelled; an arrow is not | pass | water=true arrow=false |
| W02 | a bucket cannot wash away an end portal | pass | portalStillThere=true (ENDER_PORTAL) |
| W03 | mushroom/tree growth over an end portal is cancelled, ordinary growth is not | pass | mushroomOverPortal=true clearGrowth=false treeOverPortal=true |
| O01 | op outside op-guard.allowed is removed, a listed one stays | pass | Sahip=true Hacker=false kicked=true |
| A01 | alerts reach tcguard.alerts holders only | pass | staff=true other=false |
| S01 | status and reload commands | pass | TCGuard 6.0.0: 0 guarded connections, 7 blocked |
| X01 | 20 players online: TPS stays above 19.5, nobody is kicked | pass | online=20/20 tps=19.97 |
| H01 | no TCGuard exception in any server log | pass | errors=0 |

`H01` greps every server log for exceptions thrown from TCGuard code and for load failures.

### Gate checks

The plugin checks above also pass with the gate in front; only the gate rows are repeated here.

| id | check | result | detail |
| --- | --- | --- | --- |
| G01 | player joins through the gate; server sees the real address | pass | realAddress=true chat=true |
| G02 | 600 status pings: all answered, backend asked only a few times | pass | answered=600/600 in 0.4s, backend status requests=1 |
| G03 | 250 silent sockets are dropped after the handshake timeout; players still get in | pass | joinedDuringAttack=true dropped=250/250 |
| G04 | one address opening 30 connections at once: the extra ones are refused | pass | refused=25/30 (5 may stay open) |
| G05 | malformed handshakes never reach the server | pass | stillOpen=[] reachedServer=false |
| G06 | invalid player name is refused at the gate | pass | closed=true reachedServer=false |
| G07 | a 2 MB frame streamed after login is cut by the gate's upload budget | pass | disconnected=true gateCounted=true |
| G08 | logins above logins_per_second get "busy", the server keeps up | pass | busy=19 joined=20 serverFull=1 tps=19.97 |
| G09 | a trusted address without a PROXY header is refused by the server | pass | spoofClosed=true untrustedDirectJoin=true |

### Origin lock checks

Run with a shared secret: the gate signs, the server accepts nothing else. All plugin and gate
checks pass in this mode too (G09 does not apply, direct connections are refused by design).

| id | check | result | detail |
| --- | --- | --- | --- |
| L01 | origin lock: a direct connection without the gate is refused, from any address | pass | answered: untrusted=false trusted=false |
| L02 | origin lock: unsigned or wrongly signed headers are refused | pass | answered: unsigned=false forged=false |
| L03 | origin lock: a valid signature works once; a replay and a stale one are refused | pass | answered: valid=true replay=false stale=false signedAddressSeen=true |

## What is not covered

- Server versions between the five above. They share the code path of their neighbours, but were
  not started.
- Forks other than Spigot/TacoSpigot (1.8.8) and Paper (the rest). Folia is not supported.
- BungeeCord, Waterfall, FlameCord, Aegis and Velocity behind the gate: their PROXY protocol
  support is documented and was read in their source, but not exercised by these tests. The same
  goes for a game server behind such a proxy with IP forwarding.
- The gate on Windows. The Windows binary is cross-compiled; the tests ran on Linux.
- Real game clients and modded clients. All clients were protocol bots; the packet weights were
  tuned with them.
- Real attack traffic from the internet. All attacks came from the same machine.
- `mvn package` and the CI workflow were not run; the jar was built with `plugin/tools/build.sh`.

## Running it

Offline: `cd plugin && tools/build.sh && tools/test.sh` (needs `plugin/libs/spigot-1.8.8.jar`), and
`cd gate && go test ./...`.

Full suite, Linux or WSL:

1. Server folder (default `~/tcg-server`): `spigot-1.8.8.jar`, `eula.txt`, `tests/live/server/start.sh`
   and the probe plugin built from `tests/live/probe`.
   `server.properties`: `online-mode=false`, `level-type=FLAT`. `bukkit.yml`: `connection-throttle: -1`.
2. `cd tests/live && npm install`
3. `./run.sh mylabel ../../plugin/dist/TCGuard-6.0.0.jar`, or with the gate in front:
   `TCG_GATE=../../dist/tcguard-gate-1.2.0-linux-amd64 ./run.sh mylabel ../../plugin/dist/TCGuard-6.0.0.jar`.
   Add `TCG_SECRET=anything` to run with the origin lock on.

Core suite, any version:

1. A server folder for that version with `online-mode=false`, a flat world and
   `connection-throttle: -1`, started so that its console output goes to `server.out` and its
   console input comes from a pipe named `in` in that folder.
2. `./core.sh <version> <port> "<start command>" "<stop command>" <server folder> <jar> [gate binary]`

Results land in `tests/live/results-<label>.json`. Bots connect from separate loopback addresses
(`127.30.x.1`, `127.40.x.y`, `127.50.x.y`), so run it on Linux, where the whole 127/8 block is local.
