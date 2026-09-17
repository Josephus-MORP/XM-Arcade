/* XM Arcade — wallets.
   SATS: real funds via NWC (NIP-47) to the user's own Lightning node/wallet
   service (Alby Hub, Mutiny+, NWC providers…). No custodial server here.
   XMR: real funds via the user's own monero-wallet-rpc. This page never asks
   for Monero seeds — creation/restore calls go to *your* RPC endpoint.
   Both halves work offline in clearly-labeled demo/tracker mode. */
import { nt, pool, RelayState } from "./nostr.js";
import { Signer, signEvent } from "./signer.js";
import { store } from "./util.js";

const now = () => Math.floor(Date.now() / 1000);
const hx = (h) => Uint8Array.from(String(h).match(/../g).map((x) => parseInt(x, 16)));
const hex = (b) => [...b].map((x) => x.toString(16).padStart(2, "0")).join("");

/* ============ tiny bech32 (for lud06 / lnurl decode) ============ */
const CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
function bech32Decode(str) {
  str = String(str || "").trim().toLowerCase();
  const pos = str.lastIndexOf("1");
  if (pos < 1 || pos + 7 > str.length) throw new Error("bad bech32");
  const hrp = str.slice(0, pos), data = str.slice(pos + 1);
  const vals = [...data].map((c) => { const i = CHARSET.indexOf(c); if (i < 0) throw new Error("bad bech32 char"); return i; });
  constCHK: {
    const expand = [...hrp].map((c) => c.charCodeAt(0) >> 5).concat([0], [...hrp].map((c) => c.charCodeAt(0) & 31));
    const GEN = [0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3];
    let chk = 1;
    for (const v of expand.concat(vals)) { const b = chk >> 25; chk = ((chk & 0x1ffffff) << 5) ^ v; for (let i = 0; i < 5; i++) if ((b >> i) & 1) chk ^= GEN[i]; }
    if (chk !== 1) throw new Error("bad bech32 checksum");
  }
  return { hrp, words: vals.slice(0, -6) };
}
function convertBits(data, from, to, pad) {
  let acc = 0, bits = 0; const out = [];
  for (const v of data) { acc = (acc << from) | v; bits += from; while (bits >= to) { bits -= to; out.push((acc >> bits) & ((1 << to) - 1)); } }
  if (pad && bits) out.push((acc << (to - bits)) & ((1 << to) - 1));
  return out;
}
export function lnurlDecode(lnurl) {
  const { words } = bech32Decode(lnurl);
  return new TextDecoder().decode(new Uint8Array(convertBits(words, 5, 8, false)));
}
/* BOLT11 amount from HRP only (wallet validates the rest). Returns msats. */
export function bolt11Msats(inv) {
  const m = String(inv || "").toLowerCase().match(/^ln([a-z]+?)(\d+)([pnum]?)1/);
  if (!m) return 0;
  const n = parseInt(m[2], 10), mult = { p: 1e-1, n: 100, u: 1e5, m: 1e8, "": 1e11 }[m[3] || ""];
  return Math.round((n * mult) / 10); // BTC → msat: n*mult pico-BTC → msat
}
export async function ludToPayUrl(lud16, lud06) {
  if (lud16 && lud16.includes("@")) { const [u, d] = lud16.split("@"); return `https://${d}/.well-known/lnurlp/${u}`; }
  if (lud06) return lnurlDecode(lud06);
  throw new Error("No Lightning address on this profile.");
}

/* ============ NWC (NIP-47) client ============ */
const NWC_KEY = "xm.nwc.v1";
export const Nwc = {
  conn: store.get(NWC_KEY, null), // {walletPk, relay, secret}
  _unsub: null, _pending: new Map(), _ck: null, _clientPk: "",
};
export function parseNwcUri(uri) {
  uri = String(uri || "").trim();
  if (!/^nostr\+walletconnect:\/\//i.test(uri)) throw new Error("Expected a nostr+walletconnect:// URI.");
  const u = new URL(uri);
  const walletPk = u.hostname || u.pathname.replace(/^\//, "");
  const relay = u.searchParams.get("relay") || "";
  const secret = (u.searchParams.get("secret") || "").toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(walletPk)) throw new Error("Bad wallet pubkey in NWC URI.");
  if (!relay.startsWith("wss://")) throw new Error("NWC URI needs a wss:// relay.");
  if (!/^[0-9a-f]{64}$/.test(secret)) throw new Error("Bad secret in NWC URI.");
  return { walletPk, relay, secret };
}
function nwcListen() {
  nwcClose();
  const c = Nwc.conn;
  const sk = hx(c.secret);
  Nwc._clientPk = nt.getPublicKey(sk);
  Nwc._ck = nt.nip44.v2.utils.getConversationKey(sk, c.walletPk);
  const sub = pool().subscribeMany([c.relay], [{ kinds: [23195], "#p": [Nwc._clientPk], since: now() - 120 }], {
    onevent(e) {
      try {
        if (e.pubkey !== c.walletPk) return;
        const pt = nt.nip44.v2.decrypt(e.content, Nwc._ck);
        const msg = JSON.parse(pt);
        const pend = Nwc._pending.get(msg.id || msg.correlation_id || "");
        const key = msg.id || "";
        const p = Nwc._pending.get(key);
        if (!p) return;
        Nwc._pending.delete(key);
        if (msg.error) p.reject(new Error(msg.error.message || msg.error.code || "wallet error"));
        else p.resolve(msg.result);
      } catch {}
    },
    oneose() {},
  });
  Nwc._unsub = () => { try { sub.close(); } catch {} };
}
function nwcClose() { if (Nwc._unsub) { try { Nwc._unsub(); } catch {} Nwc._unsub = null; } }
async function nwcRequest(method, params = {}) {
  const c = Nwc.conn;
  if (!c) throw new Error("No NWC wallet connected.");
  if (!Nwc._unsub) nwcListen();
  const sk = hx(c.secret);
  const id = Math.random().toString(36).slice(2);
  const content = nt.nip44.v2.encrypt(JSON.stringify({ method, params }), Nwc._ck);
  const tpl = { kind: 23194, created_at: now(), tags: [["p", c.walletPk]], content };
  const signed = nt.finalizeEvent(tpl, sk);
  const p = new Promise((resolve, reject) => {
    Nwc._pending.set(id, { resolve, reject });
    setTimeout(() => { if (Nwc._pending.has(id)) { Nwc._pending.delete(id); reject(new Error("Wallet timed out.")); } }, 30000);
  });
  // NOTE: NWC v1 correlates via event id of the request; our listener also
  // matches msg.id — most servers echo a matching id. Fallback match-all:
  Nwc._pending.set(id + ":any", Nwc._pending.get(id));
  await pool().publish([c.relay], signed);
  // race the strict id match with any-response (some wallets omit id echo)
  return p.catch(async (e) => { Nwc._pending.delete(id + ":any"); throw e; });
}
// Simpler robust variant: resolve with the *next* response from the wallet.
async function nwcCall(method, params = {}) {
  const c = Nwc.conn;
  if (!c) throw new Error("No NWC wallet connected.");
  if (!Nwc._unsub) nwcListen();
  const run = () => new Promise(async (resolve, reject) => {
    const to = setTimeout(() => { if (Nwc._routeAll === handler) Nwc._routeAll = null; reject(new Error("Wallet timed out.")); }, 30000);
    const handler = { resolve: (v) => { clearTimeout(to); resolve(v); }, reject: (e) => { clearTimeout(to); reject(e); } };
    Nwc._routeAll = handler;
    try {
      const sk = hx(c.secret);
      const content = nt.nip44.v2.encrypt(JSON.stringify({ method, params }), Nwc._ck);
      const signed = nt.finalizeEvent({ kind: 23194, created_at: now(), tags: [["p", c.walletPk]], content }, sk);
      await Promise.all(pool().publish([c.relay], signed));
    } catch (e) { if (Nwc._routeAll === handler) Nwc._routeAll = null; clearTimeout(to); reject(e); }
  });
  const p = _nwcChain.then(run, run);
  _nwcChain = p.catch(() => {});
  return p;
}
export function connectNwc(uri) {
  const c = parseNwcUri(uri);
  Nwc.conn = c;
  store.set(NWC_KEY, c);
  // rebuild listener with route-all support
  nwcClose();
  const sk = hx(c.secret);
  Nwc._clientPk = nt.getPublicKey(sk);
  Nwc._ck = nt.nip44.v2.utils.getConversationKey(sk, c.walletPk);
  const sub = pool().subscribeMany([c.relay], [{ kinds: [23195], "#p": [Nwc._clientPk], since: now() - 120 }], {
    onevent(e) {
      try {
        if (e.pubkey !== c.walletPk) return;
        const msg = JSON.parse(nt.nip44.v2.decrypt(e.content, Nwc._ck));
        const h = Nwc._routeAll;
        if (h) {
          Nwc._routeAll = null;
          if (msg.error) h.reject(new Error(msg.error.message || msg.error.code || "wallet error"));
          else h.resolve(msg.result);
        }
      } catch {}
    },
    oneose() {},
  });
  Nwc._unsub = () => { try { sub.close(); } catch {} };
  return c;
}
export function disconnectNwc() { nwcClose(); Nwc.conn = null; Nwc._ck = null; store.del(NWC_KEY); }
export const nwcConnected = () => !!Nwc.conn;
export const nwcGetBalance = () => nwcCall("get_balance");            // → {balance: msats}
export const nwcPayInvoice = (invoice) => nwcCall("pay_invoice", { invoice });
export const nwcMakeInvoice = (amountMsats, memo = "XM Arcade") => nwcCall("make_invoice", { amount: amountMsats, default_memo: memo });
export const nwcLookup = (invoice) => nwcCall("lookup_invoice", { invoice });

/* ============ zaps (NIP-57) ============ */
export async function zapProfile({ toPk, toLud16, toLud06, sats, comment = "", targetEvent = null }) {
  if (!nwcConnected()) throw new Error("Connect an NWC wallet first (Wallet → sats).");
  const payUrl = await ludToPayUrl(toLud16, toLud06);
  const lnurl = await (await fetch(payUrl)).json();
  if (!lnurl.allowsNostr || !lnurl.nostrPubkey) throw new Error("That address doesn't accept Nostr zaps.");
  const msats = Math.round(Number(sats) * 1000);
  if (lnurl.minSendable && msats < lnurl.minSendable) throw new Error(`Minimum is ${Math.ceil(lnurl.minSendable / 1000)} sats.`);
  if (lnurl.maxSendable && msats > lnurl.maxSendable) throw new Error(`Maximum is ${Math.floor(lnurl.maxSendable / 1000)} sats.`);
  const zapReq = nt.nip57.makeZapRequest({ profile: toPk, event: targetEvent, amount: msats, relays: RelayState.relays.slice(0, 4), comment });
  const signed = await signEvent(zapReq);
  const cb = new URL(lnurl.callback);
  cb.searchParams.set("amount", String(msats));
  cb.searchParams.set("nostr", JSON.stringify(signed));
  if (comment) cb.searchParams.set("comment", comment);
  const inv = await (await fetch(cb.toString())).json();
  if (!inv.pr) throw new Error("LNURL server didn't return an invoice.");
  return nwcPayInvoice(inv.pr);
}

/* ============ Monero (monero-wallet-rpc adapter) ============ */
const XMR_KEY = "xm.xmr.v1";
export const Xmr = {
  cfg: store.get(XMR_KEY, { url: "http://127.0.0.1:18082/json_rpc", user: "", pass: "", label: "" }),
  mode: store.get("xm.xmr.mode.v1", null), // 'rpc' | 'external' | null
};
export function saveXmrMode() { store.set("xm.xmr.mode.v1", Xmr.mode); }
export function saveXmrCfg() { store.set(XMR_KEY, Xmr.cfg); }
export const XMR_ADDR = /^4[0-9AB][1-9A-HJ-NP-Za-km-z]{93}$/;
export const piconeroToXmr = (p) => Number(p || 0) / 1e12;
export const xmrToPiconero = (x) => BigInt(Math.round(Number(x || 0) * 1e12)).toString();
async function xmrRpc(method, params = {}) {
  const { url, user, pass } = Xmr.cfg;
  const headers = { "Content-Type": "application/json" };
  if (user) headers.Authorization = "Basic " + btoa(user + ":" + pass);
  const r = await fetch(url, { method: "POST", headers, body: JSON.stringify({ jsonrpc: "2.0", id: "xm", method, params }) });
  if (!r.ok) throw new Error(`RPC HTTP ${r.status} — is monero-wallet-rpc running with --rpc-bind-ip reachable + CORS enabled?`);
  const j = await r.json();
  if (j.error) throw new Error(j.error.message || "wallet RPC error");
  return j.result;
}
export const xmrStatus = () => xmrRpc("get_version");
export const xmrAddress = (account = 0) => xmrRpc("get_address", { account_index: account });
export const xmrBalance = (account = 0) => xmrRpc("get_balance", { account_index: account });
export const xmrTransfer = (address, amountXmr, { account = 0, priority = 1 } = {}) =>
  xmrRpc("transfer", { account_index: account, destinations: [{ amount: xmrToPiconero(amountXmr), address }], priority, do_not_relay: false });
export const xmrCreateWallet = (filename, password, language = "English") =>
  xmrRpc("create_wallet", { filename, password, language });
export const xmrOpenWallet = (filename, password = "") => xmrRpc("open_wallet", { filename, password });
export const xmrTx = (txid) => xmrRpc("get_transfer_by_txid", { txid });
