# TCGuard Gate

A small program that stands in front of your Minecraft server or proxy. It handles everything that
happens before a player logs in, and it can hide the real server completely.

[Türkçe](README_TR.md) · [Back to main page](../README.md)

One file, no dependencies, Linux and Windows. Works in front of Spigot and Paper 1.8 - 1.21 (with the TCGuard plugin),
BungeeCord, Waterfall, FlameCord, Aegis and Velocity.

## What it does

| | |
| --- | --- |
| Ping floods | server-list pings are answered from a cache; the server is asked every few seconds at most |
| Silent connections | sockets that never finish the handshake are closed after 5 seconds |
| Connection floods | limits on open connections and connect rate per address |
| Login floods | at most N logins per second reach the server; the rest see "Server is busy" |
| Garbage | malformed handshakes, invalid names and non-Minecraft traffic never reach the server |
| Upload abuse | a logged-in client may only send a fixed amount of data per second |
| Real addresses | the player's address is passed on with PROXY protocol v2 |
| Origin lock | with a `secret`, the server accepts only connections signed by the gate |
| Several servers | one gate can front many servers (`routes`) |

It does not guess who is a bot, and it cannot absorb a large volumetric DDoS.

## Run

1. Download the file for your system from the release page.
2. Start it once: `./tcguard-gate-<version>-linux-amd64` (or the `.exe`). It writes `gate.json`.
3. Edit `gate.json`, start it again.

```json
{
  "listen": ":25565",
  "backend": "127.0.0.1:25566",
  "proxy_protocol": true,
  "secret": "a-long-random-string"
}
```

| Option | Meaning |
| --- | --- |
| `listen` | the port players connect to |
| `backend` | your server or proxy |
| `proxy_protocol` | pass on the player's address (leave on) |
| `secret` | origin lock; same value as `proxy-protocol.secret` in the plugin |
| `routes` | instead of `listen`/`backend`: a list of them, for several servers |

The remaining options (limits, timeouts, messages) are fine as they are; they are explained in
[docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md).

**Important:** the server behind the gate must not be reachable from the internet, or attackers go
around the gate. Bind it to `127.0.0.1`, use a firewall, or turn on the origin lock.

## Behind the gate

| Backend | Setting |
| --- | --- |
| Spigot / Paper + TCGuard plugin | `proxy-protocol.secret` (or `trusted: [gate address]`) in the plugin config |
| BungeeCord / Waterfall / FlameCord / Aegis | `proxy_protocol: true` on the listener |
| Velocity | `haproxy-protocol = true` |

## Build

```bash
go test ./...
tools/build.sh     # Go 1.22+, writes binaries to ../dist
```
