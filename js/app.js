/* XM Arcade — router, actions, live loading, workbench, boot. */
import { $, $$, esc, store, money, timeAgo, ic, avatar, copyText, debounce } from "./util.js";
import { RelayState, saveRelays, onRelayStatus, relayStatus, pool, refreshProbes, setBlossomServers, blossomUpload, blossomServers, toNpub, toNsec, DEFAULT_RELAYS, decodeNip19, publish, reqFast } from "./nostr.js";
import { Signer, isSignedIn, npub, createLocalKey, loginLocalInput, loginNip07, hasNip07, parseBunkerUri, pairingState, loginBunkerUri, pairNewSigner, signEvent, logout, restoreSession } from "./signer.js";
import {
  cachedProfile, displayName, fetchProfiles, publishProfile, fetchContacts, setFollow,
  publishNote, react, repost, fetchAuthorNotes, fetchGlobalNotes,
  fetchShorts, publishShort, fetchGroups, fetchChannelMessages, sendGroupMessage,
  subscribeChannel, isConcordEncrypted, decryptConcord, getGroupKey, setGroupKey,
  newGroupKey, shareGroupKey, collectKeyShares, groupChannelId, fetchMusic,
  publishTrack, DEMO, DemoStore,
} from "./data.js";
import {
  Nwc, nwcConnected, connectNwc, disconnectNwc, nwcGetBalance, nwcPayInvoice,
  nwcMakeInvoice, zapProfile, ludToPayUrl, bolt11Msats,
  Xmr, saveXmrCfg, xmrStatus, xmrAddress, xmrBalance, xmrTransfer, XMR_ADDR, piconeroToXmr,
} from "./wallets.js";
import { catalog, localApps, addUpload, removeUpload, buildFrameDoc, feedAppToChannel, parseAppCard, BUILTINS } from "./webxdc.js";
import { V, SHEETS, S, saveUI, SECTIONS, SEC_LABEL, relayChip } from "./ui.js";
import { paneMenuItems } from "./ui2.js";

window.__relayStatus = relayStatus;

/* ================= tiny shell: toast / dropdown / sheets ================= */
const phone = $("#phone");
let host = null, stack = [], ddOpen = false, sheetWrap = null, toastT = null;
let activeSub = null, chanCtx = { group: null, channel: "general" }, gameCtx = { app: null };

export function toast(msg, icon = "check") {
  const t = $("#toast"); if (!t) return;
  t.innerHTML = ic(icon) + `<span>${esc(msg)}</span>`;
  t.classList.add("on"); clearTimeout(toastT);
  toastT = setTimeout(() => t.classList.remove("on"), 2400);
}
function trace() {
  const el = $("#trace");
  if (el) el.innerHTML = stack.map((id, i) => `${i ? ' <span style="opacity:.5">→</span> ' : ""}<b>${SEC_LABEL[id] || id}</b>`).join("");
  const wb = $("#wbRelays");
  if (wb) wb.innerHTML = RelayState.relays.map((u) => { const s = relayStatus(u); return `<span class="live-dot ${s === "live" ? "" : s === "down" ? "down" : "idle"}"></span> ${esc(u)}`; }).join("<br>");
}
function openDD() {
  ddOpen = true;
  $("#ddPanel").innerHTML = paneMenuItems(stack[0]);
  $("#ddScrim").classList.add("on"); $("#ddPanel").classList.add("on");
  $$(".dd-btn").forEach((b) => b.classList.add("on"));
}
function closeDD() { if (!ddOpen) return false; ddOpen = false; $("#ddScrim").classList.remove("on"); $("#ddPanel").classList.remove("on"); $$(".dd-btn").forEach((b) => b.classList.remove("on")); return true; }
function openSheet(id, ctx = {}) {
  closeSheet(true);
  const meta = (SHEETS[id] || (() => ({ title: id, body: "<p class=sub>Missing sheet.</p>" })))(ctx);
  sheetWrap = document.createElement("div");
  sheetWrap.innerHTML = `<div class="sheet-scrim" data-act="closeSheet"></div><div class="sheet" id="sheetEl"><div class="grip"></div><h3>${meta.title}</h3>${meta.sub ? `<div class="sh-sub">${meta.sub}</div>` : ""}<div class="sheet-body">${meta.body}</div>${meta.footer ? `<div class="tag-editor-footer">${meta.footer}</div>` : ""}</div>`;
  phone.appendChild(sheetWrap);
  const opening = sheetWrap;
  requestAnimationFrame(() => {
    if (sheetWrap !== opening || !opening.isConnected) return;
    $(".sheet-scrim", opening).classList.add("on"); $(".sheet", opening).classList.add("on");
  });
  afterSheetOpen(id, ctx, opening);
}
function closeSheet(hard) {
  if (!sheetWrap) return false;
  const w = sheetWrap; sheetWrap = null; w.inert = true;
  $(".sheet-scrim", w)?.classList.remove("on"); $(".sheet", w)?.classList.remove("on");
  setTimeout(() => w.remove(), hard ? 0 : 420);
  return true;
}
function closeOverlays() { let did = false; if (ddOpen) { closeDD(); did = true; } if (sheetWrap) { closeSheet(); did = true; } return did; }

/* ================= router ================= */
const viewCtx = {};
function renderView(id) {
  const wrap = document.createElement("div");
  wrap.innerHTML = (V[id] || V.shorts)(viewCtx[id] || {});
  const el = wrap.firstElementChild;
  host.appendChild(el);
  layoutSegs(el);
  if (id === "channel") { const log = $("#chatScroll", el); if (log) log.scrollTop = log.scrollHeight; }
  if (id === "gamerun") mountGame(el);
  if (id === "shorts") watchShorts(el);
  return el;
}
function layoutSegs(root) {
  $$(".segctl", root).forEach((seg) => {
    const on = seg.querySelector("button.on"), th = seg.querySelector(".thumb");
    if (on && th) { th.style.left = on.offsetLeft + "px"; th.style.width = on.offsetWidth + "px"; }
  });
}
function show(id, dir = "push") {
  teardownLive();
  const old = host.lastElementChild;
  const el = renderView(id);
  el.classList.add("active");
  if (dir === "back") el.classList.add("pop");
  if (old) { old.classList.remove("active"); old.classList.add(dir === "back" ? "exit-fwd" : "exit-back"); setTimeout(() => old.remove(), 620); }
  trace();
}
function go(id, ds = {}) {
  if (ds.ch) S.channel = ds.ch;
  if (ds.id && id === "channel") S.groupId = ds.id;
  stack.push(id); show(id, "push"); saveUI();
  loadView(id, ds);
}
function setSection(sec) {
  stack = [sec]; S.section = sec; show(sec, "switch"); saveUI();
  loadView(sec, {});
}
function back() {
  if (closeOverlays()) return;
  if (stack.length > 1) { stack.pop(); const id = stack[stack.length - 1]; show(id, "back"); loadView(id, {}); }
}
function refresh() { const id = stack[stack.length - 1]; show(id, "switch"); loadView(id, {}); }
function teardownLive() { if (activeSub) { try { activeSub(); } catch {} activeSub = null; } if (shortsObs) { shortsObs.disconnect(); shortsObs = null; } }

/* ================= per-view live loading ================= */
let AppsCache = [], GroupsCache = [], ShortsCache = [], TracksCache = [];
async function loadView(id, ds = {}) {
  try {
    if (id === "profile") {
      const pk = ds.pk || (isSignedIn() ? Signer.pubkey : "");
      if (!pk) return;
      viewCtx.profile = { pk, loading: true, notes: [] }; paint(id);
      const [notes, follows] = await Promise.all([fetchAuthorNotes(pk, 30), fetchContacts(pk)]);
      if (pk === Signer.pubkey) { S.followUsers = follows; saveUI(); }
      await fetchProfiles([pk, ...notes.slice(0, 20).map((e) => e.pubkey)]);
      viewCtx.profile = { pk, notes, noteCount: notes.length, followCount: follows.length }; paint(id);
    }
    if (id === "shorts") await loadShorts();
    if (id === "miniapps") {
      viewCtx.miniapps = { loading: true, list: AppsCache }; paint(id);
      AppsCache = await catalog({ nostr: true });
      viewCtx.miniapps = { list: AppsCache }; paint(id);
    }
    if (id === "music") {
      viewCtx.music = { loading: true, list: TracksCache }; paint(id);
      const q = ($("#musicSearch")?.value || "").toLowerCase();
      let list = TracksCache.length && !ds.force ? TracksCache : await fetchMusic({ limit: 40 });
      TracksCache = list;
      await fetchProfiles(list.slice(0, 20).map((t) => t.pubkey));
      if (q) list = list.filter((t) => (t.title + " " + (t.tags || []).join(" ")).toLowerCase().includes(q));
      if (!list.length) list = DEMO.tracks;
      viewCtx.music = { list, q: $("#musicSearch")?.value || "" }; paint(id);
    }
    if (id === "chats") {
      viewCtx.chats = { loading: true, groups: GroupsCache }; paint(id);
      const live = await fetchGroups();
      GroupsCache = live.length ? live : [...DEMO.groups];
      // demo group always available offline at the end
      if (live.length && !GroupsCache.some((g) => g.demo)) GroupsCache.push(...DEMO.groups);
      if (!S.groupId && GroupsCache[0]) S.groupId = GroupsCache[0].id;
      viewCtx.chats = { groups: GroupsCache }; paint(id);
    }
    if (id === "channel") await loadChannel(ds);
    if (id === "wallet") await loadWallet();
    if (id === "settings") paint(id);
  } catch (e) { console.warn("load", id, e); }
}
function paint(id) {
  if (stack[stack.length - 1] !== id) return;
  const old = host.lastElementChild;
  const el = renderView(id);
  el.classList.add("active");
  if (old) { old.classList.remove("active"); setTimeout(() => old.remove(), 50); }
  trace();
}
async function loadShorts() {
  viewCtx.shorts = { loading: true, list: ShortsCache }; paint("shorts");
  let list = [];
  if (S.shortTab === "tags" && S.followTags.length) list = await fetchShorts({ tags: S.followTags, limit: 30 });
  else if (S.shortTab === "follows" && isSignedIn()) {
    const f = await fetchContacts(Signer.pubkey);
    if (f.length) list = await fetchShorts({ authors: f.slice(0, 40), limit: 30 });
  } else list = await fetchShorts({ limit: 30 });
  if (!list.length) list = [...DEMO.shorts];
  ShortsCache = list;
  const profiles = await fetchProfiles(list.map((s) => s.pubkey));
  viewCtx.shorts = { list, profiles }; paint("shorts");
  const el = host.lastElementChild; if (el) watchShorts(el);
}
let shortsObs = null;
function watchShorts(root) {
  if (shortsObs) shortsObs.disconnect();
  const vids = $$("video", root);
  if (!vids.length || !("IntersectionObserver" in window)) return;
  shortsObs = new IntersectionObserver((ents) => {
    ents.forEach((en) => {
      const v = en.target;
      if (en.intersectionRatio > 0.6) { v.play().catch(() => {}); } else v.pause();
    });
  }, { root: $("#shortsScroll", root), threshold: [0.6] });
  vids.forEach((v) => shortsObs.observe(v));
}
async function loadChannel(ds = {}) {
  const g = GroupsCache.find((x) => x.id === S.groupId) || GroupsCache[0];
  if (!g) { viewCtx.channel = { messages: [] }; paint("channel"); return; }
  chanCtx = { group: g, channel: S.channel };
  viewCtx.channel = { group: g, loading: true, messages: [] }; paint("channel");
  if (g.demo) {
    const id = groupChannelId(g, S.channel);
    viewCtx.channel = { group: g, messages: DemoStore.for(id).map((m) => ({ who: m.who, text: m.text, t: m.t })) };
    paint("channel");
    return;
  }
  const raw = await fetchChannelMessages(g, S.channel, 60);
  await fetchProfiles(raw.map((e) => e.pubkey));
  const id = groupChannelId(g, S.channel);
  const messages = [];
  for (const e of raw) {
    const enc = isConcordEncrypted(e);
    let text = e.content;
    if (enc) {
      const pt = await decryptConcord(e, id);
      text = pt == null ? "🔒 locked message — import the channel key" : pt;
    }
    const card = parseAppCard(enc ? "" : text);
    messages.push({ pubkey: e.pubkey, who: displayName(cachedProfile(e.pubkey)), text: card ? text.replace(/\{[^]*"type"\s*:\s*"webxdc"[^]*\}/, "").trim() : text, t: timeAgo(e.created_at), encrypted: enc, appCard: card });
  }
  viewCtx.channel = { group: g, messages }; paint("channel");
  // live tail
  activeSub = subscribeChannel(g, S.channel, async (e) => {
    if (stack[stack.length - 1] !== "channel") return;
    const enc = isConcordEncrypted(e);
    let text = e.content;
    if (enc) { const pt = await decryptConcord(e, id); text = pt == null ? "🔒 locked message — import the channel key" : pt; }
    await fetchProfiles([e.pubkey]);
    const card = parseAppCard(enc ? "" : text);
    viewCtx.channel.messages.push({ pubkey: e.pubkey, who: displayName(cachedProfile(e.pubkey)), text: card ? text.replace(/\{[^]*"type"\s*:\s*"webxdc"[^]*\}/, "").trim() : text, t: timeAgo(e.created_at), encrypted: enc, appCard: card });
    paint("channel");
  });
}
async function loadWallet() {
  viewCtx.wallet = { ...(viewCtx.wallet || {}) }; paint("wallet");
  const ctx = {};
  if (nwcConnected()) {
    try { const b = await nwcGetBalance(); ctx.satsBal = Math.floor(Number(b.balance || 0) / 1000); }
    catch { ctx.satsBal = null; toast("NWC wallet didn't answer", "info"); }
  }
  try {
    await xmrStatus();
    const [bal, addr] = await Promise.all([xmrBalance().catch(() => null), xmrAddress().catch(() => null)]);
    ctx.xmr = {
      ok: true,
      balance: bal ? piconeroToXmr(bal.balance ?? 0).toFixed(6) : "?",
      address: addr?.address || addr?.addresses?.[0] || "",
    };
  } catch (e) { ctx.xmr = { ok: false, error: /Failed to fetch|NetworkError/i.test(e.message) ? "Can't reach the RPC URL from this browser." : e.message }; }
  viewCtx.wallet = ctx; paint("wallet");
}

/* ================= game mount ================= */
async function mountGame(el) {
  const app = gameCtx.app || AppsCache.find((a) => a.id === (S.game || "")) || { id: "builtin-hello", title: "Hello Arcade", source: "builtin", file: "webxdc/hello/index.html" };
  gameCtx.app = app;
  const hostEl = $("#gameHost", el);
  try {
    const me = isSignedIn() ? cachedProfile(Signer.pubkey) : null;
    const { html } = await buildFrameDoc(app, { selfAddr: Signer.pubkey ? Signer.pubkey.slice(0, 8) : "guest", selfName: me ? displayName(me) : "you" });
    const f = document.createElement("iframe");
    f.className = "game-frame"; f.setAttribute("sandbox", "allow-scripts"); f.setAttribute("title", app.title);
    f.srcdoc = html;
    hostEl.innerHTML = ""; hostEl.appendChild(f);
    const hello = () => { try { f.contentWindow.postMessage({ __xm: "webxdc-hello", selfAddr: Signer.pubkey?.slice(0, 8) || "guest", selfName: me ? displayName(me) : "you" }, "*"); } catch {} };
    setTimeout(hello, 600);
  } catch (e) { hostEl.innerHTML = `<p class="tiny" style="text-align:center;padding:30px;color:#f66">${esc(e.message)}</p>`; }
}
window.addEventListener("message", (ev) => {
  const m = ev.data || {};
  if (!m.__xm) return;
  if (m.__xm === "webxdc-chat" && m.text) {
    if (stack[stack.length - 1] === "channel" && chanCtx.group && !chanCtx.group.demo) {
      sendGroupMessage(chanCtx.group, S.channel, String(m.text).slice(0, 500)).then(() => toast("Score fed to channel", "send")).catch((e) => toast(e.message, "info"));
    } else toast("Game says: " + String(m.text).slice(0, 80), "game");
  }
  if (m.__xm === "webxdc-update" && gameCtx.app) {
    try {
      const k = "xm.webxdc.updates." + gameCtx.app.id;
      const log = store.get(k, []); log.push(m.update); store.set(k, log.slice(-200));
    } catch {}
  }
});

/* ================= sheets: after-open fill ================= */
let FeedDraft = { appId: "", groupId: "", channel: "" };
async function afterSheetOpen(id, ctx, wrap) {
  if (id === "feedApp" || id === "feedPick") {
    if (!AppsCache.length) AppsCache = await catalog({ nostr: false });
    if (id === "feedPick") {
      const box = $("#feedAppList", wrap);
      box.innerHTML = AppsCache.map((a) => `<button class="row" data-act="feedNow" data-id="${esc(a.id)}"><span class="ic">${ic("game")}</span><span style="flex:1"><b>${esc(a.title)}</b><small>${esc(a.source)}</small></span>${ic("chevR")}</button>`).join("");
      return;
    }
    FeedDraft = { appId: ctx.id || "", groupId: S.groupId, channel: "" };
    if (!GroupsCache.length) GroupsCache = [...(await fetchGroups()), ...DEMO.groups];
    const box = $("#feedGroups", wrap);
    const app = AppsCache.find((a) => a.id === FeedDraft.appId);
    box.innerHTML = `<p class="tiny" style="margin-bottom:8px">1 · Pick a group ${app ? `for <b>${esc(app.title)}</b>` : ""}</p>` +
      GroupsCache.map((g) => `<button class="row" data-act="feedGroup" data-id="${esc(g.id)}">${avatar(g.name, 36, g.picture)}<span style="flex:1"><b>${esc(g.name)}</b><small>${g.demo ? "demo" : "NIP-29"}</small></span>${FeedDraft.groupId === g.id ? ic("check") : ic("chevR")}</button>`).join("") +
      `<div id="feedChans" style="margin-top:10px"></div>`;
  }
  if (id === "chanKey") {
    const g = chanCtx.group;
    const box = $("#chanKeyBox", wrap);
    if (box && g) box.textContent = getGroupKey(groupChannelId(g, S.channel)) || "(none on this device)";
  }
}

/* ================= actions ================= */
function val(id, root = document) { return ($(id.startsWith("#") ? id : "#" + id, root)?.value || "").trim(); }
async function actGuard(fn) { try { await fn(); } catch (e) { toast(e.message || "Something failed", "info"); } }

phone.addEventListener("click", (e) => {
  const t = e.target.closest("[data-act]");
  if (ddOpen && !e.target.closest("#ddPanel") && !e.target.closest('[data-act="dd"]')) closeDD();
  if (!t) return;
  const d = t.dataset, a = d.act;
  ripple(e, t);

  switch (a) {
    case "dd": ddOpen ? closeDD() : openDD(); return;
    case "closeDD": closeDD(); return;
    case "section": closeDD(); setSection(d.sec); return;
    case "go": closeDD(); go(d.view, d); return;
    case "mapGo": go(d.view, d); return;
    case "back": back(); return;
    case "sheet": openSheet(d.sheet, d); return;
    case "sheet2": openSheet(d.sheet, d); return;
    case "closeSheet": closeSheet(); return;
    case "toast": toast(d.toast || "Noted"); return;
  }

  actGuard(async () => {
    /* ---- auth ---- */
    if (a === "createGo") {
      const name = val("pfName") || "you", about = val("pfAbout");
      createLocalKey();
      try { await publishProfile({ name, display_name: name, about }); } catch {}
      try { await fetchContacts(Signer.pubkey); } catch {}
      S.section = S.startingPane || "shorts"; stack = [S.section]; saveUI(); show(S.section, "switch"); loadView(S.section, {});
      toast("Account created — welcome to XM Arcade", "spark");
      return;
    }
    if (a === "nsecPaste") { try { $("#nsecInput").value = await navigator.clipboard.readText(); } catch { toast("Clipboard blocked — paste manually", "info"); } return; }
    if (a === "nsecReveal") { const i = $("#nsecInput"); i.type = i.type === "password" ? "text" : "password"; return; }
    if (a === "nsecGo") {
      loginLocalInput(val("nsecInput"));
      try { await fetchContacts(Signer.pubkey); collectKeyShares(); } catch {}
      S.section = S.startingPane || "shorts"; stack = [S.section]; saveUI(); show(S.section, "switch"); loadView(S.section, {});
      toast("Logged in with device key", "key"); return;
    }
    if (a === "nip07") {
      if (!hasNip07()) { $("#signerPane").innerHTML = `<div class="card"><b>No extension found.</b><p class="tiny" style="margin-top:6px">Install Alby/nos2x, or use Amber pairing below.</p></div>`; return; }
      await loginNip07();
      stack = [S.startingPane || "shorts"]; S.section = stack[0]; saveUI(); show(S.section, "switch"); loadView(S.section, {}); toast("Extension signer connected", "key"); return;
    }
    if (a === "bunkerPaste") {
      $("#signerPane").innerHTML = `<input class="field mono" id="bunkerUri" placeholder="bunker://…" autocapitalize="off" spellcheck="false"><button class="btn block" style="margin-top:10px" data-act="bunkerGo">Connect</button>`;
      return;
    }
    if (a === "bunkerGo") {
      t.disabled = true; t.textContent = "Waiting for signer…";
      await loginBunkerUri(val("bunkerUri"));
      stack = [S.startingPane || "shorts"]; S.section = stack[0]; saveUI(); show(S.section, "switch"); loadView(S.section, {}); toast("Remote signer connected", "key"); return;
    }
    if (a === "pairNew") {
      const pair = pairingState();
      $("#signerPane").innerHTML = `<div class="card"><b style="font-size:13px">Approve in Amber</b><div class="keybox" style="margin:8px 0;font-size:10.5px">${esc(pair.uri)}</div>
        <div style="display:flex;gap:8px"><button class="btn sm" data-act="copyPair">Copy</button><button class="btn sm line" data-act="pairCancel">Cancel</button></div>
        <p class="tiny" id="pairStatus" style="margin-top:8px">Waiting for approval… (up to 5 min)</p></div>`;
      window.__pair = pair;
      pairNewSigner(pair, (s) => { const el = $("#pairStatus"); if (el) el.textContent = s === "approved" ? "Approved — finishing login…" : "Waiting for approval… (up to 5 min)"; })
        .then(() => { stack = [S.startingPane || "shorts"]; S.section = stack[0]; saveUI(); show(S.section, "switch"); loadView(S.section, {}); toast("Paired with Amber", "key"); })
        .catch((err) => toast(err.message, "info"));
      return;
    }
    if (a === "copyPair") { await copyText(window.__pair.uri); toast("Pairing string copied"); return; }
    if (a === "pairCancel") { $("#signerPane").innerHTML = ""; return; }
    if (a === "logout") { logout(); stack = ["welcome"]; saveUI(); show("welcome", "switch"); toast("Logged out"); return; }

    /* ---- profile / notes ---- */
    if (a === "follow") {
      if (!isSignedIn()) { toast("Sign in to follow", "info"); return; }
      const pk = d.pk, on = S.followUsers.includes(pk);
      await setFollow(pk, !on);
      S.followUsers = on ? S.followUsers.filter((x) => x !== pk) : [...S.followUsers, pk]; saveUI();
      closeSheet(true); refresh(); toast(on ? "Unfollowed" : "Following", on ? "check" : "plus"); return;
    }
    if (a === "author" || a === "visitProfile") {
      if (!d.pk || d.pk.startsWith("demo")) { toast("Demo placeholder — no key", "info"); return; }
      closeSheet(true);
      await fetchProfiles([d.pk]);
      if (a === "author") { openSheet("author", { pk: d.pk }); return; }
      stack.push("profile"); show("profile", "push"); loadView("profile", { pk: d.pk }); return;
    }
    if (a === "copyNpub") { await copyText(toNpub(d.pk)); toast("npub copied"); return; }
    if (a === "nsecShow") {
      $("#nsecReveal").type = "text"; $("#nsecReveal").value = toNsec(Signer.localSk); return;
    }
    if (a === "nsecCopy") { await copyText(toNsec(Signer.localSk)); toast("nsec copied — guard it", "key"); return; }
    if (a === "profileSave") {
      await publishProfile({ display_name: val("epName"), name: val("epName"), about: val("epAbout"), picture: val("epPic"), lud16: val("epLud") });
      closeSheet(true); refresh(); toast("Profile published"); return;
    }
    if (a === "postGo") {
      const ta = $("#composeText");
      const text = (ta?.value || "").trim();
      const f = $("#composeFile")?.files?.[0];
      let body = text;
      if (f) { toast("Uploading attachment…", "upload"); const up = await blossomUpload(f, (x) => signEvent(x)); body += `\n${up.url}`; }
      if (!body.trim()) { toast("Write something first", "info"); return; }
      let tags = [];
      try {
        const rt = ta?.dataset.replyTo ? JSON.parse(ta.dataset.replyTo) : null;
        if (rt && rt.id) tags = [["e", rt.id, "", "reply"], ["p", rt.pubkey || ""], ["k", String(rt.kind ?? 1)]];
      } catch {}
      const { res } = await publishNote(body, tags);
      back(); toast(res.ok ? `Posted to ${res.oks}/${res.total} relays` : "No relay accepted it", res.ok ? "check" : "info"); return;
    }
    if (a === "reply") {
      if (!isSignedIn()) { toast("Sign in to reply", "info"); return; }
      const id = d.eid;
      if (!id || id.startsWith("demo") || id.startsWith("dn")) { toast("Demo post — replies are local only", "info"); return; }
      const evs = await reqFast({ ids: [id] });
      const target = evs[0];
      if (!target) { toast("Original not found on relays", "info"); return; }
      go("compose", {}); setTimeout(() => { const ta = $("#composeText"); if (ta) { ta.value = ""; ta.dataset.replyTo = JSON.stringify({ id: target.id, pubkey: target.pubkey, kind: target.kind }); ta.placeholder = "Replying… (kind " + target.kind + ")"; } }, 350);
      return;
    }
    if (a === "react" || a === "repost") {
      if (!isSignedIn()) { toast("Sign in first", "info"); return; }
      const id = d.eid;
      if (!id || id.startsWith("demo")) { toast("Demo post — nothing to sign", "info"); return; }
      const evs = await reqFast({ ids: [id] });
      if (!evs[0]) { toast("Not found on relays", "info"); return; }
      const r = a === "react" ? await react(evs[0], evs[0].kind) : await repost(evs[0]);
      toast(r.ok ? (a === "react" ? "Liked" : "Reposted") : "No relay accepted it", r.ok ? "heart" : "info"); return;
    }

    /* ---- shorts ---- */
    if (a === "shortTab") { S.shortTab = d.val; saveUI(); loadShorts(); return; }
    if (a === "tagTap" || a === "tagOpen") {
      const tag = (d.tag || "").toLowerCase();
      if (S.followTags.includes(tag)) { S.followTags = S.followTags.filter((x) => x !== tag); toast("Unfollowed #" + tag, "hash"); }
      else { S.followTags.push(tag); toast("Following #" + tag, "hash"); }
      saveUI(); refresh(); return;
    }
    if (a === "videoToggle") {
      const v = t.tagName === "VIDEO" ? t : t.querySelector("video");
      if (v) { v.muted = false; v.paused ? v.play().catch(() => {}) : v.pause(); }
      return;
    }
    if (a === "tagAdd") { const v = val("tagAdd").replace(/^#/, "").toLowerCase(); if (v && !S.followTags.includes(v)) S.followTags.push(v); saveUI(); openSheet("tags"); return; }
    if (a === "tagDel") { S.followTags = S.followTags.filter((x) => x !== d.tag); saveUI(); openSheet("tags"); return; }

    /* ---- studio ---- */
    if (a === "studioGo") {
      if (!isSignedIn()) { toast("Sign in to publish", "info"); return; }
      const f = $("#studioFile")?.files?.[0];
      const title = val("studioTitle") || (f ? f.name : "Untitled");
      const tags = val("studioTags").split(/\s+/).map((x) => x.replace(/^#/, "")).filter(Boolean);
      if (!f) { toast("Choose a file first", "info"); return; }
      $("#studioStatus").textContent = "Uploading to Blossom…";
      const up = await blossomUpload(f, (x) => signEvent(x));
      $("#studioStatus").textContent = "Publishing pointer to relays…";
      if (f.type.startsWith("video")) {
        await publishShort({ url: up.url, title, tags, mime: f.type, sha256: up.sha256 });
        toast("Short published", "video"); setSection("shorts");
      } else if (f.type.startsWith("audio")) {
        await publishTrack({ url: up.url, title, tags, mime: f.type });
        toast("Track published", "note"); setSection("music");
      } else { toast("Need an audio or video file", "info"); }
      return;
    }

    /* ---- apps ---- */
    if (a === "appFilter") { S.tagFilter = d.val; saveUI(); paint("miniapps"); return; }
    if (a === "play") {
      const app = AppsCache.find((x) => x.id === d.id);
      if (!app) return;
      gameCtx.app = app; S.game = app.id;
      stack.push("gamerun"); show("gamerun", "push"); return;
    }
    if (a === "playUrl") {
      gameCtx.app = { id: "url:" + d.url, title: d.title || "Shared app", source: "nostr", url: d.url };
      stack.push("gamerun"); show("gamerun", "push"); return;
    }
    if (a === "appUploadGo") {
      const f = $("#appFile")?.files?.[0];
      await addUpload(f, { title: val("appTitle"), desc: val("appDesc"), tags: val("appTags").split(/\s+/) });
      closeSheet(true); AppsCache = await catalog({ nostr: true }); viewCtx.miniapps = { list: AppsCache }; paint("miniapps");
      toast("App added to your arcade", "game"); return;
    }
    if (a === "feedApp") {
      if (!isSignedIn()) { toast("Sign in to feed apps", "info"); return; }
      const app = AppsCache.find((x) => x.id === d.id);
      openSheet("feedApp", { id: d.id, title: app?.title }); return;
    }
    if (a === "feedGroup") {
      FeedDraft.groupId = d.id; FeedDraft.channel = "";
      const g = GroupsCache.find((x) => x.id === d.id);
      const chans = g?.channels || [{ name: "general" }, { name: "media" }];
      $("#feedChans").innerHTML = `<p class="tiny" style="margin:6px 0">2 · Pick a channel in <b>${esc(g.name)}</b></p>` +
        chans.map((c) => `<button class="row" data-act="feedChan" data-ch="${esc(c.name)}"><span class="ic">${ic("hash")}</span><span style="flex:1"><b>#${esc(c.name)}</b></span>${ic("chevR")}</button>`).join("");
      return;
    }
    if (a === "feedChan") {
      FeedDraft.channel = d.ch;
      const app = AppsCache.find((x) => x.id === FeedDraft.appId);
      const g = GroupsCache.find((x) => x.id === FeedDraft.groupId);
      $("#feedChans").innerHTML += `<button class="btn block" style="margin-top:10px" data-act="feedConfirm">Feed ${esc(app?.title || "app")} to #${esc(d.ch)}</button>`;
      return;
    }
    if (a === "feedConfirm") {
      const app = AppsCache.find((x) => x.id === FeedDraft.appId);
      const g = GroupsCache.find((x) => x.id === FeedDraft.groupId);
      t.disabled = true; t.textContent = "Uploading & posting…";
      if (g.demo) { DemoStore.push(groupChannelId(g, FeedDraft.channel), "You", `🎮 ${app.title} (demo feed)`); }
      else await feedAppToChannel(app, g, FeedDraft.channel);
      closeSheet(true); S.groupId = g.id; S.channel = FeedDraft.channel; saveUI();
      stack = ["chats"]; setSection("chats");
      S.groupId = g.id; stack.push("channel"); show("channel", "push"); loadView("channel", {});
      toast(`Fed ${app?.title} to #${FeedDraft.channel}`, "send"); return;
    }
    if (a === "feedNow") {
      const app = AppsCache.find((x) => x.id === d.id);
      const g = chanCtx.group;
      t.disabled = true; t.textContent = "Feeding…";
      if (!g || g.demo) DemoStore.push(groupChannelId(g || DEMO.groups[0], S.channel), "You", `🎮 ${app.title} (demo feed)`);
      else await feedAppToChannel(app, g, S.channel);
      closeSheet(true); loadView("channel", {}); toast(`Fed ${app?.title} to #${S.channel}`, "send"); return;
    }

    /* ---- music ---- */
    if (a === "musicFilter") { S.musicTag = d.val; saveUI(); paint("music"); return; }
    if (a === "trackPlay") {
      const tr = (viewCtx.music?.list || []).find((x) => x.id === d.eid);
      if (!tr) return;
      if (!tr.url) { toast("Demo track — no audio", "info"); return; }
      S.track = tr; paint("music"); return;
    }
    if (a === "trackQueue") {
      const tr = (viewCtx.music?.list || []).find((x) => x.id === d.eid);
      if (tr && tr.url) { S.queue.push(tr); toast("Queued: " + tr.title.slice(0, 30), "plus"); paint("music"); }
      else toast("Demo track — no audio", "info");
      return;
    }
    if (a === "trackToggle") { const au = $("audio", phone); if (au) au.paused ? au.play() : au.pause(); return; }

    /* ---- chats ---- */
    if (a === "chatGroup") { S.groupId = d.id; S.channel = "general"; saveUI(); paint("chats"); return; }
    if (a === "openChannel") { S.groupId = d.id; S.channel = d.ch; saveUI(); stack.push("channel"); show("channel", "push"); loadView("channel", {}); return; }
    if (a === "lockToggle") {
      S.locked = !S.locked;
      if (S.locked && chanCtx.group && !getGroupKey(groupChannelId(chanCtx.group, S.channel))) {
        setGroupKey(groupChannelId(chanCtx.group, S.channel), newGroupKey());
        toast("Fresh Concord key made — share it from the key button", "lock");
      }
      paint("channel"); return;
    }
    if (a === "chatSend") {
      const inp = $("#chatInput"); const text = (inp?.value || "").trim();
      if (!text) return;
      if (!isSignedIn()) { toast("Sign in to chat", "info"); return; }
      const g = chanCtx.group;
      inp.value = "";
      if (!g || g.demo) { DemoStore.push(groupChannelId(g || DEMO.groups[0], S.channel), "You", text); loadView("channel", {}); return; }
      const r = await sendGroupMessage(g, S.channel, text, { encrypted: S.locked });
      if (!r.res.ok) toast("No group relay accepted it", "info");
      else { viewCtx.channel.messages.push({ pubkey: Signer.pubkey, who: "You", text, t: "now", encrypted: S.locked }); paint("channel"); }
      return;
    }
    if (a === "newChanGo") {
      const name = (val("newChanName") || "").toLowerCase().replace(/[^a-z0-9-]/g, "-").replace(/-+/g, "-");
      if (!name) { toast("Name the channel", "info"); return; }
      const lock = $("#newChanLock")?.checked;
      const g = GroupsCache.find((x) => x.id === S.groupId);
      if (g && !g.demo && !g.channels?.some((c) => c.name === name)) g.channels = [...(g.channels || []), { name, topic: lock ? "🔒 Concord-encrypted" : "" }];
      S.channel = name; S.locked = !!lock; saveUI();
      if (lock && g) setGroupKey(groupChannelId(g, name), newGroupKey());
      closeSheet(true); stack.push("channel"); show("channel", "push"); loadView("channel", {});
      toast("Channel #" + name + (lock ? " (locked)" : ""), "hash"); return;
    }
    if (a === "chanKeyCopy") { const g = chanCtx.group; await copyText(getGroupKey(groupChannelId(g, S.channel)) || ""); toast("Channel key copied"); return; }
    if (a === "chanKeyNew") {
      const g = chanCtx.group; const k = newGroupKey();
      setGroupKey(groupChannelId(g, S.channel), k);
      const box = $("#chanKeyBox"); if (box) box.textContent = k;
      toast("New channel key set", "lock"); return;
    }
    if (a === "chanKeyImport") {
      const k = val("chanKeyImport").toLowerCase();
      if (!/^[0-9a-f]{64}$/.test(k)) { toast("Need 64 hex chars", "info"); return; }
      setGroupKey(groupChannelId(chanCtx.group, S.channel), k);
      closeSheet(true); loadView("channel", {}); toast("Channel key imported", "lock"); return;
    }
    if (a === "chanKeyShare") {
      const to = val("chanKeyTo");
      const dd = decodeNip19(to);
      const pk = dd?.type === "npub" ? dd.data : (/^[0-9a-f]{64}$/.test(to) ? to : "");
      if (!pk) { toast("Paste an npub", "info"); return; }
      const r = await shareGroupKey(chanCtx.group, S.channel, pk);
      toast(r.ok ? "Key gift-wrapped to member" : "No relay accepted it", r.ok ? "send" : "info"); return;
    }

    /* ---- zaps ---- */
    if (a === "zap") { openSheet("zap", { eid: d.eid || "", pk: d.pk || "" }); return; }
    if (a === "zapAmt") { S.zapAmt = Number(d.val); saveUI(); openSheet("zap", { eid: t.dataset.eid || "", pk: t.dataset.pk || "" }); return; }
    if (a === "zapGo") {
      if (!isSignedIn()) { toast("Sign in to zap", "info"); return; }
      const custom = Number(val("zapCustom") || 0);
      const sats = custom > 0 ? custom : S.zapAmt;
      const comment = val("zapComment");
      let pk = d.pk, eid = d.eid || null;
      if ((!pk || pk.startsWith("demo")) && !eid) { toast("Demo post — nothing to zap", "info"); return; }
      t.disabled = true; t.textContent = "Zapping…";
      if (!pk || pk.startsWith("demo")) { const evs = await reqFast({ ids: [eid] }); pk = evs[0]?.pubkey; }
      await fetchProfiles([pk]);
      const p = cachedProfile(pk);
      await zapProfile({ toPk: pk, toLud16: p.lud16, toLud06: p.lud06, sats, comment, targetEvent: eid?.startsWith("demo") ? null : eid });
      closeSheet(true); toast(`⚡ ${money(sats)} sats sent`, "zap"); return;
    }

    /* ---- snaps (XMR) ---- */
    if (a === "snap") { openSheet("snap", { eid: d.eid || "", pk: d.pk || "" }); return; }
    if (a === "snapAmt") { S.snapAmt = Number(d.val); saveUI(); openSheet("snap", { eid: d.eid || "", pk: d.pk || "" }); return; }
    if (a === "snapGo") {
      const to = val("snapTo").trim();
      if (!XMR_ADDR.test(to)) { toast("Paste their Monero address (starts with 4)", "info"); return; }
      const custom = Number(val("snapCustom") || 0);
      const amt = custom > 0 ? custom : S.snapAmt;
      if (!(amt > 0)) { toast("Enter an amount", "info"); return; }
      t.disabled = true; t.textContent = "Snapping…";
      const r = await xmrTransfer(to, amt);
      const h = r.tx_hash ? ` · ${r.tx_hash.slice(0, 8)}` : "";
      closeSheet(true); toast(`Snapped ${amt} XMR${h}`, "xmr"); return;
    }

    /* ---- wallet ---- */
    if (a === "nwcGo") { connectNwc(val("nwcUri")); closeSheet(true); toast("NWC wallet connected", "zap"); loadWallet(); return; }
    if (a === "nwcDrop") { disconnectNwc(); loadWallet(); toast("NWC disconnected"); return; }
    if (a === "satsRefresh") { loadWallet(); return; }
    if (a === "invGo") {
      const sats = Number(val("invAmt") || 0);
      const r = await nwcMakeInvoice(Math.round(sats * 1000), val("invMemo") || "XM Arcade");
      const inv = r.invoice || r.pr;
      $("#invOut").innerHTML = `<div class="invoice-box"><b>⚡ ${money(sats)} sats</b><code>${esc(inv)}</code><button class="btn sm" style="margin-top:10px" data-act="copyInv" data-inv="${esc(inv)}">Copy invoice</button></div>`;
      return;
    }
    if (a === "copyInv") { await copyText(d.inv); toast("Invoice copied"); return; }
    if (a === "payGo") {
      const to = val("payTo");
      t.disabled = true; t.textContent = "Paying…";
      if (/^lnbc/i.test(to)) { await nwcPayInvoice(to); }
      else if (to.includes("@")) {
        const [u, dom] = to.split("@");
        const lnurl = await (await fetch(`https://${dom}/.well-known/lnurlp/${u}`)).json();
        const msats = lnurl.minSendable || 1000;
        const cb = new URL(lnurl.callback); cb.searchParams.set("amount", String(msats));
        const inv = await (await fetch(cb.toString())).json();
        await nwcPayInvoice(inv.pr);
      } else throw new Error("Paste a BOLT11 invoice or user@domain.");
      $("#payOut").innerHTML = `<p class="tiny" style="color:var(--ok)">Paid ✓</p>`;
      loadWallet(); toast("Paid", "zap"); return;
    }
    if (a === "xmrGo") {
      Xmr.cfg = { url: val("xmrUrl"), user: val("xmrUser"), pass: val("xmrPass") }; saveXmrCfg();
      t.disabled = true; t.textContent = "Testing…";
      const v = await xmrStatus();
      closeSheet(true); toast("Monero RPC live (v" + (v.version || "?") + ")", "check"); loadWallet(); return;
    }
    if (a === "xmrRefresh") { loadWallet(); return; }
    if (a === "xmrCopy") { const ad = viewCtx.wallet?.xmr?.address; if (ad) { await copyText(ad); toast("Address copied"); } return; }
    if (a === "xmrSendGo") {
      const to = val("xmrTo"), amt = Number(val("xmrAmt") || 0);
      if (!XMR_ADDR.test(to)) { toast("That Monero address looks wrong", "info"); return; }
      if (!(amt > 0)) { toast("Enter an amount", "info"); return; }
      t.disabled = true; t.textContent = "Sending…";
      const r = await xmrTransfer(to, amt);
      $("#xmrOut").innerHTML = `<p class="tiny" style="color:var(--ok)">Sent ✓ <span class="mono">${esc(r.tx_hash || "")}</span></p>`;
      loadWallet(); return;
    }

    /* ---- settings ---- */
    if (a === "themeFlip") { setTheme(S.theme === "dark" ? "light" : "dark"); refresh(); return; }
    if (a === "setStart") { S.startingPane = d.sec; saveUI(); openSheet("startingPane"); return; }
    if (a === "relayAdd") {
      const u = val("relayAdd");
      if (!/^wss:\/\/.+/.test(u)) { toast("Need a wss:// URL", "info"); return; }
      if (!RelayState.relays.includes(u)) RelayState.relays.push(u);
      saveRelays(); refreshProbes(); openSheet("relays"); trace(); return;
    }
    if (a === "relayDel") { RelayState.relays = RelayState.relays.filter((x) => x !== d.url); saveRelays(); refreshProbes(); openSheet("relays"); trace(); return; }
    if (a === "relayReset") { RelayState.relays = [...DEFAULT_RELAYS]; saveRelays(); refreshProbes(); openSheet("relays"); trace(); return; }
    if (a === "groupRelayAdd") {
      const u = val("groupRelayAdd");
      if (!/^wss:\/\/.+/.test(u)) { toast("Need a wss:// URL", "info"); return; }
      if (!RelayState.groupRelays.includes(u)) RelayState.groupRelays.push(u);
      saveRelays(); refreshProbes(); openSheet("groupRelays"); return;
    }
    if (a === "groupRelayDel") { RelayState.groupRelays = RelayState.groupRelays.filter((x) => x !== d.url); saveRelays(); openSheet("groupRelays"); return; }
    if (a === "blossomAdd") {
      const u = val("blossomAdd").replace(/\/$/, "");
      if (!/^https:\/\/.+/.test(u)) { toast("Need an https:// URL", "info"); return; }
      setBlossomServers([...blossomServers(), u]); openSheet("blossom"); return;
    }
    if (a === "blossomDel") { setBlossomServers(blossomServers().filter((x) => x !== d.url)); openSheet("blossom"); return; }
  });
});
function ripple(e, t) {
  try {
    const r = t.getBoundingClientRect();
    const s = document.createElement("span");
    s.className = "ripple";
    const sz = Math.max(r.width, r.height);
    s.style.cssText = `width:${sz}px;height:${sz}px;left:${e.clientX - r.left - sz / 2}px;top:${e.clientY - r.top - sz / 2}px`;
    t.style.position = t.style.position || "relative"; t.style.overflow = "hidden";
    t.appendChild(s); setTimeout(() => s.remove(), 650);
  } catch {}
}

/* inputs: chat send on Enter, music search */
phone.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && e.target.id === "chatInput") { e.preventDefault(); $("[data-act=chatSend]", phone)?.click(); }
});
phone.addEventListener("input", debounce((e) => {
  if (e.target.id === "musicSearch") {
    const q = e.target.value.toLowerCase();
    const list = TracksCache.filter((t) => !q || (t.title + " " + (t.tags || []).join(" ")).toLowerCase().includes(q));
    viewCtx.music = { list: list.length || q ? list : DEMO.tracks, q: e.target.value };
    const pos = e.target.selectionStart;
    paint("music");
    const n = $("#musicSearch"); if (n) { n.focus(); n.setSelectionRange(pos, pos); }
  }
}, 220));
phone.addEventListener("change", (e) => {
  if (e.target.id === "studioFile") {
    const f = e.target.files[0];
    if (!f) return;
    const url = URL.createObjectURL(f);
    $("#studioPrev").innerHTML = f.type.startsWith("video") ? `<video src="${url}" controls style="width:100%;border-radius:14px;max-height:220px"></video>` : `<audio src="${url}" controls style="width:100%"></audio>`;
    if (!$("#studioTitle").value) $("#studioTitle").value = f.name.replace(/\.[^.]+$/, "").replace(/[-_]+/g, " ");
  }
  if (e.target.id === "composeFile") {
    const f = e.target.files[0];
    if (f) $("#composeAtt").textContent = "📎 " + f.name;
  }
});

/* ================= workbench ================= */
function setTheme(th) {
  S.theme = th; saveUI();
  document.documentElement.dataset.theme = th;
  $$("#themeSeg button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.themeSet === th)));
}
function fit() {
  const st = $(".stage"), dev = $("#device");
  if (!st || !dev || window.innerWidth <= 860) { dev.style.transform = ""; return; }
  const r = st.getBoundingClientRect();
  const s = Math.min(1, (r.height - 8) / 940, (r.width - 8) / 440);
  dev.style.transform = `scale(${s})`;
}
function wireWorkbench() {
  $$("#themeSeg button").forEach((b) => b.addEventListener("click", () => setTheme(b.dataset.themeSet)));
  $("#mapBtn")?.addEventListener("click", () => { stack.push("map"); show("map", "push"); });
  $("#coachBtn")?.addEventListener("click", () => { stack.push("loginSigner"); show("loginSigner", "push"); });
  $("#wireBtn")?.addEventListener("click", (e) => {
    const on = document.body.classList.toggle("wire");
    e.currentTarget.querySelector("span").textContent = on ? "on" : "off";
  });
  $("#guidesBtn")?.addEventListener("click", (e) => {
    const g = $("#guides"); const on = g.classList.toggle("on");
    e.currentTarget.querySelector("span").textContent = on ? "on" : "off";
  });
  $("#resetBtn")?.addEventListener("click", () => { logout(); stack = ["welcome"]; saveUI(); show("welcome", "switch"); });
  addEventListener("resize", fit);
  addEventListener("keydown", (e) => {
    if (e.target.matches("input,textarea")) return;
    if (e.key === "Escape") back();
    else if (e.key === "m" || e.key === "M") { stack.push("map"); show("map", "push"); }
    else if (e.key === "t" || e.key === "T") setTheme(S.theme === "dark" ? "light" : "dark");
    else if (e.key === "g" || e.key === "G") $("#guidesBtn")?.click();
    else if (e.key === "w" || e.key === "W") $("#wireBtn")?.click();
  });
  onRelayStatus(() => { trace(); const chips = $$(".online-chip", phone); if (chips.length && liveRelayCount() >= 0) { /* keep chips fresh on next paint */ } });
  setInterval(trace, 5000);
}

/* ================= boot ================= */
async function boot() {
  phone.innerHTML = `<div id="viewHost" style="position:absolute;inset:0;z-index:1"></div>
    <div class="dd-scrim" id="ddScrim" data-act="closeDD"></div><div class="dd-panel" id="ddPanel"></div>
    <div class="toast" id="toast"></div>
    <div class="guides" id="guides"><div class="gl" style="top:44px"></div><div class="gl" style="bottom:88px"></div><div class="gc" style="left:16px"></div><div class="gc" style="right:16px"></div><div class="gc" style="left:50%;border-color:rgba(255,209,102,.28)"></div></div>`;
  host = $("#viewHost");
  document.documentElement.dataset.theme = S.theme || "dark";
  setTheme(S.theme || "dark");
  wireWorkbench(); fit();
  try { pool(); refreshProbes(); } catch {}
  const ok = await restoreSession().catch(() => false);
  if (ok && isSignedIn()) {
    try { S.followUsers = await fetchContacts(Signer.pubkey); await fetchProfiles([Signer.pubkey]); collectKeyShares(); } catch {}
    stack = [S.startingPane || "shorts"]; S.section = stack[0];
  } else stack = ["welcome"];
  saveUI(); show(stack[0], "switch"); loadView(stack[0], {});
  setTimeout(fit, 300);
}
boot();
