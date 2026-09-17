/* XM Arcade — Nostr relay layer (vendored nostr-tools, offline-capable).
   Wraps SimplePool with relay health tracking + NIP-19 helpers. */
import * as tools from "./vendor/nostr-tools.mjs";
import { SimplePool } from "./vendor/nostr-pool.mjs";
import { store, uid } from "./util.js";

export const nt = tools;
export const DEFAULT_RELAYS = [
  "wss://nos.lol",
  "wss://relay.primal.net",
  "wss://relay.ditto.pub",
];
export const GROUP_RELAYS_DEFAULT = ["wss://groups.fiatjaf.com"];
export const SEARCH_RELAYS_DEFAULT = ["wss://relay.nostr.band"];

const SKEY = "xm.relays.v1";
// One-time migration: relay.damus.io is defunct — swap it for the Ditto
// relay in stored lists. User-added relays are kept; dupes removed.
const DEAD_RELAYS = { "wss://relay.damus.io": "wss://relay.ditto.pub" };
function loadRelays() {
  const s = store.get(SKEY, null);
  if (!s) return [...DEFAULT_RELAYS];
  const m = [];
  for (const u of s) {
    const v = DEAD_RELAYS[u] || u;
    if (v && !m.includes(v)) m.push(v);
  }
  const out = m.length ? m : [...DEFAULT_RELAYS];
  if (out.join("|") !== s.join("|")) store.set(SKEY, out);
  return out;
}
export const RelayState = {
  relays: loadRelays(),
  groupRelays: store.get("xm.grouprelays.v1", null) || [...GROUP_RELAYS_DEFAULT],
  status: new Map(), // url -> 'live' | 'connecting' | 'down'
  pool: null,
  listeners: new Set(),
};
export function saveRelays() { store.set(SKEY, RelayState.relays); store.set("xm.grouprelays.v1", RelayState.groupRelays); }
export function onRelayStatus(fn) { RelayState.listeners.add(fn); return () => RelayState.listeners.delete(fn); }
function emit() { RelayState.listeners.forEach((fn) => { try { fn(); } catch {} }); }
export function relayStatus(url) { return RelayState.status.get(url) || "connecting"; }
export function liveRelayCount() { let n = 0; RelayState.relays.forEach((u) => { if (RelayState.status.get(u) === "live") n++; }); return n; }

export function pool() {
  if (!RelayState.pool) {
    RelayState.pool = new SimplePool();
    // Probe each relay in background so the UI can show health dots.
    RelayState.relays.forEach((url) => probe(url));
    RelayState.groupRelays.forEach((url) => probe(url));
  }
  return RelayState.pool;
}
async function probe(url) {
  RelayState.status.set(url, "connecting"); emit();
  try {
    await pool().ensureRelay(url);
    RelayState.status.set(url, "live");
  } catch { RelayState.status.set(url, "down"); }
  emit();
}
export function refreshProbes() { [...RelayState.relays, ...RelayState.groupRelays].forEach((u) => probe(u)); }

/* Query with EOSE semantics. Returns deduped, verified events, newest first. */
export async function req(filters, { relays = RelayState.relays, timeout = 8000 } = {}) {
  const list = Array.isArray(filters) ? filters : [filters];
  if (!relays.length) return [];
  try {
    const all = [];
    for (const f of list) {
      const evs = await pool().querySync(relays, f);
      all.push(...evs);
    }
    const seen = new Map();
    for (const e of all) {
      if (e && e.id && !seen.has(e.id)) {
        try { if (tools.verifyEvent(e)) seen.set(e.id, e); } catch {}
      }
    }
    return [...seen.values()].sort((a, b) => b.created_at - a.created_at);
  } catch { return []; }
}
/* Same as req but with an explicit timeout race (for slow relays). */
export async function reqFast(filters, opts = {}) {
  const { timeout = 6000 } = opts;
  return Promise.race([
    req(filters, opts),
    new Promise((res) => setTimeout(() => res([]), timeout)),
  ]);
}
export function subscribe(filters, onEvent, { relays = RelayState.relays } = {}) {
  const list = Array.isArray(filters) ? filters : [filters];
  let alive = true;
  const sub = pool().subscribeMany(relays, list, {
    onevent(e) { if (alive && e && tools.verifyEvent(e)) { try { onEvent(e); } catch {} } },
    oneose() {},
  });
  return () => { alive = false; try { sub.close(); } catch {} };
}
export async function publish(event, relays = RelayState.relays) {
  // NOTE: SimplePool.publish returns one promise PER relay (an array).
  const list = relays && relays.length ? relays : RelayState.relays;
  let outcomes = [];
  try {
    const arr = pool().publish(list, event);
    const raced = (Array.isArray(arr) ? arr : [arr]).map((p) =>
      Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error("timeout")), 8000))])
    );
    outcomes = await Promise.allSettled(raced);
  } catch { outcomes = list.map(() => ({ status: "rejected" })); }
  const oks = outcomes.filter((r) => r.status === "fulfilled").length;
  return { ok: oks > 0, oks, total: list.length };
}

/* ---- NIP-19 helpers ---- */
export function toNpub(hex) { try { return tools.nip19.npubEncode(hex); } catch { return ""; } }
export function toNsec(hexBytes) { try { return tools.nip19.nsecEncode(hexBytes); } catch { return ""; } }
export function decodeNip19(s) {
  try { return tools.nip19.decode(String(s || "").trim()); } catch { return null; }
}
export function parseKeyInput(s) {
  s = String(s || "").trim();
  if (!s) return null;
  if (s.startsWith("nsec1")) { const d = decodeNip19(s); if (d && d.type === "nsec") return { sk: d.data }; }
  if (/^[0-9a-f]{64}$/i.test(s)) return { sk: Uint8Array.from(s.toLowerCase().match(/../g).map((b) => parseInt(b, 16))) };
  return null;
}
export function tagVal(e, name) { const t = (e.tags || []).find((t) => t[0] === name); return t ? t[1] : ""; }
export function tagVals(e, name) { return (e.tags || []).filter((t) => t[0] === name).map((t) => t[1]); }

/* ---- Blossom (BUD-01/02) media upload with Nostr auth (kind 24242) ---- */
export function blossomServers() { return store.get("xm.blossom.v1", ["https://blossom.primal.net", "https://blossom.ditto.pub"]); }
export function setBlossomServers(list) { store.set("xm.blossom.v1", list); }
export async function blossomUpload(file, signTemplate) {
  const servers = blossomServers();
  const buf = new Uint8Array(await file.arrayBuffer());
  const digest = await crypto.subtle.digest("SHA-256", buf);
  const sha = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  const auth = await signTemplate({ kind: 24242, created_at: Math.floor(Date.now() / 1000), tags: [["t", "upload"], ["x", sha], ["expiration", String(Math.floor(Date.now() / 1000) + 300)]], content: "Upload " + (file.name || "file") });
  const payload = new Blob([buf], { type: file.type || "application/octet-stream" });
  let lastErr = null;
  for (const base of servers) {
    try {
      const r = await fetch(base.replace(/\/$/, "") + "/upload", {
        method: "PUT", headers: { Authorization: "Nostr " + btoa(JSON.stringify(auth)), "Content-Type": payload.type }, body: payload,
      });
      if (!r.ok) throw new Error("HTTP " + r.status);
      const j = await r.json();
      const url = j.url || (base.replace(/\/$/, "") + "/" + sha);
      return { url, sha256: sha, mime: file.type, size: file.size, name: file.name };
    } catch (e) { lastErr = e; }
  }
  throw lastErr || new Error("upload failed");
}
export { uid };
