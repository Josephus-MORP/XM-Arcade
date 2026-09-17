/* XM Arcade — screens 2/2: chats, wallet, settings, player, studio + sheets. */
import { $, $$, esc, fmt, money, timeAgo, ic, avatar, xlogo, coinMark, richText } from "./util.js";
import { RelayState, liveRelayCount, blossomServers, toNpub } from "./nostr.js";
import { Signer, isSignedIn, npub } from "./signer.js";
import { cachedProfile, displayName, groupChannelId, getGroupKey, DEMO } from "./data.js";
import { recentEmojis } from "./data.js";
import { Nwc, nwcConnected, Xmr, piconeroToXmr } from "./wallets.js";
import { V, SHEETS, S, SECTIONS, topbar, rowItem, emptyState, modeBadge, relayChip } from "./ui.js";

/* ================= CHATS ================= */
V.chats = (ctx = {}) => {
  const groups = ctx.groups || [];
  const g = groups.find((x) => x.id === S.groupId) || groups[0];
  if (g && !S.groupId) S.groupId = g.id;
  const channels = g ? (g.channels || [{ name: "general", topic: "everything and anything" }, { name: "media", topic: "pics, clips, apps" }]) : [];
  return `<div class="view opaque chats-view" data-view="chats">
    ${topbar("Chats", `<button class="icon-btn bare" data-act="sheet" data-sheet="newChannel" aria-label="New channel">${ic("plus")}</button>`)}
    <div style="display:flex;flex:1;min-height:0">
      <div style="width:76px;flex:0 0 auto;border-right:1px solid var(--line);overflow-y:auto;padding:10px 0 20px;display:flex;flex-direction:column;align-items:center;gap:14px">
        <button data-act="sheet" data-sheet="groupInfo" style="width:48px;height:48px;border-radius:16px;border:1.5px dashed var(--line-2);display:grid;place-items:center;color:var(--text-3)" aria-label="Add group">${ic("plus")}</button>
        ${groups.map((x) => `<button class="community-button" data-act="chatGroup" data-id="${esc(x.id)}" aria-label="${esc(x.name)}" style="${x.id === (g && g.id) ? "box-shadow:0 0 0 2px var(--primary)" : ""}">${avatar(x.name, 44, x.picture)}</button>`).join("")}
        ${ctx.loading ? `<span class="tiny">…</span>` : ""}
      </div>
      <div class="scroller" style="flex:1;padding:12px 14px">
        ${g ? `<div style="display:flex;align-items:center;gap:10px">${avatar(g.name, 40, g.picture)}
          <div style="min-width:0;flex:1"><div class="h3" style="font-size:16px">${esc(g.name)}</div><div class="tiny">${g.demo ? "demo group" : "NIP-29 group"}</div></div>
          ${modeBadge(!g.demo)}</div>
          <div style="display:flex;gap:8px;margin:10px 0"><span class="chip green">${ic("lock")} Concord · encrypted</span><span class="chip">serverless</span></div>
          <div class="h3" style="font-size:12px;letter-spacing:.1em;text-transform:uppercase;color:var(--text-3);margin:0 0 6px">Channels</div>
          ${channels.map((c) => `<button class="chan-row" data-act="openChannel" data-id="${esc(g.id)}" data-ch="${esc(c.name)}">
            <span class="ic" style="background:var(--surface-2);color:var(--text-2)">${ic("hash")}</span>
            <span style="flex:1;min-width:0"><b>${esc(c.name)}</b><small style="display:block;color:var(--text-3);font-size:12px">${esc(c.topic || "")}</small></span>
            ${getGroupKey(groupChannelId(g, c.name)) ? `<span class="tiny" style="color:var(--ok)">${ic("lock")}</span>` : ""}${ic("chevR")}</button>`).join("")}
          <button class="btn line block sm" style="margin-top:12px" data-act="sheet" data-sheet="newChannel">${ic("plus")} New channel</button>`
        : emptyState("No groups", ctx.loading ? "Asking group relays…" : "No Nostr groups on your group relays yet. Add one in Settings → Relays.", `<button class="btn sm" style="margin-top:14px" data-act="sheet" data-sheet="relays">Relay settings</button>`)}
      </div>
    </div>
  </div>`;
};

V.channel = (ctx = {}) => {
  const g = ctx.group, ch = S.channel || "general";
  const msgs = ctx.messages || [];
  const id = g ? groupChannelId(g, ch) : "";
  const hasKey = id && getGroupKey(id);
  return `<div class="view opaque" data-view="channel">
    <header class="appbar"><button class="icon-btn bare" data-act="back" aria-label="Back">${ic("chevL")}</button>
      <button class="dd-btn" data-act="dd">${avatar(g ? g.name : "c", 26)}<b>#${esc(ch)}</b></button>
      <span class="spread"></span>
      ${S.locked ? `<span class="chip green">${ic("lock")} E2EE</span>` : `<button class="chip" data-act="lockToggle" style="cursor:pointer">open</button>`}
      ${relayChip()}</header>
    <div class="tiny" style="padding:6px 16px 0">${g ? esc(g.name) : ""} · ${S.locked ? (hasKey ? "Concord-encrypted" : "locked — import the channel key") : "public channel"}</div>
    <div class="scroller" id="chatScroll" style="flex:1;padding:12px 14px;display:flex;flex-direction:column">
      ${ctx.loading ? `<p class="tiny" style="text-align:center">Loading messages…</p>` : ""}
      ${msgs.map((m) => {
        const mine = m.pubkey === Signer.pubkey;
        const card = m.appCard ? `<div class="card" style="margin:6px 0;padding:10px"><b>🎮 ${esc(m.appCard.title || "webxdc app")}</b><div class="tiny">${esc((m.appCard.file || m.appCard.url || "").slice(0, 60))}</div><button class="btn xs" style="margin-top:8px" data-act="playUrl" data-url="${esc(m.appCard.url || "")}" data-title="${esc(m.appCard.title || "Shared app")}">Play</button></div>` : "";
        const lock = m.encrypted ? ` <span class="tiny">🔒</span>` : "";
        return `<div class="msg ${mine ? "me" : "them"} ${m.encrypted ? "locked" : ""}">${mine ? "" : `<span class="who">${esc(m.who || "")}</span>`}${richText(m.text)}${lock}${card}<small>${esc(m.t || "")}</small></div>`;
      }).join("") || (ctx.loading ? "" : `<p class="tiny" style="text-align:center">Quiet in here — say hello.</p>`)}
    </div>
    <div style="padding:0 12px 6px;display:flex;gap:8px;align-items:center">
      <button class="pill ${S.locked ? "on" : ""}" data-act="lockToggle">${ic("lock")} ${S.locked ? "Locked" : "Open"}</button>
      ${S.locked ? `<button class="pill" data-act="sheet" data-sheet="chanKey">${hasKey ? "Share key" : "Import key"}</button>` : ""}
      <button class="pill" data-act="sheet" data-sheet="feedPick">🎮 Feed app</button>
    </div>
    <div class="composer-bar"><input id="chatInput" placeholder="Message #${esc(ch)}${S.locked ? " — encrypted" : ""}" autocomplete="off"><button class="send-btn" data-act="chatSend" aria-label="Send">${ic("send")}</button></div>
  </div>`;
};

/* ================= WALLET ================= */
function satsHalf(ctx) {
  const bal = ctx.satsBal;
  const ready = nwcConnected();
  return `<section class="wallet-half ${ready ? "wallet-ready" : ""}" data-wallet="sats">
    <div class="wallet-half-head">${coinMark("sats")}
      <div class="wallet-currency"><h2>Bitcoin</h2><span>sats · Lightning</span></div>
      <span class="wallet-status ${ready ? "is-ready" : ""}">${ready ? "NWC live" : "Not set up"}</span></div>
    ${!ready ? `<div class="wallet-welcome-text"><h3>Welcome to sats</h3><p>Connect your own Lightning wallet with Nostr Wallet Connect — funds stay there.</p></div>
      <div class="wallet-actions"><button class="btn block wallet-primary" data-act="sheet" data-sheet="nwc">${ic("nwcLink")} Connect NWC wallet</button><button class="btn block line" style="margin-top:8px" data-act="sheet" data-sheet="satsLocal">${ic("plus")} Create local app wallet</button></div>`
    : `<div class="wallet-balance-block"><span class="wallet-caption">LIVE BALANCE</span>
        <div class="wallet-amount tnum">${bal == null ? "…" : money(bal)} <small>sats</small></div>
        <p>via NWC · ${esc((Nwc.conn && Nwc.conn.relay) || "")}</p></div>
      <div class="wallet-ready-actions">
        <button class="btn wallet-primary" data-act="sheet" data-sheet="satsSend">${ic("send")} Send</button>
        <button class="btn line" data-act="sheet" data-sheet="satsReceive">${ic("qr")} Receive</button>
      </div>
      <div style="display:flex;gap:8px;margin-top:8px"><button class="btn sm ghost" data-act="satsRefresh">Refresh</button><button class="btn sm ghost" data-act="nwcDrop">Disconnect</button></div>`}
  </section>`;
}
function xmrHalf(ctx) {
  const st = ctx.xmr || {};
  const ready = !!st.ok;
  return `<section class="wallet-half ${ready ? "wallet-ready" : ""}" data-wallet="xmr">
    <div class="wallet-half-head">${coinMark("xmr")}
      <div class="wallet-currency"><h2>Monero</h2><span>XMR · private payments</span></div>
      <span class="wallet-status ${ready ? "is-ready" : ""}">${ready ? (st.external ? "External" : "RPC live") : "Not set up"}</span></div>
    ${!ready ? `<div class="wallet-welcome-text"><h3>Welcome to Monero</h3><p>Point XM Arcade at <b>your own</b> monero-wallet-rpc. No seeds touch this app.</p></div>
      <div class="wallet-actions"><button class="btn block wallet-primary" data-act="sheet" data-sheet="xmrLocal">${ic("plus")} Create local wallet</button>
      <button class="btn block line" style="margin-top:8px" data-act="sheet" data-sheet="xmrExternal">${ic("send")} Use external wallet</button></div>
      <div style="margin-top:8px"><button class="btn sm ghost" data-act="sheet" data-sheet="xmrCfg">Advanced: connect wallet RPC</button></div>
      ${st.error ? `<p class="tiny" style="color:var(--live);margin-top:8px">${esc(st.error)}</p>` : ""}`
    : st.external ? `<div class="wallet-balance-block"><span class="wallet-caption">EXTERNAL WALLET</span>
        <div class="wallet-amount tnum" style="font-size:19px">Pays open in your wallet app</div>
        <p>Xaps hand a Monero invoice to Cake Wallet (or any <span class="mono">monero:</span> app).</p></div>
      <div class="wallet-ready-actions">
        <button class="btn wallet-primary" data-act="sheet" data-sheet="xmrSendExt">${ic("send")} Send</button>
        <button class="btn line" data-act="sheet" data-sheet="xmrCfg">${ic("gear")} Switch to RPC</button>
      </div>`
    : `<div class="wallet-balance-block"><span class="wallet-caption">LIVE BALANCE</span>
        <div class="wallet-amount tnum">${st.balance ?? "…"} <small>XMR</small></div>
        <p class="mono" style="font-size:11px;word-break:break-all">${esc(st.address || "")}</p></div>
      <div class="wallet-ready-actions">
        <button class="btn wallet-primary" data-act="sheet" data-sheet="xmrSend">${ic("send")} Send</button>
        <button class="btn line" data-act="xmrCopy">${ic("copy")} Address</button>
      </div>
      <div style="display:flex;gap:8px;margin-top:8px"><button class="btn sm ghost" data-act="xmrRefresh">Refresh</button><button class="btn sm ghost" data-act="sheet" data-sheet="xmrCfg">RPC settings</button></div>`}
  </section>`;
}
V.wallet = (ctx = {}) => `<div class="view opaque wallet-view" data-view="wallet">
  ${topbar("Wallet", `<button class="icon-btn bare" data-act="sheet" data-sheet="hotWallet" aria-label="Hot wallet notice">${ic("info")}</button>`)}
  <div class="wallet-warning-bar"><button class="hot-wallet-chip" data-act="sheet" data-sheet="hotWallet"><span class="warning-symbol">!</span> Hot wallets <small>Read first</small></button></div>
  <div class="scroller"><div class="wallet-split">${xmrHalf(ctx)}${satsHalf(ctx)}</div>
    <div style="padding:0 16px 30px"><p class="tiny" style="line-height:1.6">Sats move through your NWC wallet; XMR moves through your wallet RPC. XM Arcade never holds funds or seeds — both halves are remote controls with big red disconnect buttons.</p></div>
  </div>
</div>`;

/* ================= SETTINGS ================= */
V.settings = (ctx = {}) => `<div class="view opaque settings-view" data-view="settings">
  ${topbar("Settings")}
  <div class="scroller" style="padding-bottom:40px">
    <div class="grouplabel">Account</div>
    <div class="rows">
      ${isSignedIn() ? rowItem({ icon: "key", title: "Back up keys", sub: npub().slice(0, 20) + "… · " + (Signer.label || ""), sheet: "keyBackup" }) + rowItem({ act: "logout", icon: "logout", title: "Log out", sub: "Forget this device session", chev: false }) : rowItem({ to: "welcome", icon: "user", title: "Sign in", sub: "Create, nsec, or Amber" })}
    </div>
    <div class="grouplabel">Appearance</div>
    <div class="rows">
      ${rowItem({ act: "themeFlip", icon: "spark", title: "Theme", sub: S.theme === "dark" ? "Dark" : "Light", value: S.theme, chev: false })}
      <div class="row" style="cursor:default"><span style="min-width:0;flex:1"><b>Secondary color</b><small>Slide to recolor accents</small><input type="range" class="accentHue" min="0" max="360" step="1" value="${S.accentHue ?? 187}" aria-label="Secondary color" style="width:100%;margin:10px 0 2px"></span></div>
      ${rowItem({ icon: "home", title: "Starting pane", sub: "Opens to " + (SECTIONS.find((x) => x.id === S.startingPane)?.label || S.startingPane), sheet: "startingPane" })}
    </div>
    <div class="grouplabel">Network</div>
    <div class="rows">
      ${rowItem({ icon: "relay", title: "Relays", sub: `${liveRelayCount()}/${RelayState.relays.length} live · tap to add or remove`, sheet: "relays" })}
      ${rowItem({ icon: "users", title: "Group relays (NIP-29)", sub: RelayState.groupRelays.join(", ") || "none", sheet: "groupRelays" })}
      ${rowItem({ icon: "upload", title: "Blossom servers", sub: blossomServers()[0] || "none", sheet: "blossom" })}
    </div>
    <div class="grouplabel">Tags</div>
    <div class="rows">
      ${rowItem({ icon: "hash", title: "Followed tags", sub: S.followTags.map((t) => "#" + t).join(" ") || "none yet", sheet: "tags" })}
    </div>
    <div class="grouplabel">About</div>
    <div class="rows">
      ${rowItem({ icon: "info", title: "XM Arcade", sub: "Live Nostr client · Amber-compatible", value: "v1.0", sheet: "about" })}
    </div>
  </div>
</div>`;

/* ================= GAME RUN ================= */
V.gamerun = (ctx = {}) => {
  const a = ctx.app || { title: S.game || "Game" };
  return `<div class="view immersive opaque" data-view="gamerun">
    <div class="game-frame-wrap">
      <div class="game-topbar">
        <button class="icon-btn" style="background:rgba(255,255,255,.12);color:#fff" data-act="back" aria-label="Back">${ic("chevL")}</button>
        <div style="flex:1;min-width:0"><b style="font-size:15px">${esc(a.title)}</b><div class="tiny" style="color:#9aa">${esc(a.source || "webxdc")} · sandboxed</div></div>
        <button class="icon-btn" style="background:rgba(255,255,255,.12);color:#fff" data-act="feedApp" data-id="${esc(a.id || "")}" aria-label="Feed to channel">${ic("send")}</button>
      </div>
      <div id="gameHost" style="flex:1;position:relative;background:#0b0d14">
        <p class="tiny" style="text-align:center;padding:30px;color:#9aa">Loading ${esc(a.title)}…</p>
      </div>
    </div>
  </div>`;
};

/* ================= STUDIO (upload short / track) ================= */
V.studio = () => `<div class="view opaque" data-view="studio">
  <header class="appbar"><button class="icon-btn bare" data-act="back" aria-label="Back">${ic("chevL")}</button><b style="font-size:15px">Studio</b><span class="spread"></span>${relayChip()}</header>
  <div class="scroller" style="padding:20px">
    <h1 class="h2">Publish media</h1>
    <p class="sub" style="margin:8px 0 16px">Video becomes a NIP-71 short, audio becomes a music track. Files upload to your Blossom server first — relays only carry the signed pointer.</p>
    <label class="tiny" style="display:block;margin:0 0 7px;font-weight:700">File</label>
    <input type="file" id="studioFile" class="field" accept="video/*,audio/*">
    <label class="tiny" style="display:block;margin:16px 0 7px;font-weight:700">Title</label>
    <input class="field" id="studioTitle" placeholder="Give it a name" maxlength="90">
    <label class="tiny" style="display:block;margin:16px 0 7px;font-weight:700">Hashtags <span style="font-weight:500;color:var(--text-3)">(space separated)</span></label>
    <input class="field" id="studioTags" placeholder="relayweek video" maxlength="120">
    <div id="studioPrev" style="margin-top:14px"></div>
    <button class="btn block" style="margin-top:16px" data-act="studioGo">Upload & publish</button>
    <p class="tiny" style="margin-top:10px;line-height:1.6" id="studioStatus">Blossom: ${esc(blossomServers()[0] || "none configured")}</p>
  </div>
</div>`;

/* ================= COMPOSE ================= */
V.compose = () => `<div class="view opaque" data-view="compose">
  <header class="appbar"><button class="icon-btn bare" data-act="back" aria-label="Back">${ic("chevL")}</button><b style="font-size:15px">New note</b><span class="spread"></span><button class="btn sm" data-act="postGo">Post</button></header>
  <div class="scroller" style="padding:16px 20px">
    <textarea class="field" id="composeText" placeholder="What's happening on the relays?" style="min-height:140px"></textarea>
    <div style="display:flex;gap:8px;margin-top:10px">
      <label class="btn sm ghost" style="cursor:pointer">Attach<input type="file" id="composeFile" hidden accept="image/*,video/*,audio/*"></label>
      <span class="tiny" id="composeAtt" style="align-self:center"></span>
    </div>
    <p class="tiny" style="margin-top:12px">Signed as ${esc(isSignedIn() ? npub().slice(0, 18) + "…" : "nobody")} → ${RelayState.relays.length} relays.</p>
  </div>
</div>`;

/* ================= SHEETS ================= */
SHEETS.relays = () => ({
  title: "Relays", sub: `${liveRelayCount()}/${RelayState.relays.length} connected`,
  body: `<div id="relayList">${RelayState.relays.map((u) => relayRow(u)).join("")}</div>
    <div style="display:flex;gap:8px;margin-top:10px"><input class="field" id="relayAdd" placeholder="wss://…"><button class="btn" data-act="relayAdd">Add</button></div>
    <button class="btn line block" style="margin-top:10px" data-act="relayReset">Restore defaults</button>`,
});
function relayRow(u) {
  const st = (window.__relayStatus && window.__relayStatus(u)) || "connecting";
  return `<div class="relay-row"><span class="live-dot ${st === "live" ? "" : st === "down" ? "down" : "idle"}"></span><code>${esc(u)}</code><button class="icon-btn bare" data-act="relayDel" data-url="${esc(u)}" aria-label="Remove">${ic("trash")}</button></div>`;
}
SHEETS.groupRelays = () => ({
  title: "Group relays", sub: "NIP-29 Concord servers",
  body: `${RelayState.groupRelays.map((u) => `<div class="relay-row"><span class="live-dot idle"></span><code>${esc(u)}</code><button class="icon-btn bare" data-act="groupRelayDel" data-url="${esc(u)}">${ic("trash")}</button></div>`).join("")}
    <div style="display:flex;gap:8px;margin-top:10px"><input class="field" id="groupRelayAdd" placeholder="wss://groups.…"><button class="btn" data-act="groupRelayAdd">Add</button></div>
    <p class="tiny" style="margin-top:10px">Try <span class="mono">wss://groups.fiatjaf.com</span> or your community's own group relay.</p>`,
});
SHEETS.blossom = () => ({
  title: "Blossom servers", sub: "Media uploads (BUD-01)",
  body: `${blossomServers().map((u, i) => `<div class="relay-row"><code>${esc(u)}</code>${i > 0 ? `<button class="icon-btn bare" data-act="blossomDel" data-url="${esc(u)}">${ic("trash")}</button>` : `<span class="tiny">primary</span>`}</div>`).join("")}
    <div style="display:flex;gap:8px;margin-top:10px"><input class="field" id="blossomAdd" placeholder="https://…"><button class="btn" data-act="blossomAdd">Add</button></div>`,
});
SHEETS.author = (ctx = {}) => {
  const p = cachedProfile(ctx.pk || "");
  const pk = ctx.pk || "";
  if (!pk) return { title: "Nobody", body: `<p class="sub">Demo placeholder — no key.</p>` };
  const followed = S.followUsers.includes(pk);
  return {
    title: displayName(p), sub: "@" + (p.name || pk.slice(0, 8)),
    body: `<div style="display:flex;justify-content:center;margin-bottom:12px">${avatar(displayName(p), 76, p.picture)}</div>
    ${p.about ? `<p class="sub" style="text-align:center;margin-bottom:12px">${esc(p.about)}</p>` : ""}
    <div class="rows" style="padding:0">
      ${pk !== Signer.pubkey ? rowItem({ act: "follow", pk, icon: followed ? "check" : "plus", title: followed ? "Following" : "Follow", sub: "Their notes join your follows", chev: false }) : ""}
      ${rowItem({ act: "visitProfile", pk, icon: "user", title: "Visit profile", chev: false })}
      ${rowItem({ act: "zap", pk, icon: "zap", tint: "var(--zap)", title: "Zap", sub: p.lud16 || p.lud06 || "needs a Lightning address", chev: false })}
      ${rowItem({ act: "xap", pk, icon: "xmr", tint: "#ed4357", title: "Xap", sub: "Send XMR via wallet RPC", chev: false })}
      ${rowItem({ act: "copyNpub", pk, icon: "copy", title: "Copy npub", chev: false })}
    </div>`,
  };
};
SHEETS.react = (ctx = {}) => {
  const quick = ["\u2764\uFE0F", "\u{1F602}", "\u{1F525}", "\u{1F44D}", "\u{1F389}", "\u{1F62E}", "\u{1F622}", "\u{1F44F}"];
  const recent = recentEmojis();
  const cell = (inner, emoji, eurl) => `<button class="react-cell" data-act="reactGo" data-eid="${esc(ctx.eid || "")}" data-emoji="${esc(emoji)}"${eurl ? ` data-eurl="${esc(eurl)}"` : ""}>${inner}</button>`;
  return {
    title: "React", sub: "Pick an emoji",
    body: `<div class="react-grid">${quick.map((e) => cell(`<span style="font-size:22px">${e}</span>`, e)).join("")}</div>
    ${recent.length ? `<div class="tiny" style="font-weight:800;margin:10px 0 6px">Recent</div><div class="react-grid">${recent.map((r) => r.u ? cell(`<img src="${esc(r.u)}" alt="${esc(r.t)}" loading="lazy">`, r.t, r.u) : cell(`<span style="font-size:22px">${esc(r.t)}</span>`, r.t)).join("")}</div>` : ""}
    <div id="reactPacks" data-eid="${esc(ctx.eid || "")}"><p class="tiny" style="margin-top:10px">Loading emoji packs…</p></div>
    <div style="display:flex;gap:8px;margin-top:12px"><input class="field" id="reactCustom" placeholder="Any emoji…" autocomplete="off"><button class="btn" data-act="reactGo" data-eid="${esc(ctx.eid || "")}">Send</button></div>
    <p class="tiny" style="margin-top:8px;text-align:center">Your phone keyboard has the full emoji set.</p>`,
  };
};
SHEETS.zap = (ctx = {}) => ({
  title: "Lightning zap", sub: ctx.eid ? "for this post" : "to this user",
  body: `<div class="zap-grid">${[21, 210, 1000, 5000].map((a) => `<button class="zap-amt ${S.zapAmt === a ? "on" : ""}" data-act="zapAmt" data-val="${a}">⚡ ${money(a)}<small>sats</small></button>`).join("")}</div>
    <input class="field" id="zapCustom" inputmode="numeric" placeholder="Custom sats amount">
    <input class="field" id="zapComment" placeholder="Comment (optional)" style="margin-top:8px">
    <button class="btn block" style="margin-top:12px" data-act="zapGo" data-eid="${esc(ctx.eid || "")}" data-pk="${esc(ctx.pk || "")}">${ic("zap")} Zap it</button>
    <p class="tiny" style="margin-top:10px;text-align:center">${nwcConnected() ? "Pays from your NWC wallet." : "Connect an NWC wallet in Wallet → sats first."}</p>`,
});
SHEETS.needWallet = (ctx = {}) => ({
  title: "Connect a wallet",
  sub: ctx.kind === "xmr" ? "for XMR xaps" : "for sats zaps",
  body: `<p class="sub" style="text-align:center;margin:6px 0 14px">${ctx.kind === "xmr" ? "Please connect a wallet to xap." : "Please connect a wallet to zap."}</p>
    <button class="btn block" data-act="needWalletGo">Open Wallet</button>`,
});
SHEETS.xap = (ctx = {}) => ({
  title: "Monero xap", sub: ctx.eid ? "for this post" : "to this user",
  body: `<div class="zap-grid">${[0.001, 0.01, 0.05, 0.1].map((a) => `<button class="zap-amt xap-amt ${S.xapAmt === a ? "on" : ""}" data-act="xapAmt" data-val="${a}" data-eid="${esc(ctx.eid || "")}" data-pk="${esc(ctx.pk || "")}">${coinMark("xmr", 15)} ${a}<small>XMR</small></button>`).join("")}</div>
    <input class="field" id="xapCustom" inputmode="decimal" placeholder="Custom XMR amount">
    <label class="tiny" style="display:block;margin:10px 0 4px">Their Monero address</label>
    <input class="field mono" id="xapTo" placeholder="4…" autocapitalize="off" spellcheck="false">
    <button class="btn block" style="margin-top:12px" data-act="xapGo" data-eid="${esc(ctx.eid || "")}" data-pk="${esc(ctx.pk || "")}">${coinMark("xmr", 18)} ${Xmr.mode === "external" ? "Xap via wallet app" : "Xap it"}</button>
    <p class="tiny" style="margin-top:10px;text-align:center">${Xmr.mode === "external" ? "Opens your wallet app (e.g. Cake Wallet) with the payment ready." : "Pays from your wallet RPC (Wallet → Monero)."} Profiles don\u2019t carry XMR addresses yet — ask them for theirs. No public receipt: the amount stays private.</p>`,
});
SHEETS.editProfile = () => {
  const p = isSignedIn() ? cachedProfile(Signer.pubkey) : {};
  return {
    title: "Edit profile", sub: "Publishes kind 0 to your relays",
    body: `<label class="tiny">Display name</label><input class="field" id="epName" value="${esc(p.display_name || p.name || "")}" style="margin:4px 0 10px">
    <label class="tiny">About</label><input class="field" id="epAbout" value="${esc(p.about || "")}" style="margin:4px 0 10px">
    <label class="tiny">Picture URL</label><input class="field" id="epPic" value="${esc(p.picture || "")}" style="margin:4px 0 10px">
    <label class="tiny">Lightning address (lud16)</label><input class="field" id="epLud" value="${esc(p.lud16 || "")}" placeholder="you@…" style="margin:4px 0 12px">
    <button class="btn block" data-act="profileSave">Save profile</button>`,
  };
};
SHEETS.keyBackup = () => {
  const local = Signer.method === "local";
  return {
    title: "Back up keys", sub: Signer.label || "session",
    body: `<label class="tiny">Public (npub — safe to share)</label><div class="keybox" style="margin:6px 0 10px">${esc(npub())}</div>
    <button class="btn sm ghost" data-act="copyNpub" data-pk="${Signer.pubkey || ""}">Copy npub</button>
    ${local ? `<label class="tiny" style="display:block;margin:14px 0 6px">Secret (nsec — NEVER share)</label>
      <input class="field mono" id="nsecReveal" type="password" readonly value="hidden — tap reveal">
      <div style="display:flex;gap:8px;margin-top:8px"><button class="btn sm line" data-act="nsecShow">Reveal</button><button class="btn sm line" data-act="nsecCopy">Copy</button></div>
      <p class="tiny" style="margin-top:10px;color:var(--live)">Anyone with this key IS you. Store it in Amber or offline — never in screenshots or chats.</p>`
    : `<p class="tiny" style="margin-top:12px;line-height:1.7">Your secret lives in your ${esc(Signer.method === "nip46" ? "remote signer (Amber)" : "browser extension")}. Back it up there — XM Arcade can't show what it never held.</p>`}`,
  };
};
SHEETS.appUpload = () => ({
  title: "Upload app", sub: ".xdc · .webxdc · .zip · single .html (20 MB)",
  body: `<input type="file" id="appFile" class="field" accept=".xdc,.webxdc,.zip,.html">
    <label class="tiny" style="display:block;margin:12px 0 4px">Title *</label><input class="field" id="appTitle" maxlength="48">
    <label class="tiny" style="display:block;margin:12px 0 4px">Description</label><input class="field" id="appDesc" maxlength="140">
    <label class="tiny" style="display:block;margin:12px 0 4px">Tags (space separated)</label><input class="field" id="appTags" placeholder="arcade puzzle">
    <button class="btn block" style="margin-top:14px" data-act="appUploadGo">Add to arcade</button>
    <p class="tiny" style="margin-top:10px">Stored on this device (IndexedDB). Use Feed to share it to a channel via Blossom.</p>`,
});
SHEETS.feedPick = () => ({
  title: "Feed to this channel", sub: "Pick an app from your arcade",
  body: `<div id="feedAppList"><p class="tiny">Loading arcade…</p></div>`,
});
SHEETS.feedApp = (ctx = {}) => ({
  title: "Feed to a channel", sub: `${ctx.title || "This app"} will appear inside the channel`,
  body: `<div id="feedGroups"><p class="tiny">Loading your groups…</p></div>`,
});
SHEETS.newChannel = () => ({
  title: "New channel", sub: "Concord channels are just tags — anyone can start one",
  body: `<input class="field" id="newChanName" placeholder="channel-name" maxlength="32">
    <label style="display:flex;gap:10px;align-items:center;margin:12px 0"><input type="checkbox" id="newChanLock" checked style="width:20px;height:20px"><span><b>🔒 Concord-encrypted</b><br><small class="tiny">A fresh channel key is made on this device.</small></span></label>
    <button class="btn block" data-act="newChanGo">Create channel</button>`,
});
SHEETS.chanKey = () => ({
  title: "Channel key", sub: "Concord-encrypted channel",
  body: `<label class="tiny">This device's key (hex)</label><div class="keybox" id="chanKeyBox" style="margin:6px 0 10px">…</div>
    <div style="display:flex;gap:8px"><button class="btn sm line" data-act="chanKeyCopy">Copy</button><button class="btn sm line" data-act="chanKeyNew">New key</button></div>
    <label class="tiny" style="display:block;margin:14px 0 6px">Import a key from an admin</label>
    <div style="display:flex;gap:8px"><input class="field mono" id="chanKeyImport" placeholder="64 hex chars"><button class="btn" data-act="chanKeyImport">Save</button></div>
    <label class="tiny" style="display:block;margin:14px 0 6px">Send key to a member (device-key sessions)</label>
    <div style="display:flex;gap:8px"><input class="field mono" id="chanKeyTo" placeholder="npub1…"><button class="btn" data-act="chanKeyShare">Send</button></div>`,
});
SHEETS.nwc = () => ({
  title: "Connect NWC wallet", sub: "Nostr Wallet Connect (NIP-47)",
  body: `<p class="sub" style="margin-bottom:10px">From Alby Hub, Mutiny+, or any NWC provider: create a connection with <b>get_balance · pay · invoices</b> permission and paste it here.</p>
    <input class="field mono" id="nwcUri" placeholder="nostr+walletconnect://…" autocapitalize="off" spellcheck="false">
    <button class="btn block" style="margin-top:12px" data-act="nwcGo">Connect</button>
    <p class="tiny" style="margin-top:10px">The connection secret stays on this device. Budgets live in your wallet, not here.</p>`,
});
SHEETS.satsReceive = () => ({
  title: "Receive sats", sub: "Invoice from your NWC wallet",
  body: `<label class="tiny">Amount (sats)</label><input class="field" id="invAmt" inputmode="numeric" value="1000" style="margin:4px 0 10px">
    <label class="tiny">Memo</label><input class="field" id="invMemo" value="XM Arcade" style="margin:4px 0 12px">
    <button class="btn block" data-act="invGo">Create invoice</button><div id="invOut" style="margin-top:12px"></div>`,
});
SHEETS.satsSend = () => ({
  title: "Send sats", sub: "Pay a BOLT11 invoice or Lightning address",
  body: `<input class="field mono" id="payTo" placeholder="lnbc… or you@…" style="margin-bottom:12px">
    <button class="btn block" data-act="payGo">Pay</button><div id="payOut" style="margin-top:12px"></div>`,
});
SHEETS.xmrCfg = () => ({
  title: "Monero RPC", sub: "Your node, your wallet file",
  body: `<label class="tiny">monero-wallet-rpc URL</label><input class="field mono" id="xmrUrl" value="${esc(Xmr.cfg.url)}" style="margin:4px 0 10px">
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px"><div><label class="tiny">RPC user</label><input class="field" id="xmrUser" value="${esc(Xmr.cfg.user)}" style="margin-top:4px"></div><div><label class="tiny">RPC pass</label><input class="field" id="xmrPass" type="password" value="${esc(Xmr.cfg.pass)}" style="margin-top:4px"></div></div>
    <button class="btn block" style="margin-top:12px" data-act="xmrGo">Save & test</button>
    <p class="tiny" style="margin-top:10px;line-height:1.7">Run e.g. <span class="mono">monero-wallet-rpc --rpc-bind-port 18082 --disable-rpc-login</span> on a machine you trust, ideally with restricted RPC + TLS. This page never asks for your 25-word seed.</p>`,
});
SHEETS.xmrSend = () => ({
  title: "Send XMR", sub: "via your wallet RPC",
  body: `<label class="tiny">Destination address</label><input class="field mono" id="xmrTo" placeholder="4…" style="margin:4px 0 10px">
    <label class="tiny">Amount (XMR)</label><input class="field" id="xmrAmt" inputmode="decimal" placeholder="0.01" style="margin:4px 0 12px">
    <button class="btn block" data-act="xmrSendGo">Review & send</button><div id="xmrOut" style="margin-top:12px"></div>`,
});
SHEETS.xmrLocal = () => ({
  title: "Local Monero wallet", sub: "What it would take",
  body: `<div class="card"><p class="sub" style="line-height:1.7">A real local Monero wallet must download and scan the <b>whole chain</b> (100&nbsp;GB+, hours of sync) — there is no secure shortcut, so XM Arcade doesn't fake one.</p></div>
    <div style="display:flex;flex-direction:column;gap:8px;margin-top:12px">
      <button class="btn block" data-act="sheet" data-sheet="xmrExternal">Use external wallet instead</button>
      <button class="btn block line" data-act="sheet" data-sheet="xmrCfg">Advanced: connect wallet RPC</button>
    </div>`,
});
SHEETS.xmrExternal = () => ({
  title: "Use external wallet", sub: "Cake Wallet & friends",
  body: `<p class="sub" style="line-height:1.7">Xaps and sends open a <span class="mono">monero:</span> payment in your wallet app with the address + amount filled in. <a href="https://cakewallet.com" target="_blank" rel="noopener"><b>Cake Wallet</b></a> (free, open source) is a good pick on Android and iOS.</p>
    ${Xmr.mode === "external" ? `<p class="tiny" style="color:var(--ok);margin-top:10px">External wallet is active.</p>` : `<button class="btn block" style="margin-top:12px" data-act="xmrExternalGo">Set external wallet</button>`}`,
});
SHEETS.xmrSendExt = () => ({
  title: "Send XMR", sub: "via your wallet app",
  body: `<label class="tiny">Destination address</label><input class="field mono" id="xmrExtTo" placeholder="4…" style="margin:4px 0 10px">
    <label class="tiny">Amount (XMR)</label><input class="field" id="xmrExtAmt" inputmode="decimal" placeholder="0.01" style="margin:4px 0 12px">
    <button class="btn block" data-act="xmrExtGo">${coinMark("xmr", 18)} Open in wallet app</button>`,
});
SHEETS.satsLocal = () => ({
  title: "Local Lightning wallet", sub: "Self-custodial in 5 minutes",
  body: `<div class="card"><p class="sub" style="line-height:1.7">A true in-app Lightning node needs an always-on server, so XM Arcade connects to <b>your own node</b> instead. <a href="https://getalby.com" target="_blank" rel="noopener"><b>Alby Hub</b></a> (free, open source, self-custodial) runs on a home computer or Start9 box:</p></div>
    <p class="tiny" style="margin:10px 0 4px;line-height:1.7">1. Install Alby Hub and create a wallet<br>2. Copy its NWC connection string<br>3. Paste it below:</p>
    <input class="field mono" id="localNwc" placeholder="nostr+walletconnect://…" autocapitalize="off" spellcheck="false">
    <button class="btn block" style="margin-top:12px" data-act="localGo">${ic("nwcLink")} Connect my node</button>`,
});
SHEETS.hotWallet = () => ({
  title: "Hot wallet notice",
  body: `<div class="card"><p class="sub" style="line-height:1.7">These are <b>hot wallets</b> — convenient for zaps and play money, wrong for life savings. For large values use a dedicated wallet (Cake Wallet, Feather, a hardware signer) and keep most funds cold.</p></div>`,
  footer: `<button class="btn block" data-act="closeSheet">I understand</button>`,
});
SHEETS.tags = () => ({
  title: "Followed tags", sub: "Powers the Shorts tags tab",
  body: `<div class="chiprow" style="margin-bottom:10px">${S.followTags.map((t) => `<span class="pill on">#${esc(t)} <button data-act="tagDel" data-tag="${esc(t)}" style="margin-left:4px">✕</button></span>`).join("") || `<span class="tiny">none yet</span>`}</div>
    <div style="display:flex;gap:8px"><input class="field" id="tagAdd" placeholder="add tag…"><button class="btn" data-act="tagAdd">Add</button></div>`,
});
SHEETS.startingPane = () => ({
  title: "Starting pane", sub: "Opens here after sign-in",
  body: `<div class="rows" style="padding:0">${SECTIONS.map((s) => rowItem({ act: "setStart", sec: s.id, icon: s.icon, title: s.label, tail: S.startingPane === s.id ? `<span class="tiny" style="color:var(--primary)">current</span>` : "", chev: false })).join("")}</div>`,
});
SHEETS.groupInfo = () => ({
  title: "Concord groups", sub: "NIP-29 + Concord encryption",
  body: `<p class="sub" style="line-height:1.7">Groups live on <b>group relays</b> (NIP-29). Public channels are signed plaintext; 🔒 channels add a shared <b>Concord key</b> (NIP-44) that admins pass to members as gift-wrapped secrets.</p>
  <div class="rows" style="padding:12px 0 0">${rowItem({ act: "sheet2", sheet: "groupRelays", icon: "relay", title: "Group relays", sub: RelayState.groupRelays.join(", ") || "none" })}</div>`,
});
SHEETS.about = () => ({
  title: "XM Arcade", sub: "v1.0 · live Nostr client",
  body: `<p class="sub" style="line-height:1.7">Shorts (NIP-71) · Concord groups (NIP-29 + NIP-44/59) · sats via NWC + zaps (NIP-47/57) · Monero via wallet RPC · music · webxdc arcade.<br><br>Signers: device key or NIP-46 remote (Amber). Crypto: vendored nostr-tools (MIT, paulmillr.com) — works offline, relays excluded.</p>`,
});
SHEETS.noteThread = (ctx = {}) => ({ title: "Replies", sub: "Thread", body: `<div id="threadBody"><p class="tiny">Loading…</p></div>` });

export function paneMenuItems(current) {
  return SECTIONS.map((s) => `<button class="dd-item" data-act="section" data-sec="${s.id}"><span class="ic">${ic(s.icon)}</span><span class="pane-menu-copy"><b>${s.label}</b><small>${s.sub}</small></span>${current === s.id ? `<span class="cur">${ic("check")}</span>` : ""}</button>`).join("");
}
export function mapView() {
  const groups = [
    ["Getting in", [["welcome", "Welcome", "three ways in"], ["createAccount", "Create account", "fresh keypair"], ["loginNsec", "Login with nsec", "paste a key"], ["loginSigner", "Login with signer", "Amber / NIP-46"]]],
    ["Arcade", [["shorts", "Shorts", "NIP-71 feed"], ["miniapps", "Mini Apps", "webxdc"], ["music", "Music", "tracks + queue"], ["studio", "Studio", "publish media"]]],
    ["Social", [["profile", "Profile", "you + notes"], ["chats", "Chats", "Concord groups"], ["channel", "Channel", "inside a group"], ["compose", "Compose", "new note"]]],
    ["Money & system", [["wallet", "Wallet", "XMR + sats"], ["settings", "Settings", "relays, keys, tags"]]],
  ];
  return `<div class="view opaque" data-view="map"><header class="appbar"><button class="icon-btn bare" data-act="back">${ic("close")}</button><b>Screen map</b><span class="spread"></span><span class="chip">live build</span></header>
  <div class="scroller" style="padding:6px 16px 30px">${groups.map(([g, items]) => `<div class="grouplabel">${g}</div><div class="map-grid">${items.map(([id, t, s]) => `<button class="map-card" data-act="mapGo" data-view="${id}"><span class="ic" style="background:var(--primary-dim);color:var(--primary)">${ic("grid")}</span><b>${t}</b><small>${s}</small></button>`).join("")}</div>`).join("")}</div></div>`;
}
V.map = () => mapView();
