#!/bin/bash
LABEL=$1; JAR=$2; S=${TCG_SERVER:-$HOME/tcg-server}; B=$(cd "$(dirname "$0")" && pwd)
cp "$JAR" "$S/plugins/TCGuard.jar"
rm -f "$B"/results-$LABEL*.json "$B/health-$LABEL.txt"
if [ -n "$TCG_GATE" ]; then
  export TCG_PORT=25566 TCG_GATE_LOG=/tmp/gate.out
  mkdir -p /tmp/tcg-gate && printf '{"listen": ":25566", "backend": "127.0.0.1:25565", "secret": "%s"}' "$TCG_SECRET" > /tmp/tcg-gate/gate.json
  (cd /tmp/tcg-gate && exec "$TCG_GATE" -config gate.json > "$TCG_GATE_LOG" 2>&1) &
  GATE_PID=$!
  trap 'kill $GATE_PID 2>/dev/null' EXIT
fi
TGROUPS=("C01 C02 C04 C05 P01 P02 P03 P04 P05 P06" "F01 F02 F03 F04 C03" "N01 N02 N03 N04 N05 N06 N07" "I01 I02 I03 I04 I05 W01 W02 W03 O01 A01 S01" "X01")
[ -n "$TCG_GATE" ] && TGROUPS+=("gate")
i=0
for g in "${TGROUPS[@]}"; do
  i=$((i+1))
  "$B/srv.sh" stop
  rm -rf "$S/plugins/TCGuard"; mkdir -p "$S/plugins/TCGuard"
  printf 'op-guard:\n  allowed: [Sahip]\n' > "$S/plugins/TCGuard/config.yml"
  [ -n "$TCG_GATE" ] && printf 'proxy-protocol:\n  trusted: [127.0.0.1]\n  secret: "%s"\n' "$TCG_SECRET" >> "$S/plugins/TCGuard/config.yml"
  "$B/srv.sh" start clean >/dev/null || { echo "server start failed"; exit 1; }
  if [ "$g" = gate ]; then (cd "$B" && timeout 900 node gate.js $LABEL-g$i); else (cd "$B" && timeout 900 node suite.js $LABEL-g$i $g); fi
  sleep 2
  "$B/srv.sh" stop
  errors=$(grep -cE "at net\.tcguard|Could not pass event .* to TCGuard|Error occurred while (enabling|disabling) TCGuard|Task #[0-9]+ for TCGuard .* generated an exception|Could not load 'plugins/TCGuard" "$S/server.out")
  grep -q "Enabling TCGuard" "$S/server.out" || errors=$((errors+1))
  echo "group$i errors=$errors" | tee -a "$B/health-$LABEL.txt"
  cp "$S/server.out" "$B/server-$LABEL-g$i.log"
done
node -e "
const fs=require('fs');let all=[];
for(let i=1;i<=$i;i++){const f='$B/results-$LABEL-g'+i+'.json';if(fs.existsSync(f))all=all.concat(JSON.parse(fs.readFileSync(f)));}
const errors=fs.readFileSync('$B/health-$LABEL.txt','utf8').trim().split('\n').reduce((n,l)=>n+ +l.split('=')[1],0);
all.push({id:'H01',name:'no TCGuard exception in any server log',pass:errors===0,detail:'errors='+errors});
fs.writeFileSync('$B/results-$LABEL.json',JSON.stringify(all,null,1));
console.log('$LABEL TOTAL: '+all.filter(r=>r.pass).length+'/'+all.length);"
