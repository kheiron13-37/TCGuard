# Contributing

## Scope

TCGuard protects against crashes, exploits and griefing. Anti-cheat, anti-bot heuristics, chat
filtering, login security and lag limiters are out of scope; pull requests adding them will be
closed. Better versions of what is already here are always welcome.

## A change is ready when

- an exploit fix comes with a test that reproduces the attack (`tests/live`, see
  `docs/EXPLOITS.md`) and a row in the catalogue,
- all live checks pass, including the F checks for false positives; a change outside the 1.8-only
  classes also passes the core suite on a current Paper,
- `plugin/tools/test.sh` and `go test ./...` in `gate/` pass.

## Code

- Small and direct. No layer, interface or option without a second user.
- No comments in the code. Names carry the meaning; anything longer goes in the pull request or `docs/`.
- No swallowed exceptions. Handle it or let it surface.
- Plugin: Java 8 language level, Bukkit API 1.8.8, no dependencies. One jar has to load on 1.8
  through 1.21, so nothing may link against a class that exists on one version only. Netty threads
  never touch Bukkit state except through `TCGuard.sync`.
- Gate: standard library only.

## Running the tests

```bash
cd plugin && tools/build.sh && tools/test.sh
cd gate && go test ./...
cd tests/live && npm install && TCG_GATE=../../dist/tcguard-gate-<version>-linux-amd64 ./run.sh mine ../../plugin/dist/TCGuard-<version>.jar
# other versions: see docs/TESTING.md, "Running it"
```
