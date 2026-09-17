package com.xmarcade.app.ui

import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.xmarcade.app.Platform
import com.xmarcade.app.core.AppScope
import com.xmarcade.app.core.MiniApp
import com.xmarcade.app.core.Nip19
import com.xmarcade.app.core.NostrEvent
import com.xmarcade.app.core.Player
import com.xmarcade.app.core.Profile
import com.xmarcade.app.core.Repo
import com.xmarcade.app.core.ShortV
import com.xmarcade.app.core.Signer
import com.xmarcade.app.core.Track
import com.xmarcade.app.core.Webxdc
import com.xmarcade.app.core.displayName
import com.xmarcade.app.core.fmtNum
import com.xmarcade.app.core.timeAgo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Own profile cache for top bars. */
object Me {
  var profile by mutableStateOf(Profile(""))
  fun refresh() {
    val pk = Signer.pubkey ?: ""
    profile = if (pk.isEmpty()) Profile("") else Repo.cachedProfile(pk)
  }
}

// ================= Profile =================
@Composable
fun ProfileScreen(pkArg: String? = null) {
  val scope = rememberCoroutineScope()
  val me = pkArg ?: (Signer.pubkey ?: "")
  val isMe = me.isNotEmpty() && me == Signer.pubkey && Signer.isSignedIn()
  val isPush = Nav.stack.size > 1
  val following = S.followUsers.contains(me)
  var p by remember(me) { mutableStateOf(if (me.isEmpty() || me.startsWith("demo")) Profile("") else Repo.cachedProfile(me)) }
  var notes by remember(me) { mutableStateOf<List<NostrEvent>>(emptyList()) }
  var loading by remember(me) { mutableStateOf(true) }

  LaunchedEffect(me) {
    if (me.isEmpty() || me.startsWith("demo")) { loading = false; return@LaunchedEffect }
    scope.launch(Dispatchers.IO) {
      try { Repo.fetchProfiles(listOf(me)) } catch (_: Exception) {}
      val prof = Repo.cachedProfile(me)
      val list = try { Repo.fetchAuthorNotes(me) } catch (_: Exception) { emptyList() }
      list.forEach { ViewCache.notesById[it.id] = it }
      withContext(Dispatchers.Main) { p = prof; notes = list; loading = false }
    }
  }

  Column(Modifier.fillMaxSize()) {
    if (isPush) BackBar("Profile", onBack = { Nav.back() })
    else TopBar("Profile", displayName(Me.profile), Me.profile.picture, onDd = { Nav.ddOpen = true })
    if (me.isEmpty()) {
      EmptyState("Not signed in", "Create an account or log in to see your profile.") {
        Spacer(Modifier.height(8.dp))
        XMButton("Welcome", { Nav.reset("welcome") }, small = true)
      }
      return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
      item {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
          Box(Modifier.fillMaxWidth().height(110.dp).background(
            Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFF22D3EE))))) {
            if (p.banner.isNotEmpty()) AsyncImg(p.banner, Modifier.matchParentSize())
          }
          Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Avatar(displayName(p), 64.dp, p.picture)
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(displayName(p), style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                  Spacer(Modifier.width(8.dp))
                  ModeBadge(!me.startsWith("demo"))
                }
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                  try { Acts.copyText(Nip19.npubEncode(me), "npub copied") } catch (_: Exception) {}
                }, verticalAlignment = Alignment.CenterVertically) {
                  Text(if (me.length > 24) me.take(24) + "…" else me,
                    fontFamily = Mono, fontSize = 12.sp, color = LocalXM.current.text2)
                  Spacer(Modifier.width(4.dp))
                  AppIcon("copy", 13.dp, tint = LocalXM.current.text3)
                }
              }
            }
            if (!isMe && !me.startsWith("demo")) {
              Spacer(Modifier.height(12.dp))
              Row(Modifier.fillMaxWidth()) {
                XMButton(if (following) "Following" else "+ Follow", { Acts.doFollow(me) },
                  Modifier.weight(1f), kind = if (following) BtnKind.Line else BtnKind.Primary, small = true)
                Spacer(Modifier.width(8.dp))
                XMButton("Zap", { Acts.doZapOpen("", me) }, Modifier.weight(1f),
                  kind = BtnKind.Line, small = true, icon = "zap")
                Spacer(Modifier.width(8.dp))
                XMButton("Xap", { Acts.doXapOpen("", me) }, Modifier.weight(1f),
                  kind = BtnKind.Line, small = true)
              }
            }
            if (isMe) {
              Spacer(Modifier.height(12.dp))
              Row(Modifier.fillMaxWidth()) {
                XMButton("+ Post", { Nav.go("compose") }, Modifier.weight(1f), small = true)
                Spacer(Modifier.width(8.dp))
                XMButton("Edit profile", { Nav.openSheet(Sheet("editProfile")) }, Modifier.weight(1f),
                  kind = BtnKind.Line, small = true)
                Spacer(Modifier.width(8.dp))
                XMButton("My keys", { Nav.openSheet(Sheet("keys")) }, Modifier.weight(1f),
                  kind = BtnKind.Line, small = true)
              }
            }
            if (p.about.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(p.about, fontSize = 14.sp) }
            if (p.nip05.isNotEmpty()) {
              Spacer(Modifier.height(4.dp))
              Text("✓ " + p.nip05, fontSize = 13.sp, color = LocalXM.current.ok)
            }
            Spacer(Modifier.height(10.dp))
            Row {
              Text("Following ", fontSize = 13.sp, color = LocalXM.current.text2)
              Text(fmtNum(S.followUsers.size.toLong()), fontSize = 13.sp, fontWeight = FontWeight.Bold)
              Spacer(Modifier.width(16.dp))
              Text("Events ", fontSize = 13.sp, color = LocalXM.current.text2)
              Text(fmtNum(notes.size.toLong()), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
        Spacer(Modifier.height(12.dp))
      }
      if (loading) item {
        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Loading…", color = LocalXM.current.text2)
        }
      }
      items(notes, key = { it.id }) { e ->
        val ap = if (e.pubkey == me) p else Repo.cachedProfile(e.pubkey)
        NoteCard(e, ap, onAuthor = { Acts.openAuthor(e.pubkey) },
          onReply = { Acts.doReplyCompose(e.id) }, onRepost = { Acts.doRepost(e.id) },
          onReact = { Acts.doReactOpen(e.id) }, onZap = { Acts.doZapOpen(e.id, e.pubkey) },
          onXap = { Acts.doXapOpen(e.id, e.pubkey) },
          onTag = { t -> S.tagFilter = t; S.shortTab = "tags"; S.save(); Nav.setSection("shorts") },
          onUrl = { Platform.openUri(it) })
        Spacer(Modifier.height(10.dp))
      }
      if (!loading && notes.isEmpty()) item {
        EmptyState("No notes yet", "This account hasn't posted.") {}
      }
      item { Spacer(Modifier.height(30.dp)) }
    }
  }
}

// ================= Shorts =================
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen() {
  val scope = rememberCoroutineScope()
  var loading by remember { mutableStateOf(true) }
  var list by remember { mutableStateOf<List<ShortV>>(emptyList()) }
  var tab by remember { mutableStateOf(S.shortTab) }
  val tag = S.tagFilter
  var replying by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(tab, tag) {
    loading = true
    scope.launch(Dispatchers.IO) {
      val tags = when (tab) {
        "for" -> listOf("webxdc")
        "tags" -> if (tag == "All") S.followTags.toList().ifEmpty { listOf("webxdc") } else listOf(tag)
        else -> S.followTags.toList().ifEmpty { listOf("webxdc") }
      }
      val authors = if (tab == "for") S.followUsers.toList() else emptyList()
      val res = try { Repo.fetchShorts(tags, if (authors.size > 100) authors.take(100) else authors, 40) }
      catch (_: Exception) { emptyList() }
      withContext(Dispatchers.Main) {
        list = if (res.isEmpty()) {
          val d = Repo.Demo.shorts()
          if (tab == "tags" && tag != "All") d.filter { it.tags.contains(tag.lowercase()) } else d
        } else res
        ViewCache.shorts = list
        loading = false
      }
    }
  }

  val pagerState = rememberPagerState { list.size }
  LaunchedEffect(list.size) {
    if (list.isNotEmpty() && pagerState.currentPage >= list.size) pagerState.scrollToPage(0)
  }
  Box(Modifier.fillMaxSize().background(Color.Black)) {
    if (loading) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading shorts…", color = Color.White.copy(alpha = 0.7f))
      }
    } else if (list.isEmpty()) {
      Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("No shorts yet", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text("Be the first to post one.", color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(12.dp))
        Row {
          XMButton("Show global", { tab = "global"; S.shortTab = "global"; S.save() }, kind = BtnKind.Line, small = true)
          Spacer(Modifier.width(8.dp))
          XMButton("Upload", { Nav.go("studio", "short") }, small = true)
        }
      }
    } else {
      VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { idx ->
        val sh = list.getOrNull(idx) ?: return@VerticalPager
        ShortPage(sh, active = pagerState.currentPage == idx,
          replying = replying == sh.id, onReplyToggle = { replying = if (replying == sh.id) null else sh.id },
          onReplied = { replying = null },
          onTagGo = { t -> S.tagFilter = t; S.shortTab = "tags"; S.save(); tab = "tags" })
      }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically) {
      Row(Modifier.clip(RoundedCornerShape(99.dp)).clickable { Nav.ddOpen = true }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (displayName(Me.profile).isNotEmpty()) Avatar(displayName(Me.profile), 26.dp, Me.profile.picture)
        else XLogo(26.dp)
      }
      Spacer(Modifier.weight(1f))
      listOf("for" to "For you", "global" to "Global", "tags" to "Tags").forEach { (id, label) ->
        val on = tab == id
        Box(Modifier.clip(RoundedCornerShape(99.dp))
          .background(if (on) Color.White else Color.White.copy(alpha = 0.16f))
          .clickable { tab = id; S.shortTab = id; S.save() }
          .padding(horizontal = 13.dp, vertical = 7.dp)) {
          Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (on) Color.Black else Color.White)
        }
        Spacer(Modifier.width(6.dp))
      }
      Spacer(Modifier.weight(1f))
      if (tab == "tags") {
        Box(Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.16f))
          .clickable { Nav.openSheet(Sheet("tags")) }.padding(horizontal = 11.dp, vertical = 7.dp)) {
          Text("#$tag", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(6.dp))
      }
      Box(Modifier.size(38.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.16f))
        .clickable { Nav.go("studio", "short") }, contentAlignment = Alignment.Center) {
        AppIcon("plus", 20.dp, tint = Color.White)
      }
    }
  }
}

@Composable
private fun ShortPage(sh: ShortV, active: Boolean, replying: Boolean, onReplyToggle: () -> Unit,
  onReplied: () -> Unit, onTagGo: (String) -> Unit) {
  var field by remember(sh.id) { mutableStateOf("") }
  var authorName by remember(sh.id) { mutableStateOf(if (sh.demo) sh.author else "") }
  var authorPic by remember(sh.id) { mutableStateOf("") }
  LaunchedEffect(sh.id) {
    if (!sh.demo && sh.pubkey.length == 64) {
      try { Repo.fetchProfiles(listOf(sh.pubkey)) } catch (_: Exception) {}
      val p = Repo.cachedProfile(sh.pubkey)
      authorName = displayName(p); authorPic = p.picture
    }
  }
  Box(Modifier.fillMaxSize()) {
    if (sh.url.isNotEmpty()) ShortVideo(sh.url, sh.thumb, active)
    else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1A1230), Color(0xFF0B1526))))) {
      if (sh.thumb.isNotEmpty()) AsyncImg(sh.thumb, Modifier.fillMaxSize())
      Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp))
        .background(Color.Black.copy(alpha = 0.55f)).padding(16.dp)) {
        Text(if (sh.demo) sh.title.ifEmpty { "Demo short" } else "No playable video on relays",
          color = Color.White, fontSize = 14.sp)
      }
    }
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
      .padding(14.dp)) {
      if (sh.demo || sh.id.startsWith("demo") || sh.id.startsWith("dn")) ModeBadge(false)
      Spacer(Modifier.height(6.dp))
      Text(sh.title.ifEmpty { "Untitled" }, color = Color.White, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(2.dp))
      Text((if (sh.dur > 0) "${sh.dur.toInt()}s · " else "") + timeAgo(sh.createdAt),
        color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
      if (sh.tags.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row {
          sh.tags.take(3).forEach { t ->
            Box(Modifier.padding(end = 6.dp).clip(RoundedCornerShape(99.dp))
              .background(Color.White.copy(alpha = 0.16f)).clickable { onTagGo(t) }
              .padding(horizontal = 10.dp, vertical = 5.dp)) {
              Text("#$t", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
          }
        }
      }
      Spacer(Modifier.height(10.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { Acts.openAuthor(sh.pubkey) },
          verticalAlignment = Alignment.CenterVertically) {
          Avatar(authorName.ifEmpty { "?" }, 34.dp, authorPic)
          Spacer(Modifier.width(8.dp))
          Text(authorName.ifEmpty { "…" }, color = Color.White, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ShortAct("reply", onReplyToggle)
        ShortAct("zap") { Acts.doZapOpen(sh.id, sh.pubkey) }
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.16f))
          .clickable { Acts.doXapOpen(sh.id, sh.pubkey) }, contentAlignment = Alignment.Center) {
          CoinMark("xmr", 22.dp)
        }
        Spacer(Modifier.width(8.dp))
        ShortAct("heart") { Acts.doReactOpen(sh.id) }
      }
      if (replying) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.weight(1f)) {
            XMField(field, { field = it }, "Reply to this short…", maxChars = 280)
          }
          Spacer(Modifier.width(8.dp))
          XMButton("Send", {
            val b = field; field = ""
            Acts.doReplyInline(sh.id, b, onReplied)
          }, small = true)
        }
      }
    }
  }
}

@Composable
private fun ShortAct(icon: String, onClick: () -> Unit) {
  Box(Modifier.size(42.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.16f))
    .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    AppIcon(icon, 20.dp, tint = Color.White)
  }
  Spacer(Modifier.width(8.dp))
}

// ================= Mini Apps =================
private fun appCat(app: MiniApp): String =
  app.tags.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Game"

@Composable
fun MiniAppsScreen() {
  val scope = rememberCoroutineScope()
  var loading by remember { mutableStateOf(true) }
  var apps by remember { mutableStateOf<List<MiniApp>>(emptyList()) }
  var cat by remember { mutableStateOf("All") }
  LaunchedEffect(Unit) {
    scope.launch(Dispatchers.IO) {
      val res = try { Webxdc.catalog() } catch (_: Exception) { emptyList() }
      withContext(Dispatchers.Main) {
        apps = res
        ViewCache.apps = apps
        loading = false
      }
    }
  }
  val cats = remember(apps) { listOf("All") + apps.map { appCat(it) }.distinct().sorted() }
  val list = if (cat == "All") apps else apps.filter { appCat(it) == cat }
  Column(Modifier.fillMaxSize()) {
    TopBar("Mini Apps", displayName(Me.profile), Me.profile.picture, onDd = { Nav.ddOpen = true }, trailing = {
      BareIconBtn("upload", "Upload", { Nav.openSheet(Sheet("upload")) })
    })
    if (loading) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading arcade…", color = LocalXM.current.text2) }
      return
    }
    if (apps.isEmpty()) {
      EmptyState("No mini apps yet", "Upload a .zip game or discover apps from Nostr.") {
        Spacer(Modifier.height(8.dp))
        XMButton("Upload .zip", { Nav.openSheet(Sheet("upload")) }, small = true)
      }
      return
    }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
      item(span = { GridItemSpan(2) }) {
        Row(Modifier.padding(vertical = 8.dp)) {
          cats.forEach { c ->
            Pill(c, cat == c) { cat = c }
            Spacer(Modifier.width(8.dp))
          }
        }
      }
      items(list, key = { it.id }) { app ->
        Column(Modifier.padding(5.dp).clip(RoundedCornerShape(18.dp))
          .background(MaterialTheme.colorScheme.surface).clickable { Nav.go("gamerun", app) }.padding(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            AppTile(app, 52.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(app.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("${app.source} · ${app.author.ifEmpty { "anon" }}", fontSize = 12.sp,
                color = LocalXM.current.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
          if (app.desc.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(app.desc, fontSize = 12.5.sp, color = LocalXM.current.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
          Spacer(Modifier.height(8.dp))
          Row {
            XMButton("Play", { Nav.go("gamerun", app) }, Modifier.weight(1f), small = true, icon = "play")
            Spacer(Modifier.width(8.dp))
            XMButton("Feed", { Nav.openSheet(Sheet("feedApp", mapOf("appId" to app.id))) },
              Modifier.weight(1f), kind = BtnKind.Line, small = true, icon = "send")
          }
        }
      }
      item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(30.dp)) }
    }
  }
}

@Composable
fun AppTile(app: MiniApp, size: androidx.compose.ui.unit.Dp) {
  Box(Modifier.size(size).clip(RoundedCornerShape(14.dp))
    .background(Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFF22D3EE)))),
    contentAlignment = Alignment.Center) {
    Text(app.title.firstOrNull()?.uppercase() ?: "?", color = Color.White,
      fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
  }
}

// ================= Game run =================
class GameBridge(private val app: MiniApp) {
  @JavascriptInterface
  fun post(kind: String, data: String) {
    AppScope.launch(Dispatchers.IO) {
      try {
        if (kind == "webxdc-chat") {
          val g = LiveCtx.group
          if (g != null && !g.demo && Signer.isSignedIn()) {
            val ch = LiveCtx.channel
            val enc = Repo.getGroupKey(Repo.groupChannelId(g, ch)).isNotEmpty()
            Repo.sendGroupMessage(g, ch, data, enc)
          } else if (g != null && g.demo) {
            Repo.DemoStore.push("${g.id}/${LiveCtx.channel}",
              displayName(Me.profile).ifEmpty { "you" }, data)
          }
        } else {
          Webxdc.pushUpdate(app.id, data)
        }
      } catch (_: Exception) {}
    }
  }
}

@Composable
fun GameRunScreen(app: MiniApp) {
  val scope = rememberCoroutineScope()
  var url by remember(app.id) { mutableStateOf<String?>(null) }
  var err by remember(app.id) { mutableStateOf<String?>(null) }
  var web by remember { mutableStateOf<WebView?>(null) }
  LaunchedEffect(app.id) {
    scope.launch(Dispatchers.IO) {
      try {
        val u = Webxdc.mount(app)
        withContext(Dispatchers.Main) { url = u }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { err = e.message ?: "Mount failed" }
      }
    }
  }
  DisposableEffect(app.id) {
    onDispose { try { web?.destroy() } catch (_: Exception) {} }
  }
  Column(Modifier.fillMaxSize()) {
    BackBar(app.title, onBack = { Nav.back() }, right = {
      BareIconBtn("send", "Feed to channel", { Nav.openSheet(Sheet("feedApp", mapOf("appId" to app.id))) })
    })
    Text("${app.source} · sandboxed", fontSize = 12.sp, color = LocalXM.current.text3,
      modifier = Modifier.padding(start = 18.dp, bottom = 6.dp))
    Box(Modifier.fillMaxSize()) {
      when {
        err != null -> EmptyState("Couldn't launch", err!!) {}
        url == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("Loading ${app.title}…", color = LocalXM.current.text2)
        }
        else -> AndroidView(factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            webViewClient = WebViewClient()
            addJavascriptInterface(GameBridge(app), "androidPost")
            loadUrl(url!!)
            web = this
          }
        }, modifier = Modifier.fillMaxSize())
      }
    }
  }
}

// ================= Music =================
@Composable
fun MusicScreen() {
  val scope = rememberCoroutineScope()
  var loading by remember { mutableStateOf(true) }
  var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
  var authors by remember { mutableStateOf(mapOf<String, String>()) }
  var q by remember { mutableStateOf("") }
  val tag = S.musicTag
  LaunchedEffect(Unit) {
    scope.launch(Dispatchers.IO) {
      val res = try { Repo.fetchMusic() } catch (_: Exception) { emptyList() }
      val list = res.ifEmpty { Repo.Demo.tracks() }
      val real = list.filter { !it.demo && it.pubkey.length == 64 }.map { it.pubkey }.distinct().take(50)
      try { Repo.fetchProfiles(real) } catch (_: Exception) {}
      val am = real.associateWith { displayName(Repo.cachedProfile(it)) }
      withContext(Dispatchers.Main) {
        tracks = list
        authors = am
        ViewCache.tracks = tracks
        loading = false
      }
    }
  }
  fun artist(t: Track): String =
    if (t.demo) t.title.substringAfter("—").trim().ifEmpty { "demo" }
    else authors[t.pubkey] ?: displayName(Repo.cachedProfile(t.pubkey))
  val tags = remember(tracks) { listOf("All") + tracks.flatMap { it.tags }.distinct().sorted() }
  val list = tracks.filter {
    (tag == "All" || it.tags.contains(tag.lowercase())) &&
      (q.isEmpty() || it.title.contains(q, true) || artist(it).contains(q, true))
  }
  val current = Player.current.collectAsState().value
  val playing = Player.playing.collectAsState().value
  val queue = Player.queue.collectAsState().value
  Column(Modifier.fillMaxSize()) {
    TopBar("Music", displayName(Me.profile), Me.profile.picture, onDd = { Nav.ddOpen = true }, trailing = {
      BareIconBtn("plus", "Studio", { Nav.go("studio", "track") })
    })
    Column(Modifier.padding(horizontal = 14.dp)) {
      XMField(q, { q = it }, "Search tracks…")
      Spacer(Modifier.height(8.dp))
      Row {
        tags.take(6).forEach { t ->
          Pill(if (t == "All") "All" else "#$t", tag == t) { S.musicTag = t; S.save() }
          Spacer(Modifier.width(8.dp))
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    if (loading) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading music…", color = LocalXM.current.text2) }
      return
    }
    LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp)) {
      if (list.isEmpty()) item { EmptyState("No tracks", "Try another search or upload one.") {} }
      items(list, key = { it.id }) { t ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surface).padding(12.dp),
          verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable {
              Player.enqueue(t)
              Player.play(t)
            }, contentAlignment = Alignment.Center) {
            AppIcon(if (current?.id == t.id && playing) "pause" else "play", 22.dp,
              tint = MaterialTheme.colorScheme.primary)
          }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(t.title.ifEmpty { "Untitled" }, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp,
              maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist(t), fontSize = 12.5.sp, color = LocalXM.current.text2,
              maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          BareIconBtn("list", "Queue", { Player.enqueue(t); Nav.toast("Queued") }, size = 38.dp)
          BareIconBtn("zap", "Zap", { Acts.doZapOpen(t.id, t.pubkey) }, size = 38.dp)
        }
        Spacer(Modifier.height(8.dp))
      }
      item { Spacer(Modifier.height(12.dp)) }
    }
    if (current != null) {
      Row(Modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface).clickable { Nav.openSheet(Sheet("queue")) }
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(99.dp))
          .background(MaterialTheme.colorScheme.primary)
          .clickable { Player.toggle() }, contentAlignment = Alignment.Center) {
          AppIcon(if (playing) "pause" else "play", 18.dp, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(current!!.title.ifEmpty { "Untitled" }, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("${queue.size} in queue", fontSize = 12.sp, color = LocalXM.current.text2)
        }
        AppIcon("chevR", 18.dp, tint = LocalXM.current.text3)
      }
    }
  }
}
