package com.xmarcade.app.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.xmarcade.app.core.NostrEvent
import com.xmarcade.app.core.Profile
import com.xmarcade.app.core.displayName
import com.xmarcade.app.core.timeAgo
import kotlinx.coroutines.delay

/** Live channel context for game chat feed-in. */
object LiveCtx {
  var group: com.xmarcade.app.core.Group? = null
  var channel: String = "general"
  var demo: Boolean = false
}

// ---------- bottom-sheet scaffold ----------
@Composable
fun SheetScaffold(title: String, sub: String = "", body: @Composable () -> Unit, footer: @Composable () -> Unit = {}) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
    Box(Modifier.width(44.dp).height(4.dp).clip(RoundedCornerShape(99.dp))
      .background(LocalXM.current.line2).align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (sub.isNotEmpty()) {
      Spacer(Modifier.height(2.dp))
      Text(sub, fontSize = 13.sp, color = LocalXM.current.text2)
    }
    Spacer(Modifier.height(14.dp))
    body()
    footer()
  }
}

// ---------- apps dropdown panel ----------
@Composable
fun DdPanel(current: String, onSection: (String) -> Unit, onDismiss: () -> Unit) {
  val xm = LocalXM.current
  Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)) {
    Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp))
      .background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
      SECTIONS.forEach { (t, icon) ->
        val (id, label, sub) = t
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onSection(id) }.padding(10.dp),
          verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(xm.surface2),
            contentAlignment = Alignment.Center) { AppIcon(icon, 20.dp) }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(sub, fontSize = 12.5.sp, color = xm.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          if (current == id) AppIcon("check", 18.dp, tint = MaterialTheme.colorScheme.primary)
        }
      }
    }
  }
}

// ---------- note card ----------
@Composable
fun NoteCard(e: NostrEvent, p: Profile, showDemo: Boolean = true,
  onAuthor: () -> Unit, onReply: () -> Unit, onRepost: () -> Unit,
  onReact: () -> Unit, onZap: () -> Unit, onXap: () -> Unit, onTag: (String) -> Unit, onUrl: (String) -> Unit) {
  val xm = LocalXM.current
  val demo = e.id.startsWith("demo") || e.id.startsWith("dn")
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onAuthor),
        verticalAlignment = Alignment.CenterVertically) {
        Avatar(displayName(p), 38.dp, p.picture)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(displayName(p), fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(timeAgo(e.createdAt) + if (demo) " · demo" else "", fontSize = 12.sp, color = xm.text3)
        }
      }
      if (demo && showDemo) ModeBadge(false)
    }
    Spacer(Modifier.height(8.dp))
    RichText(e.content, MaterialTheme.colorScheme.onSurface, onTag = onTag, onUrl = onUrl)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      NoteAct("reply", "Reply", onReply)
      NoteAct("refresh", "Repost", onRepost)
      NoteAct("heart", "Like", onReact)
      NoteAct("zap", "Zap", onZap)
      Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onXap).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CoinMark("xmr", 18.dp); Spacer(Modifier.width(5.dp))
        Text("Xap", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = xm.text2)
      }
    }
  }
}

@Composable
private fun NoteAct(icon: String, label: String, onClick: () -> Unit) {
  Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(6.dp),
    verticalAlignment = Alignment.CenterVertically) {
    AppIcon(icon, 17.dp, tint = LocalXM.current.text2)
    Spacer(Modifier.width(5.dp))
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalXM.current.text2)
  }
}

// ---------- short video ----------
@Composable
fun ShortVideo(url: String, thumb: String, active: Boolean, onFirstTap: () -> Unit = {}) {
  var view by remember { mutableStateOf<VideoView?>(null) }
  var prepared by remember(url) { mutableStateOf(false) }
  var playing by remember(url) { mutableStateOf(false) }
  var flash by remember { mutableStateOf<String?>(null) }
  var tapped by remember(url) { mutableStateOf(false) }

  LaunchedEffect(active, prepared) {
    val v = view ?: return@LaunchedEffect
    if (!prepared) return@LaunchedEffect
    if (active && !v.isPlaying) { v.start(); playing = true }
    if (!active && v.isPlaying) { v.pause(); playing = false }
  }
  LaunchedEffect(flash) { if (flash != null) { delay(750); flash = null } }
  DisposableEffect(url) { onDispose { try { view?.stopPlayback() } catch (_: Exception) {} } }

  Box(Modifier.fillMaxSize().clickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null) {
    val v = view ?: return@clickable
    if (!tapped) { tapped = true; onFirstTap() }
    if (v.isPlaying) { v.pause(); playing = false; flash = "pause" }
    else { v.start(); playing = true; flash = "play" }
  }) {
    if (thumb.isNotEmpty() && !playing) AsyncImg(thumb, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    AndroidView(factory = { ctx ->
      VideoView(ctx).apply {
        setVideoURI(Uri.parse(url))
        setOnPreparedListener { mp ->
          mp.isLooping = true
          prepared = true
          if (active) { start(); playing = true }
        }
        setOnErrorListener { _, _, _ -> true }
        view = this
      }
    }, modifier = Modifier.fillMaxSize())
    AnimatedVisibility(visible = flash != null, enter = fadeIn(), exit = fadeOut(),
      modifier = Modifier.align(Alignment.Center)) {
      Box(Modifier.size(84.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center) {
        AppIcon(if (flash == "play") "play" else "pause", 38.dp, tint = Color.White)
      }
    }
  }
}
