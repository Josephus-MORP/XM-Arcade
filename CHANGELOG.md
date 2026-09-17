# Changelog

All notable changes to this project. Versions follow [SemVer](https://semver.org/).

## [Unreleased]

## [1.0.3] — 2026-09-17

- Welcome screen now shows the neon XM app icon instead of the drawn mark.

## [1.0.2] — 2026-09-17

- Shorts feed fixed: Global is unfiltered NIP-71 again (kinds 21/22),
  For-you shows people you follow, Tags honors followed tags; demo
  placeholders only when relays truly return nothing.
- Fresh installs start with 10 followed tags (#music #funny #cute #family
  #action #adventure #rock #country #acoustic #nature); existing installs
  keep their tags; tap-to-follow unchanged.
- Studio short form adds a description line, space-separated hashtags, and a
  collaborator line (paste npub or @-pick from follows, tagged as p tags).

## [1.0.1] — 2026-09-17

- Full native Android rebuild: Kotlin + Jetpack Compose (versionCode 3),
  same package id for update continuity.
- Shorts + Music: hashtag chips follow/unfollow on tap, green dot on followed
  tags; followed tags render green in note text.
- Profile pane: key backup removed (Settings → My keys is the only place).
- Concord groups are not NIP-29: README corrected to the Concord protocol
  (Armada family).
- Default Blossom servers add data.haus, nostr.download, blossom.band.
- Renamed XMR payments: Xap → Xap (buttons, sheets, toasts).
- Appearance: Secondary color hue slider (Settings + side panel), saved on device.
- Removed the NIP-07 browser-extension login (Android-first: device key or Amber).
- Pairing can now open Amber directly from inside the app (nostrconnect deep link).
- Shorts: tap video to play/pause with a brief overlay icon; reply opens an inline
  composer over the bottom third without pausing the video.
- Reactions: emoji picker everywhere (quick picks, recents, your + author's NIP-30
  packs, any-emoji input).
- Zap/Xap without a wallet now prompt to connect one, with a button to Wallet.
- Mini Apps: webxdc discovery includes extensionless Blossom URLs + NIP-50 search.
- Chats: demo groups removed — only real Nostr groups; Ditto relay added for
  broader discovery.
- Wallet: Monero card has Create local wallet (honest requirements) + Use external
  wallet (Cake Wallet handoff for xaps/sends); sats card has Create local app
  wallet (self-custodial Alby Hub guide).

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
- Xaps: XMR tips with the red M mark next to every sats ⚡ zap (notes, shorts,
  music, profiles) — amount + recipient address, sent via your wallet RPC.
- Relay defaults: nos.lol, relay.primal.net, relay.ditto.pub (defunct Damus
  removed; stored lists migrate automatically) plus Blossom fallback
  `https://blossom.ditto.pub`. Settings → Relays spells out add/remove.

**Verified**: headless QA boots the app, mounts an embedded game, and reports zero page errors; live-network checks exercised all 7 panes, real video/audio/groups playback, and a signed kind-1 publish read back from 3/3 relays.
