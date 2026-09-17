package com.xmarcade.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Live data layer over Nostr. Mirrors data.js + blossom from nostr.js. */
object Repo {
  // ---------- caches ----------
  private val profiles = mutableMapOf<String, Profile>()
  private val profileAt = mutableMapOf<String, Long>()
  private val contacts = mutableMapOf<String, List<String>>()
  var follows: List<String> = emptyList()
  var groupsCache: List<Group> = emptyList()
  private val groupKeys = mutableMapOf<String, String>()
  private val packCache = mutableMapOf<String, List<EmojiPack>>()

  fun init() {
    try {
      val c = Store.getObj("xm.cache.v1") ?: return
      c.optJSONObject("profiles")?.let { ps ->
        ps.keys().forEach { pk ->
          val o = ps.optJSONObject(pk) ?: return@forEach
          profiles[pk] = Profile(pk, o.optString("name"), o.optString("display_name"), o.optString("about"),
            o.optString("picture"), o.optString("banner"), o.optString("nip05"),
            o.optString("lud16"), o.optString("lud06"), o.optLong("_at"))
          profileAt[pk] = o.optLong("_at")
        }
      }
      c.optJSONObject("contacts")?.let { cs ->
        cs.keys().forEach { pk -> contacts[pk] = (0 until (cs.optJSONArray(pk)?.length() ?: 0)).map { cs.getJSONArray(pk).getString(it) } }
      }
      follows = c.optJSONArray("follows")?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()
      c.optJSONArray("groups")?.let { ga ->
        groupsCache = (0 until ga.length()).mapNotNull { i ->
          val o = ga.optJSONObject(i) ?: return@mapNotNull null
          val chans = o.optJSONArray("channels")?.let { ca ->
            (0 until ca.length()).mapNotNull { j ->
              val co = ca.optJSONObject(j) ?: return@mapNotNull null
              ChanDef(co.optString("name"), co.optString("topic"))
            }
          } ?: emptyList()
          Group(o.optString("id"), o.optString("host"), o.optString("d"), o.optString("name"),
            o.optString("about"), o.optString("picture"), groupRelays(), chans)
        }
      }
      c.optJSONObject("groupKeys")?.let { gk -> gk.keys().forEach { groupKeys[it] = gk.optString(it) } }
    } catch (_: Exception) {}
  }

  private fun saveCache() {
    try {
      val ps = JSONObject()
      profiles.forEach { (pk, p) ->
        ps.put(pk, JSONObject().put("name", p.name).put("display_name", p.displayName).put("about", p.about)
          .put("picture", p.picture).put("banner", p.banner).put("nip05", p.nip05)
          .put("lud16", p.lud16).put("lud06", p.lud06).put("_at", profileAt[pk] ?: 0))
      }
      val cs = JSONObject()
      contacts.forEach { (k, v) -> cs.put(k, JSONArray(v)) }
      Store.setObj("xm.cache.v1", JSONObject().put("profiles", ps).put("contacts", cs)
        .put("follows", JSONArray(follows))
        .put("groups", JSONArray(groupsCache.take(40).map {
          JSONObject().put("id", it.id).put("host", it.host).put("d", it.d).put("name", it.name)
            .put("about", it.about).put("picture", it.picture)
            .put("channels", JSONArray(it.channels.map { c ->
              JSONObject().put("name", c.name).put("topic", c.topic)
            }))
        }))
        .put("groupKeys", JSONObject(groupKeys as Map<String, String>)))
    } catch (_: Exception) {}
  }

  // ---------- profiles ----------
  fun cachedProfile(pk: String): Profile = profiles[pk] ?: Profile(pk)

  suspend fun fetchProfiles(pubkeys: List<String>): Map<String, Profile> = withContext(Dispatchers.IO) {
    val fresh = pubkeys.filter { it.matches(Regex("^[0-9a-f]{64}$")) }.distinct().take(60)
    val uncached = fresh.filter { !profiles.containsKey(it) }
    if (uncached.isNotEmpty()) {
      val evs = RelayPool.queryOne(nostrFilter(kinds = listOf(0), authors = uncached), RelayPool.relays, 6000)
      for (e in evs) {
        val p = parseProfile(e.pubkey, e.content, e.createdAt)
        if ((profileAt[e.pubkey] ?: 0) < e.createdAt) {
          profiles[e.pubkey] = p; profileAt[e.pubkey] = e.createdAt
        }
      }
      saveCache()
    }
    fresh.associateWith { cachedProfile(it) }
  }

  suspend fun publishProfile(meta: Map<String, String>): PublishRes = withContext(Dispatchers.IO) {
    val e = Signer.signEvent(0, JSONObject(meta as Map<String, String>).toString())
    val r = RelayPool.publish(e, RelayPool.relays)
    Signer.pubkey?.let { pk ->
      profiles[pk] = cachedProfile(pk).copy(
        name = meta["name"] ?: "", displayName = meta["display_name"] ?: "",
        about = meta["about"] ?: "", picture = meta["picture"] ?: "",
        lud16 = meta["lud16"] ?: "", at = nowSec())
      profileAt[pk] = nowSec(); saveCache()
    }
    r
  }

  // ---------- contacts ----------
  suspend fun fetchContacts(pk: String = Signer.pubkey ?: ""): List<String> = withContext(Dispatchers.IO) {
    if (pk.isEmpty()) return@withContext emptyList()
    contacts[pk]?.let { return@withContext it }
    val evs = RelayPool.queryOne(nostrFilter(kinds = listOf(3), authors = listOf(pk), limit = 1), RelayPool.relays, 6000)
    val list = if (evs.isNotEmpty()) Ev.tagVals(evs[0], "p").filter { it.matches(Regex("^[0-9a-f]{64}$")) }.distinct() else emptyList()
    contacts[pk] = list
    if (pk == Signer.pubkey) follows = list
    saveCache()
    list
  }

  suspend fun setFollow(pk: String, follow: Boolean): PublishRes = withContext(Dispatchers.IO) {
    val mine = fetchContacts(Signer.pubkey ?: "")
    val next = if (follow) (mine + pk).distinct() else mine - pk
    val evs = RelayPool.queryOne(nostrFilter(kinds = listOf(3), authors = listOf(Signer.pubkey ?: ""), limit = 1), RelayPool.relays, 6000)
    val keep = if (evs.isNotEmpty()) evs[0].tags.filter { it.getOrNull(0) != "p" } else emptyList()
    val e = Signer.signEvent(3, if (evs.isNotEmpty()) evs[0].content else "", keep + next.map { listOf("p", it) })
    val r = RelayPool.publish(e, RelayPool.relays)
    contacts[Signer.pubkey ?: ""] = next; follows = next; saveCache()
    r
  }

  // ---------- notes ----------
  suspend fun publishNote(content: String, tags: List<List<String>> = emptyList()): Pair<NostrEvent, PublishRes> =
    withContext(Dispatchers.IO) {
      val e = Signer.signEvent(1, content, tags)
      e to RelayPool.publish(e, RelayPool.relays)
    }

  suspend fun react(target: NostrEvent, kind: Int = 1, content: String = "+", emojiTag: List<String>? = null): PublishRes =
    withContext(Dispatchers.IO) {
      val tags = mutableListOf(listOf("e", target.id, "", ""), listOf("p", target.pubkey), listOf("k", kind.toString()))
      if (emojiTag != null) tags.add(emojiTag)
      RelayPool.publish(Signer.signEvent(7, content, tags), RelayPool.relays)
    }

  data class RecentEmoji(val t: String, val u: String = "")
  fun recentEmojis(): List<RecentEmoji> {
    val a = Store.getArr("xm.emoji.recent.v1") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
      val o = a.optJSONObject(i) ?: return@mapNotNull null
      RecentEmoji(o.optString("t"), o.optString("u", ""))
    }
  }
  fun pushRecentEmoji(item: RecentEmoji) {
    val list = (listOf(item) + recentEmojis().filter { it.t != item.t }).take(18)
    Store.setArr("xm.emoji.recent.v1", JSONArray(list.map { JSONObject().put("t", it.t).put("u", it.u) }))
  }

  suspend fun getEmojiPacks(pk: String): List<EmojiPack> = withContext(Dispatchers.IO) {
    if (pk.isEmpty() || pk.startsWith("demo")) return@withContext emptyList()
    packCache[pk]?.let { return@withContext it }
    val out = mutableListOf<EmojiPack>()
    try {
      val evs = RelayPool.queryOne(nostrFilter(kinds = listOf(10030), authors = listOf(pk), limit = 5), RelayPool.relays, 6000)
      for (e in evs) {
        val name = Ev.tagVal(e, "d").ifEmpty { "pack" }
        val emojis = e.tags.filter { it.getOrNull(0) == "emoji" && !it.getOrNull(1).isNullOrEmpty() && !it.getOrNull(2).isNullOrEmpty() }
          .map { EmojiRef(it[1], it[2]) }
        if (emojis.isNotEmpty()) out.add(EmojiPack(name, emojis))
      }
    } catch (_: Exception) {}
    packCache[pk] = out
    out
  }

  suspend fun repost(target: NostrEvent): PublishRes = withContext(Dispatchers.IO) {
    RelayPool.publish(Signer.signEvent(6, Ev.toJsonArray(target), listOf(listOf("e", target.id, ""), listOf("p", target.pubkey))), RelayPool.relays)
  }

  suspend fun fetchGlobalNotes(limit: Int = 40): List<NostrEvent> =
    RelayPool.queryOne(nostrFilter(kinds = listOf(1), limit = limit), RelayPool.relays, 7000)

  suspend fun fetchAuthorNotes(pk: String, limit: Int = 40): List<NostrEvent> =
    RelayPool.query(listOf(
      nostrFilter(kinds = listOf(1), authors = listOf(pk), limit = limit),
      nostrFilter(kinds = listOf(6), authors = listOf(pk), limit = 10),
    ), RelayPool.relays, 7000)

  suspend fun fetchThread(id: String): List<NostrEvent> =
    RelayPool.queryOne(nostrFilter(kinds = listOf(1), tags = mapOf("e" to listOf(id)), limit = 50), RelayPool.relays, 7000)

  // ---------- shorts ----------
  suspend fun fetchShorts(tags: List<String> = emptyList(), authors: List<String> = emptyList(), limit: Int = 30): List<ShortV> =
    withContext(Dispatchers.IO) {
      val f = nostrFilter(kinds = listOf(21, 22), limit = limit,
        tags = if (tags.isNotEmpty()) mapOf("t" to tags) else emptyMap(),
        authors = if (authors.isNotEmpty()) authors else null)
      RelayPool.queryOne(f, RelayPool.relays, 8000).map { parseShort(it) }.filter { it.url.isNotEmpty() }
    }

  suspend fun publishShort(url: String, title: String, tags: List<String>, thumb: String = "", mime: String = "", sha256: String = "", duration: Double = 0.0, desc: String = "", collabs: List<String> = emptyList(), dim: String = "", size: Long = 0, fallbacks: List<String> = emptyList()) =
    withContext(Dispatchers.IO) {
      val t = mutableListOf(listOf("url", url), listOf("title", title.ifEmpty { "XM Arcade short" })) +
        tags.map { listOf("t", it.removePrefix("#").lowercase()) } +
        collabs.filter { it.matches(Regex("^[0-9a-f]{64}$")) }.distinct().map { listOf("p", it) }
      val all = t.toMutableList()
      if (thumb.isNotEmpty() || mime.isNotEmpty() || sha256.isNotEmpty() || fallbacks.isNotEmpty() || dim.isNotEmpty() || size > 0) {
        val im = mutableListOf("imeta", "url $url")
        if (mime.isNotEmpty()) im.add("m $mime")
        if (thumb.isNotEmpty()) im.add("image $thumb")
        if (sha256.isNotEmpty()) im.add("x $sha256")
        if (duration > 0) im.add("duration $duration")
        if (dim.isNotEmpty()) im.add("dim $dim")
        if (size > 0) im.add("size $size")
        for (fb in fallbacks.distinct().take(8)) if (fb.isNotEmpty() && fb != url) im.add("fallback $fb")
        all.add(im)
      }
      val e = Signer.signEvent(22, desc.ifEmpty { title }, all)
      e to RelayPool.publish(e, RelayPool.relays)
    }

  // ---------- Concord groups ----------
  fun groupRelays(): List<String> = RelayPool.groupRelays.ifEmpty { RelayPool.relays }
  const val CONCORD_TAG = "concord1:"

  suspend fun fetchGroups(): List<Group> = withContext(Dispatchers.IO) {
    val evs = RelayPool.queryOne(nostrFilter(kinds = listOf(39000), limit = 100), groupRelays(), 8000)
    val out = mutableListOf<Group>()
    for (e in evs) {
      val id = Ev.tagVal(e, "d")
      if (id.isEmpty()) continue
      val meta = try { JSONObject(e.content) } catch (_: Exception) { JSONObject() }
      out.add(Group("${e.pubkey.take(8)}/$id", e.pubkey, id,
        meta.optString("name", id), meta.optString("about", ""), meta.optString("picture", ""),
        groupRelays()))
    }
    if (out.isNotEmpty()) { groupsCache = out.take(40); saveCache() }
    if (out.isNotEmpty()) out else groupsCache
  }

  fun groupChannelId(group: Group, channel: String): String = "${group.host}:${group.d}:$channel"

  suspend fun fetchChannelMessages(group: Group, channel: String, limit: Int = 60): List<NostrEvent> =
    RelayPool.queryOne(nostrFilter(kinds = listOf(9), tags = mapOf("h" to listOf(group.d)), limit = minOf(100, limit * 2)),
      group.relays.ifEmpty { groupRelays() }, 8000)
      .filter { (Ev.tagVal(it, "c").ifEmpty { "general" }) == channel }
      .take(limit).reversed()

  fun isConcordEncrypted(e: NostrEvent): Boolean =
    e.tags.any { it.getOrNull(0) == "concord" } || e.content.startsWith(CONCORD_TAG)

  fun getGroupKey(id: String): String = groupKeys[id] ?: ""
  fun setGroupKey(id: String, keyHex: String) { groupKeys[id] = keyHex; saveCache() }
  fun newGroupKey(): String = Hex.encode(random32())

  suspend fun decryptConcord(e: NostrEvent, id: String): String? = withContext(Dispatchers.IO) {
    val k = getGroupKey(id)
    if (k.isEmpty()) return@withContext null
    try { Nip44.decrypt(e.content.removePrefix(CONCORD_TAG), Hex.decode(k)) } catch (_: Exception) { null }
  }

  suspend fun sendGroupMessage(group: Group, channel: String, text: String, encrypted: Boolean = false) =
    withContext(Dispatchers.IO) {
      val id = groupChannelId(group, channel)
      var content = text
      val extra = mutableListOf<List<String>>()
      if (encrypted) {
        var k = getGroupKey(id)
        if (k.isEmpty()) { k = newGroupKey(); setGroupKey(id, k) }
        content = CONCORD_TAG + Nip44.encryptRandom(text, Hex.decode(k))
        extra.add(listOf("concord", "1"))
      }
      val e = Signer.signEvent(9, content, listOf(listOf("h", group.d), listOf("c", channel)) + extra)
      e to RelayPool.publish(e, group.relays.ifEmpty { groupRelays() })
    }

  suspend fun shareGroupKey(group: Group, channel: String, memberPk: String): PublishRes = withContext(Dispatchers.IO) {
    val id = groupChannelId(group, channel)
    val key = getGroupKey(id)
    if (key.isEmpty()) throw IllegalStateException("No channel key yet — send one locked message first.")
    if (Signer.method != "local" || Signer.localSk == null)
      throw IllegalStateException("Automatic sharing needs a device key. Copy the key and send it manually.")
    val rumorContent = JSONObject().put("app", "xm-arcade").put("type", "concord-key").put("channel", id).put("key", key).toString()
    val wrap = Nip59.wrap(1, rumorContent, listOf(listOf("p", memberPk)), Signer.localSk!!, memberPk)
    RelayPool.publish(wrap, RelayPool.relays)
  }

  suspend fun collectKeyShares(): Int = withContext(Dispatchers.IO) {
    if (Signer.method != "local" || Signer.pubkey == null || Signer.localSk == null) return@withContext 0
    val wraps = RelayPool.queryOne(nostrFilter(kinds = listOf(1059),
      tags = mapOf("p" to listOf(Signer.pubkey!!)), limit = 30, since = nowSec() - 30 * 86400), RelayPool.relays, 7000)
    var n = 0
    for (w in wraps) {
      try {
        val rumor = Nip59.unwrap(w, Signer.localSk!!) ?: continue
        val j = JSONObject(rumor.content)
        if (j.optString("app") == "xm-arcade" && j.optString("type") == "concord-key" &&
          j.has("channel") && Regex("^[0-9a-f]{64}$").matches(j.optString("key"))) {
          if (!groupKeys.containsKey(j.getString("channel"))) { groupKeys[j.getString("channel")] = j.getString("key"); n++ }
        }
      } catch (_: Exception) {}
    }
    if (n > 0) saveCache()
    n
  }

  fun subscribeChannel(group: Group, channel: String, onEvent: (NostrEvent) -> Unit): () -> Unit =
    RelayPool.subscribe(
      listOf(nostrFilter(kinds = listOf(9), tags = mapOf("h" to listOf(group.d)), since = nowSec() - 5)),
      group.relays.ifEmpty { groupRelays() },
    ) { e -> if (Ev.tagVal(e, "c").ifEmpty { "general" } == channel) { try { onEvent(e) } catch (_: Exception) {} } }

  // ---------- music ----------
  suspend fun fetchMusic(tag: String = "", limit: Int = 40): List<Track> = withContext(Dispatchers.IO) {
    val filters = mutableListOf(nostrFilter(kinds = listOf(1), tags = mapOf("t" to listOf(tag.ifEmpty { "music" })), limit = limit))
    if (tag.isEmpty()) filters.add(nostrFilter(kinds = listOf(1063), limit = 20))
    val evs = RelayPool.query(filters, RelayPool.relays, 8000)
    val out = mutableListOf<Track>()
    for (e in evs) {
      if (e.kind == 1063) {
        val url = Ev.tagVal(e, "url")
        val mime = Ev.tagVal(e, "m")
        if ((url.isNotEmpty() && mime.contains("audio", ignoreCase = true)) ||
          (url.isNotEmpty() && Regex("\\.(mp3|m4a|ogg|oga|opus|wav|flac)(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(url))) {
          out.add(Track(e.id, e.pubkey, e.createdAt, url,
            Ev.tagVal(e, "name").ifEmpty { Ev.tagVal(e, "title").ifEmpty { "Untitled track" } },
            Ev.tagVals(e, "t").map { it.lowercase() }))
        }
        continue
      }
      parseTrack(e)?.let { out.add(it) }
    }
    out.take(limit)
  }

  suspend fun publishTrack(url: String, title: String, tags: List<String>, mime: String = "") =
    withContext(Dispatchers.IO) {
      val e = Signer.signEvent(1, "$title\n$url", listOf(listOf("r", url), listOf("title", title),
        listOf("m", mime.ifEmpty { "audio/mpeg" })) + tags.map { listOf("t", it.removePrefix("#").lowercase()) })
      e to RelayPool.publish(e, RelayPool.relays)
    }

  // ---------- app discovery ----------
  data class DiscoveredApp(val id: String, val pubkey: String, val createdAt: Long, val url: String, val title: String, val tags: List<String>)

  suspend fun fetchDiscoveredApps(limit: Int = 30): List<DiscoveredApp> = withContext(Dispatchers.IO) {
    val base = try {
      RelayPool.query(listOf(
        nostrFilter(kinds = listOf(1), tags = mapOf("t" to listOf("webxdc")), limit = limit),
        nostrFilter(kinds = listOf(1063), tags = mapOf("t" to listOf("webxdc")), limit = 20),
      ), RelayPool.relays, 7000)
    } catch (_: Exception) { emptyList() }
    val search = try {
      RelayPool.queryOne(nostrFilter(kinds = listOf(1), search = "webxdc", limit = 20),
        listOf("wss://relay.nostr.band", "wss://relay.ditto.pub"), 7000)
    } catch (_: Exception) { emptyList() }
    val seen = mutableSetOf<String>()
    val out = mutableListOf<DiscoveredApp>()
    val urlRe = Regex("https?://[^\\s)]+")
    for (e in (base + search)) {
      if (!seen.add(e.id)) continue
      val urls = urlRe.findAll(e.content).map { it.value }.toList()
      val file = urls.firstOrNull { Regex("\\.(xdc|webxdc|html|zip)(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        ?: urls.firstOrNull { Regex("[0-9a-f]{64}", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        ?: (if (e.kind == 1063) Ev.tagVal(e, "url") else "")
      if (file.isEmpty()) continue
      out.add(DiscoveredApp(e.id, e.pubkey, e.createdAt, file,
        Ev.tagVal(e, "title").ifEmpty { e.content.lineSequence().firstOrNull()?.take(60)?.ifEmpty { "Shared app" } ?: "Shared app" },
        Ev.tagVals(e, "t")))
      if (out.size >= limit) break
    }
    out
  }

  // ---------- demo fallback ----------
  object Demo {
    fun shorts(now: Long = nowSec()) = listOf(
      ShortV("demo1", "demo-noor", now - 600, "", tags = listOf("relayweek", "video"), title = "Relay week, in motion", demo = true, author = "Noor Haddad"),
      ShortV("demo2", "demo-mira", now - 3600, "", tags = listOf("concord", "howto"), title = "Building together", demo = true, author = "Mira Kestrel"),
      ShortV("demo3", "demo-tobias", now - 7200, "", tags = listOf("film", "sunrise"), title = "Morning light", demo = true, author = "Tobias Lund"),
    )
    fun tracks(now: Long = nowSec()) = listOf(
      Track("dt1", "demo", now - 300, "", "Dawn Chorus — Kite Theory", listOf("indie", "electronic"), true),
      Track("dt2", "demo", now - 900, "", "Chiptune Sunrise — 8bit Garden", listOf("chiptune"), true),
      Track("dt3", "demo", now - 1800, "", "Relay Hum — Nostrilia", listOf("ambient"), true),
    )
  }

  object DemoStore {
    data class Msg(val who: String, val text: String, val t: String)
    private fun all(): MutableMap<String, MutableList<Msg>> {
      val o = Store.getObj("xm.demo.msgs.v1") ?: return mutableMapOf()
      val m = mutableMapOf<String, MutableList<Msg>>()
      o.keys().forEach { k ->
        val a = o.optJSONArray(k) ?: return@forEach
        m[k] = (0 until a.length()).map { i ->
          val e = a.optJSONObject(i); Msg(e.optString("who"), e.optString("text"), e.optString("t"))
        }.toMutableList()
      }
      return m
    }
    private fun save(m: Map<String, List<Msg>>) {
      val o = JSONObject()
      m.forEach { (k, v) -> o.put(k, JSONArray(v.map { JSONObject().put("who", it.who).put("text", it.text).put("t", it.t) })) }
      Store.setObj("xm.demo.msgs.v1", o)
    }
    fun forId(id: String): List<Msg> =
      all()[id] ?: listOf(Msg("Mira Kestrel", "Demo channel — connect a group relay to go live.", "12:02"))
    fun push(id: String, who: String, text: String) {
      val m = all()
      val t = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
      m[id] = ((m[id] ?: forId(id)) + Msg(who, text, t)).takeLast(100).toMutableList()
      save(m)
    }
  }

  // ---------- blossom ----------
  fun blossomServers(): List<String> =
    Store.getStrList("xm.blossom.v1", emptyList()).ifEmpty { listOf("https://blossom.primal.net", "https://blossom.ditto.pub", "https://data.haus", "https://nostr.download", "https://blossom.band") }
  fun setBlossomServers(list: List<String>) = Store.setStrList("xm.blossom.v1", list)

  data class BlossomUp(val url: String, val sha256: String, val mime: String, val size: Long, val name: String)

  private suspend fun blossomAuth(server: String, sha: String, name: String): String {
    val auth = Signer.signEvent(24242, "Upload $name",
      listOf(listOf("t", "upload"), listOf("x", sha), listOf("server", server),
        listOf("expiration", (nowSec() + 300).toString())))
    return "Nostr " + B64.encode(Ev.toJsonArray(auth).toByteArray(Charsets.UTF_8))
  }

  private suspend fun blossomHead(server: String, sha: String): Boolean =
    try { Net.head("${server.trimEnd('/')}/$sha") == 200 } catch (_: Exception) { false }

  private suspend fun blossomPut(server: String, bytes: ByteArray, mime: String, sha: String, name: String): String {
    val base = server.trimEnd('/')
    val j = Net.putBytes("$base/upload", bytes, mime.ifEmpty { "application/octet-stream" },
      mapOf("Authorization" to blossomAuth(base, sha, name), "X-SHA-256" to sha))
    return j.optString("url", "").ifEmpty { "$base/$sha" }
  }

  /** Ask a server to fetch the blob itself. Null = unsupported/failed → caller direct-PUTs. */
  private suspend fun blossomMirror(server: String, originUrl: String, sha: String, name: String): String? {
    val base = server.trimEnd('/')
    return try {
      val j = Net.putJson("$base/mirror", JSONObject().put("url", originUrl),
        mapOf("Authorization" to blossomAuth(base, sha, name)))
      j.optString("url", "").ifEmpty { "$base/$sha" }
    } catch (_: Exception) { null }
  }

  data class BlossomMultiUp(val urls: List<String>, val sha256: String, val mime: String, val size: Long, val name: String)

  /** nostube-style fan-out: upload once, then HEAD-check + mirror everywhere else
   *  SIMULTANEOUSLY. Succeeds if ANY server ends up with the bytes; mirror
   *  failures retry once, then give up quietly — one sick server never fails
   *  the upload, and the user never has to think about it. */
  suspend fun blossomUploadMulti(bytes: ByteArray, name: String, mime: String): BlossomMultiUp =
    withContext(Dispatchers.IO) {
      val sha = Hex.encode(Sha.sha256(bytes))
      val servers = blossomServers().map { it.trimEnd('/') }.filter { it.isNotEmpty() }.distinct()
      if (servers.isEmpty()) throw RuntimeException("no Blossom servers configured")
      var primary = ""
      var primaryServer = ""
      var lastErr: Exception? = null
      for (s in servers) {
        try {
          primary = if (blossomHead(s, sha)) "$s/$sha" else blossomPut(s, bytes, mime, sha, name)
          primaryServer = s
          break
        } catch (e: Exception) { lastErr = e }
      }
      if (primary.isEmpty()) throw lastErr ?: RuntimeException("upload failed")
      val rest = servers.filter { it != primaryServer }
      val mirrored = rest.map { s ->
        async {
          try {
            if (blossomHead(s, sha)) return@async "$s/$sha"
            blossomMirror(s, primary, sha, name) ?: blossomPut(s, bytes, mime, sha, name)
          } catch (_: Exception) {
            try {
              delay(1500)
              if (blossomHead(s, sha)) "$s/$sha"
              else blossomMirror(s, primary, sha, name) ?: blossomPut(s, bytes, mime, sha, name)
            } catch (_: Exception) { null }
          }
        }
      }.awaitAll().filterNotNull()
      BlossomMultiUp((listOf(primary) + mirrored).distinct(), sha, mime, bytes.size.toLong(), name)
    }

  suspend fun blossomUpload(bytes: ByteArray, name: String, mime: String): BlossomUp = withContext(Dispatchers.IO) {
    val m = blossomUploadMulti(bytes, name, mime)
    BlossomUp(m.urls.first(), m.sha256, m.mime, m.size, m.name)
  }

  // ---------- group management ----------
  suspend fun createGroup(name: String, about: String, picture: String = ""): Group = withContext(Dispatchers.IO) {
    val me = Signer.pubkey ?: throw IllegalStateException("Sign in first")
    val d = "xm-" + uid()
    val meta = JSONObject().put("name", name).put("about", about).put("picture", picture).toString()
    val e = Signer.signEvent(39000, meta, listOf(listOf("d", d)))
    RelayPool.publish(e, groupRelays())
    val g = Group("${me.take(8)}/$d", me, d, name, about, picture, groupRelays(), listOf(ChanDef("general", "Main channel")))
    groupsCache = (listOf(g) + groupsCache).take(40)
    saveCache()
    g
  }

  suspend fun updateGroupMeta(g: Group, name: String, about: String, picture: String): Group = withContext(Dispatchers.IO) {
    val meta = JSONObject().put("name", name).put("about", about).put("picture", picture).toString()
    val e = Signer.signEvent(39000, meta, listOf(listOf("d", g.d)))
    try { RelayPool.publish(e, g.relays.ifEmpty { groupRelays() }) } catch (_: Exception) {}
    val ng = g.copy(name = name, about = about, picture = picture)
    groupsCache = groupsCache.map { if (it.id == g.id) ng else it }
    saveCache()
    ng
  }

  fun addLocalChannel(gid: String, chan: ChanDef) {
    groupsCache = groupsCache.map { g ->
      if (g.id == gid && g.channels.none { it.name == chan.name }) g.copy(channels = g.channels + chan) else g
    }
    saveCache()
  }

  fun removeGroup(gid: String) {
    groupsCache = groupsCache.filter { it.id != gid }
    saveCache()
  }
}
