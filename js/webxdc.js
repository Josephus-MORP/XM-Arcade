/* XM Arcade — webxdc collection: builtins + .xdc/.webxdc uploads + Nostr #webxdc.
   Real sandboxed execution: apps run in sandbox="allow-scripts" iframes with a
   webxdc.js-compatible shim (sendUpdate / setUpdateListener / selfAddr…).
   Zip-based .xdc bundles are extracted in-browser (stored + deflated entries)
   and served to the frame as blob URLs — no server needed. */
import { store, uid } from "./util.js";
import { fetchDiscoveredApps, sendGroupMessage } from "./data.js";
import { blossomUpload } from "./nostr.js";
import { signEvent, Signer } from "./signer.js";
import { publish } from "./nostr.js";

/* ---------- tiny idb for app blobs ---------- */
const DB = "xm-arcade", OS = "webxdc";
function idb() {
  return new Promise((res, rej) => {
    const q = indexedDB.open(DB, 1);
    q.onupgradeneeded = () => q.result.createObjectStore(OS);
    q.onsuccess = () => res(q.result);
    q.onerror = () => rej(q.error);
  });
}
async function idbPut(k, v) { const d = await idb(); return new Promise((res, rej) => { const t = d.transaction(OS, "readwrite").objectStore(OS).put(v, k); t.onsuccess = res; t.onerror = () => rej(t.error); }); }
async function idbGet(k) { const d = await idb(); return new Promise((res, rej) => { const t = d.transaction(OS).objectStore(OS).get(k); t.onsuccess = () => res(t.result); t.onerror = () => rej(t.error); }); }
async function idbDel(k) { const d = await idb(); return new Promise((res, rej) => { const t = d.transaction(OS, "readwrite").objectStore(OS).delete(k); t.onsuccess = res; t.onerror = () => rej(t.error); }); }

/* ---------- minimal zip reader (stored + deflate) ---------- */
async function unzipAll(blob) {
  const buf = new Uint8Array(await blob.arrayBuffer());
  const dv = new DataView(buf.buffer);
  const dec = new TextDecoder();
  // find EOCD
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 66000); i--) {
    if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error("Not a zip archive.");
  const count = dv.getUint16(eocd + 10, true);
  let cd = dv.getUint32(eocd + 16, true);
  const files = {};
  for (let n = 0; n < count; n++) {
    if (dv.getUint32(cd, true) !== 0x02014b50) break;
    const method = dv.getUint16(cd + 10, true);
    const csize = dv.getUint32(cd + 20, true);
    const nameLen = dv.getUint16(cd + 28, true), extraLen = dv.getUint16(cd + 30, true), comLen = dv.getUint16(cd + 32, true);
    const name = dec.decode(buf.subarray(cd + 46, cd + 46 + nameLen));
    const lho = dv.getUint32(cd + 42, true);
    cd += 46 + nameLen + extraLen + comLen;
    if (name.endsWith("/") || !name || name.includes("..")) continue;
    const nl = dv.getUint16(lho + 26, true), el = dv.getUint16(lho + 28, true);
    const start = lho + 30 + nl + el;
    const raw = buf.subarray(start, start + csize);
    let data;
    if (method === 0) data = raw.slice();
    else if (method === 8) {
      try {
        const ds = new DecompressionStream("deflate-raw");
        const w = ds.writable.getWriter(); w.write(raw); w.close();
        data = new Uint8Array(await new Response(ds.readable).arrayBuffer());
      } catch { continue; }
    } else continue;
    files[name.replace(/^\.\//, "")] = data;
  }
  return files;
}
const mimeFor = (n) =>
  /\.html?$/.test(n) ? "text/html" : /\.js$/.test(n) ? "text/javascript" : /\.css$/.test(n) ? "text/css"
  : /\.json$/.test(n) ? "application/json" : /\.svg$/.test(n) ? "image/svg+xml" : /\.png$/.test(n) ? "image/png"
  : /\.jpe?g$/.test(n) ? "image/jpeg" : /\.gif$/.test(n) ? "image/gif" : /\.webp$/.test(n) ? "image/webp"
  : /\.mp3$/.test(n) ? "audio/mpeg" : /\.ogg$/.test(n) ? "audio/ogg" : /\.wav$/.test(n) ? "audio/wav"
  : "application/octet-stream";

/* ---------- webxdc guest shim (injected into every app frame) ---------- */
export const SHIM_SRC = `;(function(){
  var listeners=[], updates=[], serial=0, selfAddr='device-'+Math.random().toString(36).slice(2,8), selfName='you';
  try{
    var q=new URLSearchParams(location.search);
    if(q.get('selfAddr')) selfAddr=q.get('selfAddr');
    if(q.get('selfName')) selfName=q.get('selfName');
  }catch(e){}
  function flush(){ listeners.forEach(function(l){ try{ l.cb(l.last===0?updates.filter(function(u){return u.serial>l.last}):updates.filter(function(u){return u.serial>l.last})); l.last=serial; }catch(e){} }); }
  window.webxdc = {
    selfAddr: selfAddr, selfName: selfName,
    setUpdateListener: function(cb, s){ listeners.push({cb:cb,last:s||0}); flush(); return Promise.resolve(); },
    getAllUpdates: function(){ return Promise.resolve(updates.slice()); },
    sendUpdate: function(update, descr){
      serial++; var u={payload:update, summary:descr||'', serial:serial, max_serial:serial};
      updates.push(u);
      try{ parent.postMessage({__xm:'webxdc-update', update:u}, '*'); }catch(e){}
      flush(); return Promise.resolve();
    },
    sendToChat: function(msg){ try{ parent.postMessage({__xm:'webxdc-chat', text:(msg&&msg.text)||String(msg||'')}, '*'); }catch(e){} return Promise.resolve(); }
  };
  window.addEventListener('message', function(ev){
    var m=ev.data||{};
    if(m && m.__xm==='webxdc-inject' && m.update){ serial=Math.max(serial,m.update.serial||0); updates.push(m.update); flush(); }
    if(m && m.__xm==='webxdc-hello'){ if(m.selfAddr) { selfAddr=m.selfAddr; window.webxdc.selfAddr=m.selfAddr; } if(m.selfName){ selfName=m.selfName; window.webxdc.selfName=m.selfName; } }
  });
  try{ parent.postMessage({__xm:'webxdc-ready'}, '*'); }catch(e){}
})();`;

/* ---------- catalog ---------- */
const META_KEY = "xm.webxdc.meta.v1";
export const BUILTINS = [
  { id: "builtin-hello", source: "builtin", title: "Hello Arcade", desc: "The classic first webxdc — counters sync between players.", tags: ["starter"], file: "webxdc/hello/index.html", author: "XM Arcade" },
  { id: "builtin-taprace", source: "builtin", title: "Tap Race", desc: "20-second tap sprint. Feed your score to a channel.", tags: ["arcade"], file: "webxdc/taprace/index.html", author: "XM Arcade" },
];
export function localApps() { return store.get(META_KEY, []); }
function saveLocalApps(list) { store.set(META_KEY, list); }
export async function catalog({ nostr = true } = {}) {
  const list = [...BUILTINS.map((b) => ({ ...b, plays: "–", zaps: "–" }))];
  for (const l of localApps()) list.push({ ...l, source: "local" });
  if (nostr) {
    try {
      const found = await fetchDiscoveredApps(20);
      for (const f of found) list.push({ id: "nostr:" + f.id, source: "nostr", title: f.title, desc: "Shared on Nostr · " + (f.url || "").slice(0, 60), tags: f.tags || ["webxdc"], url: f.url, author: f.pubkey.slice(0, 8) + "…" });
    } catch {}
  }
  return list;
}
export async function addUpload(file, { title = "", desc = "", tags = [] } = {}) {
  if (!file) throw new Error("Choose a file first.");
  if (file.size > 20 * 1024 * 1024) throw new Error("20 MB limit for local apps.");
  const name = (file.name || "").toLowerCase();
  if (!/\.(xdc|webxdc|zip|html)$/.test(name)) throw new Error("Need a .xdc / .webxdc / .zip (or single-file .html).");
  if (!title.trim()) throw new Error("Give the app a title.");
  const all = [...BUILTINS, ...localApps()];
  if (all.some((a) => a.title.toLowerCase() === title.trim().toLowerCase())) throw new Error("An app with that title already exists.");
  const id = "local-" + uid();
  const buf = await file.arrayBuffer();
  await idbPut(id, { name: file.name, type: file.type, buf });
  // sniff a friendlier title from manifest / html
  let finalTitle = title.trim();
  try {
    if (/\.(xdc|webxdc|zip)$/.test(name)) {
      const files = await unzipAll(new Blob([buf]));
      const man = files["manifest.toml"];
      if (man) { const m = new TextDecoder().decode(man).match(/name\s*=\s*"([^"]+)"/); if (m) finalTitle = m[1]; }
      if (!files["index.html"] && !Object.keys(files).some((k) => k.endsWith(".html"))) throw new Error("Archive has no index.html.");
    }
  } catch (e) { if (/index\.html/.test(e.message)) throw e; }
  const meta = { id, title: finalTitle, desc: desc.trim(), tags: tags.map((t) => String(t).replace(/^#/, "")).filter(Boolean), fileName: file.name, size: file.size, addedAt: Date.now(), plays: "0", zaps: "0" };
  const list = localApps(); list.unshift(meta); saveLocalApps(list);
  return meta;
}
export async function removeUpload(id) { await idbDel(id); saveLocalApps(localApps().filter((a) => a.id !== id)); }

/* ---------- resolve an entry to playable frame HTML ---------- */
async function entryFiles(entry) {
  if (entry.source === "builtin") {
    // Single-file builds embed builtins here so no fetch is needed.
    const embedded = globalThis.__BUILTIN_APPS && globalThis.__BUILTIN_APPS[entry.file];
    if (embedded) return { "index.html": new TextEncoder().encode(embedded) };
    const r = await fetch(entry.file);
    if (!r.ok) throw new Error("Builtin app missing: " + entry.file);
    return { "index.html": new TextEncoder().encode(await r.text()) };
  }
  if (entry.source === "local") {
    const rec = await idbGet(entry.id);
    if (!rec) throw new Error("App file is gone from this device.");
    const blob = new Blob([rec.buf], { type: rec.type });
    if (/\.html$/.test(rec.name.toLowerCase())) return { "index.html": new Uint8Array(rec.buf) };
    return unzipAll(blob);
  }
  // nostr / url
  const r = await fetch(entry.url);
  if (!r.ok) throw new Error("Couldn't download the shared app.");
  const blob = await r.blob();
  const ct = (r.headers.get("content-type") || "").toLowerCase();
  if (ct.includes("text/html") || /\.html(\?|#|$)/.test(entry.url)) return { "index.html": new Uint8Array(await blob.arrayBuffer()) };
  return unzipAll(blob);
}
export async function buildFrameDoc(entry, { selfAddr, selfName }) {
  const files = await entryFiles(entry);
  const names = Object.keys(files);
  const idxName = files["index.html"] ? "index.html" : names.find((n) => n.toLowerCase().endsWith(".html"));
  if (!idxName) throw new Error("App has no HTML entry point.");
  // blob URLs for every asset so relative paths keep working
  const urls = {};
  for (const [n, data] of Object.entries(files)) {
    urls[n] = URL.createObjectURL(new Blob([data], { type: mimeFor(n) }));
  }
  let html = new TextDecoder().decode(files[idxName]);
  // rewrite relative asset refs to blob URLs
  const base = idxName.includes("/") ? idxName.slice(0, idxName.lastIndexOf("/") + 1) : "";
  const resolve = (p) => {
    if (/^(https?:|data:|blob:|#)/i.test(p)) return p;
    const abs = (base + p).replace(/\/+/g, "/").replace(/^\//, "");
    const key = files[abs] ? abs : files[p.replace(/^\.\//, "")] ? p.replace(/^\.\//, "") : null;
    return key ? urls[key] : p;
  };
  html = html.replace(/(src|href)=["']([^"']+)["']/gi, (m, a, p) => `${a}="${resolve(p)}"`);
  // inject shim first
  html = html.replace(/<head[^>]*>/i, (m) => `${m}<script>${SHIM_SRC}<\/script>`);
  if (!/<script>;\(function\(\)\{/.test(html)) html = `<script>${SHIM_SRC}<\/script>` + html;
  return { html, revoke: () => Object.values(urls).forEach((u) => URL.revokeObjectURL(u)) };
}

/* ---------- feed an app to a Concord channel ---------- */
export async function feedAppToChannel(entry, group, channel) {
  let appUrl = entry.url || "";
  let fileLine = entry.fileName || entry.file || "";
  if (entry.source === "local") {
    const rec = await idbGet(entry.id);
    if (!rec) throw new Error("App file is gone from this device.");
    const up = await blossomUpload(new File([rec.buf], rec.name, { type: rec.type || "application/octet-stream" }), (t) => signEvent(t));
    appUrl = up.url; fileLine = rec.name;
  } else if (entry.source === "builtin") {
    // Single-file builds have no fetchable files — use the embedded copy.
    const embedded = globalThis.__BUILTIN_APPS && globalThis.__BUILTIN_APPS[entry.file];
    const blob = embedded
      ? new Blob([embedded], { type: "text/html" })
      : await (await fetch(entry.file)).blob();
    const up = await blossomUpload(new File([blob], entry.id + ".html", { type: "text/html" }), (t) => signEvent(t));
    appUrl = up.url; fileLine = entry.id + ".html";
  }
  if (!appUrl) throw new Error("This app has no shareable file.");
  const body = `🎮 ${entry.title}\n${entry.desc || "Play it inside XM Arcade."}\n${appUrl}`;
  const tags = [["r", appUrl], ["t", "webxdc"], ...(entry.tags || []).slice(0, 4).map((t) => ["t", String(t).replace(/^#/, "")]), ["title", entry.title]];
  const note = await signEvent({ kind: 1, content: body, tags });
  const pub = await publish(note);
  // pointer inside the channel so members see a Play card
    const card = JSON.stringify({ app: "xm-arcade", type: "webxdc", title: entry.title, url: appUrl, file: fileLine, note: note.id });
  await sendGroupMessage(group, channel, `🎮 ${entry.title} — tap Play in XM Arcade\n${card}`, {});
  return { note, pub, url: appUrl };
}
export function parseAppCard(text) {
  const m = String(text || "").match(/\{[^]*"type"\s*:\s*"webxdc"[^]*\}/);
  if (!m) return null;
  try { const j = JSON.parse(m[0]); return j && j.type === "webxdc" ? j : null; } catch { return null; }
}
