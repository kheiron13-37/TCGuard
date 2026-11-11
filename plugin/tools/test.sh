#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
jar=$(ls "$root"/dist/TCGuard-*.jar | head -1)
cp="$jar:${TEST_CLASSPATH:-$root/libs/spigot-1.8.8.jar}"
out="$root/.build-tests"
rm -rf "$out" && mkdir -p "$out"
"${JAVAC:-javac}" -Xlint:-options -encoding UTF-8 -cp "$cp" -d "$out" "$root"/tests/unit/net/tcguard/*.java
"${JAVA:-java}" -cp "$out:$cp" net.tcguard.UnitTests
"${JAVA:-java}" -cp "$out:$cp" net.tcguard.FrameFuzz "${FUZZ_ITERATIONS:-20000}"
