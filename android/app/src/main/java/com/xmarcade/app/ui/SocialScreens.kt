package com.xmarcade.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmarcade.app.Platform
import com.xmarcade.app.core.AppScope
import com.xmarcade.app.core.ChanDef
import com.xmarcade.app.core.ChatMsg
import com.xmarcade.app.core.Group
import com.xmarcade.app.core.MiniApp
import com.xmarcade.app.core.Nip19
import com.xmarcade.app.core.NostrEvent
import com.xmarcade.app.core.Profile
import com.xmarcade.app.core.Repo
import com.xmarcade.app.core.Signer
import com.xmarcade.app.core.Webxdc
import com.xmarcade.app.core.displayName
import com.xmarcade.app.core.parseAppCard
import com.xmarcade.app.core.timeAgo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChannelData(val group: Group, val channel: String)

private val DEMO_GROUPS = listOf(
  Group("demo/lounge", "demo", "lounge", "XM Lounge", "Demo group — everything here stays on this device.",
    "", emptyList(), listOf(ChanDef("general", "Say hi"), ChanDef("arcade", "Game talk")), true),
)

private fun chansOf(g: Group): List<ChanDef> =
  g.channels.ifEmpty { listOf(ChanDef("general", "Main channel")) }

// ================= Chats =================
@Composable
fun ChatsScreen() {
  val scope = rememberCoroutineScope()
  var groups by remember { mutableStateOf(Repo.groupsCache) }
  var loading by remember { mutableStateOf(groups.isEmpty()) }

  LaunchedEffect(Unit) {
    scope.launch(Dispatchers.IO) {
      try {
        val fresh = Repo.fetchGroups()
        withContext(Dispatchers.Main) { groups = fresh.ifEmpty { Repo.groupsCache } }
      } catch (_: Exception) {}
      withContext(Dispatchers.Main) { loading = false }
    }
  }
  // refresh after group/channel sheets close
  LaunchedEffect(Nav.sheet) {
    if (Nav.sheet == null) groups = Repo.groupsCache
  }

  val shown = groups.ifEmpty { DEMO_GROUPS }
  val sel = shown.firstOrNull { it.id == S.groupId } ?: shown.firstOrNull()

  Column(Modifier.fillMaxSize()) {
    TopBar("Concord Chats", displayName(Me.profile), Me.profile.picture, onDd = { Nav.ddOpen = true })
    if (loading) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Finding your groups…", color = LocalXM.current.text2)
      }
      return
    }
    Row(Modifier.fillMaxSize()) {
      Column(Modifier.width(76.dp).verticalScroll(rememberScrollState())
        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
          .clickable { Nav.openSheet(Sheet("newGroup")) }, contentAlignment = Alignment.Center) {
          AppIcon("plus", 22.dp, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        shown.forEach { g ->
          val on = sel?.id == g.id
          val gname = g.name.ifEmpty { "Group" }
          Box(Modifier.size(52.dp).clip(RoundedCornerShape(if (on) 18.dp else 99.dp))
            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else LocalXM.current.surface2)
            .clickable { S.groupId = g.id; S.save() }, contentAlignment = Alignment.Center) {
            if (g.picture.isNotEmpty()) AsyncImg(g.picture, Modifier.matchParentSize())
            else Text(gname.first().uppercase(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
          }
          Spacer(Modifier.height(8.dp))
        }
      }
      Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 14.dp)) {
        val g = sel
        if (g == null) {
          EmptyState("No Concord groups", "Create one — everything here is NIP-29 native.") {
            Spacer(Modifier.height(8.dp))
            XMButton("+ New group", { Nav.openSheet(Sheet("newGroup")) }, small = true)
          }
        } else {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(LocalXM.current.surface2),
              contentAlignment = Alignment.Center) {
              if (g.picture.isNotEmpty()) AsyncImg(g.picture, Modifier.matchParentSize())
              else Text(g.name.firstOrNull()?.uppercase() ?: "G", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name.ifEmpty { "Group" }, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                  maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (g.demo) { Spacer(Modifier.width(6.dp)); ModeBadge(false) }
              }
              Text("${chansOf(g).size} channels", fontSize = 12.5.sp, color = LocalXM.current.text2)
            }
            if (!g.demo) BareIconBtn("edit", "Edit", { Nav.openSheet(Sheet("groupEdit", mapOf("gid" to g.id))) }, size = 38.dp)
          }
          Spacer(Modifier.height(8.dp))
          Text("NIP-29 group · channels below. Tap one to open it.", fontSize = 12.5.sp, color = LocalXM.current.text3)
          Spacer(Modifier.height(10.dp))
          chansOf(g).forEach { ch ->
            val locked = !g.demo && Repo.getGroupKey(Repo.groupChannelId(g, ch.name)).isNotEmpty()
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
              .background(if (S.channel == ch.name && S.groupId == g.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
              .clickable {
                S.groupId = g.id; S.channel = ch.name; S.save()
                Nav.go("channel", ChannelData(g, ch.name))
              }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
              AppIcon("hash", 18.dp, tint = LocalXM.current.text2)
              Spacer(Modifier.width(10.dp))
              Column(Modifier.weight(1f)) {
                Text("#${ch.name}", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                if (ch.topic.isNotEmpty()) Text(ch.topic, fontSize = 12.sp, color = LocalXM.current.text2,
                  maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              if (locked) AppIcon("lock", 15.dp, tint = LocalXM.current.warn)
            }
            Spacer(Modifier.height(8.dp))
          }
          if (!g.demo) {
            XMButton("+ New channel", { Nav.openSheet(Sheet("newChannel", mapOf("gid" to g.id))) },
              Modifier.fillMaxWidth(), kind = BtnKind.Line, small = true)
          }
          Spacer(Modifier.height(20.dp))
        }
      }
    }
  }
}

// ================= Channel =================
private suspend fun toChatMsg(e: NostrEvent, g: Group, channel: String): ChatMsg {
  val id = Repo.groupChannelId(g, channel)
  val who = if (e.pubkey.length == 64) displayName(Repo.cachedProfile(e.pubkey)) else e.pubkey.ifEmpty { "anon" }
  if (Repo.isConcordEncrypted(e)) {
    val plain = try { Repo.decryptConcord(e, id) } catch (_: Exception) { null }
    return if (plain == null)
      ChatMsg(e.pubkey, who, "🔒 Encrypted — tap to request key", timeAgo(e.createdAt), true, null)
    else
      ChatMsg(e.pubkey, who, plain, timeAgo(e.createdAt), true, try { parseAppCard(plain) } catch (_: Exception) { null })
  }
  return ChatMsg(e.pubkey, who, e.content, timeAgo(e.createdAt), false,
    try { parseAppCard(e.content) } catch (_: Exception) { null })
}

@Composable
fun ChannelScreen(data: ChannelData) {
  val scope = rememberCoroutineScope()
  val g = remember(data.group.id) {
    (if (data.group.demo) DEMO_GROUPS else Repo.groupsCache).firstOrNull { it.id == data.group.id } ?: data.group
  }
  val channel = S.channel
  val demo = g.demo
  val chanId = remember(g.id, channel) { Repo.groupChannelId(g, channel) }
  var msgs by remember(g.id, channel) { mutableStateOf<List<ChatMsg>>(emptyList()) }
  var fullyLocked by remember(g.id, channel) { mutableStateOf(false) }
  var loading by remember(g.id, channel) { mutableStateOf(true) }
  var field by remember(g.id, channel) { mutableStateOf("") }
  val hasKey = !demo && Repo.getGroupKey(chanId).isNotEmpty()

  LaunchedEffect(g.id, channel) {
    LiveCtx.group = g; LiveCtx.channel = channel; LiveCtx.demo = demo
    if (demo) {
      msgs = Repo.DemoStore.forId("${g.id}/$channel").map { ChatMsg("", it.who, it.text, it.t) }
      loading = false
      return@LaunchedEffect
    }
    scope.launch(Dispatchers.IO) {
      try {
        val evs = Repo.fetchChannelMessages(g, channel, 100)
        try {
          Repo.fetchProfiles(evs.map { it.pubkey }.distinct().take(50))
        } catch (_: Exception) {}
        val parsed = evs.map { toChatMsg(it, g, channel) }
        withContext(Dispatchers.Main) {
          msgs = parsed
          fullyLocked = parsed.isNotEmpty() && parsed.all { it.encrypted && it.text.startsWith("🔒") }
          loading = false
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { loading = false; Nav.toast("History failed: ${e.message}") }
      }
    }
    if (!demo) {
      val stop = Repo.subscribeChannel(g, channel) { e ->
        AppScope.launch(Dispatchers.IO) {
          try { Repo.fetchProfiles(listOf(e.pubkey)) } catch (_: Exception) {}
          val m = try { toChatMsg(e, g, channel) } catch (_: Exception) { return@launch }
          withContext(Dispatchers.Main) {
            if (msgs.none { it.pubkey == m.pubkey && it.text == m.text && it.t == m.t }) {
              msgs = msgs + m
              if (!(m.encrypted && m.text.startsWith("🔒"))) fullyLocked = false
            }
          }
        }
      }
      LiveSub.hold(stop)
    }
  }
  DisposableEffect(g.id, channel) {
    onDispose { LiveSub.release() }
  }

  fun send() {
    val body = field.trim()
    if (body.isEmpty()) return
    field = ""
    scope.launch(Dispatchers.IO) {
      try {
        if (demo) {
          val who = displayName(Me.profile).ifEmpty { "you" }
          Repo.DemoStore.push("${g.id}/$channel", who, body)
          withContext(Dispatchers.Main) { msgs = msgs + ChatMsg("", who, body, "now") }
        } else {
          if (!Signer.isSignedIn()) throw IllegalStateException("Sign in to send")
          val enc = Repo.getGroupKey(chanId).isNotEmpty()
          val (ev, _) = Repo.sendGroupMessage(g, channel, body, enc)
          val m = toChatMsg(ev, g, channel)
          withContext(Dispatchers.Main) {
            if (msgs.none { it.pubkey == m.pubkey && it.text == m.text }) msgs = msgs + m
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { Nav.toast("Send failed: ${e.message}") }
      }
    }
  }

  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      BareIconBtn("chevL", "Back", { Nav.back() })
      Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { Nav.ddOpen = true }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (displayName(Me.profile).isNotEmpty()) Avatar(displayName(Me.profile), 26.dp, Me.profile.picture)
        else XLogo(26.dp)
        Spacer(Modifier.width(8.dp))
        Text("#$channel", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Spacer(Modifier.weight(1f))
      if (hasKey) StaticChip("E2EE", "lock")
      else if (demo) StaticChip("demo")
      Spacer(Modifier.width(6.dp))
      RelayChip(onClick = { Nav.openSheet(Sheet("relays")) })
    }
    Text(g.name.ifEmpty { "Group" } + " · NIP-29 · kind 9",
      fontSize = 12.sp, color = LocalXM.current.text3, modifier = Modifier.padding(start = 18.dp, bottom = 6.dp))
    if (loading) {
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text("Loading messages…", color = LocalXM.current.text2)
      }
    } else if (fullyLocked) {
      Column(Modifier.weight(1f).fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        AppIcon("lock", 44.dp, tint = LocalXM.current.warn)
        Spacer(Modifier.height(12.dp))
        Text("Locked — request the key", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text("Messages are NIP-44 encrypted for members.", fontSize = 14.sp, color = LocalXM.current.text2)
        Spacer(Modifier.height(12.dp))
        XMButton("Request key", { Nav.openSheet(Sheet("reqKey", mapOf("gid" to g.id, "ch" to channel))) }, small = true)
      }
    } else {
      LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), reverseLayout = true) {
        items(msgs.reversed(), key = { it.pubkey + "|" + it.t + "|" + it.text.hashCode() }) { m ->
          MsgBubble(m, g, channel)
        }
      }
    }
    if (!fullyLocked) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Pill(if (demo) "Demo" else if (hasKey) "🔒 Encrypted" else "🔒 Open", true) {
          Nav.openSheet(Sheet("chanKey", mapOf("gid" to g.id, "ch" to channel)))
        }
        Spacer(Modifier.width(8.dp))
        if (!demo && Signer.isSignedIn()) {
          Pill("Key", false) { Nav.openSheet(Sheet("shareKey", mapOf("gid" to g.id, "ch" to channel))) }
          Spacer(Modifier.width(8.dp))
        }
        Pill("Feed", false) { Nav.openSheet(Sheet("feedApp")) }
      }
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
          XMField(field, { field = it }, "Message #$channel…", maxChars = 2000)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.primary)
          .clickable { send() }, contentAlignment = Alignment.Center) {
          AppIcon("send", 20.dp, tint = Color.White)
        }
      }
    }
  }
}

private object LiveSub {
  var stop: (() -> Unit)? = null
  fun hold(s: () -> Unit) { try { stop?.invoke() } catch (_: Exception) {}; stop = s }
  fun release() { try { stop?.invoke() } catch (_: Exception) {}; stop = null }
}

@Composable
private fun MsgBubble(m: ChatMsg, g: Group, channel: String) {
  val myPk = Signer.pubkey ?: ""
  val me = Signer.isSignedIn() && m.pubkey.isNotEmpty() && m.pubkey == myPk
  val locked = m.encrypted && m.text.startsWith("🔒")
  val scope = rememberCoroutineScope()
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
    if (!me) {
      Text(m.who.ifEmpty { "anon" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalXM.current.text3,
        modifier = Modifier.padding(start = 12.dp, bottom = 2.dp).clickable { Acts.openAuthor(m.pubkey) })
    }
    when {
      locked -> Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
        .background(LocalXM.current.surface2)
        .clickable { Nav.openSheet(Sheet("reqKey", mapOf("gid" to g.id, "ch" to channel))) }.padding(12.dp)) {
        Text("🔒 Encrypted — tap to request key", fontSize = 14.sp, color = LocalXM.current.text2)
      }
      m.appCard != null -> {
        val card = m.appCard
        Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
          Text(card.title.ifEmpty { "Shared app" }, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
          if (card.note.isNotEmpty()) Text(card.note, fontSize = 12.5.sp, color = LocalXM.current.text2,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(8.dp))
          XMButton("Open in Arcade", {
            scope.launch(Dispatchers.IO) {
              try {
                val apps = try { Webxdc.catalog() } catch (_: Exception) { emptyList() }
                val want = card.file.ifEmpty { card.url }
                val found = apps.firstOrNull { it.url == want || it.file == want || it.url == card.url }
                val target = found ?: MiniApp(want, "channel", card.title.ifEmpty { "Shared app" }, card.note,
                  url = card.url, file = card.file)
                withContext(Dispatchers.Main) { Nav.go("gamerun", target) }
              } catch (e: Exception) {
                withContext(Dispatchers.Main) { Nav.toast("Launch failed: ${e.message}") }
              }
            }
          }, small = true, icon = "play")
        }
      }
      else -> Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
        .background(if (me) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
        .padding(horizontal = 13.dp, vertical = 9.dp)) {
        Column {
          Text(m.text, fontSize = 14.5.sp)
          Text(m.t, fontSize = 11.sp, color = LocalXM.current.text3)
        }
      }
    }
  }
}

// ================= Compose =================
@Composable
fun ComposeScreen(replyTo: ReplyTo? = null) {
  val scope = rememberCoroutineScope()
  var body by remember { mutableStateOf("") }
  var target by remember { mutableStateOf<NostrEvent?>(null) }
  var fileName by remember { mutableStateOf("") }
  var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
  var busy by remember { mutableStateOf(false) }

  LaunchedEffect(replyTo?.id) {
    if (replyTo != null) {
      scope.launch(Dispatchers.IO) {
        val t = Acts.fetchTarget(replyTo.id)
        withContext(Dispatchers.Main) { target = t }
      }
    }
  }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    BackBar(if (replyTo == null) "New post" else "Reply", onBack = { Nav.back() })
    Column(Modifier.padding(horizontal = 18.dp)) {
      if (target != null) {
        val tp = remember(target!!.pubkey) { Repo.cachedProfile(target!!.pubkey) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
          .background(LocalXM.current.surface2).padding(12.dp)) {
          Text("Replying to ${displayName(tp)}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.height(4.dp))
          Text(target!!.content, fontSize = 13.5.sp, color = LocalXM.current.text2,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
      }
      XMField(body, { body = it }, "What's happening?", minLines = 5, singleLine = false, maxChars = 2000,
        modifier = Modifier.height(140.dp))
      Spacer(Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        XMButton(if (fileName.isEmpty()) "Attach" else "Replace", {
          Platform.pickFile("*/*") { name, bytes -> fileName = name; fileBytes = bytes }
        }, kind = BtnKind.Line, small = true, icon = "image")
        if (fileName.isNotEmpty()) {
          Spacer(Modifier.width(8.dp))
          Text(fileName, fontSize = 12.5.sp, color = LocalXM.current.text2, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          BareIconBtn("close", "Remove", { fileName = ""; fileBytes = null }, size = 32.dp)
        }
      }
      Spacer(Modifier.height(14.dp))
      XMButton("Post", {
        if (body.trim().isEmpty() && fileBytes == null) { Nav.toast("Write something first"); return@XMButton }
        if (!Signer.isSignedIn()) { Nav.toast("Sign in first"); return@XMButton }
        busy = true
        scope.launch(Dispatchers.IO) {
          try {
            val tags = mutableListOf<List<String>>()
            if (replyTo != null) {
              tags.add(listOf("e", replyTo.id, "", "reply"))
              tags.add(listOf("p", replyTo.pubkey))
              tags.add(listOf("k", replyTo.kind.toString()))
            }
            val fb = fileBytes
            if (fb != null) {
              val mime = Platform.mimeFor(fileName)
              val up = Repo.blossomUpload(fb, fileName.ifEmpty { "attach" }, mime)
              tags.add(listOf("imeta", "url ${up.url}", "m $mime", "x ${up.sha256}", "size ${up.size}"))
            }
            val (_, res) = Repo.publishNote(body.trim(), tags)
            withContext(Dispatchers.Main) {
              busy = false
              Nav.back()
              Nav.toast(if (res.ok) "Posted on ${res.oks}/${res.total} relays" else "No relay accepted it")
            }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) { busy = false; Nav.toast("Post failed: ${e.message}") }
          }
        }
      }, Modifier.fillMaxWidth(), enabled = !busy)
      Spacer(Modifier.height(30.dp))
    }
  }
}

// ================= Studio =================
@Composable
fun StudioScreen(kindArg: String? = null) {
  val scope = rememberCoroutineScope()
  var kind by remember { mutableStateOf(kindArg ?: "short") }
  var fileName by remember(kind) { mutableStateOf("") }
  var fileBytes by remember(kind) { mutableStateOf<ByteArray?>(null) }
  var title by remember(kind) { mutableStateOf("") }
  var tags by remember(kind) { mutableStateOf("") }
  var desc by remember(kind) { mutableStateOf("") }
  var collabInput by remember(kind) { mutableStateOf("") }
  var collabs by remember(kind) { mutableStateOf(listOf<Pair<String, String>>()) }
  var followOpts by remember { mutableStateOf(listOf<Profile>()) }
  var busy by remember { mutableStateOf(false) }
  var resultShort by remember { mutableStateOf<com.xmarcade.app.core.ShortV?>(null) }
  var resultTrack by remember { mutableStateOf<com.xmarcade.app.core.Track?>(null) }
  var resultApp by remember { mutableStateOf<MiniApp?>(null) }

  LaunchedEffect(kind) {
    if (kind != "short" || !Signer.isSignedIn()) return@LaunchedEffect
    scope.launch(Dispatchers.IO) {
      try {
        val f = (Repo.fetchContacts() + S.followUsers.toList()).distinct()
          .filter { it.matches(Regex("^[0-9a-f]{64}$")) }.take(200)
        if (f.isNotEmpty()) Repo.fetchProfiles(f)
        val opts = f.map { Repo.cachedProfile(it) }
        withContext(Dispatchers.Main) { followOpts = opts }
      } catch (_: Exception) {}
    }
  }

  fun tagNames(): List<String> = tags.split(Regex("[,\\s]+")).map { it.trim().lowercase().removePrefix("#") }.filter { it.isNotEmpty() }

  fun addPk(pk: String, label: String) {
    if (!Regex("^[0-9a-f]{64}$").matches(pk)) return
    if (collabs.none { it.first == pk }) collabs = collabs + (pk to label.ifEmpty { pk.take(8) + "…" })
  }

  fun addCollabFromInput() {
    val t = collabInput.trim()
    if (t.isEmpty()) return
    val d = try { Nip19.decode(t) } catch (_: Exception) { null }
    val pk = if (d != null && (d.type == "npub" || d.type == "nprofile")) d.dataHex else ""
    if (pk.isNotEmpty() && Regex("^[0-9a-f]{64}$").matches(pk)) {
      val known = followOpts.firstOrNull { it.pubkey == pk }?.let { displayName(it) } ?: ""
      addPk(pk, known.ifEmpty { t.take(12) + "…" })
      collabInput = ""
    } else Nav.toast("Paste an npub or pick an @name below")
  }

  fun pick() {
    val mime = when (kind) { "game" -> "application/zip"; "track" -> "audio/*"; else -> "video/*" }
    Platform.pickFile(mime) { name, bytes ->
      fileName = name; fileBytes = bytes
      if (title.isEmpty()) title = name.substringBeforeLast(".").replace("_", " ").replace("-", " ")
    }
  }

  fun go() {
    val fb = fileBytes
    if (fb == null) { Nav.toast("Pick a file first"); return }
    if (!Signer.isSignedIn()) { Nav.toast("Sign in first"); return }
    busy = true
    scope.launch(Dispatchers.IO) {
      try {
        when (kind) {
          "game" -> {
            val app = Webxdc.addUpload(fb, fileName, title.ifEmpty { fileName }, "", tagNames())
            withContext(Dispatchers.Main) {
              busy = false; resultApp = app
              Nav.toast("Game installed — tap Play")
            }
          }
          "track" -> {
            val mime = Platform.mimeFor(fileName)
            val up = Repo.blossomUpload(fb, fileName, mime)
            val tn = (listOf("music") + tagNames()).distinct()
            val (ev, res) = Repo.publishTrack(up.url, title.ifEmpty { fileName }, tn, mime)
            val t = com.xmarcade.app.core.Track(ev.id, ev.pubkey, ev.createdAt, up.url, title.ifEmpty { fileName }, tn)
            withContext(Dispatchers.Main) {
              busy = false; resultTrack = t
              Nav.toast(if (res.ok) "Track published" else "No relay accepted it")
            }
          }
          else -> {
            val mime = Platform.mimeFor(fileName)
            val up = Repo.blossomUpload(fb, fileName, mime)
            val tn = tagNames().distinct()
            val (ev, res) = Repo.publishShort(up.url, title.ifEmpty { fileName }, tn, "", mime, up.sha256, 0.0,
              desc.trim(), collabs.map { it.first })
            val sh = com.xmarcade.app.core.ShortV(ev.id, ev.pubkey, ev.createdAt, up.url, "", mime, 0.0, tn, title.ifEmpty { fileName })
            withContext(Dispatchers.Main) {
              busy = false; resultShort = sh
              Nav.toast(if (res.ok) "Short published" else "No relay accepted it")
            }
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { busy = false; Nav.toast("Studio failed: ${e.message}") }
      }
    }
  }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    BackBar("Studio", onBack = { Nav.back() })
    Column(Modifier.padding(horizontal = 18.dp)) {
      Row {
        listOf("short" to "Short", "game" to "Game", "track" to "Music").forEach { (id, label) ->
          Pill(label, kind == id) { kind = id }
          Spacer(Modifier.width(8.dp))
        }
      }
      Spacer(Modifier.height(14.dp))
      XMButton(when (kind) {
        "game" -> if (fileName.isEmpty()) "Pick .zip" else fileName
        "track" -> if (fileName.isEmpty()) "Pick audio" else fileName
        else -> if (fileName.isEmpty()) "Pick video" else fileName
      }, { pick() }, Modifier.fillMaxWidth(), kind = BtnKind.Line, icon = "upload")
      Spacer(Modifier.height(12.dp))
      if (kind != "game") {
        XMField(title, { title = it }, "Title", maxChars = 120)
        Spacer(Modifier.height(10.dp))
        if (kind == "short") {
          XMField(desc, { desc = it }, "Description", maxChars = 500, singleLine = false, minLines = 2)
          Spacer(Modifier.height(10.dp))
        }
        XMField(tags, { tags = it }, if (kind == "short") "hashtags space separated" else "tags, comma, separated", maxChars = 200)
        Spacer(Modifier.height(10.dp))
        if (kind == "short") {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
              XMField(collabInput, { collabInput = it }, "@name or paste npub…", maxChars = 120)
            }
            Spacer(Modifier.width(8.dp))
            XMButton("Add", { addCollabFromInput() }, small = true)
          }
          val tok = collabInput.split(Regex("\\s+")).lastOrNull()?.trim() ?: ""
          if (tok.startsWith("@") && tok.length >= 2) {
            val q = tok.drop(1).lowercase()
            val matches = followOpts.filter { p ->
              val n = p.displayName.ifEmpty { p.name }
              n.isNotEmpty() && n.lowercase().contains(q)
            }.take(5)
            if (matches.isNotEmpty()) {
              Spacer(Modifier.height(6.dp))
              Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)) {
                matches.forEach { p ->
                  Row(Modifier.fillMaxWidth().clickable {
                    addPk(p.pubkey, displayName(p))
                    collabInput = collabInput.removeSuffix(tok).trimEnd()
                  }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(displayName(p), 30.dp, p.picture)
                    Spacer(Modifier.width(10.dp))
                    Text(displayName(p), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                      maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }
          if (collabs.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row {
              collabs.forEach { (pk, label) ->
                Row(Modifier.clip(RoundedCornerShape(99.dp))
                  .background(LocalXM.current.surface2)
                  .padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                  Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    color = LocalXM.current.text2)
                  Spacer(Modifier.width(6.dp))
                  Text("✕", fontSize = 12.sp, color = LocalXM.current.text3,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                      collabs = collabs.filter { it.first != pk }
                    })
                }
                Spacer(Modifier.width(6.dp))
              }
            }
          }
          Spacer(Modifier.height(12.dp))
        } else {
          Spacer(Modifier.height(2.dp))
        }
      } else {
        XMField(title, { title = it }, "Game title (optional)", maxChars = 120)
        Spacer(Modifier.height(12.dp))
      }
      XMButton(when (kind) {
        "game" -> "Install game"; "track" -> "Upload track"; else -> "Upload short"
      }, { go() }, Modifier.fillMaxWidth(), enabled = !busy)
      Spacer(Modifier.height(8.dp))
      Text(when (kind) {
        "game" -> ".zip with index.html at its root · 20 MB cap"
        "track" -> "audio → Blossom → kind 1 · 10 MB cap"
        else -> "video → Blossom → kind 22 · 16 MB cap"
      }, fontSize = 12.5.sp, color = LocalXM.current.text3)
      resultShort?.let { sh ->
        Spacer(Modifier.height(14.dp))
        Text("Posted ✓", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
          ShortVideo(sh.url, "", true)
        }
      }
      resultTrack?.let { t ->
        Spacer(Modifier.height(14.dp))
        Text("Published ✓", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.surface).padding(12.dp),
          verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable {
              com.xmarcade.app.core.Player.enqueue(t); com.xmarcade.app.core.Player.play(t)
            }, contentAlignment = Alignment.Center) {
            AppIcon("play", 20.dp, tint = MaterialTheme.colorScheme.primary)
          }
          Spacer(Modifier.width(10.dp))
          Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
      }
      resultApp?.let { app ->
        Spacer(Modifier.height(14.dp))
        Text("Installed ✓", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.surface).padding(12.dp),
          verticalAlignment = Alignment.CenterVertically) {
          AppTile(app, 48.dp)
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(app.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("local upload", fontSize = 12.sp, color = LocalXM.current.text2)
          }
          XMButton("Play", { Nav.go("gamerun", app) }, small = true, icon = "play")
        }
      }
      Spacer(Modifier.height(30.dp))
    }
  }
}
