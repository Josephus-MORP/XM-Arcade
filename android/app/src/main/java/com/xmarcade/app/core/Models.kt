package com.xmarcade.app.core

import org.json.JSONObject

/** App data models + pure event parsers (mirrors data.js). */
data class Profile(
  val pubkey: String, val name: String = "", val displayName: String = "",
  val about: String = "", val picture: String = "", val banner: String = "",
  val nip05: String = "", val lud16: String = "", val lud06: String = "", val at: Long = 0,
)

fun parseProfile(pubkey: String, content: String, createdAt: Long): Profile {
  return try {
    val j = JSONObject(content)
    Profile(pubkey, j.optString("name", ""), j.optString("display_name", ""),
      j.optString("about", ""), j.optString("picture", ""), j.optString("banner", ""),
      j.optString("nip05", ""), j.optString("lud16", ""), j.optString("lud06", ""), createdAt)
  } catch (_: Exception) { Profile(pubkey) }
}

fun shortPk(pk: String): String = "nostr:" + pk.take(8) + "…"
fun displayName(p: Profile): String = p.displayName.ifEmpty { p.name.ifEmpty { shortPk(p.pubkey) } }

data class ShortV(
  val id: String, val pubkey: String, val createdAt: Long, val url: String,
  val thumb: String = "", val mime: String = "", val dur: Double = 0.0,
  val tags: List<String> = emptyList(), val title: String = "",
  val demo: Boolean = false, val author: String = "",
)

private val VID_EXT = Regex("\\.(mp4|m4v|mov|webm|ogv)(\\?|#|$)", RegexOption.IGNORE_CASE)
private val VID_URL_IN_TEXT = Regex("https?://\\S+\\.(?:mp4|m4v|mov|webm|ogv)(?:[?#]\\S*)?", RegexOption.IGNORE_CASE)

fun parseShort(e: NostrEvent): ShortV {
  var url = Ev.tagVal(e, "url")
  var thumb = ""; var mime = ""; var dur = 0.0
  for (t in e.tags) {
    if (t[0] == "imeta") for (f in t.drop(1)) {
      val sp = f.split(" ", limit = 2)
      val k = sp[0]; val v = sp.getOrElse(1) { "" }
      if (k == "url" && url.isEmpty()) url = v
      if (k == "image" && thumb.isEmpty()) thumb = v
      if (k == "m" && mime.isEmpty()) mime = v
      if (k == "duration") dur = v.toDoubleOrNull() ?: 0.0
    }
    if (t[0] == "r" && url.isEmpty() && t.getOrNull(1)?.let { VID_EXT.containsMatchIn(it) } == true) url = t[1]
    if (t[0] == "thumb" && thumb.isEmpty()) thumb = t.getOrNull(1) ?: ""
  }
  if (url.isEmpty()) url = VID_URL_IN_TEXT.find(e.content)?.value ?: ""
  val tags = Ev.tagVals(e, "t").map { it.lowercase() }
  val title = Ev.tagVal(e, "title").ifEmpty { e.content.lineSequence().firstOrNull()?.take(90) ?: "" }
  return ShortV(e.id, e.pubkey, e.createdAt, url, thumb, mime, dur, tags, title)
}

data class Track(
  val id: String, val pubkey: String, val createdAt: Long, val url: String,
  val title: String, val tags: List<String> = emptyList(), val demo: Boolean = false,
)

private val AUD_EXT = Regex("\\.(mp3|m4a|ogg|oga|opus|wav|flac)(\\?|#|$)", RegexOption.IGNORE_CASE)
private val URL_IN_TEXT = Regex("https?://[^\\s)]+")

fun parseTrack(e: NostrEvent): Track? {
  var url = ""
  for (u in URL_IN_TEXT.findAll(e.content)) if (AUD_EXT.containsMatchIn(u.value)) { url = u.value; break }
  if (url.isEmpty()) for (t in e.tags) {
    if ((t[0] == "r" || t[0] == "url") && t.getOrNull(1)?.let { AUD_EXT.containsMatchIn(it) } == true) { url = t[1]; break }
  }
  if (url.isEmpty()) return null
  val title = Ev.tagVal(e, "title").ifEmpty { e.content.lineSequence().firstOrNull()?.take(80)?.ifEmpty { "Untitled track" } ?: "Untitled track" }
  return Track(e.id, e.pubkey, e.createdAt, url, title, Ev.tagVals(e, "t").map { it.lowercase() })
}

data class MiniApp(
  val id: String, val source: String, val title: String, val desc: String = "",
  val tags: List<String> = emptyList(), val url: String = "", val author: String = "",
  val file: String = "", val fileName: String = "", val size: Long = 0,
  val authorPk: String = "", val eventId: String = "", val icon: String = "",
)

data class ChanDef(val name: String, val topic: String = "")
data class Group(
  val id: String, val host: String, val d: String, val name: String,
  val about: String = "", val picture: String = "",
  val relays: List<String> = emptyList(), val channels: List<ChanDef> = emptyList(),
  val demo: Boolean = false,
)

data class AppCard(val title: String, val url: String, val file: String, val note: String)
private val CARD_RE = Regex("\\{[^}]*\"type\"\\s*:\\s*\"webxdc\"[^}]*\\}")

fun parseAppCard(text: String): AppCard? {
  val m = CARD_RE.find(text ?: "") ?: return null
  return try {
    val j = JSONObject(m.value)
    if (j.optString("type") != "webxdc") null
    else AppCard(j.optString("title", "webxdc app"), j.optString("url", ""), j.optString("file", ""), j.optString("note", ""))
  } catch (_: Exception) { null }
}

data class ChatMsg(
  val pubkey: String, val who: String, val text: String, val t: String,
  val encrypted: Boolean = false, val appCard: AppCard? = null,
)

data class EmojiRef(val code: String, val url: String)
data class EmojiPack(val name: String, val emojis: List<EmojiRef>, val who: String = "")

fun timeAgo(ts: Long): String {
  val s = maxOf(1, nowSec() - ts)
  return when {
    s < 60 -> "${s}s"
    s < 3600 -> "${s / 60}m"
    s < 86400 -> "${s / 3600}h"
    s < 86400 * 7 -> "${s / 86400}d"
    else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date(ts * 1000))
  }
}

fun fmtNum(n: Long): String = when {
  n >= 1_000_000 -> "${(n / 100000.0) / 10}${if ((n / 100000) % 10 == 0L) "" else ""}M".let {
    val v = n / 1_000_000.0
    (if (v >= 10 || v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)) + "M"
  }
  n >= 1000 -> {
    val v = n / 1000.0
    (if (n >= 10000 || v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)) + "K"
  }
  else -> n.toString()
}

fun money(n: Long): String = "%,d".format(java.util.Locale.US, n)
