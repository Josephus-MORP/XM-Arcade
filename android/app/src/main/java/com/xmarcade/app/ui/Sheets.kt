package com.xmarcade.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmarcade.app.Platform
import com.xmarcade.app.core.ChanDef
import com.xmarcade.app.core.Ev
import com.xmarcade.app.core.Group
import com.xmarcade.app.core.Net
import com.xmarcade.app.core.Nip19
import com.xmarcade.app.core.Nip57
import com.xmarcade.app.core.Nwc
import com.xmarcade.app.core.Player
import com.xmarcade.app.core.RelayPool
import com.xmarcade.app.core.Repo
import com.xmarcade.app.core.Signer
import com.xmarcade.app.core.Webxdc
import com.xmarcade.app.core.Xmr
import com.xmarcade.app.core.bolt11Msats
import com.xmarcade.app.core.displayName
import com.xmarcade.app.core.lnurlDecode
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SheetHost(sheet: Sheet) {
  Column(Modifier.verticalScroll(rememberScrollState())) {
    when (sheet.id) {
      "relays" -> RelaysSheet()
      "blossom" -> BlossomSheet()
      "tags" -> TagsSheet()
      "author" -> AuthorSheet(sheet.args["pk"] ?: "")
      "react" -> ReactSheet(sheet.args["eid"] ?: "", sheet.args["pk"] ?: "")
      "zap" -> ZapSheet(sheet.args["eid"] ?: "", sheet.args["pk"] ?: "")
      "xap" -> XapSheet(sheet.args["pk"] ?: "")
      "needWallet" -> NeedWalletSheet(sheet.args["kind"] ?: "sats")
      "nwc" -> NwcSheet()
      "xmr" -> XmrSheet()
      "receive" -> ReceiveSheet(sheet.args["kind"] ?: "sats")
      "pay" -> PaySheet(sheet.args["kind"] ?: "sats")
      "keys" -> KeysSheet()
      "editProfile" -> EditProfileSheet()
      "chanKey" -> ChanKeySheet(sheet.args["gid"] ?: "", sheet.args["ch"] ?: "general")
      "shareKey" -> ShareKeySheet(sheet.args["gid"] ?: "", sheet.args["ch"] ?: "general")
      "reqKey" -> ReqKeySheet(sheet.args["gid"] ?: "", sheet.args["ch"] ?: "general")
      "newGroup" -> NewGroupSheet()
      "groupEdit" -> GroupEditSheet(sheet.args["gid"] ?: "")
      "newChannel" -> NewChannelSheet(sheet.args["gid"] ?: "")
      "feedApp" -> FeedAppSheet(sheet.args["appId"] ?: "")
      "upload" -> UploadSheet()
      "queue" -> QueueSheet()
      "about" -> AboutSheet()
      else -> SheetScaffold("?", "", {}, {})
    }
  }
}

private fun groupOf(gid: String): Group? = Repo.groupsCache.firstOrNull { it.id == gid }

// ================= relays =================
@Composable
private fun RelaysSheet() {
  val status = RelayPool.status.collectAsState().value
  var list by remember { mutableStateOf(RelayPool.relays) }
  var glist by remember { mutableStateOf(RelayPool.groupRelays) }
  var add by remember { mutableStateOf("") }
  var gadd by remember { mutableStateOf("") }
  SheetScaffold("Relays", "Nostr sockets for posts, shorts and zaps", {
    list.forEach { u ->
      val st = status[u] ?: "…"
      Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp))
        .background(LocalXM.current.surface2).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (st == "live") "●" else "○", fontSize = 13.sp,
          color = if (st == "live") LocalXM.current.ok else LocalXM.current.text3)
        Spacer(Modifier.width(8.dp))
        Text(u, fontFamily = Mono, fontSize = 12.5.sp, modifier = Modifier.weight(1f),
          maxLines = 1, overflow = TextOverflow.Ellipsis)
        BareIconBtn("trash", "Remove", { if (list.size > 1) list = list - u }, size = 32.dp)
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) { XMField(add, { add = it }, "wss://…", mono = true) }
      Spacer(Modifier.width(8.dp))
      XMButton("Add", {
        var u = add.trim()
        if (u.isEmpty()) return@XMButton
        if (!u.startsWith("wss://")) u = "wss://$u"
        if (!list.contains(u)) list = list + u
        add = ""
      }, small = true)
    }
    Spacer(Modifier.height(10.dp))
    Row {
      XMButton("Save", {
        RelayPool.setRelays(list); RelayPool.setGroupRelays(glist)
        RelayPool.refreshProbes()
        Nav.toast("Relays saved")
        Nav.closeSheet()
      }, Modifier.weight(1f), small = true)
      Spacer(Modifier.width(8.dp))
      XMButton("Reset", {
        list = RelayPool.DEFAULT_RELAYS; glist = RelayPool.GROUP_RELAYS_DEFAULT
      }, Modifier.weight(1f), kind = BtnKind.Line, small = true)
    }
    Spacer(Modifier.height(14.dp))
    Text("Concord group relays", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    glist.forEach { u ->
      val st = status[u] ?: "…"
      Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp))
        .background(LocalXM.current.surface2).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (st == "live") "●" else "○", fontSize = 13.sp,
          color = if (st == "live") LocalXM.current.ok else LocalXM.current.text3)
        Spacer(Modifier.width(8.dp))
        Text(u, fontFamily = Mono, fontSize = 12.5.sp, modifier = Modifier.weight(1f),
          maxLines = 1, overflow = TextOverflow.Ellipsis)
        BareIconBtn("trash", "Remove", { if (glist.size > 1) glist = glist - u }, size = 32.dp)
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) { XMField(gadd, { gadd = it }, "wss://…", mono = true) }
      Spacer(Modifier.width(8.dp))
      XMButton("Add", {
        var u = gadd.trim()
        if (u.isEmpty()) return@XMButton
        if (!u.startsWith("wss://")) u = "wss://$u"
        if (!glist.contains(u)) glist = glist + u
        gadd = ""
      }, small = true)
    }
  })
}

@Composable
private fun BlossomSheet() {
  var text by remember { mutableStateOf(Repo.blossomServers().joinToString("\n")) }
  SheetScaffold("Blossom servers", "One per line — media uploads try each in order", {
    XMField(text, { text = it }, "https://…", mono = true, singleLine = false, minLines = 3)
    Spacer(Modifier.height(12.dp))
    XMButton("Save", {
      val list = text.lines().map { it.trim() }.filter { it.startsWith("http") }
      if (list.isEmpty()) { Nav.toast("Keep at least one server"); return@XMButton }
      Repo.setBlossomServers(list)
      Nav.toast("Blossom servers saved")
      Nav.closeSheet()
    }, Modifier.fillMaxWidth())
  })
}

@Composable
private fun TagsSheet() {
  var add by remember { mutableStateOf("") }
  SheetScaffold("Followed tags", "Tap a tag to unfollow it", {
    if (S.followTags.isEmpty()) Text("No tags yet.", color = LocalXM.current.text2)
    S.followTags.toList().forEach { t ->
      Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
        .background(LocalXM.current.surface2).clickable {
          S.followTags.remove(t); S.save()
          if (S.tagFilter == t) { S.tagFilter = "All"; S.save() }
          Nav.toast("Unfollowed #$t")
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#$t", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
        AppIcon("close", 16.dp, tint = LocalXM.current.text3)
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) { XMField(add, { add = it }, "nostr", maxChars = 40) }
      Spacer(Modifier.width(8.dp))
      XMButton("Add", {
        val t = add.trim().lowercase().removePrefix("#")
        if (t.isEmpty()) return@XMButton
        if (!S.followTags.contains(t)) { S.followTags.add(t); S.save() }
        add = ""
        Nav.toast("Following #$t")
      }, small = true)
    }
  })
}

// ================= author / react =================
@Composable
private fun AuthorSheet(pk: String) {
  val scope = rememberCoroutineScope()
  var p by remember(pk) { mutableStateOf(Repo.cachedProfile(pk)) }
  val following = S.followUsers.contains(pk)
  LaunchedEffect(pk) {
    scope.launch(Dispatchers.IO) {
      try { Repo.fetchProfiles(listOf(pk)) } catch (_: Exception) {}
      withContext(Dispatchers.Main) { p = Repo.cachedProfile(pk) }
    }
  }
  SheetScaffold(displayName(p), "npub " + pk.take(12) + "…", {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Avatar(displayName(p), 56.dp, p.picture)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        if (p.about.isNotEmpty()) Text(p.about, fontSize = 13.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (p.nip05.isNotEmpty()) Text("✓ " + p.nip05, fontSize = 12.5.sp, color = LocalXM.current.ok)
      }
    }
    Spacer(Modifier.height(14.dp))
    Row {
      XMButton(if (following) "Following" else "+ Follow", { Acts.doFollow(pk) },
        Modifier.weight(1f), kind = if (following) BtnKind.Line else BtnKind.Primary, small = true)
      Spacer(Modifier.width(8.dp))
      XMButton("Visit", { Acts.visitProfile(pk) }, Modifier.weight(1f), kind = BtnKind.Line, small = true)
    }
    Spacer(Modifier.height(8.dp))
    Row {
      XMButton("Zap", { Nav.closeSheet(); Acts.doZapOpen("", pk) }, Modifier.weight(1f),
        kind = BtnKind.Line, small = true, icon = "zap")
      Spacer(Modifier.width(8.dp))
      XMButton("Xap", { Nav.closeSheet(); Acts.doXapOpen("", pk) }, Modifier.weight(1f),
        kind = BtnKind.Line, small = true)
    }
  })
}

private val REACT_DEFAULTS = listOf("❤️", "🔥", "👍", "😂", "😮", "😢", "👏", "⚡", "🎮", "💯", "🙌", "🤝")

@Composable
private fun ReactSheet(eid: String, pk: String) {
  val scope = rememberCoroutineScope()
  val recents = remember { try { Repo.recentEmojis() } catch (_: Exception) { emptyList() } }
  var custom by remember { mutableStateOf("") }
  var packs by remember { mutableStateOf<List<com.xmarcade.app.core.EmojiPack>>(emptyList()) }
  LaunchedEffect(pk) {
    scope.launch(Dispatchers.IO) {
      val r = try { Repo.getEmojiPacks(pk) } catch (_: Exception) { emptyList() }
      withContext(Dispatchers.Main) { packs = r }
    }
  }
  SheetScaffold("React", "Pick an emoji — it signs a kind 7", {
    if (recents.isNotEmpty()) {
      Text("Recent", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalXM.current.text2)
      Spacer(Modifier.height(6.dp))
      Row {
        recents.take(6).forEach { r ->
          Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(LocalXM.current.surface2)
            .clickable { Acts.doReactSend(eid, r.t, r.u) }, contentAlignment = Alignment.Center) {
            Text(r.t, fontSize = 22.sp)
          }
          Spacer(Modifier.width(8.dp))
        }
      }
      Spacer(Modifier.height(10.dp))
    }
    REACT_DEFAULTS.chunked(6).forEach { row ->
      Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        row.forEach { e ->
          Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(LocalXM.current.surface2)
            .clickable { Acts.doReactSend(eid, e) }, contentAlignment = Alignment.Center) {
            Text(e, fontSize = 22.sp)
          }
          Spacer(Modifier.width(8.dp))
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) { XMField(custom, { custom = it }, "or type one…", maxChars = 12) }
      Spacer(Modifier.width(8.dp))
      XMButton("Send", { Acts.doReactSend(eid, custom.trim()) }, small = true)
    }
    packs.forEach { pack ->
      Spacer(Modifier.height(10.dp))
      Text(pack.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalXM.current.text2)
      Spacer(Modifier.height(6.dp))
      pack.emojis.chunked(6).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
          row.forEach { er ->
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(LocalXM.current.surface2)
              .clickable { Acts.doReactSend(eid, ":${er.code}:", er.url) },
              contentAlignment = Alignment.Center) {
              AsyncImg(er.url, Modifier.size(30.dp), fallback = { Text(":${er.code}:", fontSize = 10.sp) })
            }
            Spacer(Modifier.width(8.dp))
          }
        }
      }
    }
  })
}

// ================= zap / xap =================
private suspend fun doZap(pk: String, eid: String, sats: Int, comment: String): String {
  val prof = try { Repo.fetchProfiles(listOf(pk))[pk] } catch (_: Exception) { null } ?: Repo.cachedProfile(pk)
  val lud16 = prof.lud16
  val payUrl = when {
    lud16.contains("@") -> {
      val (u, d) = lud16.split("@", limit = 2)
      "https://$d/.well-known/lnurlp/$u"
    }
    prof.lud06.isNotEmpty() -> lnurlDecode(prof.lud06)
    else -> throw IllegalStateException("No lightning address on this profile")
  }
  val meta = Net.getJson(payUrl)
  if (meta.optString("tag") != "payRequest") throw IllegalStateException("Not a lightning pay endpoint")
  val msats = sats * 1000L
  if (msats < meta.optLong("minSendable", 1000) || msats > meta.optLong("maxSendable", Long.MAX_VALUE))
    throw IllegalStateException("Amount outside the wallet's range")
  val cb = meta.optString("callback")
  if (cb.isEmpty()) throw IllegalStateException("No callback from the wallet")
  val zapReq = Nip57.zapRequest(pk, eid.ifEmpty { null }, msats, RelayPool.relays, comment)
  val signed = Signer.signEvent(9734, zapReq.content, zapReq.tags)
  val sep = if (cb.contains("?")) "&" else "?"
  val inv = Net.getJson("$cb${sep}amount=$msats&nostr=${URLEncoder.encode(Ev.toJson(signed).toString(), "UTF-8")}")
  val pr = inv.optString("pr", "")
  if (pr.isEmpty()) throw IllegalStateException(inv.optString("reason", "No invoice returned"))
  val paid = Nwc.payInvoice(pr)
  return paid.optString("preimage", paid.toString())
}

@Composable
private fun ZapSheet(eid: String, pk: String) {
  val scope = rememberCoroutineScope()
  var amt by remember { mutableStateOf(S.zapAmt.toString()) }
  var comment by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf<String?>(null) }
  SheetScaffold("Zap ⚡", "Pay a lightning invoice through your NWC wallet", {
    if (done != null) {
      Text("Zapped ✓ payment proof:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      KeyBox(done!!)
      Spacer(Modifier.height(10.dp))
      XMButton("Close", { Nav.closeSheet() }, Modifier.fillMaxWidth())
    } else {
      XMField(amt, { amt = it.filter { c -> c.isDigit() }.take(9) }, "210", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
      Spacer(Modifier.height(4.dp))
      Text("sats", fontSize = 12.sp, color = LocalXM.current.text3)
      Spacer(Modifier.height(8.dp))
      XMField(comment, { comment = it }, "comment (optional)", maxChars = 140)
      Spacer(Modifier.height(12.dp))
      XMButton("Zap $amt sats", {
        val s = amt.toIntOrNull() ?: 0
        if (s <= 0) { Nav.toast("Enter an amount"); return@XMButton }
        busy = true
        scope.launch(Dispatchers.IO) {
          try {
            val proof = doZap(pk, eid, s, comment.trim())
            S.zapAmt = s; S.save()
            withContext(Dispatchers.Main) { busy = false; done = proof }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) { busy = false; Nav.toast("Zap failed: ${e.message}") }
          }
        }
      }, Modifier.fillMaxWidth(), enabled = !busy, icon = "zap")
    }
  })
}

@Composable
private fun XapSheet(pk: String) {
  val scope = rememberCoroutineScope()
  var addr by remember { mutableStateOf("") }
  var amt by remember { mutableStateOf(S.xapAmt.toString()) }
  var busy by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf<String?>(null) }
  SheetScaffold("Xap — Monero tip", "On-chain transfer from your connected node", {
    if (done != null) {
      Text("Sent ✓ tx:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      KeyBox(done!!)
      Spacer(Modifier.height(10.dp))
      XMButton("Close", { Nav.closeSheet() }, Modifier.fillMaxWidth())
    } else {
      XMField(addr, { addr = it.trim() }, "recipient Monero address", mono = true)
      Spacer(Modifier.height(10.dp))
      XMField(amt, { amt = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, "0.01",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
      Spacer(Modifier.height(4.dp))
      Text("XMR", fontSize = 12.sp, color = LocalXM.current.text3)
      Spacer(Modifier.height(12.dp))
      XMButton("Send $amt XMR", {
        val a = amt.toDoubleOrNull() ?: 0.0
        if (!Xmr.ADDR.matches(addr)) { Nav.toast("That address doesn't look right"); return@XMButton }
        if (a <= 0) { Nav.toast("Enter an amount"); return@XMButton }
        busy = true
        scope.launch(Dispatchers.IO) {
          try {
            val r = Xmr.transfer(addr, a)
            S.xapAmt = a; S.save()
            withContext(Dispatchers.Main) { busy = false; done = r.optString("tx_hash", r.toString()) }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) { busy = false; Nav.toast("Xap failed: ${e.message}") }
          }
        }
      }, Modifier.fillMaxWidth(), enabled = !busy, icon = "send")
    }
  })
}

@Composable
private fun NeedWalletSheet(kind: String) {
  SheetScaffold(if (kind == "xmr") "No Monero wallet" else "No sats wallet",
    if (kind == "xmr") "Connect your node to send xaps." else "Connect an NWC wallet to zap.", {
    if (kind == "xmr") {
      XMButton("Connect node", { Nav.openSheet(Sheet("xmr")) }, Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      XMButton("Use external wallet", {
        Xmr.mode = "external"; Xmr.saveMode()
        Nav.closeSheet()
        Nav.toast("External wallet mode — sends open your wallet app")
      }, Modifier.fillMaxWidth(), kind = BtnKind.Line)
    } else {
      XMButton("Connect NWC", { Nav.openSheet(Sheet("nwc")) }, Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      Text("Any Nostr Wallet Connect wallet works: Alby, Mutiny, NWC-ready nodes.",
        fontSize = 12.5.sp, color = LocalXM.current.text3)
    }
  })
}

// ================= nwc / xmr =================
@Composable
private fun NwcSheet() {
  val scope = rememberCoroutineScope()
  var uri by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var connected by remember { mutableStateOf(Nwc.connected()) }
  SheetScaffold("NWC wallet", "Nostr Wallet Connect — sats stay in your wallet", {
    if (connected) {
      Text("Connected to:", fontSize = 13.sp, color = LocalXM.current.text2)
      Spacer(Modifier.height(6.dp))
      KeyBox(Nwc.conn?.relay ?: "")
      Spacer(Modifier.height(12.dp))
      XMButton("Disconnect", {
        Nwc.disconnect(); connected = false
        Nav.toast("NWC disconnected")
      }, Modifier.fillMaxWidth(), kind = BtnKind.Line)
    } else {
      XMField(uri, { uri = it.trim() }, "nostr+walletconnect://…", mono = true)
      Spacer(Modifier.height(10.dp))
      Row {
        XMButton("Paste", {
          val p = Platform.paste()
          if (p.isNotEmpty()) { uri = p; Nav.toast("Pasted") }
        }, Modifier.weight(1f), kind = BtnKind.Line, small = true)
        Spacer(Modifier.width(8.dp))
        XMButton("Connect", {
          if (uri.isEmpty()) { Nav.toast("Paste an NWC URI first"); return@XMButton }
          busy = true
          scope.launch(Dispatchers.IO) {
            try {
              Nwc.connect(uri)
              Nwc.getBalanceMsats()
              withContext(Dispatchers.Main) {
                busy = false; connected = true
                Nav.toast("Wallet connected")
                Nav.closeSheet()
              }
            } catch (e: Exception) {
              try { Nwc.disconnect() } catch (_: Exception) {}
              withContext(Dispatchers.Main) { busy = false; Nav.toast("Connect failed: ${e.message}") }
            }
          }
        }, Modifier.weight(1f), small = true, enabled = !busy)
      }
    }
  })
}

@Composable
private fun XmrSheet() {
  val scope = rememberCoroutineScope()
  var url by remember { mutableStateOf(Xmr.cfg.url) }
  var user by remember { mutableStateOf(Xmr.cfg.user) }
  var pass by remember { mutableStateOf(Xmr.cfg.pass) }
  var busy by remember { mutableStateOf(false) }
  SheetScaffold("Monero node", "monero-wallet-rpc with --rpc-login, reachable from this phone", {
    XMField(url, { url = it.trim() }, "http://127.0.0.1:18082/json_rpc", mono = true)
    Spacer(Modifier.height(10.dp))
    Row {
      Box(Modifier.weight(1f)) { XMField(user, { user = it }, "rpc user") }
      Spacer(Modifier.width(8.dp))
      Box(Modifier.weight(1f)) { XMField(pass, { pass = it }, "rpc password", password = true) }
    }
    Spacer(Modifier.height(12.dp))
    XMButton("Save & test", {
      if (url.isEmpty()) { Nav.toast("Enter the RPC URL"); return@XMButton }
      busy = true
      scope.launch(Dispatchers.IO) {
        try {
          Xmr.cfg.url = url; Xmr.cfg.user = user; Xmr.cfg.pass = pass
          Xmr.saveCfg()
          Xmr.mode = "rpc"; Xmr.saveMode()
          Xmr.status()
          withContext(Dispatchers.Main) {
            busy = false
            Nav.toast("Monero connected")
            Nav.closeSheet()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) { busy = false; Nav.toast("Node failed: ${e.message}") }
        }
      }
    }, Modifier.fillMaxWidth(), enabled = !busy)
    Spacer(Modifier.height(8.dp))
    Row {
      XMButton("External instead", {
        Xmr.mode = "external"; Xmr.saveMode()
        Nav.closeSheet()
        Nav.toast("External wallet mode")
      }, Modifier.weight(1f), kind = BtnKind.Line, small = true)
      Spacer(Modifier.width(8.dp))
      XMButton("Disconnect", {
        Xmr.mode = null; Xmr.saveMode()
        Nav.closeSheet()
        Nav.toast("Monero disconnected")
      }, Modifier.weight(1f), kind = BtnKind.Ghost, small = true)
    }
  })
}

// ================= receive / pay =================
@Composable
private fun ReceiveSheet(kind: String) {
  val scope = rememberCoroutineScope()
  var amt by remember { mutableStateOf("") }
  var memo by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var out by remember { mutableStateOf("") }
  var addr by remember { mutableStateOf("") }
  LaunchedEffect(kind) {
    if (kind == "xmr") {
      scope.launch(Dispatchers.IO) {
        try {
          val a = Xmr.address().optString("address", "")
          withContext(Dispatchers.Main) { addr = a }
        } catch (_: Exception) {}
      }
    }
  }
  SheetScaffold(if (kind == "xmr") "Receive XMR" else "Receive sats",
    if (kind == "xmr") "Your node address — share it with the sender." else "Create an invoice in your NWC wallet.", {
    if (kind == "xmr") {
      if (addr.isEmpty()) Text("Loading address…", color = LocalXM.current.text2)
      else {
        KeyBox(addr)
        Spacer(Modifier.height(10.dp))
        Row {
          XMButton("Copy", { Acts.copyText(addr, "Address copied") }, Modifier.weight(1f), kind = BtnKind.Line, small = true)
          Spacer(Modifier.width(8.dp))
          XMButton("Payment URI", {
            Acts.copyText(Xmr.paymentUri(addr, amt.toDoubleOrNull() ?: 0.0), "Payment URI copied")
          }, Modifier.weight(1f), kind = BtnKind.Line, small = true)
        }
        Spacer(Modifier.height(10.dp))
        XMField(amt, { amt = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, "amount for the URI (optional)",
          keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
      }
    } else {
      if (out.isEmpty()) {
        XMField(amt, { amt = it.filter { c -> c.isDigit() }.take(12) }, "sats",
          keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        Spacer(Modifier.height(10.dp))
        XMField(memo, { memo = it }, "memo (optional)", maxChars = 140)
        Spacer(Modifier.height(12.dp))
        XMButton("Create invoice", {
          val s = amt.toLongOrNull() ?: 0L
          if (s <= 0) { Nav.toast("Enter an amount"); return@XMButton }
          busy = true
          scope.launch(Dispatchers.IO) {
            try {
              val r = Nwc.makeInvoice(s * 1000, memo.ifEmpty { "XM Arcade" })
              withContext(Dispatchers.Main) { busy = false; out = r.optString("invoice", "") }
              if (out.isEmpty()) withContext(Dispatchers.Main) { Nav.toast("No invoice returned") }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) { busy = false; Nav.toast("Invoice failed: ${e.message}") }
            }
          }
        }, Modifier.fillMaxWidth(), enabled = !busy, icon = "qr")
      } else {
        KeyBox(out)
        Spacer(Modifier.height(10.dp))
        XMButton("Copy invoice", { Acts.copyText(out, "Invoice copied") }, Modifier.fillMaxWidth(),
          kind = BtnKind.Line, small = true)
      }
    }
  })
}

@Composable
private fun PaySheet(kind: String) {
  val scope = rememberCoroutineScope()
  var inv by remember { mutableStateOf("") }
  var addr by remember { mutableStateOf("") }
  var amt by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf<String?>(null) }
  SheetScaffold(if (kind == "xmr") "Send XMR" else "Pay invoice",
    if (kind == "xmr") "On-chain from your node." else "Pays through your NWC wallet.", {
    if (done != null) {
      Text("Paid ✓", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      KeyBox(done!!)
      Spacer(Modifier.height(10.dp))
      XMButton("Close", { Nav.closeSheet() }, Modifier.fillMaxWidth())
    } else if (kind == "xmr") {
      XMField(addr, { addr = it.trim() }, "recipient address", mono = true)
      Spacer(Modifier.height(10.dp))
      XMField(amt, { amt = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, "0.01",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
      Spacer(Modifier.height(12.dp))
      XMButton("Send", {
        val a = amt.toDoubleOrNull() ?: 0.0
        if (!Xmr.ADDR.matches(addr)) { Nav.toast("That address doesn't look right"); return@XMButton }
        if (a <= 0) { Nav.toast("Enter an amount"); return@XMButton }
        busy = true
        scope.launch(Dispatchers.IO) {
          try {
            val r = Xmr.transfer(addr, a)
            withContext(Dispatchers.Main) { busy = false; done = r.optString("tx_hash", r.toString()) }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) { busy = false; Nav.toast("Send failed: ${e.message}") }
          }
        }
      }, Modifier.fillMaxWidth(), enabled = !busy, icon = "send")
    } else {
      XMField(inv, { inv = it.trim() }, "lnbc…", mono = true)
      if (inv.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("≈ ${bolt11Msats(inv) / 1000} sats", fontSize = 12.5.sp, color = LocalXM.current.text2)
      }
      Spacer(Modifier.height(10.dp))
      Row {
        XMButton("Paste", {
          val p = Platform.paste()
          if (p.isNotEmpty()) { inv = p }
        }, Modifier.weight(1f), kind = BtnKind.Line, small = true)
        Spacer(Modifier.width(8.dp))
        XMButton("Pay", {
          if (inv.isEmpty()) { Nav.toast("Paste an invoice first"); return@XMButton }
          busy = true
          scope.launch(Dispatchers.IO) {
            try {
              val r = Nwc.payInvoice(inv)
              withContext(Dispatchers.Main) {
                busy = false
                done = r.optString("preimage", r.toString())
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) { busy = false; Nav.toast("Pay failed: ${e.message}") }
            }
          }
        }, Modifier.weight(1f), small = true, enabled = !busy, icon = "zap")
      }
    }
  })
}

// ================= keys / profile =================
@Composable
private fun KeysSheet() {
  var show by remember { mutableStateOf(false) }
  val npub = try { Nip19.npubEncode(Signer.pubkey ?: "") } catch (_: Exception) { "" }
  val nsec = if (Signer.method == "local") {
    try { Signer.localSk?.let { Nip19.nsecEncode(it) } ?: "" } catch (_: Exception) { "" }
  } else ""
  SheetScaffold("My keys", "Back these up somewhere safe", {
    Text("Public (npub) — safe to share", fontSize = 13.sp, color = LocalXM.current.text2)
    Spacer(Modifier.height(6.dp))
    KeyBox(npub)
    Spacer(Modifier.height(8.dp))
    XMButton("Copy npub", { Acts.copyText(npub, "npub copied") }, Modifier.fillMaxWidth(),
      kind = BtnKind.Line, small = true)
    Spacer(Modifier.height(14.dp))
    if (nsec.isNotEmpty()) {
      Text("Secret (nsec) — never share", fontSize = 13.sp, color = LocalXM.current.live)
      Spacer(Modifier.height(6.dp))
      KeyBox(if (show) nsec else "•".repeat(48))
      Spacer(Modifier.height(8.dp))
      Row {
        XMButton(if (show) "Hide" else "Show", { show = !show }, Modifier.weight(1f),
          kind = BtnKind.Line, small = true)
        Spacer(Modifier.width(8.dp))
        XMButton("Copy nsec", { Acts.copyText(nsec, "nsec copied — keep it secret") }, Modifier.weight(1f),
          kind = BtnKind.Line, small = true)
      }
    } else {
      Text("Key held by ${Signer.label.ifEmpty { "external signer" }} — XM Arcade can't show it.",
        fontSize = 13.5.sp, color = LocalXM.current.text2)
    }
  })
}

@Composable
private fun EditProfileSheet() {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf(Me.profile.displayName.ifEmpty { Me.profile.name }) }
  var about by remember { mutableStateOf(Me.profile.about) }
  var pic by remember { mutableStateOf(Me.profile.picture) }
  var banner by remember { mutableStateOf(Me.profile.banner) }
  var busy by remember { mutableStateOf(false) }
  SheetScaffold("Edit profile", "Published as kind 0 to your relays", {
    XMField(name, { name = it }, "Display name", maxChars = 60)
    Spacer(Modifier.height(10.dp))
    XMField(about, { about = it }, "About", minLines = 3, singleLine = false, maxChars = 280)
    Spacer(Modifier.height(10.dp))
    XMField(pic, { pic = it.trim() }, "Picture URL", mono = true)
    Spacer(Modifier.height(10.dp))
    XMField(banner, { banner = it.trim() }, "Banner URL", mono = true)
    Spacer(Modifier.height(12.dp))
    XMButton("Save", {
      busy = true
      scope.launch(Dispatchers.IO) {
        try {
          val res = Repo.publishProfile(mapOf("name" to name.trim(), "about" to about.trim(),
            "picture" to pic, "banner" to banner))
          try { Repo.fetchProfiles(listOf(Signer.pubkey ?: "")) } catch (_: Exception) {}
          withContext(Dispatchers.Main) {
            busy = false
            Me.refresh()
            Nav.closeSheet()
            Nav.toast(if (res.ok) "Profile saved" else "No relay accepted it")
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) { busy = false; Nav.toast("Save failed: ${e.message}") }
        }
      }
    }, Modifier.fillMaxWidth(), enabled = !busy)
  })
}

// ================= Concord keys / groups =================
@Composable
private fun ChanKeySheet(gid: String, ch: String) {
  val g = groupOf(gid)
  SheetScaffold("Channel key", "#$ch", {
    if (g == null) {
      Text("Group not found.", color = LocalXM.current.text2)
    } else if (g.demo) {
      Text("Demo group — nothing is encrypted.", color = LocalXM.current.text2)
    } else {
      val key = Repo.getGroupKey(Repo.groupChannelId(g, ch))
      if (key.isEmpty()) {
        Text("This channel is open — no key yet. Lock it by creating a key, or request one from a member.",
          fontSize = 14.sp, color = LocalXM.current.text2)
        Spacer(Modifier.height(12.dp))
        Row {
          XMButton("Request key", { Nav.openSheet(Sheet("reqKey", mapOf("gid" to gid, "ch" to ch))) },
            Modifier.weight(1f), small = true)
          Spacer(Modifier.width(8.dp))
          XMButton("Lock it", {
            Repo.setGroupKey(Repo.groupChannelId(g, ch), Repo.newGroupKey())
            Nav.toast("Channel locked — share the key from the Key panel")
            Nav.closeSheet()
          }, Modifier.weight(1f), kind = BtnKind.Line, small = true)
        }
      } else {
        Text("Share this with members only:", fontSize = 13.sp, color = LocalXM.current.text2)
        Spacer(Modifier.height(6.dp))
        KeyBox(key)
        Spacer(Modifier.height(10.dp))
        XMButton("Copy key", { Acts.copyText(key, "Key copied") }, Modifier.fillMaxWidth(),
          kind = BtnKind.Line, small = true)
      }
    }
  })
}

@Composable
private fun ShareKeySheet(gid: String, ch: String) {
  val scope = rememberCoroutineScope()
  val g = groupOf(gid)
  var member by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  SheetScaffold("Share key", "Gift-wrapped (NIP-59) to one member", {
    if (g == null || g.demo) {
      Text("Demo group — nothing to share.", color = LocalXM.current.text2)
    } else {
      val has = Repo.getGroupKey(Repo.groupChannelId(g, ch)).isNotEmpty()
      if (!has) {
        Text("No channel key yet — send one locked message first.", color = LocalXM.current.text2)
      } else {
        XMField(member, { member = it.trim() }, "member npub or hex", mono = true)
        Spacer(Modifier.height(12.dp))
        XMButton("Share key", {
          val v = member.trim()
          val hex = when {
            Regex("^[0-9a-fA-F]{64}$").matches(v) -> v.lowercase()
            v.startsWith("npub1") -> Nip19.decode(v)?.takeIf { it.type == "npub" }?.dataHex ?: ""
            else -> ""
          }
          if (hex.isEmpty()) { Nav.toast("Paste a member npub or hex"); return@XMButton }
          busy = true
          scope.launch(Dispatchers.IO) {
            try {
              Repo.shareGroupKey(g, ch, hex)
              withContext(Dispatchers.Main) {
                busy = false; member = ""
                Nav.toast("Key shared")
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) { busy = false; Nav.toast("Share failed: ${e.message}") }
            }
          }
        }, Modifier.fillMaxWidth(), enabled = !busy, icon = "key")
        Spacer(Modifier.height(8.dp))
        Text("Needs a device key — remote signers can't gift-wrap.", fontSize = 12.5.sp,
          color = LocalXM.current.text3)
      }
    }
  })
}

@Composable
private fun ReqKeySheet(gid: String, ch: String) {
  val scope = rememberCoroutineScope()
  val g = groupOf(gid)
  var busy by remember { mutableStateOf(false) }
  SheetScaffold("Request key", "#$ch", {
    Text("Posts a visible request in the channel. A member can share the key from the Key panel — you'll collect it automatically on next launch.",
      fontSize = 14.sp, color = LocalXM.current.text2)
    Spacer(Modifier.height(12.dp))
    XMButton("Ask in channel", {
      if (g == null || g.demo) { Nav.toast("Demo group — nothing to request"); return@XMButton }
      busy = true
      scope.launch(Dispatchers.IO) {
        try {
          val who = displayName(Me.profile).ifEmpty { "Someone" }
          Repo.sendGroupMessage(g, ch, "🔑 $who requests the channel key — please share it from the Key panel.", false)
          withContext(Dispatchers.Main) {
            busy = false
            Nav.closeSheet()
            Nav.toast("Request posted")
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) { busy = false; Nav.toast("Request failed: ${e.message}") }
        }
      }
    }, Modifier.fillMaxWidth(), enabled = !busy, icon = "key")
  })
}

@Composable
private fun NewGroupSheet() {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var about by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  SheetScaffold("New Concord group", "A NIP-29 kind 39000 set — you host it", {
    XMField(name, { name = it }, "Group name", maxChars = 60)
    Spacer(Modifier.height(10.dp))
    XMField(about, { about = it }, "About (optional)", minLines = 2, singleLine = false, maxChars = 280)
    Spacer(Modifier.height(12.dp))
    XMButton("Create group", {
      if (name.trim().isEmpty()) { Nav.toast("Name it first"); return@XMButton }
      busy = true
      scope.launch(Dispatchers.IO) {
        try {
          val g = Repo.createGroup(name.trim(), about.trim())
          withContext(Dispatchers.Main) {
            busy = false
            S.groupId = g.id; S.channel = "general"; S.save()
            Nav.closeSheet()
            Nav.toast("Group created")
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) { busy = false; Nav.toast("Create failed: ${e.message}") }
        }
      }
    }, Modifier.fillMaxWidth(), enabled = !busy)
  })
}

@Composable
private fun GroupEditSheet(gid: String) {
  val scope = rememberCoroutineScope()
  val g = groupOf(gid)
  var name by remember(gid) { mutableStateOf(g?.name ?: "") }
  var about by remember(gid) { mutableStateOf(g?.about ?: "") }
  var pic by remember(gid) { mutableStateOf(g?.picture ?: "") }
  var busy by remember { mutableStateOf(false) }
  SheetScaffold("Edit group", gid.take(24), {
    if (g == null) {
      Text("Group not found.", color = LocalXM.current.text2)
    } else {
      XMField(name, { name = it }, "Name", maxChars = 60)
      Spacer(Modifier.height(10.dp))
      XMField(about, { about = it }, "About", minLines = 2, singleLine = false, maxChars = 280)
      Spacer(Modifier.height(10.dp))
      XMField(pic, { pic = it.trim() }, "Picture URL", mono = true)
      Spacer(Modifier.height(12.dp))
      XMButton("Save", {
        busy = true
        scope.launch(Dispatchers.IO) {
          try {
            Repo.updateGroupMeta(g, name.trim(), about.trim(), pic.trim())
            withContext(Dispatchers.Main) {
              busy = false
              Nav.closeSheet()
              Nav.toast("Group saved")
            }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) { busy = false; Nav.toast("Save failed: ${e.message}") }
          }
        }
      }, Modifier.fillMaxWidth(), enabled = !busy)
      Spacer(Modifier.height(8.dp))
      XMButton("Remove from list", {
        Repo.removeGroup(gid)
        if (S.groupId == gid) { S.groupId = ""; S.save() }
        Nav.closeSheet()
        Nav.toast("Removed")
      }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost, small = true)
    }
  })
}

@Composable
private fun NewChannelSheet(gid: String) {
  var id by remember { mutableStateOf("") }
  var topic by remember { mutableStateOf("") }
  var locked by remember { mutableStateOf(false) }
  SheetScaffold("New channel", "Lives inside this group", {
    if (groupOf(gid) == null) {
      Text("Group not found.", color = LocalXM.current.text2)
    } else {
      XMField(id, { id = it.trim().lowercase().filter { c -> c.isLetterOrDigit() || c == '-' } }, "channel-id", mono = true, maxChars = 40)
      Spacer(Modifier.height(10.dp))
      XMField(topic, { topic = it }, "Topic (optional)", maxChars = 140)
      Spacer(Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Pill(if (locked) "🔒 Locked" else "🔓 Open", locked) { locked = !locked }
        Spacer(Modifier.width(8.dp))
        Text("Locked channels encrypt with a channel key.", fontSize = 12.5.sp, color = LocalXM.current.text3)
      }
      Spacer(Modifier.height(12.dp))
      XMButton("Create channel", {
        if (id.isEmpty()) { Nav.toast("Give it an id"); return@XMButton }
        val g = groupOf(gid) ?: return@XMButton
        Repo.addLocalChannel(gid, ChanDef(id, topic.trim()))
        if (locked) Repo.setGroupKey(Repo.groupChannelId(g, id), Repo.newGroupKey())
        S.groupId = gid; S.channel = id; S.save()
        Nav.closeSheet()
        Nav.toast(if (locked) "Locked channel created" else "Channel created")
      }, Modifier.fillMaxWidth())
    }
  })
}

// ================= feed app / upload / queue / about =================
@Composable
private fun FeedAppSheet(appId: String) {
  val scope = rememberCoroutineScope()
  val apps = remember { ViewCache.apps }
  var appSel by remember { mutableStateOf(appId.ifEmpty { apps.firstOrNull()?.id ?: "" }) }
  var gid by remember { mutableStateOf(LiveCtx.group?.id ?: Repo.groupsCache.firstOrNull()?.id ?: "") }
  var ch by remember { mutableStateOf(LiveCtx.channel) }
  var busy by remember { mutableStateOf(false) }
  SheetScaffold("Feed app to channel", "Posts a playable card in Concord", {
    if (apps.isEmpty()) {
      Text("No mini apps loaded — open Mini Apps first.", color = LocalXM.current.text2)
    } else {
      Text("App", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalXM.current.text2)
      Spacer(Modifier.height(6.dp))
      apps.take(8).forEach { a ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
          .background(if (appSel == a.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else LocalXM.current.surface2)
          .clickable { appSel = a.id }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
          AppTile(a, 34.dp)
          Spacer(Modifier.width(10.dp))
          Text(a.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        }
      }
      Spacer(Modifier.height(10.dp))
      Text("Group", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalXM.current.text2)
      Spacer(Modifier.height(6.dp))
      if (Repo.groupsCache.isEmpty()) Text("No groups yet — create one in Chats.", color = LocalXM.current.text2)
      Repo.groupsCache.forEach { g ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
          .background(if (gid == g.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else LocalXM.current.surface2)
          .clickable { gid = g.id }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(g.name.ifEmpty { "Group" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Spacer(Modifier.height(10.dp))
      XMField(ch, { ch = it.trim().lowercase() }, "channel", mono = true, maxChars = 40)
      Spacer(Modifier.height(12.dp))
      XMButton("Feed to #$ch", {
        val entry = apps.firstOrNull { it.id == appSel }
        val g = groupOf(gid)
        if (entry == null) { Nav.toast("Pick an app"); return@XMButton }
        if (g == null || g.demo) { Nav.toast("Pick a real group"); return@XMButton }
        if (ch.isEmpty()) { Nav.toast("Pick a channel"); return@XMButton }
        busy = true
        scope.launch(Dispatchers.IO) {
          try {
            val (_, res, _) = Webxdc.feedAppToChannel(entry, g, ch)
            withContext(Dispatchers.Main) {
              busy = false
              Nav.closeSheet()
              Nav.toast(if (res.ok) "Fed to #$ch" else "No relay accepted it")
            }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) { busy = false; Nav.toast("Feed failed: ${e.message}") }
          }
        }
      }, Modifier.fillMaxWidth(), enabled = !busy, icon = "send")
    }
  })
}

@Composable
private fun UploadSheet() {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var bytes by remember { mutableStateOf<ByteArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  SheetScaffold("Upload game", ".zip with index.html at its root · 20 MB cap", {
    XMButton(if (name.isEmpty()) "Pick .zip" else name, {
      Platform.pickFile("application/zip") { n, b -> name = n; bytes = b }
    }, Modifier.fillMaxWidth(), kind = BtnKind.Line, icon = "upload")
    if (bytes != null) {
      Spacer(Modifier.height(4.dp))
      Text("%.1f MB".format((bytes?.size ?: 0) / 1048576.0), fontSize = 12.5.sp, color = LocalXM.current.text2)
    }
    Spacer(Modifier.height(12.dp))
    XMButton("Install", {
      val b = bytes
      if (b == null) { Nav.toast("Pick a file first"); return@XMButton }
      busy = true
      scope.launch(Dispatchers.IO) {
        try {
          Webxdc.addUpload(b, name, name.substringBeforeLast("."), "", emptyList())
          withContext(Dispatchers.Main) {
            busy = false
            Nav.closeSheet()
            Nav.toast("Game installed")
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) { busy = false; Nav.toast("Install failed: ${e.message}") }
        }
      }
    }, Modifier.fillMaxWidth(), enabled = !busy && bytes != null)
  })
}

@Composable
private fun QueueSheet() {
  val queue = Player.queue.collectAsState().value
  val current = Player.current.collectAsState().value
  SheetScaffold("Queue", "${queue.size} tracks", {
    if (queue.isEmpty()) Text("Nothing queued — tap ▶ on any track.", color = LocalXM.current.text2)
    queue.forEach { t ->
      Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
        .background(if (current?.id == t.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else LocalXM.current.surface2)
        .clickable { Player.play(t) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(if (current?.id == t.id) "pause" else "play", 16.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(t.title.ifEmpty { "Untitled" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
          maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      }
    }
    Spacer(Modifier.height(10.dp))
    XMButton("Stop", { Player.stop(); Nav.closeSheet() }, Modifier.fillMaxWidth(),
      kind = BtnKind.Line, small = true)
  })
}

@Composable
private fun AboutSheet() {
  SheetScaffold("About", "", {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      XLogo(64.dp)
      Spacer(Modifier.height(10.dp))
      Text("XM Arcade 1.0.0", style = MaterialTheme.typography.titleLarge)
      Text("native Kotlin · Jetpack Compose", fontSize = 13.sp, color = LocalXM.current.text2)
    }
    Spacer(Modifier.height(12.dp))
    Text("Shorts, mini apps, music and Concord chats on Nostr — with lightning zaps and Monero xaps built in. Your keys never leave this device (or your Amber signer).",
      fontSize = 14.sp, color = LocalXM.current.text2)
    Spacer(Modifier.height(12.dp))
    XMButton("Close", { Nav.closeSheet() }, Modifier.fillMaxWidth(), kind = BtnKind.Line)
  })
}
