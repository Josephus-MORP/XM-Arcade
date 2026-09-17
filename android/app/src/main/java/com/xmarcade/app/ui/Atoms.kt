package com.xmarcade.app.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmarcade.app.core.AppScope
import com.xmarcade.app.core.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

// ---------- images ----------
private val imgCache = LruCache<String, android.graphics.Bitmap>(32)

@Composable
fun AsyncImg(url: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, fallback: @Composable () -> Unit = {}) {
  var bmp by remember(url) { mutableStateOf(imgCache.get(url)) }
  LaunchedEffect(url) {
    if (bmp == null && url.isNotEmpty()) {
      try {
        val b = withContext(Dispatchers.IO) {
          Net.client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            val bytes = r.body?.bytes() ?: return@use null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          }
        }
        if (b != null) { imgCache.put(url, b); bmp = b }
      } catch (_: Exception) {}
    }
  }
  if (bmp != null) Image(bmp!!.asImageBitmap(), null, modifier = modifier, contentScale = contentScale)
  else Box(modifier) { fallback() }
}

private val AV_PAL = listOf(
  0xFF8B7BFF to 0xFF22D3EE, 0xFFFF7AB6 to 0xFF8B7BFF, 0xFF22D3EE to 0xFF3DDC97,
  0xFFFF8A4C to 0xFFFF5470, 0xFF5B8CFF to 0xFF8B7BFF, 0xFF3DDC97 to 0xFF22D3EE,
  0xFFFFD166 to 0xFFFF8A4C, 0xFFA78BFA to 0xFFF472B6,
)

private fun fnv(s: String): Int {
  var h = 2166136261.toInt()
  for (c in s) { h = h xor c.code; h = h * 16777619 }
  return if (h == Int.MIN_VALUE) 0 else if (h < 0) -h else h
}

@Composable
fun Avatar(name: String, size: Dp, url: String = "") {
  val h = remember(name) { fnv(name.ifEmpty { "?" }) }
  val (c0, c1) = AV_PAL[h % AV_PAL.size]
  val initial = name.removePrefix("@").trim().firstOrNull()?.uppercase() ?: "?"
  Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Color(c0), Color(c1))))) {
    AsyncImg(url, Modifier.matchParentSize(), fallback = {
      Canvas(Modifier.matchParentSize()) {
        val w = size.toPx()
        repeat(3) { i ->
          val x = 12 + ((h shr (i * 5)) % 56) / 80f * w
          val y = 12 + ((h shr (i * 3 + 2)) % 56) / 80f * w
          val r = 8 + ((h shr (i * 4)) % 12) / 80f * w
          drawCircle(Color.White.copy(alpha = 0.1f + i * 0.07f), r, Offset(x, y))
        }
      }
      Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
        Text(initial, color = Color.White.copy(alpha = 0.94f), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
      }
    })
  }
}

// ---------- bars ----------
@Composable
fun RelayChip(onClick: () -> Unit) {
  val xm = LocalXM.current
  val n = RelayLive.count
  val total = RelayLive.total
  val live = n > 0
  Row(Modifier.clip(RoundedCornerShape(99.dp)).background(if (live) xm.ok.copy(alpha = 0.12f) else xm.live.copy(alpha = 0.12f))
    .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) xm.ok else xm.live))
    Spacer(Modifier.width(6.dp))
    Text("$n/$total relays", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
      color = if (live) xm.ok else xm.live)
  }
}

@Composable
fun TopBar(label: String, meName: String, mePic: String, onDd: () -> Unit, right: @Composable () -> Unit = {}, trailing: @Composable () -> Unit = {}) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
      if (meName.isNotEmpty()) Avatar(meName, 26.dp, mePic) else XLogo(26.dp)
      Spacer(Modifier.width(8.dp))
      Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.width(2.dp))
      AppIcon("chevD", 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.weight(1f))
    right()
    RelayChip(onClick = { Nav.openSheet(Sheet("relays")) })
    trailing()
  }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, right: @Composable () -> Unit = {}) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    BareIconBtn("chevL", "Back", onBack)
    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.weight(1f))
    right()
    RelayChip(onClick = { Nav.openSheet(Sheet("relays")) })
    Spacer(Modifier.width(4.dp))
  }
}

@Composable
fun BareIconBtn(icon: String, desc: String, onClick: () -> Unit, size: Dp = 42.dp) {
  Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    AppIcon(icon, 20.dp)
  }
}

// ---------- rows / buttons / fields ----------
@Composable
fun RowItem(icon: String, tint: Color? = null, title: String, sub: String = "", value: String = "",
  tail: @Composable () -> Unit = {}, chev: Boolean = true, onClick: () -> Unit) {
  val xm = LocalXM.current
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
    .clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
      .background(tint?.copy(alpha = 0.16f) ?: xm.surface2), contentAlignment = Alignment.Center) {
      AppIcon(icon, 20.dp, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.width(13.dp))
    Column(Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
      if (sub.isNotEmpty()) Text(sub, fontSize = 12.5.sp, color = xm.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (value.isNotEmpty()) Text(value, fontSize = 12.sp, color = xm.text2, fontWeight = FontWeight.SemiBold)
    tail()
    if (chev) AppIcon("chevR", 18.dp, tint = xm.text3)
  }
}

@Composable
fun GroupLabel(text: String) {
  Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
    letterSpacing = 1.2.sp, color = LocalXM.current.text3,
    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
}

enum class BtnKind { Primary, Line, Ghost }

@Composable
fun XMButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.Primary,
  small: Boolean = false, icon: String? = null, enabled: Boolean = true) {
  val h = if (small) 36.dp else 48.dp
  val shape = RoundedCornerShape(14.dp)
  val content: @Composable () -> Unit = {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
      if (icon != null) { AppIcon(icon, if (small) 16.dp else 18.dp); Spacer(Modifier.width(7.dp)) }
      Text(text, fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.Bold)
    }
  }
  when (kind) {
    BtnKind.Primary -> Button(onClick, modifier.height(h), enabled, shape = shape,
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White), content = { content() })
    BtnKind.Line -> OutlinedButton(onClick, modifier.height(h), enabled, shape = shape,
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), content = { content() })
    BtnKind.Ghost -> TextButton(onClick, modifier.height(h), enabled, shape = shape, content = { content() })
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XMField(value: String, onChange: (String) -> Unit, placeholder: String = "", modifier: Modifier = Modifier,
  mono: Boolean = false, password: Boolean = false, singleLine: Boolean = true, maxChars: Int = 0,
  keyboardType: KeyboardType = KeyboardType.Text, minLines: Int = 1) {
  val xm = LocalXM.current
  TextField(value, { v -> onChange(if (maxChars > 0) v.take(maxChars) else v) }, modifier.fillMaxWidth(),
    placeholder = { Text(placeholder, color = xm.text3) },
    singleLine = singleLine && minLines == 1, minLines = minLines,
    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    textStyle = TextStyle(fontFamily = if (mono) Mono else null, fontSize = 15.sp),
    shape = RoundedCornerShape(14.dp),
    colors = TextFieldDefaults.colors(
      focusedContainerColor = xm.surface2, unfocusedContainerColor = xm.surface2,
      focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    ))
}

@Composable
fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
  val xm = LocalXM.current
  Box(Modifier.clip(RoundedCornerShape(99.dp))
    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else xm.surface2)
    .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
      color = if (on) MaterialTheme.colorScheme.primary else xm.text2)
  }
}

/** Hashtag chip: green dot when followed, tap toggles follow. */
@Composable
fun FollowTagChip(tag: String, dark: Boolean = false, onToggle: () -> Unit = { Acts.toggleTag(tag) }) {
  val followed = S.followTags.contains(tag.lowercase())
  val xm = LocalXM.current
  Row(Modifier.clip(RoundedCornerShape(99.dp))
    .background(if (dark) Color.White.copy(alpha = 0.16f) else xm.surface2)
    .clickable(onClick = onToggle)
    .padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically) {
    if (followed) {
      Box(Modifier.size(7.dp).clip(CircleShape).background(xm.ok))
      Spacer(Modifier.width(6.dp))
    }
    Text("#$tag", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
      color = if (dark) Color.White else xm.text2)
  }
}

@Composable
fun StaticChip(text: String, icon: String? = null, green: Boolean = false) {
  val xm = LocalXM.current
  Row(Modifier.clip(RoundedCornerShape(99.dp))
    .background(if (green) xm.ok.copy(alpha = 0.12f) else xm.surface2)
    .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
    if (icon != null) { AppIcon(icon, 13.dp, tint = if (green) xm.ok else xm.text2); Spacer(Modifier.width(5.dp)) }
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (green) xm.ok else xm.text2)
  }
}

@Composable
fun EmptyState(title: String, sub: String, button: @Composable () -> Unit = {}) {
  Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(6.dp))
    Text(sub, fontSize = 14.sp, color = LocalXM.current.text2)
    Spacer(Modifier.height(6.dp))
    button()
  }
}

@Composable
fun ModeBadge(live: Boolean) {
  val xm = LocalXM.current
  Row(Modifier.clip(RoundedCornerShape(99.dp))
    .background(if (live) xm.ok.copy(alpha = 0.12f) else xm.surface2)
    .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    if (live) { Box(Modifier.size(7.dp).clip(CircleShape).background(xm.ok)); Spacer(Modifier.width(6.dp)) }
    Text(if (live) "live" else "demo", fontSize = 12.sp, fontWeight = FontWeight.Bold,
      color = if (live) xm.ok else xm.text2)
  }
}

// ---------- brand marks ----------
@Composable
fun XLogo(size: Dp) {
  Canvas(Modifier.size(size)) {
    val w = size.toPx()
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFF22D3EE)),
      Offset(0f, 0f), Offset(w, w)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.3f))
    val s = 3.4f / 40 * w
    drawLine(Color.White, Offset(13f / 40 * w, 13f / 40 * w), Offset(27f / 40 * w, 27f / 40 * w), s, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(Color.White, Offset(27f / 40 * w, 13f / 40 * w), Offset(13f / 40 * w, 27f / 40 * w), s, androidx.compose.ui.graphics.StrokeCap.Round)
  }
}

@Composable
fun CoinMark(asset: String, size: Dp) {
  val xm = LocalXM.current
  if (asset == "xmr") {
    Canvas(Modifier.size(size)) {
      val w = size.toPx()
      fun p(x: Float, y: Float) = Offset(x / 40 * w, y / 40 * w)
      drawCircle(xm.xmr.copy(alpha = 0.08f), w / 2)
      val st = Stroke(3.4f / 40 * w)
      val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(8f / 40 * w, 28f / 40 * w); lineTo(8f / 40 * w, 13f / 40 * w)
        lineTo(20f / 40 * w, 25f / 40 * w); lineTo(32f / 40 * w, 13f / 40 * w); lineTo(32f / 40 * w, 28f / 40 * w)
      }
      drawPath(path, xm.xmr, style = st)
      drawLine(xm.xmr, p(3f, 28f), p(11f, 28f), 3.4f / 40 * w)
      drawLine(xm.xmr, p(29f, 28f), p(37f, 28f), 3.4f / 40 * w)
    }
  } else {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
      Canvas(Modifier.matchParentSize()) { drawCircle(Color(0xFFEFB850)) }
      Text("₿", fontWeight = FontWeight.Black, color = Color(0xFF2D1B04),
        fontSize = (size.value * 0.6f).sp, modifier = Modifier.rotate(10f))
    }
  }
}

// ---------- rich text ----------
private val URL_RE = Regex("https?://[^\\s<]+")
private val TAG_RE = Regex("#([a-zA-Z0-9_]+)")

@Composable
fun RichText(text: String, color: Color, onTag: (String) -> Unit = {}, onUrl: (String) -> Unit = {}) {
  val accent = MaterialTheme.colorScheme.secondary
  val ok = LocalXM.current.ok
  // Rebuild spans whenever the followed-tag set changes so followed tags show green.
  val followedKey = S.followTags.joinToString(",")
  val ann = remember(text, followedKey) {
    val followed = followedKey.split(",").filter { it.isNotEmpty() }.toSet()
    buildAnnotatedString {
      var i = 0
      val marks = mutableListOf<Triple<Int, Int, String>>()
      URL_RE.findAll(text).forEach { marks.add(Triple(it.range.first, it.range.last + 1, "url:" + it.value)) }
      TAG_RE.findAll(text).forEach { m ->
        if (marks.none { m.range.first in it.first until it.second }) marks.add(Triple(m.range.first, m.range.last + 1, "tag:" + m.groupValues[1]))
      }
      marks.sortBy { it.first }
      append(text)
      marks.forEach { (s, e, k) ->
        val isTag = k.startsWith("tag:")
        val c = if (isTag && followed.contains(k.substringAfter(":").lowercase())) ok else accent
        addStyle(SpanStyle(color = c, textDecoration = if (!isTag) TextDecoration.Underline else null), s, e)
        addStringAnnotation(k.substringBefore(":"), k.substringAfter(":"), s, e)
      }
    }
  }
  ClickableText(ann, style = TextStyle(color = color, fontSize = 15.sp, lineHeight = 22.sp)) { off ->
    ann.getStringAnnotations("url", off, off).firstOrNull()?.let { onUrl(it.item); return@ClickableText }
    ann.getStringAnnotations("tag", off, off).firstOrNull()?.let { onTag(it.item) }
  }
}

// ---------- hue slider ----------
@Composable
fun HueSlider(value: Int, onChange: (Int) -> Unit) {
  val stops = remember { (0..360 step 15).map { hsl(it, 85, 55) } }
  Box(Modifier.fillMaxWidth().height(40.dp)) {
    Box(Modifier.fillMaxWidth().height(16.dp).align(Alignment.Center)
      .clip(RoundedCornerShape(99.dp)).background(Brush.horizontalGradient(stops)))
    Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..360f,
      modifier = Modifier.fillMaxWidth(),
      colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
        thumbColor = Color.White, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
    )
  }
}

@Composable
fun KeyBox(text: String) {
  val xm = LocalXM.current
  Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(xm.surface2).padding(12.dp)) {
    Text(text, fontFamily = Mono, fontSize = 12.sp, color = xm.text2)
  }
}

/** Live relay counts for chips (updated by App from RelayPool.status). */
object RelayLive {
  var count by mutableStateOf(0)
  var total by mutableStateOf(3)
}
