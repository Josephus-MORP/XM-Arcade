/* XM Arcade — live data layer over Nostr.
   Profiles (0) · notes (1) · contacts (3) · shorts NIP-71 (21/22) ·
   Concord groups NIP-29 (39000s + kind 9, plaintext or Concord-encrypted) ·
   music (notes + file metadata carrying audio) · app discovery (#webxdc).
   Everything degrades to clearly-labeled demo content when relays are
   unreachable or return nothing. */
import { nt, req, reqFast, subscribe, publish, tagVal, tagVals, RelayState } from "./nostr.js";
import { Signer, signEvent, convKey, nip44Encrypt, nip44Decrypt } from "./signer.js";
import { store } from "./util.js";

const now = () => Math.floor(Date.now() / 1000);
const CKEY = "xm.cache.v1";
const cache = store.get(CKEY, { profiles: {}, contacts: {}, groups: [], groupKeys: {}, follows: [] });

function saveCache() { try { store.set(CKEY, { profiles: cache.profiles, contacts: cache.contacts, groups: cache.groups, groupKeys: cache.groupKeys, follows: cache.follows }); } catch {} }

/* ================= profiles ================= */
export function cachedProfile(pk) {
  return cache.profiles[pk] || { pubkey: pk, name: shortPk(pk), display_name: "", about: "", picture: "", banner: "", nip05: "", lud16: "", lud06: "" };
}
export function shortPk(pk) { return "nostr:" + String(pk || "").slice(0, 8) + "…"; }
export function displayName(p) { return p.display_name || p.name || shortPk(p.pubkey); }
export async function fetchProfiles(pubkeys) {
  const fresh = [...new Set((pubkeys || []).filter(Boolean))].filter((pk) => /^[0-9a-f]{64}$/.test(pk)).slice(0, 60);
  if (!fresh.length) return {};
  const uncached = fresh.filter((pk) => !cache.profiles[pk]);
  if (uncached.length) {
    const evs = await reqFast({ kinds: [0], authors: uncached });
    for (const e of evs) {
      try {
        const j = JSON.parse(e.content);
        if (!cache.profiles[e.pubkey] || (cache.profiles[e.pubkey]._at || 0) < e.created_at) {
          cache.profiles[e.pubkey] = {
            pubkey: e.pubkey, name: j.name || "", display_name: j.display_name || "", about: j.about || "",
            picture: j.picture || "", banner: j.banner || "", nip05: j.nip05 || "",
            lud16: j.lud16 || "", lud06: j.lud06 || "", _at: e.created_at,
          };
        }
      } catch {}
    }
    saveCache();
  }
  const out = {};
  fresh.forEach((pk) => (out[pk] = cachedProfile(pk)));
  return out;
}
export async function publishProfile(meta) {
  const e = await signEvent({ kind: 0, content: JSON.stringify(meta) });
  const r = await publish(e);
  cache.profiles[Signer.pubkey] = { ...cachedProfile(Signer.pubkey), ...meta, pubkey: Signer.pubkey, _at: now() };
  saveCache();
  return r;
}

/* ================= contacts / follows ================= */
export async function fetchContacts(pk = Signer.pubkey) {
  if (!pk) return [];
  if (cache.contacts[pk]) return cache.contacts[pk];
  const evs = await reqFast({ kinds: [3], authors: [pk], limit: 1 });
  const list = evs.length ? [...new Set(tagVals(evs[0], "p").filter((x) => /^[0-9a-f]{64}$/.test(x)))] : [];
  cache.contacts[pk] = list;
  if (pk === Signer.pubkey) { cache.follows = list; }
  saveCache();
  return list;
}
export async function setFollow(pk, follow) {
  const mine = await fetchContacts(Signer.pubkey);
  const next = follow ? [...new Set([...mine, pk])] : mine.filter((x) => x !== pk);
  const evs = await reqFast({ kinds: [3], authors: [Signer.pubkey], limit: 1 });
  const keep = evs.length ? evs[0].tags.filter((t) => t[0] !== "p") : [];
  const e = await signEvent({ kind: 3, content: evs.length ? evs[0].content : "", tags: [...keep, ...next.map((p) => ["p", p])] });
  const r = await publish(e);
  cache.contacts[Signer.pubkey] = next; cache.follows = next; saveCache();
  return r;
}

/* ================= notes ================= */
export async function publishNote(content, tags = []) {
  const e = await signEvent({ kind: 1, content, tags });
  return { event: e, res: await publish(e) };
}
export async function react(target, kind = 1) {
  const e = await signEvent({ kind: 7, content: "+", tags: [["e", target.id, "", ""], ["p", target.pubkey], ["k", String(kind)]] });
  return publish(e);
}
export async function repost(target) {
  const e = await signEvent({ kind: 6, content: JSON.stringify(target), tags: [["e", target.id, ""], ["p", target.pubkey]] });
  return publish(e);
}
export async function fetchGlobalNotes(limit = 40) {
  return reqFast({ kinds: [1], limit }, { timeout: 7000 });
}
export async function fetchAuthorNotes(pk, limit = 40) {
  return reqFast([{ kinds: [1], authors: [pk], limit }, { kinds: [6], authors: [pk], limit: 10 }], { timeout: 7000 });
}

/* ================= shorts (NIP-71) ================= */
const VID_EXT = /\.(mp4|m4v|mov|webm|ogv)(\?|#|$)/i;
export function parseShort(e) {
  let url = tagVal(e, "url") || "";
  let thumb = "", mime = "", dur = 0;
  for (const t of e.tags || []) {
    if (t[0] === "imeta") {
      for (const f of t.slice(1)) {
        const [k, ...rest] = String(f).split(" ");
        const v = rest.join(" ");
        if (k === "url" && !url) url = v;
        if (k === "image" && !thumb) thumb = v;
        if (k === "m" && !mime) mime = v;
        if (k === "duration") dur = parseFloat(v) || 0;
      }
    }
    if (t[0] === "r" && !url && VID_EXT.test(t[1] || "")) url = t[1];
    if (t[0] === "thumb" && !thumb) thumb = t[1];
  }
  if (!url) { const m = String(e.content || "").match(/https?:\/\/[^\s]+\.(?:mp4|m4v|mov|webm|ogv)(?:[?#][^\s]*)?/i); if (m) url = m[0]; }
  const tags = tagVals(e, "t").map((t) => t.toLowerCase());
  const title = tagVal(e, "title") || String(e.content || "").split("\n")[0].slice(0, 90);
  return { id: e.id, pubkey: e.pubkey, created_at: e.created_at, url, thumb, mime, dur, tags, title, raw: e };
}
export async function fetchShorts({ tags = [], authors = [], limit = 30 } = {}) {
  const f = { kinds: [21, 22], limit };
  if (tags.length) f["#t"] = tags;
  if (authors.length) f.authors = authors;
  const evs = await reqFast(f, { timeout: 8000 });
  return evs.map(parseShort).filter((s) => s.url);
}
export async function publishShort({ url, title, tags = [], thumb = "", mime = "", sha256 = "", duration = 0 }) {
  const t = [["url", url], ["title", title || "XM Arcade short"], ...tags.map((x) => ["t", String(x).replace(/^#/, "").toLowerCase()])];
  if (thumb || mime || sha256) {
    const im = ["imeta", `url ${url}`];
    if (mime) im.push(`m ${mime}`);
    if (thumb) im.push(`image ${thumb}`);
    if (sha256) im.push(`x ${sha256}`);
    if (duration) im.push(`duration ${duration}`);
    t.push(im);
  }
  const e = await signEvent({ kind: 22, content: title || "", tags: t });
  return { event: e, res: await publish(e) };
}

/* ================= Concord groups (NIP-29 + Concord encryption) ================= */
export function groupRelays() { return RelayState.groupRelays.length ? RelayState.groupRelays : RelayState.relays; }
export async function fetchGroups() {
  const evs = await reqFast({ kinds: [39000], limit: 100 }, { relays: groupRelays(), timeout: 8000 });
  const groups = [];
  for (const e of evs) {
    const id = tagVal(e, "d");
    if (!id) continue;
    let meta = {};
    try { meta = JSON.parse(e.content || "{}"); } catch {}
    groups.push({
      id: e.pubkey.slice(0, 8) + "/" + id, relay: "", host: e.pubkey, d: id,
      name: meta.name || id, about: meta.about || "", picture: meta.picture || "",
      relays: groupRelays(), _at: e.created_at,
    });
  }
  if (groups.length) { cache.groups = groups.slice(0, 40); saveCache(); }
  return groups.length ? groups : cache.groups;
}
/* Channels inside a group are a Concord convention: kind 9 messages carry an
   "h" tag of "<group-d>" plus a "#c" channel tag. Public channels are
   plaintext; 🔒 channels carry ["concord","1"] and NIP-44 ciphertext. */
export const CONCORD_TAG = "concord1:";
export function groupChannelId(group, channel) { return `${group.host}:${group.d}:${channel}`; }
export async function fetchChannelMessages(group, channel, limit = 60) {
  const h = group.d;
  const evs = await reqFast(
    { kinds: [9], "#h": [h], limit: Math.min(100, limit * 2) },
    { relays: group.relays?.length ? group.relays : groupRelays(), timeout: 8000 }
  );
  return evs
    .filter((e) => (tagVal(e, "c") || "general") === channel || (!tagVal(e, "c") && channel === "general"))
    .slice(0, limit)
    .reverse();
}
export function isConcordEncrypted(e) { return (e.tags || []).some((t) => t[0] === CONCORD_TAG.slice(0, -1)) || String(e.content || "").startsWith(CONCORD_TAG); }
export function getGroupKey(id) { return cache.groupKeys[id] || ""; }
export function setGroupKey(id, keyHex) { cache.groupKeys[id] = keyHex; saveCache(); }
export function newGroupKey() { const b = nt.generateSecretKey(); return [...b].map((x) => x.toString(16).padStart(2, "0")).join(""); }
export async function decryptConcord(e, id) {
  const k = getGroupKey(id);
  if (!k) return null;
  try {
    const ck = Uint8Array.from(k.match(/../g).map((x) => parseInt(x, 16)));
    return nip44Decrypt(String(e.content).slice(CONCORD_TAG.length), ck);
  } catch { return null; }
}
export async function sendGroupMessage(group, channel, text, { encrypted = false } = {}) {
  const id = groupChannelId(group, channel);
  let content = text, extra = [];
  if (encrypted) {
    let k = getGroupKey(id);
    if (!k) { k = newGroupKey(); setGroupKey(id, k); }
    const ck = Uint8Array.from(k.match(/../g).map((x) => parseInt(x, 16)));
    content = CONCORD_TAG + nip44Encrypt(text, ck);
    extra = [["concord", "1"]];
  }
  const e = await signEvent({ kind: 9, content, tags: [["h", group.d], ["c", channel], ...extra] });
  return { event: e, res: await publish(e, group.relays?.length ? group.relays : groupRelays()) };
}
/* Share a channel key with a member via NIP-59 gift wrap (needs local key to
   wrap; remote signers can copy/paste the key instead). */
export async function shareGroupKey(group, channel, memberPk) {
  const id = groupChannelId(group, channel);
  const key = getGroupKey(id);
  if (!key) throw new Error("No channel key yet — send one locked message first.");
  if (Signer.method !== "local") throw new Error("Automatic sharing needs a device key. Copy the key and send it manually.");
  const rumor = {
    kind: 1, created_at: now(), pubkey: Signer.pubkey, tags: [["p", memberPk]],
    content: JSON.stringify({ app: "xm-arcade", type: "concord-key", channel: id, key }),
  };
  const wrap = await nt.nip59.wrapEvent(rumor, Signer.localSk, memberPk);
  return publish(wrap);
}
export async function collectKeyShares() {
  if (Signer.method !== "local" || !Signer.pubkey) return 0;
  const wraps = await reqFast({ kinds: [1059], "#p": [Signer.pubkey], limit: 30, since: now() - 30 * 86400 }, { timeout: 7000 });
  let n = 0;
  for (const w of wraps) {
    try {
      const rumor = await nt.nip59.unwrapEvent(w, Signer.localSk);
      const j = JSON.parse(rumor.content || "{}");
      if (j && j.app === "xm-arcade" && j.type === "concord-key" && j.channel && /^[0-9a-f]{64}$/.test(j.key)) {
        if (!cache.groupKeys[j.channel]) { cache.groupKeys[j.channel] = j.key; n++; }
      }
    } catch {}
  }
  if (n) saveCache();
  return n;
}
export function subscribeChannel(group, channel, onEvent) {
  return subscribe([{ kinds: [9], "#h": [group.d], since: now() - 5 }], (e) => {
    if ((tagVal(e, "c") || "general") === channel) onEvent(e);
  }, { relays: group.relays?.length ? group.relays : groupRelays() });
}

/* ================= music ================= */
const AUD_EXT = /\.(mp3|m4a|ogg|oga|opus|wav|flac)(\?|#|$)/i;
export function parseTrack(e) {
  let url = "";
  const m = String(e.content || "").match(/https?:\/\/[^\s)]+/g) || [];
  for (const u of m) if (AUD_EXT.test(u)) { url = u; break; }
  if (!url) for (const t of e.tags || []) if ((t[0] === "r" || t[0] === "url") && AUD_EXT.test(t[1] || "")) { url = t[1]; break; }
  if (!url) return null;
  const tags = tagVals(e, "t").map((t) => t.toLowerCase());
  const title = tagVal(e, "title") || String(e.content || "").split("\n")[0].slice(0, 80) || "Untitled track";
  return { id: e.id, pubkey: e.pubkey, created_at: e.created_at, url, title, tags, raw: e };
}
export async function fetchMusic({ tag = "", limit = 40 } = {}) {
  const filters = [{ kinds: [1], "#t": [tag || "music"], limit }];
  if (!tag) filters.push({ kinds: [1063], limit: 20 });
  const evs = await reqFast(filters, { timeout: 8000 });
  const out = [];
  for (const e of evs) {
    if (e.kind === 1063) {
      const url = tagVal(e, "url");
      const mime = tagVal(e, "m");
      if (url && /audio/i.test(mime || "") || (url && AUD_EXT.test(url))) {
        out.push({ id: e.id, pubkey: e.pubkey, created_at: e.created_at, url, title: tagVal(e, "name") || tagVal(e, "title") || "Untitled track", tags: tagVals(e, "t").map((t) => t.toLowerCase()), raw: e });
      }
      continue;
    }
    const t = parseTrack(e);
    if (t) out.push(t);
  }
  return out.slice(0, limit);
}
export async function publishTrack({ url, title, tags = [], mime = "" }) {
  const e = await signEvent({ kind: 1, content: `${title}\n${url}`, tags: [["r", url], ["title", title], ["m", mime || "audio/mpeg"], ...tags.map((t) => ["t", String(t).replace(/^#/, "").toLowerCase()])] });
  return { event: e, res: await publish(e) };
}

/* ================= app discovery (#webxdc notes) ================= */
export async function fetchDiscoveredApps(limit = 30) {
  const evs = await reqFast([{ kinds: [1], "#t": ["webxdc"], limit }, { kinds: [1063], "#t": ["webxdc"], limit: 20 }], { timeout: 7000 });
  const out = [];
  for (const e of evs) {
    const urls = [...String(e.content || "").matchAll(/https?:\/\/[^\s)]+/g)].map((m) => m[0]);
    const file = urls.find((u) => /\.(xdc|webxdc)(\?|#|$)/i.test(u)) || (e.kind === 1063 ? tagVal(e, "url") : "");
    if (!file) continue;
    out.push({ id: e.id, pubkey: e.pubkey, created_at: e.created_at, url: file, title: tagVal(e, "title") || String(e.content || "").split("\n")[0].slice(0, 60) || "Shared app", tags: tagVals(e, "t") });
  }
  return out;
}

/* ================= demo fallback (clearly labeled, offline-safe) ================= */
export const DEMO = {
  shorts: [
    { id: "demo1", pubkey: "demo-noor", created_at: now() - 600, url: "", thumb: "", tags: ["relayweek", "video"], title: "Relay week, in motion", author: "Noor Haddad", demo: true },
    { id: "demo2", pubkey: "demo-mira", created_at: now() - 3600, url: "", thumb: "", tags: ["concord", "howto"], title: "Building together", author: "Mira Kestrel", demo: true },
    { id: "demo3", pubkey: "demo-tobias", created_at: now() - 7200, url: "", thumb: "", tags: ["film", "sunrise"], title: "Morning light", author: "Tobias Lund", demo: true },
  ],
  tracks: [
    { id: "dt1", pubkey: "demo", created_at: now() - 300, url: "", title: "Dawn Chorus — Kite Theory", tags: ["indie", "electronic"], demo: true },
    { id: "dt2", pubkey: "demo", created_at: now() - 900, url: "", title: "Chiptune Sunrise — 8bit Garden", tags: ["chiptune"], demo: true },
    { id: "dt3", pubkey: "demo", created_at: now() - 1800, url: "", title: "Relay Hum — Nostrilia", tags: ["ambient"], demo: true },
  ],
  groups: [
    { id: "demo/harbor", host: "demo", d: "harbor", name: "Harbor Crew", about: "Demo group — connect a group relay for live Concord groups.", picture: "", relays: [], demo: true, channels: [{ name: "general", topic: "everything and anything" }, { name: "arcade-highscores", topic: "share your best runs here" }, { name: "dev-talk", topic: "building on nostr" }] },
  ],
  notes: [
    { id: "dn1", pubkey: "demo-noor", created_at: now() - 120, content: "Welcome to XM Arcade — connect to relays to see the live network. Until then, this demo note holds the fort. #relayweek", demo: true },
  ],
};
export const DemoStore = {
  messages: store.get("xm.demo.msgs.v1", {}),
  save() { store.set("xm.demo.msgs.v1", this.messages); },
  for(id) { return this.messages[id] || [{ who: "Mira Kestrel", text: "Demo channel — connect a group relay to go live.", t: "12:02" }]; },
  push(id, who, text) { const l = this.for(id); l.push({ who, text, t: new Date().toTimeString().slice(0, 5) }); this.messages[id] = l.slice(-100); this.save(); },
};
