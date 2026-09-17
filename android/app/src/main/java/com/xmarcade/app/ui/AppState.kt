package com.xmarcade.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xmarcade.app.core.AppScope
import com.xmarcade.app.core.MiniApp
import com.xmarcade.app.core.NostrEvent
import com.xmarcade.app.core.ShortV
import com.xmarcade.app.core.Store
import com.xmarcade.app.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Persisted UI prefs (xm.ui.v1). Mirrors the web S object. */
object S {
  var theme by mutableStateOf("dark")
  var section by mutableStateOf("shorts")
  var startingPane by mutableStateOf("shorts")
  var shortTab by mutableStateOf("global")
  var tagFilter by mutableStateOf("All")
  var musicTag by mutableStateOf("All")
  val followTags = mutableStateListOf<String>()
  val followUsers = mutableStateListOf<String>()
  var groupId by mutableStateOf("")
  var channel by mutableStateOf("general")
  var locked by mutableStateOf(false)
  var zapAmt by mutableStateOf(210)
  var xapAmt by mutableStateOf(0.01)
  var accentHue by mutableStateOf(187)

  fun init() {
    val o = Store.getObj("xm.ui.v1") ?: run {
      followTags.addAll(listOf("webxdc")); return
    }
    theme = o.optString("theme", "dark")
    section = o.optString("section", "shorts")
    startingPane = o.optString("startingPane", "shorts")
    shortTab = o.optString("shortTab", "global")
    tagFilter = o.optString("tagFilter", "All")
    musicTag = o.optString("musicTag", "All")
    followTags.clear()
    o.optJSONArray("followTags")?.let { a -> repeat(a.length()) { followTags.add(a.optString(it)) } }
    if (followTags.isEmpty()) followTags.add("webxdc")
    followUsers.clear()
    o.optJSONArray("followUsers")?.let { a -> repeat(a.length()) { followUsers.add(a.optString(it)) } }
    groupId = o.optString("groupId", "")
    channel = o.optString("channel", "general").ifEmpty { "general" }
    zapAmt = o.optInt("zapAmt", 210)
    xapAmt = o.optDouble("xapAmt", 0.01)
    accentHue = o.optInt("accentHue", 187)
  }

  fun save() {
    Store.setObj("xm.ui.v1", org.json.JSONObject()
      .put("theme", theme).put("section", section).put("startingPane", startingPane)
      .put("shortTab", shortTab).put("tagFilter", tagFilter).put("musicTag", musicTag)
      .put("followTags", JSONArray(followTags.toList())).put("followUsers", JSONArray(followUsers.toList()))
      .put("groupId", groupId).put("channel", channel).put("zapAmt", zapAmt)
      .put("xapAmt", xapAmt).put("accentHue", accentHue))
  }
}

val SECTIONS = listOf(
  Triple("profile", "Profile", "Your page, key, stats") to "user",
  Triple("shorts", "Shorts", "Vertical video feed") to "play",
  Triple("miniapps", "Mini Apps", "webxdc games arcade") to "game",
  Triple("music", "Music", "Tracks from the network") to "note",
  Triple("chats", "Chats", "Concord channels · encrypted") to "msg",
  Triple("wallet", "Wallet", "Monero + Bitcoin sats") to "wallet",
  Triple("settings", "Settings", "Tags, theme, keys, relays") to "gear",
)

data class Route(val id: String, val data: Any? = null)
data class Sheet(val id: String, val args: Map<String, String> = emptyMap())

/** Stack router + sheets + dropdown + toasts. Mirrors app.js shell. */
object Nav {
  val stack = mutableStateListOf<Route>()
  var sheet by mutableStateOf<Sheet?>(null)
  var ddOpen by mutableStateOf(false)
  val snacks = SnackbarHostState()

  fun toast(msg: String) {
    AppScope.launch(Dispatchers.Main) {
      try { snacks.currentSnackbarData?.dismiss(); snacks.showSnackbar(msg) } catch (_: Exception) {}
    }
  }

  fun go(id: String, data: Any? = null) { stack.add(Route(id, data)); ddOpen = false }
  fun back() {
    if (ddOpen) { ddOpen = false; return }
    if (sheet != null) { sheet = null; return }
    if (stack.size > 1) stack.removeAt(stack.lastIndex)
  }
  fun setSection(sec: String) {
    stack.clear(); stack.add(Route(sec))
    S.section = sec; S.save()
    ddOpen = false; sheet = null
  }
  fun reset(id: String) { stack.clear(); stack.add(Route(id)); ddOpen = false; sheet = null }
  fun openSheet(s: Sheet) { sheet = s }
  fun closeSheet() { sheet = null }
}

/** In-memory view caches across navigation. */
object ViewCache {
  var shorts: List<ShortV> = emptyList()
  var tracks: List<Track> = emptyList()
  var apps: List<MiniApp> = emptyList()
  var notesById = mutableMapOf<String, NostrEvent>()
}
