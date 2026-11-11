const fs = require('fs');
const net = require('net');
const mineflayer = require('mineflayer');

const LOG = process.env.TCG_LOG || (process.env.TCG_SERVER || require('os').homedir() + '/tcg-server') + '/server.out';
const FIFO = process.env.TCG_CONSOLE || '/tmp/mcin';
const HOST = '127.0.0.1', PORT = +(process.env.TCG_PORT || 25565);

const sleep = ms => new Promise(r => setTimeout(r, ms));
const size = () => fs.statSync(LOG).size;
function readFrom(off) {
  const fd = fs.openSync(LOG, 'r');
  const len = size() - off;
  if (len <= 0) { fs.closeSync(fd); return ''; }
  const b = Buffer.alloc(len); fs.readSync(fd, b, 0, len, off); fs.closeSync(fd);
  return b.toString('utf8');
}
function cmd(c) { fs.appendFileSync(FIFO, c + '\n'); }
async function waitLog(re, ms = 8000, from = 0) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    const t = readFrom(from); const m = t.match(re);
    if (m) return m;
    await sleep(150);
  }
  return null;
}

async function ask(c, re, ms = 5000) { const o = size(); cmd(c); return waitLog(re, ms, o); }
function count(re, from) { const t = readFrom(from); const m = t.match(new RegExp(re.source, 'g')); return m ? m.length : 0; }

function bot(username, ip = '127.0.0.1', extra = {}) {
  return new Promise((resolve) => {
    let done = false;
    const b = mineflayer.createBot(Object.assign({
      host: HOST, port: PORT, username, version: process.env.TCG_VERSION || '1.8.8', auth: 'offline',
      hideErrors: true, checkTimeoutInterval: 120000,
      connect: client => {
        const s = net.connect({ host: HOST, port: extra.port || PORT, localAddress: ip });
        client.setSocket(s);
      }
    }, extra));
    b.kickReason = null; b.ended = false; b.msgs = []; b.windows = [];
    b.on('message', m => { try { b.msgs.push(m.toString()); } catch (e) {} });
    b._client.on('open_window', p => b.windows.push(p));
    b.on('kicked', r => { b.kickReason = typeof r === 'string' ? r : JSON.stringify(r); });
    b.on('end', (r) => { b.ended = true; b.endReason = r; if (!done) { done = true; resolve(b); } });
    b.on('error', e => { b.err = e.message; });
    b.once('spawn', () => { if (!done) { done = true; setTimeout(() => resolve(b), 300); } });
    setTimeout(() => { if (!done) { done = true; resolve(b); } }, 15000);
  });
}
const alive = b => b && !b.ended && b.entity;
function quit(b) { try { if (b && !b.ended) b.quit(); } catch (e) {} }

function plain(reason) {
  if (reason == null) return '';
  if (typeof reason === 'string') {
    try { reason = JSON.parse(reason); } catch (e) { return reason.replace(/§./g, ''); }
    if (typeof reason === 'string') return reason.replace(/§./g, '');
  }
  const out = [];
  const walk = (v, key) => {
    if (typeof v === 'string') { if (key === 'text') out.push(v); return; }
    if (Array.isArray(v)) return v.forEach(x => walk(x, key));
    if (v && typeof v === 'object') {
      if (typeof v.type === 'string' && 'value' in v) return walk(v.value, key);
      for (const k of Object.keys(v)) walk(v[k], k);
    }
  };
  walk(reason, '');
  return out.length ? out.join('').replace(/§./g, '') : JSON.stringify(reason);
}
function kickText(b) { return (plain(b.kickReason) + ' ' + (b.endReason || '')).trim(); }
async function waitEnd(b, ms) { const e = Date.now() + ms; while (Date.now() < e && !b.ended) await sleep(100); return b.ended; }

let ipCounter = 0;
function ip() { ipCounter++; return `127.${30 + Math.floor(ipCounter / 200)}.${ipCounter % 200}.1`; }
const hasMsg = (b, re) => b.msgs.some(m => re.test(m));
const results = [];
function record(id, name, pass, detail) {
  results.push({ id, name, pass: !!pass, detail: String(detail || '') });
  console.log(`${pass ? 'PASS' : 'FAIL'} ${id} ${name} :: ${detail || ''}`);
}

module.exports = { ip, hasMsg, sleep, size, readFrom, cmd, waitLog, ask, count, bot, alive, quit, kickText, waitEnd, record, results };
