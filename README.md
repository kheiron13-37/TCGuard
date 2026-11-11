# TCGuard

Crash, exploit and grief protection for Minecraft servers, 1.8 to 1.21.

[Türkçe](README_TR.md)

TCGuard has two parts. Use either one, or both.

| Part | What it is | What it covers |
| --- | --- | --- |
| [Plugin](plugin/README.md) | Spigot / Paper plugin, one jar, no dependencies | every packet after a player logs in |
| [Gate](gate/README.md) | small program in front of the server | everything before login, and hiding the server |

```
players ──> Gate ──> BungeeCord / Velocity (optional) ──> Spigot or Paper + Plugin
```

The plugin does the most on 1.8, where the server itself has the fewest limits. On 1.9 - 1.21 it
runs the checks that do not depend on the packet format. The table is in the
[plugin README](plugin/README.md#what-runs-on-which-version).

## Quick start

**Plugin only**
1. Put `TCGuard-<version>.jar` in `plugins/`.
2. Start the server. Done; the defaults are safe.

**Plugin + Gate**
1. Do the step above.
2. Make the server reachable only from the machine the gate runs on (`server-ip=127.0.0.1` or a firewall rule).
3. Run the gate once; it writes `gate.json`. Set `listen` (the public port) and `backend` (the server).
4. Put the same random text in `secret` (gate.json) and `proxy-protocol.secret` (plugin config).

Details: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## What it does not do

Anti-cheat, bot detection, chat filtering, login security, lag limiters. It also cannot absorb a
large volumetric DDoS; that has to be filtered by your host or a service in front.

## More

- [Deployment guide](docs/DEPLOYMENT.md) — setups, BungeeCord/Velocity, origin lock
- [Exploit catalogue](docs/EXPLOITS.md) — every protection and the test that proves it
- [Test report](docs/TESTING.md)
- [Changelog](CHANGELOG.md) · [Release notes](docs/releases)
- [Contributing](CONTRIBUTING.md) · [Reporting a vulnerability](SECURITY.md)

MIT licensed, see [LICENSE](LICENSE).
