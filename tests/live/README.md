# Live tests

Real servers, real protocol clients, real hostile packets. Nothing is mocked.

[Türkçe](README_TR.md) · Results and setup: [docs/TESTING.md](../../docs/TESTING.md)

```bash
npm install

# full suite, Spigot 1.8.8
./run.sh mylabel ../../plugin/dist/TCGuard-<version>.jar                      # plugin alone
TCG_GATE=<gate binary> ./run.sh mylabel <jar>                                 # behind the gate
TCG_GATE=<gate binary> TCG_SECRET=anything ./run.sh mylabel <jar>             # with origin lock

# core suite, any version from 1.8 to 1.21
./core.sh <version> <port> <start command> <stop command> <server folder> <jar> [gate binary]
```

| File | |
| --- | --- |
| `suite.js` | every plugin check, 1.8.8 |
| `core.js` | the checks that run on every version (M01 - M09, L01 - L03) |
| `gate.js` | gate and origin lock checks |
| `h.js` | bot and console helpers |
| `run.sh`, `srv.sh` | 1.8.8: start a clean server for each group, collect results |
| `core.sh` | one server version: clean start, `core.js` directly and through the gate |
| `probe/` | test-only plugin used to read and change server state (1.8.8) |
| `results/` | results of the last release run |
