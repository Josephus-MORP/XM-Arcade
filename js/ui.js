/* XM Arcade — screens 1/2: state, atoms, auth, profile, shorts, apps, music. */
import { $, $$, esc, store, fmt, money, timeAgo, ic, icf, avatar, xlogo, coinMark, richText } from "./util.js";
import { RelayState, liveRelayCount, toNpub } from "./nostr.js";
import { Signer, isSignedIn } from "./signer.js";
import { cachedProfile, displayName, DEMO } from "./data.js";

export const SECTIONS = [
  { id: "profile", label: "Profile", icon: "user", sub: "Your page, key, stats" },
  { id: "shorts", label: "Shorts", icon: "play", sub: "Vertical video feed" },
  { id: "miniapps", label: "Mini Apps", icon: "game", sub: "webxdc games arcade" },
  { id: "music", label: "Music", icon: "note", sub: "Tracks from the network" },
  { id: "chats", label: "Chats", icon: "msg", sub: "Concord channels · encrypted" },
  { id: "wallet", label: "Wallet", icon: "wallet", sub: "Monero + Bitcoin sats" },
  { id: "settings", label: "Settings", icon: "gear", sub: "Tags, theme, keys, relays" },
];
export const SEC_LABEL = Object.fromEntries(SECTIONS.map((s) => [s.id, s.label]));

/* ---------- UI state (persisted subset) ---------- */
const saved = store.get("xm.ui.v1", {});
export const S = Object.assign({
  theme: "dark", section: "shorts", startingPane: "shorts",
  shortTab: "global", tagFilter: "All", musicTag: "All",
  followTags: ["webxdc"], followUsers: [],
  online: 0, game: null, track: null, queue: [],
  groupId: "", channel: "general", locked: false,
  zapAmt: 210, zapWho: null, xapAmt: 0.01, accentHue: 187,
  relaysSeen: false,
}, saved);
export function saveUI() {
  store.set("xm.ui.v1", {
    theme: S.theme, section: S.section, startingPane: S.startingPane,
    shortTab: S.shortTab, tagFilter: S.tagFilter, musicTag: S.musicTag,
    followTags: S.followTags, followUsers: S.followUsers,
    groupId: S.groupId, channel: S.channel, zapAmt: S.zapAmt, xapAmt: S.xapAmt, accentHue: S.accentHue,
  });
}

/* ---------- atoms ---------- */
export function relayChip() {
  const n = liveRelayCount(), total = RelayState.relays.length;
  const cls = n > 0 ? "" : "down";
  return `<button class="online-chip ${cls}" data-act="sheet" data-sheet="relays" aria-label="Relay status"><span class="dot"></span>${n}/${total} relays</button>`;
}
export function modeBadge(live) {
  return live ? `<span class="mode-badge live"><span class="live-dot"></span>live</span>` : `<span class="mode-badge demo">demo</span>`;
}
export function topbar(label, right = "", trailing = "") {
  const me = isSignedIn() ? cachedProfile(Signer.pubkey) : null;
  const av = me ? avatar(displayName(me), 26, me.picture) : xlogo(26);
  return `<header class="appbar">
    <button class="dd-btn" data-act="dd" aria-label="Open your apps">${av}<b>${esc(label)}</b><span class="chev">${ic("chevD")}</span></button>
    <span class="spread"></span>${right}${relayChip()}${trailing}</header>`;
}
export function rowItem(o = {}) {
  return `<button class="row" data-act="${o.act || (o.to ? "go" : o.sheet ? "sheet" : "toast")}" ${o.to ? `data-view="${o.to}"` : ""} ${o.sheet ? `data-sheet="${o.sheet}"` : ""} ${o.toast ? `data-toast="${esc(o.toast)}"` : ""} ${o.sec ? `data-sec="${o.sec}"` : ""} ${o.tag ? `data-tag="${esc(o.tag)}"` : ""} ${o.pk ? `data-pk="${o.pk}"` : ""} ${o.id ? `data-id="${o.id}"` : ""}>
    ${o.icon ? `<span class="ic" ${o.tint ? `style="background:color-mix(in srgb,${o.tint} 16%,transparent);color:${o.tint}"` : ""}>${ic(o.icon)}</span>` : ""}
    <span style="min-width:0;flex:1"><b>${o.title}</b>${o.sub ? `<small>${o.sub}</small>` : ""}</span>
    ${o.value ? `<span class="tiny" style="color:var(--text-2);font-weight:600">${o.value}</span>` : ""}
    ${o.sw !== undefined ? `<span class="sw ${o.sw ? "on" : ""}"></span>` : ""}
    ${o.tail || ""}${o.chev === false ? "" : `<span class="chev">${ic("chevR")}</span>`}
  </button>`;
}
export function emptyState(title, sub, btn = "") {
  return `<div class="empty"><svg viewBox="0 0 160 130" class="empty-art" aria-hidden="true"><path d="M20 96c14-40 30-58 60-58s46 18 60 58" stroke="var(--line-2)" stroke-width="3" fill="none" stroke-linecap="round"/><circle cx="80" cy="36" r="10" stroke="var(--line-2)" stroke-width="3" fill="none"/><path d="M34 112h92" stroke="var(--line-2)" stroke-width="3" stroke-linecap="round"/></svg><div class="h3">${esc(title)}</div><p class="sub" style="margin-top:6px;max-width:30ch">${sub}</p>${btn}</div>`;
}
export function noteCard(e, p, { showTarget = false } = {}) {
  const name = displayName(p);
  return `<div class="note-card" data-eid="${e.id}">
    <div class="note-head">
      <button data-act="author" data-pk="${e.pubkey}" style="display:flex;align-items:center;gap:10px;flex:1;min-width:0;text-align:left">${avatar(name, 38, p.picture)}<span style="min-width:0"><b>${esc(name)}</b><small>${timeAgo(e.created_at)}${e.demo ? " · demo" : ""}</small></span></button>
      ${e.demo ? modeBadge(false) : ""}
    </div>
    <div class="note-body">${richText(e.content)}</div>
    <div class="note-acts">
      <button data-act="reply" data-eid="${e.id}">${ic("reply")} Reply</button>
      <button data-act="repost" data-eid="${e.id}">${ic("refresh")} Repost</button>
      <button data-act="react" data-eid="${e.id}">${ic("heart")} Like</button>
      <button data-act="zap" data-eid="${e.id}" data-pk="${e.pubkey}">${ic("zap")} Zap</button>
      <button data-act="xap" data-eid="${e.id}" data-pk="${e.pubkey}">${coinMark("xmr", 18)} Xap</button>
    </div>
  </div>`;
}

/* ---------- view + sheet registries (extended by ui2.js) ---------- */
export const V = {};
export const SHEETS = {};

/* ================= WELCOME ================= */
V.welcome = () => `<div class="view opaque immersive" data-view="welcome">
  <div style="flex:1;display:grid;place-items:center;padding:0 34px">
    <div style="text-align:center;width:100%">
      <div style="display:flex;justify-content:center">${xlogo(96)}</div>
      <h1 class="h1" style="margin-top:24px">Welcome to<br>X<span style="color:#f60">M</span> Arcade</h1>
      <p class="sub" style="margin:14px auto 0;max-width:30ch">Games, shorts, music and encrypted chats — owned by nobody, signed by you.</p>
      <div style="margin-top:14px;display:flex;justify-content:center;gap:8px">${modeBadge(liveRelayCount() > 0)}<span class="tiny">real Nostr client · Amber-ready</span></div>
    </div>
  </div>
  <div style="padding:0 26px calc(30px + env(safe-area-inset-bottom,0px));display:flex;flex-direction:column;gap:10px">
    <button class="btn block" data-act="go" data-view="createAccount">Create account</button>
    <button class="btn line block" data-act="go" data-view="loginNsec">Login with nsec</button>
    <button class="btn line block" data-act="go" data-view="loginSigner">Login with signer</button>
    <div class="tiny" style="text-align:center;margin-top:8px;line-height:1.55">Your keys never leave this phone.<br>Use Amber for maximum safety.</div>
  </div>
</div>`;

V.createAccount = () => `<div class="view opaque" data-view="createAccount">
  <header class="appbar"><button class="icon-btn bare" data-act="back" aria-label="Back">${ic("chevL")}</button><b style="font-size:15px">New account</b><span class="spread"></span>${relayChip()}</header>
  <div class="scroller" style="padding:6px 20px 24px">
    <div style="display:flex;flex-direction:column;align-items:center;margin:14px 0 20px">
      <div>${avatar("you", 92)}</div>
      <p class="tiny" style="margin-top:10px">Avatar is generated from your key once created.</p>
    </div>
    <label class="tiny" style="display:block;margin:0 0 7px;font-weight:700">Display name</label>
    <input class="field" id="pfName" value="you" maxlength="24" autocomplete="off">
    <label class="tiny" style="display:block;margin:16px 0 7px;font-weight:700">About <span style="color:var(--text-3);font-weight:500">(optional)</span></label>
    <input class="field" id="pfAbout" placeholder="a line about you" maxlength="140" autocomplete="off">
    <div class="secbox" style="margin-top:20px">
      <div style="display:flex;align-items:center;gap:9px"><span style="color:var(--ok)">${ic("shield")}</span><b style="font-size:14px">Recommended relays</b></div>
      <p class="tiny" style="margin:7px 0 8px;line-height:1.5">Three well-run public relays to start. Change them any time in Settings.</p>
      ${RelayState.relays.map((r) => `<div class="tiny mono" style="display:flex;gap:8px;align-items:center;color:var(--text-2)"><span style="color:var(--ok)">${ic("check")}</span>${esc(r)}</div>`).join("")}
    </div>
    <button class="btn block" style="margin-top:20px" data-act="createGo">Generate my key ${ic("key")}</button>
    <p class="tiny" style="margin-top:10px;text-align:center;line-height:1.6">A fresh Nostr keypair is generated on this device.<br>Back it up right after — Settings → Back up keys.</p>
  </div>
</div>`;

V.loginNsec = () => `<div class="view opaque" data-view="loginNsec">
  <header class="appbar"><button class="icon-btn bare" data-act="back" aria-label="Back">${ic("chevL")}</button><b style="font-size:15px">Login with nsec</b><span class="spread"></span>${relayChip()}</header>
  <div class="scroller" style="padding:20px 20px 24px">
    <h1 class="h2">Paste your key</h1>
    <p class="sub" style="margin:8px 0 16px">From Amber or another Nostr app. Starts with <span class="mono">nsec…</span> — never share it with anyone.</p>
    <input class="field mono" id="nsecInput" type="password" placeholder="nsec1…" autocomplete="off" autocapitalize="off" spellcheck="false">
    <div style="display:flex;gap:8px;margin-top:10px"><button class="btn ghost sm" data-act="nsecReveal">Show</button><span class="spread"></span><button class="btn ghost sm" data-act="nsecPaste">Paste</button></div>
    <button class="btn block" style="margin-top:16px" data-act="nsecGo">Log in</button>
    <div class="card" style="margin-top:16px"><b style="font-size:13px">Safer alternative</b><p class="tiny" style="margin:6px 0 10px;line-height:1.6">Keep your nsec inside Amber and approve each signature there instead.</p><button class="btn line sm block" data-act="go" data-view="loginSigner">Login with signer</button></div>
    <p class="tiny" style="margin-top:12px;line-height:1.6">The key is stored on this device only, and only used to sign your events. Nothing is uploaded anywhere.</p>
  </div>
</div>`;

V.loginSigner = () => `<div class="view opaque" data-view="loginSigner">
  <header class="appbar"><button class="icon-btn bare" data-act="back" aria-label="Back">${ic("chevL")}</button><b style="font-size:15px">Login with signer</b><span class="spread"></span>${relayChip()}</header>
  <div class="scroller" style="padding:20px 20px 24px">
    <h1 class="h2">Amber & remote signers</h1>
    <p class="sub" style="margin:8px 0 16px">Your nsec stays in the signer app. XM Arcade sends signing requests over Nostr (NIP-46) — you approve each one.</p>
    <div class="rows" style="padding:0">
      ${rowItem({ act: "bunkerPaste", icon: "qr", tint: "var(--accent)", title: "Paste bunker:// URI", sub: "Exported from Amber or Nsec.app" })}
      ${rowItem({ act: "pairNew", icon: "plus", tint: "var(--ok)", title: "Pair a new signer", sub: "Approve XM Arcade inside Amber" })}
    </div>
    <div id="signerPane" style="margin-top:14px"></div>
    <div class="card" style="margin-top:14px"><b style="font-size:13px">How pairing works</b>
      <p class="tiny" style="margin:6px 0 0;line-height:1.7">1. Tap <b>Pair a new signer</b> → a <span class="mono">nostrconnect://</span> string appears.<br>2. Tap <b>Open in Amber</b> (or paste the string manually in Amber).<br>3. Approve. XM Arcade logs in — no key ever touches the browser.</p></div>
  </div>
</div>`;

/* ================= PROFILE ================= */
export function profileHeader(p, stats, mine) {
  const npub = toNpub(p.pubkey || "");
  return `<div class="card" style="margin:12px 14px 10px">
    ${p.banner ? `<div style="margin:-16px -16px 12px;border-radius:18px 18px 0 0;overflow:hidden;height:110px;background:var(--surface-2)"><img src="${esc(p.banner)}" style="width:100%;height:100%;object-fit:cover" alt="" loading="lazy" onerror="this.remove()"></div>` : ""}
    <div style="display:flex;gap:12px;align-items:center">
      ${avatar(displayName(p), 64, p.picture)}
      <div style="flex:1;min-width:0"><div class="h3">${esc(displayName(p))}</div>
        <div class="tiny mono" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(npub).slice(0, 24)}…</div></div>
      ${mine ? "" : `<button class="btn sm" data-act="follow" data-pk="${p.pubkey}">${S.followUsers.includes(p.pubkey) ? "Unfollow" : "Follow"}</button>`}
    </div>
    ${p.about ? `<p class="sub" style="margin:10px 0 0">${esc(p.about)}</p>` : ""}
    ${p.nip05 ? `<div class="tiny" style="margin-top:6px">✓ ${esc(p.nip05)}</div>` : ""}
    <div style="display:flex;gap:16px;margin-top:12px">
      <span class="tiny"><b style="color:var(--text)">${stats.notes}</b> notes</span>
      <span class="tiny"><b style="color:var(--text)">${stats.follows}</b> follows</span>
      ${p.lud16 || p.lud06 ? `<span class="tiny">⚡ zaps on</span>` : ""}
    </div>
    ${mine ? `<div style="display:flex;gap:8px;margin-top:12px"><button class="btn sm ghost" data-act="sheet" data-sheet="editProfile">Edit profile</button><button class="btn sm ghost" data-act="sheet" data-sheet="keyBackup">Keys</button><button class="btn sm ghost" data-act="go" data-view="compose">Post</button></div>`
      : `<div style="display:flex;gap:8px;margin-top:12px"><button class="btn sm ghost" data-act="zap" data-pk="${p.pubkey}">⚡ Zap</button><button class="btn sm ghost" data-act="xap" data-pk="${p.pubkey}">${coinMark("xmr", 16)} Xap</button></div>`}
  </div>`;
}
V.profile = (ctx = {}) => {
  const pk = ctx.pk || (isSignedIn() ? Signer.pubkey : "");
  const p = pk ? cachedProfile(pk) : { pubkey: "", name: "Nobody yet" };
  const mine = pk && pk === Signer.pubkey;
  const notes = (ctx.notes || []).slice(0, 30);
  return `<div class="view opaque" data-view="profile">
    ${topbar(mine ? "Profile" : displayName(p), mine ? `<button class="icon-btn bare" data-act="go" data-view="compose" aria-label="New post">${ic("edit")}</button>` : "")}
    <div class="scroller" id="profileScroll" style="padding-bottom:40px">
      ${pk ? profileHeader(p, { notes: ctx.noteCount ?? notes.length, follows: ctx.followCount ?? S.followUsers.length }, mine) : emptyState("Not signed in", "Create an account or log in to see your profile.", `<button class="btn sm" style="margin-top:14px" data-act="go" data-view="welcome">Get started</button>`)}
      <div style="padding:0 14px" id="profileNotes">
        ${ctx.loading ? `<p class="tiny" style="text-align:center;padding:20px">Loading notes from relays…</p>` : ""}
        ${notes.map((e) => noteCard(e, e.pubkey === pk ? p : cachedProfile(e.pubkey))).join("")}
        ${pk && !ctx.loading && !notes.length ? emptyState("No notes yet", "Your notes will appear here once relays return them.", "") : ""}
      </div>
    </div>
  </div>`;
};

/* ================= SHORTS ================= */
export function shortCard(s, i, profiles = {}) {
  const p = profiles[s.pubkey] || cachedProfile(s.pubkey);
  const name = s.demo ? s.author : displayName(p);
  const followed = (s.tags || []).some((t) => S.followTags.includes(t));
  const art = s.url
    ? `<video class="short-video" src="${esc(s.url)}" ${s.thumb ? `poster="${esc(s.thumb)}"` : ""} playsinline loop muted preload="metadata" data-act="videoToggle"></video>`
    : `<div class="short-fallback"><div><div style="font-size:44px">🎬</div><div class="h3" style="margin-top:10px">${esc(s.title || "Demo short")}</div><p class="tiny" style="color:#9aa;margin-top:6px">Relay video lands here when the network returns one.</p></div></div>`;
  return `<div class="short" data-idx="${i}" data-eid="${s.id || ""}">
    <div class="bgart" data-act="videoToggle">${art}</div>
    <div class="short-top">
      <button class="dd-btn" data-act="dd" aria-label="Open your apps">${avatar(isSignedIn() ? displayName(cachedProfile(Signer.pubkey)) : "x", 26)}<span class="chev">${ic("chevD")}</span></button>
      <div class="segctl" data-seg="shortTab">${["global", "tags", "follows"].map((t) => `<button class="${S.shortTab === t ? "on" : ""}" data-act="shortTab" data-val="${t}">${t}</button>`).join("")}<span class="thumb"></span></div>
      <span class="spread"></span>${relayChip()}
      <button class="sact" data-act="go" data-view="studio" aria-label="Upload a video">${ic("video")}</button>
    </div>
    <div class="short-bot">
      ${s.demo ? `<span style="display:inline-flex;margin-bottom:8px">${modeBadge(false)}</span>` : ""}
      <div class="h3" style="color:#fff;text-shadow:0 1px 8px rgba(0,0,0,.6)">${esc(s.title || "Untitled")}</div>
      <div class="musicline" style="color:#fff">⏱ ${s.dur ? Math.round(s.dur) + "s · " : ""}${timeAgo(s.created_at)}</div>
      <div class="short-tag-toolbar"><div class="tchips">${(s.tags || []).slice(0, 4).map((t) => `<button class="tchip ${S.followTags.includes(t) ? "on" : ""}" data-act="tagTap" data-tag="${esc(t)}">#${esc(t)}</button>`).join("")}</div></div>
      <div class="short-acts">
        <button class="sact author" data-act="author" data-pk="${s.pubkey}">${avatar(name, 30, p.picture)}<b>${esc(String(name).split(" ")[0])}</b></button>
        <span class="spread"></span>
        <button class="sact" data-act="reply" data-inline="1" data-eid="${s.id || ""}" aria-label="Reply">${ic("reply")}</button>
        <button class="sact" data-act="zap" data-eid="${s.id || ""}" data-pk="${s.pubkey}" aria-label="Zap">${ic("zap")}</button>
        <button class="sact" data-act="xap" data-eid="${s.id || ""}" data-pk="${s.pubkey}" aria-label="Xap (XMR)">${coinMark("xmr", 20)}</button>
        <button class="sact" data-act="react" data-eid="${s.id || ""}" aria-label="Like">${ic("heart")}</button>
      </div>
    </div>
  </div>`;
}
V.shorts = (ctx = {}) => {
  const list = ctx.list || [];
  return `<div class="view immersive" data-view="shorts">
    <div class="shorts" id="shortsScroll">
      ${list.length ? list.map((s, i) => shortCard(s, i, ctx.profiles || {})).join("") : `<div class="short" style="display:grid;place-items:center;background:var(--bg);color:var(--text)">
        <div class="empty-shorts-nav"><button class="dd-btn" data-act="dd" aria-label="Open your apps">${avatar("x", 26)}<b>Shorts</b>${ic("chevD")}</button><span class="spread"></span>${relayChip()}</div>
        <div class="empty"><div class="h3">${ctx.loading ? "Loading shorts…" : "Nothing here yet"}</div>
        <p class="sub" style="margin-top:6px;max-width:26ch">${ctx.loading ? "Asking relays for NIP-71 videos." : S.shortTab === "tags" ? "Follow a tag in the global feed and its shorts land here." : S.shortTab === "follows" ? "Follow some people and their shorts land here." : "No shorts on these relays right now — try Upload."}</p>
        <div style="display:flex;gap:8px;justify-content:center;margin-top:14px"><button class="btn sm" data-act="shortTab" data-val="global">Global</button><button class="btn sm ghost" data-act="go" data-view="studio">Upload</button></div></div></div>`}
    </div>
  </div>`;
};

/* ================= MINI APPS ================= */
const PAL = [["#8b7bff", "#22d3ee"], ["#ff7ab6", "#8b7bff"], ["#22d3ee", "#3ddc97"], ["#ff8a4c", "#ff5470"], ["#5b8cff", "#8b7bff"], ["#3ddc97", "#22d3ee"]];
function palFor(id) { let h = 0; for (const c of String(id)) h = (h * 31 + c.charCodeAt(0)) >>> 0; return PAL[h % PAL.length]; }
function initials(t) { return String(t || "?").split(/\s+/).map((w) => w[0]).join("").slice(0, 2).toUpperCase(); }
V.miniapps = (ctx = {}) => {
  const list = ctx.list || [];
  const cats = ["All", ...new Set(list.flatMap((a) => a.tags || []))].slice(0, 10);
  const shown = S.tagFilter === "All" ? list : list.filter((a) => (a.tags || []).includes(S.tagFilter));
  return `<div class="view opaque miniapps-view" data-view="miniapps">
    ${topbar("Mini Apps", "", `<button class="icon-btn upload-app-button" data-act="sheet" data-sheet="appUpload" aria-label="Upload app">${ic("upload")}</button>`)}
    <div class="miniapps-category-toolbar"><div class="chiprow miniapps-tag-strip">
      ${cats.map((c) => `<button class="pill ${S.tagFilter === c ? "on" : ""}" data-act="appFilter" data-val="${esc(c)}">${c === "All" ? "All" : "#" + esc(c)}</button>`).join("")}
    </div></div>
    <div class="scroller thin-scroll miniapps-scroll"><div class="appgrid">
      ${ctx.loading ? `<p class="tiny" style="grid-column:1/-1;text-align:center;padding:20px">Loading arcade…</p>` : ""}
      ${shown.map((a) => { const p = palFor(a.id); return `
        <div class="appcard" data-app="${esc(a.id)}">
          <div class="ac-top">
            <div class="card-identity-stack"><span class="ac-ic" style="background:linear-gradient(135deg,${p[0]},${p[1]})">${esc(initials(a.title))}</span></div>
            <div class="ac-btns">
              <button class="btn xs" data-act="play" data-id="${esc(a.id)}">Play</button>
              <button class="btn xs line" data-act="feedApp" data-id="${esc(a.id)}">Feed</button>
            </div>
          </div>
          <div class="ac-name">${esc(a.title)}</div>
          <div class="src">${esc(a.source)}${a.author ? " · " + esc(a.author) : ""}</div>
          ${a.desc ? `<div class="tiny" style="margin-top:4px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(a.desc)}</div>` : ""}
        </div>`; }).join("") || (ctx.loading ? "" : `<div class="empty" style="grid-column:1/-1"><div class="h3">No apps here</div><p class="sub" style="margin-top:6px">Upload a .webxdc file to start the arcade.</p></div>`)}
    </div></div>
  </div>`;
};

/* ================= MUSIC ================= */
V.music = (ctx = {}) => {
  const list = ctx.list || [];
  const tags = ["All", ...new Set(list.flatMap((t) => t.tags || []))].slice(0, 12);
  const shown = S.musicTag === "All" ? list : list.filter((t) => (t.tags || []).includes(S.musicTag));
  const q = S.queue, cur = S.track;
  return `<div class="view opaque" data-view="music">
    ${topbar("Music", `<button class="icon-btn bare" data-act="go" data-view="studio" aria-label="Upload audio">${ic("upload")}</button>`)}
    <div style="padding:10px 14px 0"><input class="field" id="musicSearch" placeholder="Search title, artist, #tag…" value="${esc(ctx.q || "")}" data-kind="musicSearch"></div>
    <div class="miniapps-category-toolbar"><div class="chiprow miniapps-tag-strip">
      ${tags.map((t) => `<button class="pill ${S.musicTag === t ? "on" : ""}" data-act="musicFilter" data-val="${esc(t)}">${t === "All" ? "All" : "#" + esc(t)}</button>`).join("")}
    </div></div>
    <div class="scroller" style="padding:4px 14px 12px">
      ${ctx.loading ? `<p class="tiny" style="text-align:center;padding:20px">Scanning relays for audio…</p>` : ""}
      ${shown.map((t) => `
        <div class="track-row" data-eid="${t.id}">
          <button data-act="trackPlay" data-eid="${t.id}" style="display:flex;align-items:center;gap:12px;flex:1;min-width:0;text-align:left">
            <span class="ic" style="background:var(--primary-dim);color:var(--primary);width:44px;height:44px">${ic("note")}</span>
            <span class="tinfo"><b>${esc(t.title)}</b><small>${timeAgo(t.created_at)}${t.demo ? " · demo — no audio" : ""}</small></span>
          </button>
          <button class="icon-btn bare" data-act="trackQueue" data-eid="${t.id}" aria-label="Add to queue">${ic("plus")}</button>
          <button class="icon-btn bare" data-act="zap" data-eid="${t.id}" data-pk="${t.pubkey}" aria-label="Zap">${ic("zap")}</button>
          <button class="icon-btn bare" data-act="xap" data-eid="${t.id}" data-pk="${t.pubkey}" aria-label="Xap (XMR)">${coinMark("xmr", 20)}</button>
        </div>`).join("") || (ctx.loading ? "" : emptyState("Quiet here", "No audio on these relays yet. Upload a track to start the party.", ""))}
    </div>
    ${cur ? `<div class="mini-player"><button class="icon-btn bare" data-act="trackToggle" aria-label="Play/pause">${ic("pause")}</button>
      <div style="flex:1;min-width:0"><b style="font-size:12.5px;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(cur.title)}</b><small class="tiny">${q.length} in queue</small></div>
      <audio src="${esc(cur.url)}" controls autoplay style="width:150px;height:32px"></audio></div>` : ""}
  </div>`;
};
