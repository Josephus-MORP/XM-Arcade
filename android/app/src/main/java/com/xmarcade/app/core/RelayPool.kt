package com.xmarcade.app.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/** NIP-01 filter builder. */
fun nostrFilter(
  kinds: List<Int>? = null, authors: List<String>? = null, ids: List<String>? = null,
  tags: Map<String, List<String>> = emptyMap(), since: Long? = null, until: Long? = null,
  limit: Int? = null, search: String? = null,
): JSONObject {
  val o = JSONObject()
  kinds?.let { o.put("kinds", JSONArray(it)) }
  authors?.let { o.put("authors", JSONArray(it)) }
  ids?.let { o.put("ids", JSONArray(it)) }
  tags.forEach { (k, v) -> o.put("#$k", JSONArray(v)) }
  since?.let { o.put("since", it) }
  until?.let { o.put("until", it) }
  limit?.let { o.put("limit", it) }
  search?.let { o.put("search", it) }
  return o
}

data class PublishRes(val ok: Boolean, val oks: Int, val total: Int)

private data class Sub(
  val id: String, val filters: List<JSONObject>,
  val onEvent: (NostrEvent) -> Unit, val onEose: (() -> Unit)? = null,
  val seen: MutableSet<String> = Collections.synchronizedSet(mutableSetOf()),
)

private class Conn(val url: String) {
  @Volatile var ws: WebSocket? = null
  val subs = Collections.synchronizedMap(mutableMapOf<String, Sub>())
  val oks = Collections.synchronizedMap(mutableMapOf<String, CompletableDeferred<Boolean>>())

  fun send(msg: String) { try { ws?.send(msg) } catch (_: Exception) {} }

  suspend fun connect() {
    if (ws != null) return
    RelayPool.setStatus(url, "connecting")
    val opened = CompletableDeferred<Unit>()
    val req = Request.Builder().url(url).build()
    val sock = Net.client.newWebSocket(req, RelayPool.listener(url, opened))
    ws = sock
    try {
      withTimeout(10000) { opened.await() }
      RelayPool.setStatus(url, "live")
    } catch (e: Exception) {
      try { sock.cancel() } catch (_: Exception) {}
      ws = null
      RelayPool.setStatus(url, "down")
      throw e
    }
  }
}

/** Relay connection pool: queries (EOSE), live subs, publish. Mirrors nostr.js. */
object RelayPool {
  val DEFAULT_RELAYS = listOf("wss://nos.lol", "wss://relay.primal.net", "wss://relay.ditto.pub")
  val GROUP_RELAYS_DEFAULT = listOf("wss://groups.fiatjaf.com", "wss://relay.ditto.pub")
  val SEARCH_RELAYS = listOf("wss://relay.nostr.band")

  var relays: List<String> = DEFAULT_RELAYS
    private set
  var groupRelays: List<String> = GROUP_RELAYS_DEFAULT
    private set

  private val _status = MutableStateFlow<Map<String, String>>(emptyMap())
  val status: StateFlow<Map<String, String>> = _status
  private val sockets = Collections.synchronizedMap(mutableMapOf<String, Conn>())

  fun init() {
    relays = loadWithMigration("xm.relays.v1", DEFAULT_RELAYS)
    val g = Store.getStrList("xm.grouprelays.v1", emptyList())
    groupRelays = if (g.isEmpty()) GROUP_RELAYS_DEFAULT.toList() else g
  }

  private fun loadWithMigration(key: String, dflt: List<String>): List<String> {
    val s = Store.getStrList(key, emptyList())
    if (s.isEmpty()) return dflt.toList()
    val out = s.map { if (it == "wss://relay.damus.io") "wss://relay.ditto.pub" else it }.distinct()
    val fixed = if (out.isEmpty()) dflt.toList() else out
    if (fixed != s) Store.setStrList(key, fixed)
    return fixed
  }

  fun setRelays(list: List<String>) { relays = list; Store.setStrList("xm.relays.v1", list) }
  fun setGroupRelays(list: List<String>) { groupRelays = list; Store.setStrList("xm.grouprelays.v1", list) }

  fun setStatus(url: String, st: String) {
    val m = _status.value.toMutableMap()
    m[url] = st
    _status.value = m
  }

  fun statusOf(url: String): String = _status.value[url] ?: "connecting"
  fun liveCount(list: List<String> = relays): Int = list.count { statusOf(it) == "live" }

  fun probe(url: String) = AppScope.launch {
    try { ensure(url) } catch (_: Exception) {}
  }
  fun refreshProbes() { (relays + groupRelays).distinct().forEach { probe(it) } }

  private suspend fun ensure(url: String): Conn = withContext(Dispatchers.IO) {
    sockets[url]?.let { if (it.ws != null) return@withContext it }
    val c = synchronized(sockets) { sockets.getOrPut(url) { Conn(url) } }
    c.connect()
    c
  }

  fun listener(url: String, opened: CompletableDeferred<Unit>) = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      if (!opened.isCompleted) opened.complete(Unit)
      setStatus(url, "live")
      // Resend surviving subscriptions (reconnect path).
      val c = sockets[url] ?: return
      c.subs.values.toList().forEach { s -> c.send(reqMsg(s.id, s.filters)) }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      try {
        val arr = JSONArray(text)
        if (arr.length() < 2) return
        val c = sockets[url] ?: return
        when (arr.optString(0)) {
          "EVENT" -> {
            val sub = c.subs[arr.optString(1)] ?: return
            val ev = Ev.fromJson(arr.optJSONObject(2) ?: return) ?: return
            if (!Ev.verify(ev)) return
            if (!sub.seen.add(ev.id)) return
            if (sub.seen.size > 4000) sub.seen.clear()
            try { sub.onEvent(ev) } catch (_: Exception) {}
          }
          "EOSE" -> {
            val sub = c.subs[arr.optString(1)] ?: return
            try { sub.onEose?.invoke() } catch (_: Exception) {}
          }
          "OK" -> {
            val d = c.oks.remove(arr.optString(1)) ?: return
            if (!d.isCompleted) d.complete(arr.optBoolean(2, false))
          }
          else -> {}
        }
      } catch (_: Exception) {}
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onMessage(webSocket, bytes.utf8())

    private fun dropped(e: Exception?) {
      val c = sockets[url]
      if (c != null && c.ws == null) return
      c?.ws = null
      setStatus(url, "down")
      if (!opened.isCompleted) opened.completeExceptionally(e ?: RuntimeException("ws failed"))
      // One auto-reconnect attempt while something still listens.
      if (c != null && c.subs.isNotEmpty()) AppScope.launch {
        delay(3000)
        if (c.ws == null && c.subs.isNotEmpty()) {
          try { c.connect() } catch (_: Exception) {}
        }
      }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
      dropped(RuntimeException(t.message))

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(null)
  }

  private fun reqMsg(id: String, filters: List<JSONObject>): String =
    "[\"REQ\",\"$id\",${filters.joinToString(",") { it.toString() }}]"

  /** Query with EOSE semantics. Returns deduped, verified events, newest first. */
  suspend fun query(filters: List<JSONObject>, relays: List<String>, timeoutMs: Long = 8000): List<NostrEvent> =
    withContext(Dispatchers.IO) {
      if (relays.isEmpty()) return@withContext emptyList()
      val out = Collections.synchronizedList(mutableListOf<NostrEvent>())
      relays.map { url ->
        async {
          try {
            val c = ensure(url)
            val id = "q" + uid()
            val eose = CompletableDeferred<Unit>()
            c.subs[id] = Sub(id, filters, onEvent = { out.add(it) }, onEose = { if (!eose.isCompleted) eose.complete(Unit) })
            c.send(reqMsg(id, filters))
            withTimeoutOrNull(timeoutMs) { eose.await() }
            c.subs.remove(id)
            c.send("[\"CLOSE\",\"$id\"]")
          } catch (_: Exception) { setStatus(url, statusOf(url).let { if (it == "live") it else "down" }) }
        }
      }.awaitAll()
      out.distinctBy { it.id }.sortedByDescending { it.createdAt }
    }

  suspend fun queryOne(filter: JSONObject, relays: List<String>, timeoutMs: Long = 8000): List<NostrEvent> =
    query(listOf(filter), relays, timeoutMs)

  /** Live subscription. Returns a close function. */
  fun subscribe(filters: List<JSONObject>, relays: List<String>, onEvent: (NostrEvent) -> Unit): () -> Unit {
    val id = "s" + uid()
    val sub = Sub(id, filters, onEvent, null)
    relays.forEach { url ->
      AppScope.launch {
        try {
          val c = ensure(url)
          c.subs[id] = sub
          c.send(reqMsg(id, filters))
        } catch (_: Exception) {}
      }
    }
    return {
      relays.forEach { url ->
        AppScope.launch {
          try { sockets[url]?.let { it.subs.remove(id); it.send("[\"CLOSE\",\"$id\"]") } } catch (_: Exception) {}
        }
      }
    }
  }

  suspend fun publish(ev: NostrEvent, relays: List<String>): PublishRes = withContext(Dispatchers.IO) {
    val list = relays.ifEmpty { this@RelayPool.relays }
    if (list.isEmpty()) return@withContext PublishRes(false, 0, 0)
    val msg = "[\"EVENT\",${Ev.toJsonArray(ev)}]"
    val oks = list.map { url ->
      async {
        try {
          val c = ensure(url)
          val d = CompletableDeferred<Boolean>()
          c.oks[ev.id] = d
          c.send(msg)
          withTimeoutOrNull(8000) { d.await() } == true
        } catch (_: Exception) { setStatus(url, "down"); false }
      }
    }.awaitAll().count { it }
    PublishRes(oks > 0, oks, list.size)
  }
}
