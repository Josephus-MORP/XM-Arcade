/* XM Arcade — shared utilities (no network). */
export const $ = (s, r = document) => r.querySelector(s);
export const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
export const esc = (s) =>
  String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
export const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
  del(k) { try { localStorage.removeItem(k); } catch {} },
};
export const fmt = (n) => {
  n = Number(n) || 0;
  if (n >= 1e6) return (n / 1e6).toFixed(1).replace(/\.0$/, "") + "M";
  if (n >= 1000) return (n / 1000).toFixed(n >= 10000 ? 0 : 1).replace(/\.0$/, "") + "K";
  return String(n);
};
export const money = (n) => Number(n || 0).toLocaleString("en-US");
export function timeAgo(ts) {
  const s = Math.max(1, Math.floor(Date.now() / 1000) - Number(ts || 0));
  if (s < 60) return s + "s";
  if (s < 3600) return Math.floor(s / 60) + "m";
  if (s < 86400) return Math.floor(s / 3600) + "h";
  if (s < 86400 * 7) return Math.floor(s / 86400) + "d";
  return new Date(Number(ts) * 1000).toLocaleDateString();
}
export const debounce = (fn, ms = 250) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
export const uid = () => Math.random().toString(36).slice(2, 10);
export function hash(s) { let h = 2166136261; s = String(s); for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return Math.abs(h); }

/* ---- icon set (same 24x24 stroke vocabulary as the mockups) ---- */
export const I = {
  home: "M4 11.2 12 4l8 7.2V20a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z",
  compass: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18M15.4 8.6l-2 4.8-4.8 2 2-4.8z",
  game: "M8 12h2m-1-1v2m7-1h.01M17.5 13h.01M17 6H7a5 5 0 0 0-4.9 6l1 4.4A2.6 2.6 0 0 0 6.6 18c1 0 1.6-.5 2.2-1.2L10 15.4h4l1.2 1.4c.6.7 1.2 1.2 2.2 1.2a2.6 2.6 0 0 0 2.5-1.6l1-4.4A5 5 0 0 0 17 6z",
  users: "M9 12.5a4 4 0 1 0 0-8 4 4 0 0 0 0 8m7.5-.5a3.2 3.2 0 1 0 0-6.4M3 20a6 6 0 0 1 12 0m2.2-5.2A5.6 5.6 0 0 1 21 20",
  user: "M12 12a4.2 4.2 0 1 0 0-8.4A4.2 4.2 0 0 0 12 12M4.5 20.5a7.5 7.5 0 0 1 15 0",
  chevR: "M9.5 5.5 16 12l-6.5 6.5", chevL: "M14.5 5.5 8 12l6.5 6.5", chevD: "M6 9.5 12 16l6-6.5",
  close: "M6 6l12 12M18 6 6 18", plus: "M12 5v14M5 12h14",
  bell: "M18 8.5a6 6 0 1 0-12 0c0 5.2-2 6.5-2 6.5h16s-2-1.3-2-6.5M10.3 19a2 2 0 0 0 3.4 0",
  msg: "M20.5 11.6c0 4.2-3.8 7.6-8.5 7.6a9.7 9.7 0 0 1-2.7-.4L4.5 20.5l1.3-3.6A7.3 7.3 0 0 1 3.5 11.6C3.5 7.4 7.3 4 12 4s8.5 3.4 8.5 7.6z",
  play: "M8 5.2 19 12 8 18.8z", pause: "M9 5h2.2v14H9zM12.8 5H15v14h-2.2z",
  mic: "M12 15.5a3.5 3.5 0 0 0 3.5-3.5V6a3.5 3.5 0 0 0-7 0v6a3.5 3.5 0 0 0 3.5 3.5M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18.5V21",
  camera: "M4 8.5A1.5 1.5 0 0 1 5.5 7h1.9l1.2-2h6.8l1.2 2h1.9A1.5 1.5 0 0 1 20 8.5v9A1.5 1.5 0 0 1 18.5 19h-13A1.5 1.5 0 0 1 4 17.5zM12 16a3.4 3.4 0 1 0 0-6.8 3.4 3.4 0 0 0 0 6.8",
  zap: "M13.5 2 4 14h6.5L10 22l9.5-12H13z",
  xmr: "M4.5 17.5v-11L12 14l7.5-7.5v11",
  reply: "M9 6 4 10.5 9 15M4.5 10.5H14a5.5 5.5 0 0 1 5.5 5.5v3",
  share: "M12 16V4m0 0L8 8m4-4 4 4M5 13v5.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V13",
  search: "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14m5.5 1.5L21 24",
  lock: "M6.5 10.5h11v9h-11zM8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5",
  key: "M15.5 3a5.5 5.5 0 1 0-4.2 9 5.6 5.6 0 0 0 1.9.3H14v2.2h2.2V17H18v2.4h3.2V14a5.5 5.5 0 0 0-5.7-11z",
  relay: "M12 3v3.5M4.5 8.5A9 9 0 0 0 12 21a9 9 0 0 0 7.5-12.5M12 12m-3 0a3 3 0 1 0 6 0 3 3 0 1 0-6 0",
  gear: "M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.3a2 2 0 1 1-4 0v-.2a1.6 1.6 0 0 0-2.8-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 3.6 14H3.3a2 2 0 1 1 0-4h.2a1.6 1.6 0 0 0 1.1-2.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V3a2 2 0 1 1 4 0v.2a1.6 1.6 0 0 0 2.8 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.3a2 2 0 1 1 0 4h-.2a1.6 1.6 0 0 0-1.1 1.1z",
  hash: "M5 9h14M5 15h14M10 4 8.5 20M15.5 4 14 20",
  eye: "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6",
  eyeOff: "M4 4l16 16M9.9 5.9A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.6 3.4M6.4 7.9A16.7 16.7 0 0 0 2.5 12S6 18.5 12 18.5a9.7 9.7 0 0 0 3.3-.6M9.9 10.2a3 3 0 0 0 4 4",
  image: "M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5zM8.8 11a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2M4.5 17.5 9.8 12l3.2 3 2.8-2.4 3.7 4.9",
  video: "M3.5 7.5A1.5 1.5 0 0 1 5 6h8a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 13 18H5a1.5 1.5 0 0 1-1.5-1.5zM15.5 11l5-3v8l-5-3z",
  check: "M4.5 12.5 9.5 17.5 20 6.5",
  shield: "M12 3 5 6v6c0 4.4 3 7.6 7 9 4-1.4 7-4.6 7-9V6zM9 12l2.2 2.2L15.5 10",
  send: "M4.5 12 20 4.5 15 20l-3.4-6.1z",
  copy: "M9 9h9.5v11H9zM5.5 15H4V4h11v1.6",
  edit: "M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17zM14.5 6.5l3 3",
  qr: "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2.5v2.5H14zM17.5 17.5H20V20h-2.5z",
  trash: "M5 7h14M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13M10.5 11v5.5M13.5 11v5.5",
  vol: "M5 9.5h3L12 6v12l-4-3.5H5zM16 9.5a3.5 3.5 0 0 1 0 5M18.5 7a7 7 0 0 1 0 10",
  globe: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M3.5 9h17M3.5 15h17M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18",
  info: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M12 11v5.5M12 7.6h.01",
  refresh: "M20 12a8 8 0 1 1-2.6-5.9M20 4v4.5h-4.5",
  bolt: "M11 3 5 13h5l-1 8 6-10h-5z",
  heart: "M12 20s-7-4.4-7-9.4A4.1 4.1 0 0 1 12 8a4.1 4.1 0 0 1 7 2.6C19 15.6 12 20 12 20",
  grid: "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
  list: "M4 7h16M4 12h16M4 17h16",
  dots: "M12 6h.01M12 12h.01M12 18h.01",
  spark: "M12 3.5 13.9 9l5.6 1.9-5.6 1.9L12 18.4l-1.9-5.6L4.5 11l5.6-1.9z",
  more: "M6 12h.01M12 12h.01M18 12h.01",
  wallet: "M4 7.5A1.5 1.5 0 0 1 5.5 6H18v3M4 7.5v10A1.5 1.5 0 0 0 5.5 19h14v-4.5M19.5 9.5H21v5h-1.5a2.5 2.5 0 0 1 0-5",
  smile: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M9 9.8h.01M15 9.8h.01M8.3 13.6a4.6 4.6 0 0 0 7.4 0",
  note: "M9 17.5V6l10-2v11M9 17.5a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0m10-2.5a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0",
  prev: "M17 6l-8 6 8 6zM7 6v12", next: "M7 6l8 6-8 6zM17 6v12",
  upload: "M12 16V5m0 0-4.5 4.5M12 5l4.5 4.5M5 19h14",
  filter: "M4 6h16M7 12h10M10 18h4",
  flag: "M6 21V4h11l-2 4 2 4H6",
  wave: "M3 12h2l2-5 3 11 3-8 2 4h6",
  nwcLink: "M10 13a5 5 0 0 0 7.1 0l3-3a5 5 0 0 0-7.1-7.1l-1.7 1.7M14 11a5 5 0 0 0-7.1 0l-3 3a5 5 0 0 0 7.1 7.1l1.7-1.7",
  logout: "M9 4H5.5A1.5 1.5 0 0 0 4 5.5v13A1.5 1.5 0 0 0 5.5 20H9M15 8l4 4-4 4M19 12H10",
};
export const ic = (n, cls = "ico") => `<svg class="${cls}" data-icon="${n}" viewBox="0 0 24 24" aria-hidden="true"><path d="${I[n] || I.info}"/></svg>`;
export const icf = (n, cls = "ico") => `<svg class="${cls}" data-icon="${n}" viewBox="0 0 24 24" aria-hidden="true"><path d="${I[n] || I.info}" fill="currentColor" stroke="none"/></svg>`;

const AV_PAL = [["#8b7bff", "#22d3ee"], ["#ff7ab6", "#8b7bff"], ["#22d3ee", "#3ddc97"], ["#ff8a4c", "#ff5470"], ["#5b8cff", "#8b7bff"], ["#3ddc97", "#22d3ee"], ["#ffd166", "#ff8a4c"], ["#a78bfa", "#f472b6"]];
export function avatar(name, size = 40, imgUrl = "") {
  const h = hash(name || "?"), p = AV_PAL[h % AV_PAL.length];
  if (imgUrl) return `<span class="avatar" style="width:${size}px;height:${size}px"><img src="${esc(imgUrl)}" alt="" loading="lazy" style="width:100%;height:100%;object-fit:cover;display:block" onerror="this.remove()"><svg viewBox="0 0 80 80" aria-hidden="true" style="position:absolute;inset:0"><rect width="80" height="80" fill="${p[0]}"/></svg></span>`;
  let shapes = "";
  for (let i = 0; i < 3; i++) {
    const x = 12 + ((h >> (i * 5)) % 56), y = 12 + ((h >> (i * 3 + 2)) % 56), r = 8 + ((h >> (i * 4)) % 12);
    shapes += `<circle cx="${x}" cy="${y}" r="${r}" fill="#fff" opacity="${0.1 + i * 0.07}"/>`;
  }
  const initial = esc((name || "?").replace(/^@/, "").trim().charAt(0).toUpperCase());
  return `<span class="avatar" style="width:${size}px;height:${size}px"><svg viewBox="0 0 80 80" aria-hidden="true"><defs><linearGradient id="g${h}${size}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${p[0]}"/><stop offset="1" stop-color="${p[1]}"/></linearGradient></defs><rect width="80" height="80" fill="url(#g${h}${size})"/>${shapes}<text x="40" y="52" text-anchor="middle" font-family="ui-sans-serif,sans-serif" font-size="34" font-weight="700" fill="#fff" opacity=".94">${initial}</text></svg></span>`;
}
export function xlogo(w = 26) {
  const id = "xg" + w + uid();
  return `<svg viewBox="0 0 40 40" width="${w}" height="${w}" aria-hidden="true"><defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#8b7bff"/><stop offset="1" stop-color="#22d3ee"/></linearGradient></defs><rect width="40" height="40" rx="12" fill="url(#${id})"/><path d="M13 13l14 14M27 13 13 27" stroke="#fff" stroke-width="3.4" stroke-linecap="round"/></svg>`;
}
export function coinMark(asset, size = 40) {
  return `<span class="coin-mark" data-asset="${asset}" style="width:${size}px;height:${size}px" aria-hidden="true">${asset === "xmr"
    ? `<svg viewBox="0 0 40 40"><circle cx="20" cy="20" r="20" fill="#ed435714"/><path d="M8 28V13l12 12 12-12v15" fill="none" stroke="#ed4357" stroke-width="3.4" stroke-linejoin="miter"/><path d="M3 28h8m18 0h8" stroke="#ed4357" stroke-width="3.4"/></svg>`
    : `<svg viewBox="0 0 40 40"><circle cx="20" cy="20" r="20" fill="#efb850"/><g transform="rotate(10 20 20)" fill="none" stroke="#2d1b04" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M13 12h9a4 4 0 0 1 0 8h-7m0 0h8a4 4 0 0 1 0 8H13M15 12v16M18 8v4m4-4v4M18 28v4m4-4v4"/></g></svg>`}</span>`;
}
/* linkify nostr: URIs, http(s), #tags */
export function richText(s) {
  let t = esc(s || "");
  t = t.replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener">$1</a>');
  t = t.replace(/#([a-zA-Z0-9_]+)/g, '<button class="inline-tag" data-act="tagOpen" data-tag="$1">#$1</button>');
  return t;
}
export async function copyText(s) {
  try { await navigator.clipboard.writeText(s); return true; }
  catch { const ta = document.createElement("textarea"); ta.value = s; document.body.appendChild(ta); ta.select(); try { document.execCommand("copy"); } catch {} ta.remove(); return true; }
}
