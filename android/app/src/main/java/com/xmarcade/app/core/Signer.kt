package com.xmarcade.app.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/** Signer: local device key or NIP-46 remote (Amber). Mirrors signer.js. */
object Signer {
  var method: String? = null // 'local' | 'nip46'
    private set
  var pubkey: String? = null
    private set
  var label: String = ""
    private set
  var localSk: ByteArray? = null
    private set

  data class Nip46Conn(
    val clientSk: ByteArray, val clientPk: String,
    var signerPk: String, val relay: String, val secret: String,
  )
  var nip46: Nip46Conn? = null
    private set
  private var unsub: (() -> Unit)? = null
  private val pending = Collections.synchronizedMap(mutableMapOf<String, CompletableDeferred<String>>())

  fun isSignedIn(): Boolean = method != null && pubkey != null
  fun npub(): String = pubkey?.let { Nip19.npubEncode(it) } ?: ""

  private fun persist() {
    if (method == "local" && localSk != null) {
      Store.setObj("xm.session.v1", JSONObject().put("method", "local").put("sk", Hex.encode(localSk!!)))
    } else if (method == "nip46" && nip46 != null) {
      val n = nip46!!
      Store.setObj("xm.session.v1", JSONObject().put("method", "nip46")
        .put("clientSk", Hex.encode(n.clientSk)).put("signerPk", n.signerPk)
        .put("relay", n.relay).put("secret", n.secret))
    } else Store.del("xm.session.v1")
  }

  // ---------- local device key ----------
  fun createLocal(): ByteArray {
    val sk = random32()
    loginLocal(sk)
    return sk
  }

  fun loginLocal(sk: ByteArray) {
    localSk = sk
    pubkey = Hex.encode(Secp.pubkey(sk))
    method = "local"
    label = "Device key"
    persist()
  }

  fun loginLocalInput(s: String) {
    val sk = Nip19.parseKeyInput(s) ?: throw IllegalArgumentException("That doesn't look like an nsec or 64-char hex key.")
    loginLocal(sk)
  }

  // ---------- NIP-46 ----------
  data class Bunker(val signerPk: String, val relay: String, val secret: String)
  data class Pairing(val clientSk: ByteArray, val clientPk: String, val relay: String, val secret: String, val uri: String)

  fun parseBunkerUri(uri: String): Bunker {
    val u = uri.trim()
    if (u.startsWith("nostrconnect://")) throw IllegalArgumentException("nostrconnect:// strings pair a new signer — paste a bunker:// URI to log in.")
    if (!u.startsWith("bunker://")) throw IllegalArgumentException("Expected a bunker:// URI from your signer app.")
    val rest = u.removePrefix("bunker://")
    val query = rest.substringAfter("?", "")
    var host = rest.substringBefore("?")
    if (host.endsWith("/")) host = host.dropLast(1)
    // bunker://<pubkey>?relay=..&secret=..  (pubkey may also sit in the path)
    val pubkey = host.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("Bunker URI has a bad signer pubkey.")
    val params = query.split("&").mapNotNull {
      val kv = it.split("=", limit = 2)
      if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else null
    }.toMap()
    val relay = params["relay"] ?: ""
    val secret = params["secret"] ?: ""
    if (!Regex("^[0-9a-f]{64}$").matches(pubkey)) throw IllegalArgumentException("Bunker URI has a bad signer pubkey.")
    if (!relay.startsWith("wss://")) throw IllegalArgumentException("Bunker URI needs a wss:// relay.")
    return Bunker(pubkey, relay, secret)
  }

  fun pairingState(relay: String = "wss://relay.nsec.app"): Pairing {
    val clientSk = random32()
    val clientPk = Hex.encode(Secp.pubkey(clientSk))
    val secret = Hex.encode(random32()).take(32)
    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    val uri = "nostrconnect://$clientPk?relay=${enc(relay)}&secret=$secret&name=${enc("XM Arcade")}&perms=${enc("sign_event:1,4,9,30023,nip44_encrypt,nip44_decrypt")}"
    return Pairing(clientSk, clientPk, relay, secret, uri)
  }

  private fun nip46Close() {
    try { unsub?.invoke() } catch (_: Exception) {}
    unsub = null
  }

  private fun nip46Listen() {
    nip46Close()
    val n = nip46 ?: return
    val ck = try { Nip44.conversationKey(n.clientSk, n.signerPk) } catch (_: Exception) { return }
    unsub = RelayPool.subscribe(
      listOf(nostrFilter(kinds = listOf(24133), tags = mapOf("p" to listOf(n.clientPk)), since = nowSec() - 60)),
      listOf(n.relay),
    ) { e ->
      try {
        if (e.pubkey != n.signerPk) return@subscribe
        val msg = JSONObject(Nip44.decrypt(e.content, ck))
        val id = msg.optString("id", "")
        val pend = pending.remove(id) ?: return@subscribe
        if (msg.has("error") && !msg.isNull("error")) pend.completeExceptionally(RuntimeException(msg.opt("error").toString()))
        else pend.complete(msg.opt("result")?.toString() ?: "null")
      } catch (_: Exception) {}
    }
  }

  /** NIP-46 request. Returns the raw JSON of `result`. */
  suspend fun nip46Request(method: String, paramsJson: String): String = withContext(Dispatchers.IO) {
    val n = nip46 ?: throw IllegalStateException("NIP-46 not connected.")
    val ck = Nip44.conversationKey(n.clientSk, n.signerPk)
    val id = uid()
    val content = Nip44.encrypt(JSONObject().put("id", id).put("method", method)
      .put("params", JSONArray(paramsJson)).toString(), ck, random32())
    val tpl = UnsignedEvent(24133, content,
      listOf(listOf("p", n.signerPk), listOf("expiration", (nowSec() + 120).toString())))
    val signed = Ev.finalize(tpl, n.clientSk)
    val d = CompletableDeferred<String>()
    pending[id] = d
    try {
      RelayPool.publish(signed, listOf(n.relay))
    } catch (_: Exception) {
      pending.remove(id)
      throw RuntimeException("Could not reach the signer relay " + n.relay)
    }
    val res = withTimeoutOrNull(60000) { d.await() }
      ?: throw RuntimeException("Signer timed out — approve the request in Amber.")
    pending.remove(id)
    res
  }

  suspend fun loginBunkerUri(uri: String): String = withContext(Dispatchers.IO) {
    val (signerPk, relay, secret) = parseBunkerUri(uri)
    val clientSk = random32()
    nip46 = Nip46Conn(clientSk, Hex.encode(Secp.pubkey(clientSk)), signerPk, relay, secret)
    nip46Listen()
    val ack = nip46Request("connect", JSONArray().put(signerPk).put(secret).toString())
    if (JSONObject.quote("ack") != "\"$ack\"" && ack != "ack") throw RuntimeException("Signer refused the connection.")
    val pk = nip46Request("get_public_key", "[]")
    if (!Regex("^[0-9a-f]{64}$").matches(pk)) throw RuntimeException("Signer returned an invalid public key.")
    pubkey = pk; method = "nip46"; label = "Amber / remote signer"
    persist()
    pk
  }

  suspend fun pairNewSigner(pair: Pairing, onStatus: (String) -> Unit): String = withContext(Dispatchers.IO) {
    nip46 = Nip46Conn(pair.clientSk, pair.clientPk, "", pair.relay, pair.secret)
    nip46Close()
    val close = RelayPool.subscribe(
      listOf(nostrFilter(kinds = listOf(24133), tags = mapOf("p" to listOf(pair.clientPk)), since = nowSec() - 30)),
      listOf(pair.relay),
    ) { e ->
      try {
        val ck = Nip44.conversationKey(pair.clientSk, e.pubkey)
        val msg = JSONObject(Nip44.decrypt(e.content, ck))
        if (msg.optString("result", "") == "ack" || msg.optString("method", "") == "connect") {
          nip46?.signerPk = e.pubkey
          onStatus("approved")
        }
      } catch (_: Exception) {}
    }
    unsub = close
    onStatus("waiting")
    val t0 = System.currentTimeMillis()
    while (System.currentTimeMillis() - t0 < 5 * 60 * 1000) {
      kotlinx.coroutines.delay(1500)
      if (nip46?.signerPk?.isNotEmpty() == true) break
    }
    if (nip46?.signerPk.isNullOrEmpty()) {
      nip46Close()
      throw RuntimeException("Pairing timed out — approve XM Arcade inside Amber and try again.")
    }
    nip46Listen()
    val pk = nip46Request("get_public_key", "[]")
    pubkey = pk; method = "nip46"; label = "Amber / remote signer"
    persist()
    pk
  }

  // ---------- unified ops ----------
  suspend fun signEvent(kind: Int, content: String, tags: List<List<String>> = emptyList()): NostrEvent =
    withContext(Dispatchers.IO) {
      val t = UnsignedEvent(kind, content, tags)
      if (method == "local" && localSk != null) return@withContext Ev.finalize(t, localSk!!)
      if (method == "nip46") {
        val params = JSONArray().put(JSONObject().put("created_at", t.createdAt).put("kind", kind)
          .put("tags", JSONArray(tags.map { JSONArray(it) })).put("content", content).put("pubkey", pubkey)).toString()
        val res = nip46Request("sign_event", params)
        val signed = Ev.fromJson(JSONObject(res)) ?: throw RuntimeException("Remote signer refused to sign.")
        if (signed.sig.isEmpty()) throw RuntimeException("Remote signer refused to sign.")
        return@withContext signed
      }
      throw IllegalStateException("Not signed in.")
    }

  suspend fun encrypt44(peerPk: String, text: String): String = withContext(Dispatchers.IO) {
    if (method == "local" && localSk != null)
      return@withContext Nip44.encryptRandom(text, Nip44.conversationKey(localSk!!, peerPk))
    if (method == "nip46") {
      val res = nip46Request("nip44_encrypt", JSONArray().put(peerPk).put(text).toString())
      return@withContext res
    }
    throw IllegalStateException("This signer can't do NIP-44 encryption.")
  }

  suspend fun decrypt44(peerPk: String, payload: String): String = withContext(Dispatchers.IO) {
    if (method == "local" && localSk != null)
      return@withContext Nip44.decrypt(payload, Nip44.conversationKey(localSk!!, peerPk))
    if (method == "nip46") {
      val res = nip46Request("nip44_decrypt", JSONArray().put(peerPk).put(payload).toString())
      return@withContext res
    }
    throw IllegalStateException("This signer can't do NIP-44 decryption.")
  }

  fun logout() {
    nip46Close()
    method = null; pubkey = null; localSk = null; nip46 = null
    pending.values.forEach { try { it.cancel() } catch (_: Exception) {} }
    pending.clear()
    label = ""
    persist()
  }

  suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
    val s = Store.getObj("xm.session.v1") ?: return@withContext false
    try {
      if (s.optString("method") == "local" && s.has("sk")) {
        loginLocal(Hex.decode(s.getString("sk")))
        return@withContext true
      }
      if (s.optString("method") == "nip46" && s.has("clientSk") && s.has("signerPk")) {
        val csk = Hex.decode(s.getString("clientSk"))
        nip46 = Nip46Conn(csk, Hex.encode(Secp.pubkey(csk)), s.getString("signerPk"), s.optString("relay"), s.optString("secret"))
        nip46Listen()
        return@withContext try {
          val pk = withTimeoutOrNull(12000) { nip46Request("get_public_key", "[]") }
          if (pk != null && Regex("^[0-9a-f]{64}$").matches(pk)) {
            pubkey = pk; method = "nip46"; label = "Amber / remote signer"
            true
          } else { nip46Close(); nip46 = null; false }
        } catch (_: Exception) { nip46Close(); nip46 = null; false }
      }
    } catch (_: Exception) {}
    false
  }
}

/** Missing-object shim so Signer compiles without java.util import noise. */
private object Collections2 {
  fun <K, V> synchronizedMap(m: MutableMap<K, V>): MutableMap<K, V> = Collections.synchronizedMap(m)
}
