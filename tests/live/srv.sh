#!/bin/bash
S=${TCG_SERVER:-$HOME/tcg-server}
stop() {
  if pgrep -f "spigot-1.8.8.jar" >/dev/null; then
    timeout 5 bash -c "echo stop > ${TCG_CONSOLE:-/tmp/mcin}" || true
    for i in $(seq 1 60); do pgrep -f "spigot-1.8.8.jar" >/dev/null || break; sleep 1; done
    pkill -9 -f "spigot-1.8.8.jar" 2>/dev/null
  fi
  pkill -f "tail -f /tmp/mcin" 2>/dev/null
  true
}
start() {
   [ "$1" = clean ] && rm -rf $S/world $S/world_nether $S/world_the_end $S/ops.json $S/usercache.json
  rm -f /tmp/mcin; mkfifo /tmp/mcin
  : > $S/server.out
  (tail -f /tmp/mcin | $S/start.sh > $S/server.out 2>&1 &)
  for i in $(seq 1 120); do
    grep -q "Startup successful\|Done (" $S/server.out && { echo "UP"; return 0; }
    grep -q "Error:\|Exception in thread \"main\"" $S/server.out && { echo "FAIL"; return 1; }
    sleep 1
  done
  echo TIMEOUT; return 1
}
case "$1" in
  start) start $2;;
  stop) stop;;
  restart) stop; start $2;;
esac
