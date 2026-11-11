# TCGuard Plugin

Spigot / Paper plugin for 1.8 to 1.21. One jar, no dependencies. It checks what players send after
login, before the server decodes it.

[Türkçe](README_TR.md) · [Back to main page](../README.md)

## Requirements

- Spigot, Paper or a fork, 1.8 to 1.21
- Java 8 or newer (whatever your server already runs on)

ProtocolLib is no longer needed.

## What runs on which version

| Protection | 1.8 | 1.9 - 1.21 |
| --- | --- | --- |
| Oversized frames and compression bombs, refused before decoding | yes | yes |
| Packet floods | yes, weighted per packet type | yes, all packets counted together |
| Log4Shell in chat and commands, log filter | yes | yes |
| Plugin exploit commands (`//calc`, ...) | yes | yes |
| Operator list (force-OP) | yes | yes |
| PROXY protocol and origin lock for the [gate](../gate/README.md) | yes | yes |
| Packet contents: NBT bombs, book pages, sign lines, invalid clicks | yes | no |
| Hacked creative items, over-enchanted items | yes | no |
| End portal removed by a bucket, dispenser or mushroom | yes | no |

The last three rows read the 1.8 packet format and patch 1.8 behaviour. Newer servers changed the
format with almost every release and brought limits of their own, so TCGuard does not repeat those
checks there. On start the plugin logs which set is active.

## Tested

| Server | Java | Result |
| --- | --- | --- |
| Spigot / TacoSpigot 1.8.8 | 25 | full suite |
| Paper 1.12.2 | 8 | core suite |
| Paper 1.16.5 | 8 | core suite |
| Paper 1.20.4 | 17 | core suite |
| Paper 1.21.8 | 25 | core suite |

Versions in between use the same code path as their neighbours but were not run. Folia is not
supported. Numbers and details: [docs/TESTING.md](../docs/TESTING.md).

## Install

Put the jar in `plugins/` and start the server. `plugins/TCGuard/config.yml` is created on first
start. Coming from 5.x: remove ProtocolLib if nothing else uses it; the config stays valid.
Coming from 4.x: delete the old `plugins/TCGuard` folder first.

## What it protects against

| Area | Examples |
| --- | --- |
| Hostile frames, before the server decodes them | oversized packets, compression bombs; on 1.8 also NBT bombs, huge book pages and sign lines |
| Invalid packets (1.8) | impossible window clicks, NaN vehicle input, translate components on signs and in books |
| Packet floods | too many packets are dropped, then the player is kicked |
| Hacked items (1.8) | creative items are rebuilt from vanilla properties; 32k enchants are reset |
| Log4Shell | `${jndi:...}` in chat or a command is a kick, and never reaches the log |
| Plugin exploit commands | WorldEdit `//calc`, HolographicDisplays `readtext`, Multiverse regex, PEX |
| End portals (1.8) | cannot be removed with a bucket, a dispenser or growing mushrooms |
| Force-OP | optional list of allowed operators |
| Direct connections around the gate | origin lock, see the [gate](../gate/README.md) |

The full list with tests: [docs/EXPLOITS.md](../docs/EXPLOITS.md).

## Commands

| Command | What it does |
| --- | --- |
| `/tcguard status` | version, guarded connections, blocked count |
| `/tcguard reload` | re-read the config |
| `/tcguard vl <player>` | current violation levels of a player |
| `/tcguard alerts` | turn alerts on or off for yourself |

## Permissions

| Permission | Default | |
| --- | --- | --- |
| `tcguard.admin` | op | use `/tcguard` |
| `tcguard.alerts` | op | see alerts |
| `tcguard.bypass` | nobody | skip the checks (read when the player joins) |

## Config

Every option is explained in [`resources/config.yml`](resources/config.yml). The ones you are most
likely to touch:

| Option | Meaning |
| --- | --- |
| `violations.packets.weights` | lower a value if a modded client gets flagged (`OTHER` is the one used on 1.9+) |
| `frames.modern-max-bytes` | largest frame a 1.9+ server accepts (default 1 MB) |
| `items.max-enchant-level` | 1.8: enchantments above this are reset (default 10) |
| `commands.blocked` | exploit commands to block |
| `op-guard.allowed` | names that may be op; empty = off |
| `proxy-protocol.secret` | same value as the gate's `secret`; turns on origin lock |

## Build

```bash
mvn package            # or, without Maven:
tools/build.sh         # needs libs/spigot-1.8.8.jar
tools/test.sh          # unit tests and the fuzzer
```

The plugin is compiled once against the 1.8.8 API and finds what it needs on newer servers at
runtime, so there is one jar for every version.
