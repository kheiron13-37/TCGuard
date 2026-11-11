# Deployment

TCGuard comes in two parts, and each covers what the other cannot see.

```
players ──> TCGuard Gate ──> BungeeCord / Velocity (optional) ──> Spigot / Paper + TCGuard plugin
            up to login                                           everything after login
```

**TCGuard Gate** is a small standalone program in front of everything. It handles the part of a
connection that happens before login, where the plugin is not involved yet: handshake checks,
server-list pings, connection and login rates, slow or silent sockets. After login it only passes
bytes through, with an upload budget per connection.

**The plugin** inspects every packet after login, including the ones the gate cannot read (online
mode encrypts the stream from login on).

Neither part needs the other. The gate alone protects any 1.8+ server or proxy before login; the
plugin alone protects a 1.8 - 1.21 server after login (what it checks on which version is in the
[plugin README](../plugin/README.md#what-runs-on-which-version)).

## Gate

Download the binary for your system from the release (`tcguard-gate-<version>-linux-amd64`,
`-linux-arm64`, `-windows-amd64.exe`) and run it once; it writes `gate.json` with the defaults:

```json
{
  "listen": ":25565",
  "backend": "127.0.0.1:25566",
  "proxy_protocol": true,
  "handshake_timeout_seconds": 5,
  "idle_timeout_seconds": 30,
  "max_pending_handshakes": 512,
  "per_ip": { "connections": 5, "connects_per_second": 2, "burst": 10 },
  "logins_per_second": 20,
  "name_pattern": "^[A-Za-z0-9_]{1,16}$",
  "status_cache_seconds": 3,
  "upload": { "bytes_per_second": 262144, "burst": 1048576 },
  "messages": { "busy": "...", "unavailable": "..." }
}
```

| key | meaning |
| --- | --- |
| `listen` | where players connect (one front; use `routes` for several) |
| `backend` | the server or proxy behind the gate |
| `routes` | several fronts in one gate (see below); overrides `listen`/`backend` when set |
| `proxy_protocol` | send the player's address to the backend as a PROXY protocol v2 header |
| `secret` | sign every forwarded connection (origin lock, see below) |
| `handshake_timeout_seconds` | time a connection has to finish handshake and login start |
| `idle_timeout_seconds` | a logged-in client that sends nothing for this long is dropped |
| `max_pending_handshakes` | connections allowed to be in the handshake at once, across all addresses |
| `per_ip` | open connections per address, and how fast an address may open new ones |
| `logins_per_second` | logins passed to the backend per second; the rest get `messages.busy` |
| `name_pattern` | allowed player names. Cracked servers with unusual names should widen it |
| `status_cache_seconds` | server-list pings are answered from a copy refreshed this often |
| `upload` | bytes per second a logged-in client may send, with a burst |

One line per route every 10 seconds summarises connections, logins, pings and refusals by reason.

### One gate in front of several servers

If you run more than one server (for example a lobby and a survival server on different ports, or a
few independent servers on one machine), one gate can front them all. Replace `listen`/`backend`
with a `routes` list — each entry is its own front with its own backend, and all of them share the
same limits, timeouts and messages:

```json
{
  "routes": [
    { "listen": ":25565", "backend": "127.0.0.1:30001" },
    { "listen": ":25566", "backend": "127.0.0.1:30002" },
    { "listen": ":25567", "backend": "127.0.0.1:30003" }
  ],
  "proxy_protocol": true,
  "per_ip": { "connections": 5, "connects_per_second": 2, "burst": 10 }
}
```

Players reach each server through the matching port; the gate protects every one of them and
forwards the real address to each. Per-IP and login limits are counted per route, so a flood on one
server does not use up another server's budget. Each backend must still be closed to the internet.

For a network that already has one entry proxy (BungeeCord/Velocity), you do not need `routes`: put
a single gate in front of the proxy, and the proxy fans out to the backend servers.

**The backend must not be reachable from the internet**, or attackers simply connect around the
gate. Bind it to `127.0.0.1` (`server-ip=127.0.0.1` in `server.properties`, or the proxy's bind
address) or block its port in the firewall.

### Behind the gate: Spigot or Paper with the plugin

```yaml
# plugins/TCGuard/config.yml
proxy-protocol:
  trusted: [127.0.0.1]
```

Connections from a trusted address must start with a PROXY header; a connection from there
without one is closed, so nobody on that machine can pose as the gate. Everyone else connects
normally.

Newer Paper builds have a PROXY protocol option of their own (`proxies.proxy-protocol` in
`config/paper-global.yml`). Use one or the other, not both: with TCGuard's option on, leave
Paper's off, since the header can only be read once.

### Behind the gate: BungeeCord, Velocity and their forks

The gate sends the client address as a **PROXY protocol v2** header. This is the same format
HAProxy sends, and every common Minecraft proxy decodes it with Netty's `HAProxyMessageDecoder`,
so the gate works in front of all of them. You only have to turn the feature on in the proxy:

| Proxy | Turn on PROXY protocol | Notes |
| --- | --- | --- |
| **BungeeCord** | `proxy_protocol: true` on the listener in `config.yml` | `PipelineUtils` adds `HAProxyMessageDecoder` when the listener has it on |
| **Waterfall** | `proxy_protocol: true` | BungeeCord fork, same option |
| **FlameCord** | `proxy_protocol: true` (the BungeeCord option) | FlameCord is a BungeeCord fork and keeps the listener option; its own anti-bot runs in front and the gate complements it |
| **Aegis** | `proxy_protocol: true` (the BungeeCord option) | BungeeCord fork, same option |
| **Velocity** | `haproxy-protocol = true` in `velocity.toml` | `ServerChannelInitializer` adds `HAProxyMessageDecoder` when it is on |

Turning this on without a proxy-protocol sender in front is a security hole: anyone who can reach
the proxy could then spoof their address. So enable it on the proxy **only** when the gate (or
another PROXY-protocol source such as HAProxy or TCPShield) is the only thing that can reach it —
the same "backend not reachable from the internet" rule as above.

A Spigot server **behind** such a proxy keeps the proxy's own IP forwarding (`bungeecord: true`
or Velocity modern forwarding) and does **not** use TCGuard's `proxy-protocol.trusted` — that
option is only for a Spigot server sitting directly behind the gate.

FlameCord and Aegis are anti-bot BungeeCord forks. They overlap with the gate on connection and
login rate limiting, so you do not need both for that; run the gate in front of a plain proxy, or
keep your fork and point it at the gate. The gate and the fork do not conflict — the fork simply
reads the address the gate forwards.

These proxy setups follow each project's documented PROXY protocol support (verified against the
BungeeCord and Velocity source), but only the Spigot-directly-behind-the-gate path was exercised by
the live tests.

## Origin lock

Hiding a server's address is not enough: addresses leak through old DNS records, plugins that call
home, or a scan of the hosting range. With a shared secret the server stops caring who knows its
address, because it only talks to the gate.

```json
// gate.json
{ "secret": "a-long-random-string" }
```
```yaml
# plugins/TCGuard/config.yml
proxy-protocol:
  secret: "a-long-random-string"
```

The gate then adds a signature to each connection it forwards: a timestamp and an HMAC-SHA256 over
the player's address and that timestamp. The plugin checks it before the server reads anything
else and closes every connection that

- has no header, or a header without a signature,
- was signed with a different secret,
- carries a signature older than 30 seconds,
- repeats a signature that was already used.

So a connection made straight to the server is dropped at the first packet, whatever address it
comes from. Keep both clocks on NTP; a gate whose clock is off by more than 30 seconds is refused.

The signature is only understood by the TCGuard plugin. BungeeCord and Velocity ignore it, so
behind those the lock does not apply and the firewall rule stays your protection.

### Several gates in front of one hidden server

Because the lock does not depend on addresses, the gate can run on other machines. Rent a few
small servers, run a gate on each with the same `secret` and the real server as `backend`, and
publish their addresses in DNS (several A records, or an SRV record per gate). Players only ever
see the gates. If one gate is attacked or goes down, the others keep serving, and the real server
keeps refusing everything that does not come through them.

### What this is not

This covers the part of a service like TCPShield that is software: a hidden origin that only
accepts signed traffic, filtering before login, and several entry points. It does not cover the
part that is infrastructure. A volumetric flood (tens or hundreds of Gbit/s of raw traffic) fills
the network link of whatever it is aimed at before any program sees a packet; absorbing it takes
a network with that much spare capacity. If you expect such attacks, put the gates at a host that
includes DDoS filtering, or behind a filtering service, and keep the real server hidden behind
them.

## Server software

Some bugs can only be fixed in the server itself: the plugin sees packets, not the code that
handles them. So keep the server current: the latest Paper build for your version, and on 1.8 a
fork that still gets fixes. TacoSpigot, the 1.8 fork used for the tests, is no longer maintained;
PandaSpigot is open source, updates Netty to 4.1 and stays compatible with Java 8 plugins.

TCGuard was run on Spigot/TacoSpigot 1.8.8 and Paper 1.12.2, 1.16.5, 1.20.4 and 1.21.8. On another
fork or version, run the live suite against it first (`docs/TESTING.md`).

ViaVersion on a 1.8 server is fine: the plugin inspects frames after ViaVersion has translated them
to the 1.8 format, so newer clients are checked like 1.8 clients. This was run with ViaVersion
5.12.2 and clients from 1.12.2 to 1.21.8.

## Checklist

1. Backend port closed to the internet.
2. Gate in front, `proxy_protocol` on, backend set to trust it.
3. Plugin on every game server.
4. Server software that still gets fixes.
5. If you use a host-level DDoS filter (TCPShield, OVH, Cloudflare Spectrum), the gate goes behind
   it. Volumetric floods have to be stopped before they reach your machine; no program on the
   machine can do that.
