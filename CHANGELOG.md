# Changelog

## Plugin 6.0.0 — 2026-11-11

Gate stays at 1.2.0.

### Server versions
- The plugin runs on 1.8 through 1.21 with one jar. 5.x refused to start on anything but 1.8.
- On 1.9+ the checks that do not depend on the packet format are active: frame size, compression
  bombs, packet rate, Log4Shell, the command filter, the operator list, PROXY protocol and the
  origin lock. Packet content checks, item rebuilding and the end portal patches stay 1.8-only.
  The plugin logs which set is active when it starts.
- New option `frames.modern-max-bytes` (default 1 MB): largest frame a 1.9+ server accepts.

### ProtocolLib is no longer needed
- The decoded-packet listener is gone. What it still did moved into the frame inspector, which
  runs earlier: window clicks with an invalid slot, button or mode, and packet rates.
- New in the frame inspector (1.8): NaN vehicle input, translate components on signs and in
  written books.
- Connections are matched to players by address instead of through ProtocolLib.

### Removed
Checks that were reproduced against an unprotected 1.8.8 server and turned out to do nothing
there: NaN position and invalid hotbar slot (the server kicks by itself), creative packet outside
creative and book edit without a book (ignored by the server), dispenser firing out of the world,
shearing without shears, redstone on a trapdoor, bow bomb, movement into unloaded chunks, and
closing containers across worlds. `docs/EXPLOITS.md` lists them with what the server does.

### Fixed
- The log filter was attached through reflection, which on current servers picked the wrong
  logger context and filtered nothing. It is now attached to the server's logging configuration
  directly, and removed again cleanly on disable.
- A Log4Shell lookup in chat or a command is refused on every version, so it is neither logged
  nor sent on to other players' clients.
- An uncompressed frame above the size limit is refused before it reaches the decompressor or a
  protocol translator such as ViaVersion.
- A violation level's actions run at most once in five seconds per player. A client whose score
  hovered around a level used to trigger the notification on every crossing.
- Frames still queued on a connection after its player was kicked were scored against a fresh
  record, which repeated the alerts of the kick. They are dropped now.

### Tests
- `tests/live/core.js`: one suite that runs unchanged against 1.8.8, 1.12.2, 1.16.5, 1.20.4 and
  1.21.8, directly and through the gate with the origin lock.
- Unit tests for the new frame checks; the fuzzer covers them too.

## Plugin 5.2.0, Gate 1.2.0 — 2026-11-11

### Origin lock
- Gate: `secret` signs every forwarded connection with a timestamp and an HMAC-SHA256 over the
  player's address (a TLV in the PROXY v2 header).
- Plugin: `proxy-protocol.secret`. When set, every connection must carry a valid signature;
  unsigned, wrongly signed, stale (30 s) and replayed ones are closed at the first packet. The
  server then refuses direct connections even if its address is known, and gates can run on other
  machines.
- `docs/DEPLOYMENT.md`: origin lock, several gates in front of one hidden server, and what this
  does not replace (volumetric DDoS filtering).

## Plugin 5.1.0, Gate 1.1.0 — 2026-11-11

### Gate 1.1.0
- Multiple routes: one gate can front several servers, each `listen` forwarding to its own
  `backend`, limits counted per route (`routes` in `gate.json`).
- `docs/DEPLOYMENT.md` documents PROXY protocol compatibility with BungeeCord, Waterfall,
  FlameCord, Aegis and Velocity (all decode the v2 header the gate sends).

### Plugin tests
- Live coverage for the world patches that had none: end portal flooded by a dispenser (W02),
  washed by a bucket (W03), overwritten by mushroom or tree growth (W04), shear without shears
  (W05), redstone-on-trapdoor loop (W06). `docs/EXPLOITS.md` updated.

### Gate 1.0.0 (initial)
A standalone front proxy (Go, no dependencies, Linux and Windows binaries) for everything before
login: server-list pings answered from a cache, handshake timeout for silent sockets, connections
and connect rate per address, a global login rate, handshake and name validation, an upload budget
after login, PROXY protocol v2 towards the backend.

### Plugin
- PROXY protocol v2 (`proxy-protocol.trusted`). A trusted address that sends no header is refused.
- Alerts for refused pre-join frames show the real client address behind a proxy.

### Project
- Repository split into `plugin/` and `gate/`, with a shared live suite in `tests/live`.
- Frame inspector fuzzer (`plugin/tests/unit/.../FrameFuzz.java`), run by `tools/test.sh`.
- `docs/EXPLOITS.md`: every protection with the test that reproduces the attack.
- `docs/DEPLOYMENT.md`, `SECURITY.md`, `CONTRIBUTING.md`, issue and pull request templates, CI workflow.

## 5.0.0 — 2026-11-10

Rewrite. TCGuard now does crash, exploit and grief protection for 1.8.x and nothing else.
59 source files and 10,700 lines became 10 files and 1,400 lines; the jar went from 13 MB to 46 KB.

### Removed
- Anti-cheat style checks: click/macro analysis, reach and movement heuristics, fast break/place,
  client brand scanning, self-damage.
- Anti-bot: join limits, name heuristics, captcha, settings fingerprint, GeoIP, datacenter lists.
- GuardCode (admin PIN / 2FA), admin list, login security, permission auditing.
- Chat and command throttling, plugin-list hiding, namespace command blocking.
- Lag limiters: redstone, piston, entity-per-chunk, falling block and vehicle limits.
- SQLite event log, player risk profiles, statistics, GUI, Discord webhook, proxy bridge,
  language files, the plugin API event.
- Reflection for versions other than 1.8.x. The plugin now refuses to start on them.

### Added
- The frame guard is installed when a connection is accepted, not when the player joins, so the
  first play packet is already covered.
- Compressed frames that announce an oversized inflate are refused before decompression.
- Raw tab-complete length check.
- Dispensers and buckets can no longer remove end portals.

### Changed
- A frame that fails inspection closes the connection instead of adding violation points.
- Frame size limits are per packet kind: 8 KB without an item, 64 KB with one. In 4.3.0 the single
  12 KB limit kicked players who clicked a full written book.
- Malformed packets (NaN position, bad slot, ...) are always a kick.
- Config rewritten, 524 lines to 97. `actions` are a list of `"<level> <action>"`.
- Commands: `/tcguard status|reload|alerts|vl`. Alerts go to `tcguard.alerts`.
- `tcguard.bypass` replaces the admin-list bypass.
- ProtocolLib is a hard dependency.

## 4.3.0 — 2026-11-10

### Added
- **Violation-level engine** (`violations.*`): decaying per-player scores for packets, items and commands. Each packet type has its own weight, there is a byte-based bandwidth weight, a `cancel-vls` level, `decay-delay-seconds`, and configurable actions per level (`notification`, `kick`, console commands with placeholders).
- **Pre-decode Netty guard** (`violations.packets.decoder-guard`): frames are size-checked and item NBT is scanned *before* the vanilla decoder runs. It catches depth/tag/list/array/string limits, book pages, crash skull textures and sign lines.
- **Creative item sanitizer** (`creative-items-fix`): creative items are rebuilt from vanilla properties; materials on the blacklist are refused.
- Item analysis: crash skull textures, `EntityTag`, `BlockEntityTag` (except banners), banner pattern limit.
- Configurable exploit-command honeypot (`honeypot.commands`) with built-in HolographicDisplays/Multiverse/WorldEdit/PEX entries.
- Packet blacklist, offline-packet drop, null-chunk move patch, shear patch, world-change inventory close.
- `/guard vl <player>`, `/guard alerts`, `/guard profile <player>`, console `guardcode unlock <admin>`; `/guard doctor` shows the Netty pipeline.

### Security
- Security alerts (which include IPs) are no longer sent to admin names whose GuardCode session is not verified.

### Changed
- Fingerprint waves no longer kick on their own: a wave raises attack mode (captcha challenge); kicks happen only during an attack mode that was already active.
- Honeypot strikes are `commands` violation points (3 within 30 s → kick + IP quarantine, configurable).
- The shared security and Bukkit helpers live in `src/` so the repository builds standalone.

## 4.2.0 — 2026-11-10

### Security
- Log4Shell regex ReDoS (≈O(n⁴)) replaced by a linear detector.
- Unverified admin names no longer bypass checks; on offline-mode servers, the first PIN is console-only. GuardCode lockout survives reconnects; PBKDF2 runs off the main thread.
- Admin IP list prefix matching fixed; admin IP lock is off by default in code as in config.

### Fixed
- First install wrote a config containing only `language: en`. Missing keys now fall back to the bundled defaults.
- Duplicate `lag-protection` key silently dropped the redstone/piston/entity settings.
- Fingerprint "botnet" false positive (every 10th player with the same locale was kicked).
- Commands with `:` in their arguments were blocked.
- Digging was reported as an auto-clicker.
- Death screen dropped chat and keep-alive packets.
- An ARM_ANIMATION flood was never kicked and produced one alert per packet.
- NoCom shield leaked a repeating task per teleport and revealed vanished players.
- AntiIllegal threw an exception on incompatible enchantments.
- PlayerProfiler never wrote to the database.
- PermGuard re-alerted every 30 s.
- BED/BEDROCK mix-up; pistons blocked next to bedrock.
- Thread-safety: language map, date formatting, anti-bot name clustering.

## 4.1.0
- Closed-source baseline (shared security modules, signed bridge, PBKDF2 GuardCode, bounded DB queue).
