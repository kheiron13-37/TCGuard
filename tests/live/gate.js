process.env.TCG_PORT = process.env.TCG_PORT || '25566';
const h = require('./h');
const fs = require('fs');
const net = require('net');
const mc = require('minecraft-protocol');
const { sleep, bot, alive, quit, record, ask, size, readFrom } = h;

const crypto = require('crypto');
const GATE = +process.env.TCG_PORT;
const SECRET = process.env.TCG_SECRET || '';
const GATE_LOG = process.env.TCG_GATE_LOG || '/tmp/gate.out';
const label = process.argv[2] || 'gate';
const only = process.argv.slice(3);
const tests = [];
const T = (id, name, fn) => tests.push({ id, name, fn });

let n = 0;
const addr = () => { n++; return `127.40.${Math.floor(n / 250)}.${n % 250 + 1}`; };
const varint = v => { const o = []; do { let b = v & 0x7f; v >>>= 7; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const frame = p => Buffer.concat([varint(p.length), p]);
const str = s => Buffer.concat([varint(Buffer.byteLength(s)), Buffer.from(s)]);
const handshake = (next, host = 'localhost') => frame(Buffer.concat([Buffer.from([0]), varint(47), str(host), Buffer.from([0x63, 0xdd]), varint(next)]));

function raw(localAddress, port = GATE) {
  return new Promise(resolve => {
    const s = net.connect({ host: '127.0.0.1', port, localAddress });
    s.data = Buffer.alloc(0); s.closedAt = 0; s.openedAt = Date.now();
    s.on('data', d => { s.data = Buffer.concat([s.data, d]); });
    s.on('close', () => { s.closedAt = Date.now(); });
    s.on('error', () => {});
    s.on('connect', () => resolve(s));
    setTimeout(() => resolve(s), 3000);
  });
}
const closed = async (s, ms) => { const end = Date.now() + ms; while (!s.closedAt && Date.now() < end) await sleep(50); return !!s.closedAt; };
const gateLog = () => fs.existsSync(GATE_LOG) ? fs.readFileSync(GATE_LOG, 'utf8') : '';
async function report(from) {
  await sleep(10500);
  return gateLog().slice(from);
}

T('G01', 'player joins through the gate; server sees the real address', async () => {
  const o = size();
  const b = await bot('Gecen', '127.30.9.9');
  if (!alive(b)) return record('G01', 'join through gate', false, 'join failed ' + h.kickText(b));
  b.chat('kapidan selam'); await sleep(1200);
  const log = readFrom(o);
  const real = /Gecen\[\/127\.30\.9\.9:\d+\] logged in/.test(log), chat = /Gecen: kapidan selam/.test(log);
  quit(b);
  record('G01', 'player joins through the gate; server sees the real address', real && chat, `realAddress=${real} chat=${chat}`);
});

T('G02', '600 status pings: all answered, backend asked only a few times', async () => {
  const from = gateLog().length;
  const started = Date.now();
  let ok = 0;
  for (let batch = 0; batch < 6; batch++) {
    const r = await Promise.all(Array.from({ length: 100 }, () =>
      mc.ping({ host: '127.0.0.1', port: GATE, version: '1.8.8', closeTimeout: 4000, connect: c => c.setSocket(net.connect({ host: '127.0.0.1', port: GATE, localAddress: addr() })) })
        .then(p => p && p.description ? 1 : 0).catch(() => 0)));
    ok += r.reduce((a, b) => a + b, 0);
  }
  const seconds = (Date.now() - started) / 1000;
  const log = await report(from);
  const backend = [...log.matchAll(/\(backend (\d+)\)/g)].reduce((a, m) => a + +m[1], 0);
  record('G02', '600 status pings: all answered, backend asked only a few times', ok === 600 && backend <= Math.ceil(seconds / 3) + 2,
    `answered=${ok}/600 in ${seconds.toFixed(1)}s, backend status requests=${backend}`);
});

T('G03', '250 silent sockets are dropped after the handshake timeout; players still get in', async () => {
  const socks = [];
  for (let i = 0; i < 50; i++) { const a = addr(); for (let j = 0; j < 5; j++) socks.push(raw(a)); }
  const all = await Promise.all(socks);
  await sleep(500);
  const b = await bot('Sabirli', '127.30.9.10');
  const joined = !!alive(b);
  quit(b);
  await sleep(6000);
  const dropped = all.filter(s => s.closedAt).length;
  all.forEach(s => s.destroy());
  record('G03', '250 silent sockets are dropped after the handshake timeout; players still get in', joined && dropped === 250, `joinedDuringAttack=${joined} dropped=${dropped}/250`);
});

T('G04', 'one address opening 30 connections at once: the extra ones are refused', async () => {
  const a = addr();
  const socks = await Promise.all(Array.from({ length: 30 }, () => raw(a)));
  await sleep(800);
  const refused = socks.filter(s => s.closedAt).length;
  socks.forEach(s => s.destroy());
  record('G04', 'one address opening 30 connections at once: the extra ones are refused', refused >= 25, `refused=${refused}/30 (5 may stay open)`);
});

T('G05', 'malformed handshakes never reach the server', async () => {
  const o = size();
  const cases = {
    'next state 5': handshake(5),
    'host of 1000 bytes': handshake(2, 'h'.repeat(1000)),
    'frame of 1 MB': Buffer.concat([varint(1 << 20), Buffer.alloc(1 << 20)]),
    'legacy ping': Buffer.from([0xfe, 0x01, 0xfa]),
    'garbage': Buffer.from('GET / HTTP/1.1\r\nHost: x\r\n\r\n')
  };
  const failed = [];
  for (const [name, payload] of Object.entries(cases)) {
    const s = await raw(addr());
    s.write(payload);
    if (!(await closed(s, 6000))) failed.push(name);
    s.destroy();
  }
  await sleep(500);
  const reached = /lost connection|Bad packet|Disconnecting/.test(readFrom(o));
  record('G05', 'malformed handshakes never reach the server', failed.length === 0 && !reached, `stillOpen=[${failed}] reachedServer=${reached}`);
});

T('G06', 'invalid player name is refused at the gate', async () => {
  const o = size();
  const s = await raw(addr());
  s.write(Buffer.concat([handshake(2), frame(Buffer.concat([Buffer.from([0]), str('kotu isim!')]))]));
  const c = await closed(s, 1500);
  s.destroy();
  const reached = /kotu isim/.test(readFrom(o));
  record('G06', 'invalid player name is refused at the gate', c && !reached, `closed=${c} reachedServer=${reached}`);
});

T('G07', 'a 2 MB frame streamed after login is cut by the gate\'s upload budget', async () => {
  const from = gateLog().length;
  const b = await bot('Yukleyici', '127.30.9.11');
  if (!alive(b)) return record('G07', 'upload', false, 'join failed');
  b._client.socket.write(varint(2000000));
  const chunk = Buffer.alloc(64 << 10, 0x41);
  for (let i = 0; i < 30 && !b.ended; i++) b._client.socket.write(chunk);
  await h.waitEnd(b, 5000);
  const log = await report(from);
  record('G07', 'a 2 MB frame streamed after login is cut by the gate\'s upload budget', b.ended && /upload limit \d+/.test(log), `disconnected=${b.ended} gateCounted=${/upload limit/.test(log)}`);
  quit(b);
});

T('G08', 'logins above logins_per_second get "busy", the server keeps up', async () => {
  const names = Array.from({ length: 40 }, (_, i) => 'Sel' + i);
  const bots = await Promise.all(names.map((u, i) => bot(u, `127.30.10.${i + 1}`)));
  const busy = bots.filter(b => /busy/.test(h.kickText(b))).length, inside = bots.filter(alive).length;

  const full = bots.filter(b => /server is full/i.test(h.kickText(b))).length;
  bots.forEach(quit);
  await sleep(1500);
  const t = await ask('probe tps', /tps=([\d.]+)/);
  record('G08', 'logins above logins_per_second get "busy", the server keeps up', busy >= 15 && inside + busy + full === 40 && t && +t[1] >= 19,
    `busy=${busy} joined=${inside} serverFull=${full} tps=${t && t[1]}`);
});

const login = name => Buffer.concat([handshake(2), frame(Buffer.concat([Buffer.from([0]), str(name)]))]);

function proxyHeader(secret, stamp = Date.now()) {
  const address = Buffer.from([198, 51, 100, 9, 127, 0, 0, 1, 0x9c, 0x40, 0x63, 0xdd]);
  const time = Buffer.alloc(8); time.writeBigUInt64BE(BigInt(stamp));
  const tlv = secret ? Buffer.concat([Buffer.from([0xe0, 0, 40]), time, crypto.createHmac('sha256', secret).update(address).update(time).digest()]) : Buffer.alloc(0);
  const length = Buffer.alloc(2); length.writeUInt16BE(address.length + tlv.length);
  return Buffer.concat([Buffer.from('\r\n\r\n\0\r\nQUIT\n', 'latin1'), Buffer.from([0x21, 0x11]), length, address, tlv]);
}

async function direct(from, bytes) {
  const s = await raw(from, 25565);
  s.write(bytes);
  await closed(s, 1500);
  const answered = s.data.length > 0;
  s.destroy();
  return answered;
}

if (!SECRET) T('G09', 'a trusted address without a PROXY header is refused by the server', async () => {
  const s = await raw('127.0.0.1', 25565);
  s.write(login('Sahte'));
  const c = await closed(s, 2000);
  s.destroy();
  const direct = await bot('Dogrudan', '127.30.9.12', { port: 25565 });
  const ok = !!alive(direct);
  quit(direct);
  record('G09', 'a trusted address without a PROXY header is refused by the server', c && ok, `spoofClosed=${c} untrustedDirectJoin=${ok}`);
});

if (SECRET) {
  T('L01', 'origin lock: a direct connection without the gate is refused, from any address', async () => {
    const plain = await direct('127.30.9.20', login('Dogrudan'));
    const local = await direct('127.0.0.1', login('Yerel'));
    record('L01', 'origin lock: a direct connection without the gate is refused, from any address', !plain && !local, `answered: untrusted=${plain} trusted=${local}`);
  });

  T('L02', 'origin lock: unsigned or wrongly signed headers are refused', async () => {
    const unsigned = await direct('127.30.9.21', Buffer.concat([proxyHeader(''), login('Imzasiz')]));
    const forged = await direct('127.30.9.22', Buffer.concat([proxyHeader('wrong-secret'), login('Sahte')]));
    record('L02', 'origin lock: unsigned or wrongly signed headers are refused', !unsigned && !forged, `answered: unsigned=${unsigned} forged=${forged}`);
  });

  T('L03', 'origin lock: a valid signature works once; a replay and a stale one are refused', async () => {
    const o = size();
    const header = proxyHeader(SECRET);
    const first = await direct('127.30.9.23', Buffer.concat([header, login('Imzali')]));
    const replay = await direct('127.30.9.24', Buffer.concat([header, login('Tekrar')]));
    const stale = await direct('127.30.9.25', Buffer.concat([proxyHeader(SECRET, Date.now() - 60000), login('Eski')]));
    await sleep(500);
    const address = /Imzali\[\/198\.51\.100\.9:40000\]|198\.51\.100\.9/.test(readFrom(o));
    record('L03', 'origin lock: a valid signature works once; a replay and a stale one are refused', first && !replay && !stale && address,
      `answered: valid=${first} replay=${replay} stale=${stale} signedAddressSeen=${address}`);
  });
}

(async () => {
  for (const t of tests) {
    if (only.length && !only.includes(t.id)) continue;
    try { await t.fn(); } catch (e) { record(t.id, t.name, false, 'harness error: ' + e.message); }
    await sleep(1500);
  }
  fs.writeFileSync(`${__dirname}/results-${label}.json`, JSON.stringify(h.results, null, 1));
  console.log(`${label}: ${h.results.filter(r => r.pass).length}/${h.results.length}`);
  process.exit(0);
})();
