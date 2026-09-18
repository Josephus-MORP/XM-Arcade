# Changelog

## v1.1.0 — 2026-09-18 — Total Revamp (native Kotlin + Jetpack Compose)

> Complete rebuild from scratch. Previous agent failed → fresh native Android rebuild directly from hand-drawn wireframes + stated intents.

### Added
- **Project:** Fresh `Kotlin 1.9.22 + Compose BOM 2024.10 + Material3 + Navigation 2.8.4 + Hilt + DataStore + Media3 ExoPlayer 1.4.1` scaffold; AGP 8.5.2, minSdk 26, targetSdk 34, JDK 17.
- **Logo:** Neon arcade logo with Monero orange/white **M** (`app_logo.png` + `mipmap/ic_launcher.png`) — used on launcher + welcome screen.
- **Login:** Create Account (generate npub/nsec, auto-pick random of 8 default avatars, auto-create Cake native wallets, apply defaults), Sign with nsec (paste), Login with Amber (NIP-55 intent `com.greenart7c3.nostrsigner`).
- **Defaults:** `nos.lol, ditto.pub/relay, relay.primal.net, relay.fountain.fm` + Blossom `blossom.ditto.pub, blossom.primal.net, blossom.data.haus` + follow tags `#music #action #family #funny #nature #rock #country #alternative #platformer #fps` — all removable in Settings; only for newly created accounts otherwise use profile relays.
- **Shorts:** TikTok-rival vertical feed (Amethyst/Ditto refs); header `Global | Tags | Following | ● online now | upload`; video card with clickable `#tags` → follow-tag dialog, footer `◎ name 😊 ⚡ M ↗` (react/zap/xap/share→concord), inline interactions without leaving feed (deep replies only exception); upload dialog → `BlossomManager.uploadToAll()` parallel redundancy (nostu.be pattern).
- **Mini Apps:** Armada/Ditto webxdc handling; header category tags `#fps` + upload; cards with `APP Logo + Name + Play + #tags` + footer `◎ ○ 😊 ⚡ M ↗` (share→concord), collapse/open description, scroll bar, detail expansion with hashtag chips; `include feed button sends to concord chat group`.
- **Music:** Nostria-inspired handling, custom UI; list with `Album Art + Artist + Album + Track + seek bar + ⚡ M + queue add`; header `View queue / online now / upload`; `Media3` player persists across panes except Shorts, pinned `Now Playing` bar.
- **Chats:** Armada Concord groups (NOT NIP-29) + Concord protocol repo; header `Chats | Unread | Discover | + | grid/list toggle`; rows with grab handle to reorder, `◎ Chatname @ + #channel #channel` + unread badges + `@` mention badge (only if mentioned), tap `#channel` → channel, tap row → chat, webxdc + call hooks.
- **Wallet:** Amethyst paymentTargets (external XMR + paste field) + Ditto/Amethyst NWC (sats) + Cake Wallet native construction (auto-create for new accounts); header `Wallet | ↑pay | ↓receive | +`; `M 0.02 $X.XX` + `⚡ 27,000 $X.XX` cards → recent transactions log; dialogs for linking.
- **Profile:** Own: edit banner/avatar (upload or re-pick from 8), bio, post/reply/repost notes. Other: tap avatar → profile + recent notes + `Zap ⚡ / XAP M` chips via your wallet. New profiles auto-pick random default avatar.
- **Settings:** Zap/XAP presets, light/dark + secondary hue slider (Color.hsv), hashtag follows add/remove, relays & blossom add/remove (all removable), backup nsec (show), logout, log in with second account (drawer `switch profiles` when >1), delete account, delete one/both wallets.
- **Online now chip:** Wisp protocol; header chip + drawer row `● 3 friends online now — click to see people you follow who are online right now` with count placeholder.
- **Navigation:** Bottom bar Shorts/Mini Apps/Music/Chats/Wallet + dropdown drawer Profile…Settings (mirrors sketch dropdown); all panes `LazyColumn` vertical scroll “to keep showing more options”.
- **Assets:** 8 default profile pictures (`default_avatar_1..8`) + logo drawables; vault safety `backup_rules.xml`/`data_extraction_rules.xml` excludes `secret_nsec.xml` from cloud backup.

### Changed
- Total revamp: replaced previous web/hybrid attempt with native Compose; all UI now matches wireframes 1-4 + `UI-MAPPING.md`.

### Technical
- `RelayManager` (OkHttp WebSocket, NIP-01/42 scaffolding, per-profile relays), `BlossomManager`, `WalletManager`, `ConcordManager`, `PreferencesManager` (DataStore encrypted nsec + AppSettings), `NostrCrypto` (secp256k1-kmp), `AppModule` Hilt, `XMArcadeTheme` with hue-tunable secondary.

## v1.0.0 — Initial
- Initial placeholder web build (superseded).
