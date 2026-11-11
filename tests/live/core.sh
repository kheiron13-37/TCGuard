#!/bin/bash
V=$1; PORT=$2; START=$3; STOP=$4; D=$5; JAR=$6; GATE=$7; B=$(cd "$(dirname "$0")" && pwd)
export TCG_VERSION=${TCG_CLIENT:-$V} TCG_LOG=$D/server.out TCG_CONSOLE=${TCG_CONSOLE:-$D/in}
LABEL=core-$V${TCG_CLIENT:+-client$TCG_CLIENT}
prepare() {
  $STOP; cp "$JAR" "$D/plugins/TCGuard.jar"
  rm -rf "$D/plugins/TCGuard" "$D/world" "$D/world_nether" "$D/world_the_end" "$D/ops.json"; mkdir -p "$D/plugins/TCGuard"
  printf 'op-guard:\n  allowed: [Sahip]\n%s' "$1" > "$D/plugins/TCGuard/config.yml"
  $START >/dev/null || { echo "server start failed"; exit 1; }
}
prepare ""
(cd "$B" && TCG_PORT=$PORT timeout 900 node core.js $LABEL)
if [ -n "$GATE" ]; then
  prepare $'proxy-protocol:\n  secret: "core-test-secret"\n'
  G=$(mktemp -d)
  printf '{"listen": ":%s", "backend": "127.0.0.1:%s", "secret": "core-test-secret"}' $((PORT+1000)) $PORT > $G/gate.json
  (cd $G && exec "$GATE" -config gate.json > $G/out 2>&1) & GP=$!
  sleep 1
  (cd "$B" && TCG_PORT=$((PORT+1000)) TCG_BACKEND_PORT=$PORT TCG_SECRET=core-test-secret TCG_GATE_LOG=$G/out timeout 900 node core.js $LABEL-lock)
  kill $GP 2>/dev/null
fi
$STOP
