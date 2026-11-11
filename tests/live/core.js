const h = require('./h');
const fs = require('fs');
const net = require('net');
const zlib = require('zlib');
const crypto = require('crypto');
const { sleep, bot, alive, quit, record, ask, size, readFrom, count, cmd, waitEnd, kickText } = h;

const VERSION = process.env.TCG_VERSION || '1.8.8';
const SECRET = process.env.TCG_SECRET || '';
const BACKEND = +(process.env.TCG_BACKEND_PORT || process.env.TCG_PORT || 25565);
const label = process.argv[2] || 'core';
const only = process.argv.slice(3);
const tests = [];
const T = (id, name, fn) => tests.push({ id, name, fn });

let n = 0;
const ip = () => `127.50.${Math.floor(++n / 200)}.${n % 200 + 1}`;
const varint = v => { const o = []; do { let b = v & 0x7f; v >>>= 7; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const frame = p => Buffer.concat([varint(p.length), p]);
const str = s => Buffer.concat([varint(Buffer.byteLength(s)), Buffer.from(s)]);
const swing = b => b._client.write('arm_animation', { hand: 0 });

T('M01', 'join and chat', async () => {
  const o = size();
  const b = await bot('Ahmet', ip());
  if (!alive(b)) return record('M01', 'join and chat', false, 'join failed: ' + kickText(b));
  b.chat('merhaba dunya'); await sleep(1500);
  const said = /Ahmet.*merhaba dunya/.test(readFrom(o));
  const vl = await ask('tcguard vl Ahmet', /Ahmet: packets ([\d.]+)/);
  const status = await ask('tcguard status', /TCGuard [\d.]+: (\d+) guarded connections/);
  quit(b);
  record('M01', 'join and chat', said && vl && status && +status[1] >= 1, `chat=${said} packetVls=${vl && vl[1]} guarded=${status && status[1]}`);
});

T('M02', 'normal play for 15 seconds raises nothing', async () => {
  const b = await bot('Gezgin', ip());
  if (!alive(b)) return record('M02', 'normal play', false, 'join failed');
  const o = size();
  b.setControlState('forward', true); b.setControlState('jump', true);
  for (let i = 0; i < 150 && !b.ended; i++) { if (i % 3 === 0) swing(b); if (i % 20 === 0) b.look(i / 10, 0); await sleep(100); }
  b.clearControlStates();
  const alerts = count(/Gezgin (failed|sent)/, o);
  record('M02', 'normal play for 15 seconds raises nothing', !b.ended && alerts === 0, `disconnected=${b.ended} alerts=${alerts}`); quit(b);
});

T('M03', 'packet flood -> dropped, then kicked', async () => {
  const b = await bot('Oguzhan', ip());
  if (!alive(b)) return record('M03', 'flood', false, 'join failed');
  const o = size();
  for (let i = 0; i < 4000 && !b.ended; i++) swing(b);
  await waitEnd(b, 6000); await sleep(800);
  const alerts = count(/Oguzhan failed \S+ rate/, o);
  record('M03', 'packet flood -> dropped, then kicked', b.ended && alerts >= 1 && alerts <= 3, `disconnected=${b.ended} alerts=${alerts}`); quit(b);
});

T('M04', 'frame above the size limit is refused', async () => {
  const b = await bot('Nihat', ip());
  if (!alive(b)) return record('M04', 'big frame', false, 'join failed');
  const o = size();
  const bytes = VERSION.startsWith('1.8') ? 70000 : 1200000;
  b._client.socket.write(frame(Buffer.concat([varint(0), Buffer.from([0x0e]), Buffer.alloc(bytes, 1)])));
  await waitEnd(b, 5000); await sleep(500);
  const logged = /Nihat sent a hostile frame: frame of/.test(readFrom(o));

  let gate = false;
  if (!logged && process.env.TCG_GATE_LOG) { await sleep(11000); gate = /upload limit/.test(fs.readFileSync(process.env.TCG_GATE_LOG, 'utf8')); }
  record('M04', 'frame above the size limit is refused', b.ended && (logged || gate), `disconnected=${b.ended} plugin=${logged} gate=${gate} bytes=${bytes}`); quit(b);
});

T('M05', 'compressed frame announcing a huge size is refused before inflating', async () => {
  const b = await bot('Bomba', ip());
  if (!alive(b)) return record('M05', 'zip bomb', false, 'join failed');
  const o = size();
  const body = Buffer.concat([varint(2000000), zlib.deflateSync(Buffer.alloc(2000000))]);
  b._client.socket.write(frame(body));
  await waitEnd(b, 5000); await sleep(500);
  const logged = /Bomba sent a hostile frame: compressed frame inflating/.test(readFrom(o));
  record('M05', 'compressed frame announcing a huge size is refused before inflating', b.ended && logged, `disconnected=${b.ended} logged=${logged} wire=${body.length}B`); quit(b);
});

T('M06', 'exploit command is blocked, three in a row -> kick', async () => {
  const b = await bot('Canan', ip());
  if (!alive(b)) return record('M06', 'commands', false, 'join failed');
  for (let i = 0; i < 3 && !b.ended; i++) { b.chat(i === 1 ? '/worldedit:/calc 1+1' : '//calc 1+1'); await sleep(1400); }
  await waitEnd(b, 3000);
  record('M06', 'exploit command is blocked, three in a row -> kick', b.ended, `disconnected=${b.ended} reason=${kickText(b)}`); quit(b);
});

T('M07', 'op outside op-guard.allowed is removed, a listed one stays', async () => {
  const a = await bot('Sahip', ip()), b = await bot('Hacker', ip());
  const o = size();
  cmd('op Sahip'); cmd('op Hacker');
  await waitEnd(b, 5000); await sleep(500);
  const removed = /removed operator status from Hacker/.test(readFrom(o));
  const ops = fs.readFileSync(require('path').dirname(process.env.TCG_LOG) + '/ops.json', 'utf8');
  quit(a); quit(b);
  record('M07', 'op outside op-guard.allowed is removed, a listed one stays', removed && b.ended && /Sahip/.test(ops) && !/Hacker/.test(ops),
    `removed=${removed} kicked=${b.ended} opsFile=${(ops.match(/"name": "(\w+)"/g) || []).join(',')}`);
});

T('M08', 'a log4j lookup is refused in chat and never reaches the log', async () => {
  const o = size();
  const b = await bot('Emre', ip());
  if (!alive(b)) return record('M08', 'log4j', false, 'join failed');
  b.chat('${jndi:ldap://127.0.0.1:9/a}');
  await waitEnd(b, 4000);
  cmd('say ${jndi:ldap://127.0.0.1:9/b} konsol'); cmd('say duz mesaj'); await sleep(1500);
  const log = readFrom(o);
  const refused = /Emre sent a hostile frame: log4j lookup/.test(log);
  quit(b);
  record('M08', 'a log4j lookup is refused in chat and never reaches the log', b.ended && refused && !/jndi:ldap/.test(log) && /duz mesaj/.test(log),
    `kicked=${b.ended} refused=${refused} lookupLogged=${/jndi:ldap/.test(log)} plainLineLogged=${/duz mesaj/.test(log)}`);
});

T('M09', '20 players online, nobody is kicked', async () => {
  const bots = [];
  for (let i = 0; i < 20; i++) { bots.push(await bot('Oyuncu' + i, ip())); await sleep(250); }
  await sleep(15000);
  const online = bots.filter(alive).length;
  const o = size();
  cmd('tps'); await sleep(1000);
  const tps = (readFrom(o).replace(/\x1b\[[\d;]*m|§./g, '').match(/TPS from last[^:]*: \*?([\d.]+)/) || [])[1];
  bots.forEach(quit);
  record('M09', '20 players online, nobody is kicked', online === 20, `online=${online}/20 tps=${tps || 'n/a'}`);
});

function proxyHeader(secret, stamp = Date.now()) {
  const address = Buffer.from([198, 51, 100, 9, 127, 0, 0, 1, 0x9c, 0x40, 0x63, 0xdd]);
  const time = Buffer.alloc(8); time.writeBigUInt64BE(BigInt(stamp));
  const tlv = secret ? Buffer.concat([Buffer.from([0xe0, 0, 40]), time, crypto.createHmac('sha256', secret).update(address).update(time).digest()]) : Buffer.alloc(0);
  const length = Buffer.alloc(2); length.writeUInt16BE(address.length + tlv.length);
  return Buffer.concat([Buffer.from('\r\n\r\n\0\r\nQUIT\n', 'latin1'), Buffer.from([0x21, 0x11]), length, address, tlv]);
}
const login = name => Buffer.concat([frame(Buffer.concat([Buffer.from([0]), varint(47), str('localhost'), Buffer.from([0x63, 0xdd]), varint(2)])),
  frame(Buffer.concat([Buffer.from([0]), str(name)]))]);
function direct(from, bytes) {
  return new Promise(resolve => {
    const s = net.connect({ host: '127.0.0.1', port: BACKEND, localAddress: from });
    let got = 0;
    s.on('data', d => { got += d.length; });
    s.on('error', () => {});
    s.on('connect', () => s.write(bytes));
    setTimeout(() => { s.destroy(); resolve(got > 0); }, 1500);
  });
}

if (SECRET) {
  T('L01', 'origin lock: player joins through the gate with the real address', async () => {
    const o = size();
    const b = await bot('Gecen', '127.50.90.9');
    const joined = !!alive(b);
    await sleep(500);
    const real = /Gecen\[\/127\.50\.90\.9:\d+\] logged in/.test(readFrom(o));
    quit(b);
    record('L01', 'origin lock: player joins through the gate with the real address', joined && real, `joined=${joined} realAddress=${real}`);
  });
  T('L02', 'origin lock: direct, unsigned and forged connections are refused', async () => {
    const plain = await direct('127.50.90.20', login('Dogrudan'));
    const unsigned = await direct('127.50.90.21', Buffer.concat([proxyHeader(''), login('Imzasiz')]));
    const forged = await direct('127.50.90.22', Buffer.concat([proxyHeader('wrong-secret'), login('Sahte')]));
    record('L02', 'origin lock: direct, unsigned and forged connections are refused', !plain && !unsigned && !forged, `answered: direct=${plain} unsigned=${unsigned} forged=${forged}`);
  });
  T('L03', 'origin lock: a valid signature works once; replay and stale are refused', async () => {
    const header = proxyHeader(SECRET);
    const first = await direct('127.50.90.23', Buffer.concat([header, login('Imzali')]));
    const replay = await direct('127.50.90.24', Buffer.concat([header, login('Tekrar')]));
    const stale = await direct('127.50.90.25', Buffer.concat([proxyHeader(SECRET, Date.now() - 60000), login('Eski')]));
    record('L03', 'origin lock: a valid signature works once; replay and stale are refused', first && !replay && !stale, `answered: valid=${first} replay=${replay} stale=${stale}`);
  });
}

(async () => {
  for (const t of tests) {
    if (only.length && !only.includes(t.id)) continue;
    try { await t.fn(); } catch (e) { record(t.id, t.name, false, 'harness error: ' + e.message); }
    await sleep(1200);
  }
  const errors = (readFrom(0).match(/at net\.tcguard|Could not pass event .* to TCGuard|Error occurred while (enabling|disabling) TCGuard/g) || []).length;
  if (!only.length) record('H01', 'no TCGuard exception in the server log', errors === 0, 'errors=' + errors);
  fs.writeFileSync(`${__dirname}/results-${label}.json`, JSON.stringify(h.results, null, 1));
  console.log(`${label}: ${h.results.filter(r => r.pass).length}/${h.results.length}`);
  process.exit(0);
})();
