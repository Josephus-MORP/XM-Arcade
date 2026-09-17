# Changelog

All notable changes to this project. Versions follow [SemVer](https://semver.org/).

## [1.0.0] — 2026-09-16

Initial public release.

**App** (web + Android)
- Profile: kind-0 metadata, notes feed, follow/unfollow, composer with Blossom attachments, like/repost/reply
- Shorts: NIP-71 global / tags / follows feeds, playback, zaps, Studio upload
- Mini Apps: webxdc arcade with 2 built-in games, `.xdc`/`.webxdc`/`.zip` uploads, `#webxdc` discovery, Feed-to-Concord channels
- Music: relay audio discovery, search, queue, sticky player, per-track zaps, Studio publish
- Chats: Concord groups (NIP-29) with optional shared-key (NIP-44) 🔒 channels, keys shareable via NIP-59 gift wraps
- Wallet: sats via NWC (NIP-47) + NIP-57 zaps; XMR via `monero-wallet-rpc`. No seeds ever requested or stored
- Settings: key backup, themes, relay/group-relay/Blossom editors, followed tags
- Signing: device nsec, NIP-07 extension, NIP-46 remote (Amber `bunker://` / `nostrconnect://`)
- Single-file build (`tools/build-web.py`): whole client in one HTML file
- Android app (`com.xmarcade.app`): offline-first WebView shell, file uploads, external signer intents
- Snaps: XMR tips with the red M mark next to every sats ⚡ zap (notes, shorts,
  music, profiles) — amount + recipient address, sent via your wallet RPC.
- Relay defaults: nos.lol, relay.primal.net, relay.ditto.pub (defunct Damus
  removed; stored lists migrate automatically) plus Blossom fallback
  `https://blossom.ditto.pub`. Settings → Relays spells out add/remove.

**Verified**: headless QA boots the app, mounts an embedded game, and reports zero page errors; live-network checks exercised all 7 panes, real video/audio/groups playback, and a signed kind-1 publish read back from 3/3 relays.
