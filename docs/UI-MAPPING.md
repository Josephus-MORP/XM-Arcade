# UI Mapping — Wireframes → Compose

## Reference 1 (Welcome + Settings icon)
- Top card “Welcome to XM Arcade” → `LoginScreen` centered logo + 3 buttons stacked: Create / Sign with nsec / Login with Amber.
  - Red boxes in sketch = distinct button styles: primary orange, outlined, and cyan-tinted “Amber”.
- Bottom card “O Settings” → Bottom drawer entry + BottomBar settings tab. Red O = avatar placeholder → `XmAvatar` with orange border.

## Reference 2 (Plan + Shorts + Mini Apps)
Top-left dropdown:
- `O Profile/shorts/miniapps/music/chats/wallet/settings` with “!” badges → `XmDrawer` list; “O” = pfp; “# online now (click to see …)” → Wisp chip in header + drawer bottom row.

Top-right video box:
- `your pfp global tags follow? online now upload` header → `ShortsScreen` header row with FilterChips Global/Tags/Following + Wisp + upload.
- Center “video” → black `Box` with ExoPlayer placeholder + play icon.
- `#tag #tag …` over video → clickable `Text("#$tag")` → follow-tag dialog (“follow tag? y/n to follow” per annotation).
- Bottom row `O name 😊 ⚡ M < V details share` → avatar (click → visit profile / follow user popup) • comment/react • zap • xap • share → concord • details arrow. Annotated “include feed button sends to concord chat group” → share handler.
- Arrows showing “click to draw a button ‘follow tag?’” → implemented as `AlertDialog`.

Bottom-left Mini Apps:
- Header `your pfp Mini App – category tags eg "fps" – upload` → `CategoryTagRow`.
- Two entries `App Logo App Name Play + O @ M < V` → `MiniAppCard`; red/blue annotations: “npub pfp collapse or open description box” → expand toggle, “scroll bar” → LazyColumn vertical, “include feed button sends to concord chat group” → share.

## Reference 3 (Music + Chats + Wallet)
Music top:
- `your pfp Music to view and add to queue online now upload new + add to queue` → headers + chips + upload icon.
- Album Art + O artist + zap/xap + play bar → `MusicTrackRow`.
- Bottom “Now Playing” → persistent `NowPlayingBar` (also hosted in `MainActivity` across routes).

Chats top-right:
- `O Chats Unread discover new groups + to create … only appears if you have been mentioned` → header chips.
- Toggle list vs block view → `toggleView()`.
- Rows: `O chatname @ #channel #channel • number = recent unread`, dots “grab to rearrange list order”, “click to enter specific channel / click to enter chat” → separate click handlers on #channel vs row.
- Bottom row blue → read vs red → unread (`hasMention` orange @ badge).

Wallet bottom-left:
- Header `O Wallet pay↑ receive↓ + add/create wallet` → wallet header row.
- `M 0.02 $X.XX ✓` and `⚡ 27,000 $X.XX ✓ -> to see most recent transactions log` → `WalletCard` + tx log dialog.

## Reference 4 (App detail expanded)
- `APP Logo App Name #tag #tag Play` + bottom row `O uploading user npubavatar ↩ react zap xap` with red annotations “forward mini app”, “react”, “zap”, “xap”, “uploading user npubavatar” → `MiniAppCard(expanded=true)` extra chip row.

---

All sketches use vertical red/blue lines for scroll / category separation — implemented as `Divider` + `LazyColumn` vertical spacing.
