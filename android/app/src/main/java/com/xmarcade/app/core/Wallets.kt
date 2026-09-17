package com.xmarcade.app.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64 as JBase64

/** NWC (NIP-47) client. Mirrors wallets.js route-all design. */
object Nwc {
  data class Conn(val walletPk: String, val relay: String, val secret: String)

  var conn: Conn? = null
    private set
  private var unsub: (() -> Unit)? = null
  private var ck: ByteArray? = null
  private var clientPk: String = ""
  private var routeAll: CompletableDeferred<JSONObject>? = null
  private val chain = Mutex()

  fun init() {
    try {
      Store.getObj("xm.nwc.v1")?.let {
        conn = Conn(it.getString("walletPk"), it.getString("relay"), it.getString("secret"))
      }
    } catch (_: Exception) {}
  }

  fun parseNwcUri(uri: String): Conn {
    val u = uri.trim()
    if (!u.startsWith("nostr+walletconnect://", ignoreCase = true))
      throw IllegalArgumentException("Expected a nostr+walletconnect:// URI.")
    val rest = u.substringAfter("://")
    val query = rest.substringAfter("?", "")
    val walletPk = rest.substringBefore("?").trimStart('/')
    val params = query.split("&").mapNotNull {
      val kv = it.split("=", limit = 2)
      if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else null
    }.toMap()
    val relay = params["relay"] ?: ""
    val secret = (params["secret"] ?: "").lowercase()
    if (!Regex("^[0-9a-f]{64}$").matches(walletPk)) throw IllegalArgumentException("Bad wallet pubkey in NWC URI.")
    if (!relay.startsWith("wss://")) throw IllegalArgumentException("NWC URI needs a wss:// relay.")
    if (!Regex("^[0-9a-f]{64}$").matches(secret)) throw IllegalArgumentException("Bad secret in NWC URI.")
    return Conn(walletPk, relay, secret)
  }

  fun connected(): Boolean = conn != null

  fun connect(uri: String): Conn {
    val c = parseNwcUri(uri)
    conn = c
    Store.setObj("xm.nwc.v1", JSONObject().put("walletPk", c.walletPk).put("relay", c.relay).put("secret", c.secret))
    listen()
    return c
  }

  fun disconnect() {
    try { unsub?.invoke() } catch (_: Exception) {}
    unsub = null; conn = null; ck = null
    Store.del("xm.nwc.v1")
  }

  /** Re-subscribe the response listener (call on boot when a session exists). */
  fun listen() {
    try { unsub?.invoke() } catch (_: Exception) {}
    val c = conn ?: return
    val sk = Hex.decode(c.secret)
    clientPk = Hex.encode(Secp.pubkey(sk))
    ck = Nip44.conversationKey(sk, c.walletPk)
    unsub = RelayPool.subscribe(
      listOf(nostrFilter(kinds = listOf(23195), tags = mapOf("p" to listOf(clientPk)), since = nowSec() - 120)),
      listOf(c.relay),
    ) { e ->
      try {
        if (e.pubkey != c.walletPk) return@subscribe
        val msg = JSONObject(Nip44.decrypt(e.content, ck ?: return@subscribe))
        val h = routeAll ?: return@subscribe
        routeAll = null
        if (msg.has("error") && !msg.isNull("error")) {
          val err = msg.opt("error")
          val em = if (err is JSONObject) err.optString("message", err.optString("code", "wallet error")) else err.toString()
          h.completeExceptionally(RuntimeException(em))
        } else {
          val r = msg.opt("result")
          h.complete(if (r is JSONObject) r else JSONObject().put("value", r?.toString() ?: ""))
        }
      } catch (_: Exception) {}
    }
  }

  suspend fun call(method: String, params: JSONObject = JSONObject()): JSONObject {
    val c = conn ?: throw IllegalStateException("No NWC wallet connected.")
    return chain.withLock {
      val k = ck ?: throw IllegalStateException("No NWC wallet connected.")
      val d = CompletableDeferred<JSONObject>()
      routeAll = d
      try {
        val sk = Hex.decode(c.secret)
        val content = Nip44.encrypt(JSONObject().put("method", method).put("params", params).toString(), k, random32())
        val signed = Ev.finalize(UnsignedEvent(23194, content, listOf(listOf("p", c.walletPk))), sk)
        RelayPool.publish(signed, listOf(c.relay))
      } catch (e: Exception) {
        if (routeAll === d) routeAll = null
        throw e
      }
      val res = withTimeoutOrNull(30000) { d.await() }
      if (routeAll === d) routeAll = null
      res ?: throw RuntimeException("Wallet timed out.")
    }
  }

  suspend fun getBalanceMsats(): Long = call("get_balance").optLong("balance", 0)
  suspend fun payInvoice(invoice: String): JSONObject = call("pay_invoice", JSONObject().put("invoice", invoice))
  suspend fun makeInvoice(amountMsats: Long, memo: String = "XM Arcade"): JSONObject =
    call("make_invoice", JSONObject().put("amount", amountMsats).put("default_memo", memo))
  suspend fun lookupInvoice(invoice: String): JSONObject = call("lookup_invoice", JSONObject().put("invoice", invoice))
}

/** BOLT11 amount from HRP only (wallet validates the rest). Returns msats. */
fun bolt11Msats(inv: String): Long {
  val m = Regex("^ln([a-z]+?)(\\d+)([pnum]?)1", RegexOption.IGNORE_CASE).find(inv.trim()) ?: return 0
  val n = m.groupValues[2].toLongOrNull() ?: return 0
  val mult = when (m.groupValues[3].lowercase()) {
    "p" -> 0.1; "n" -> 100.0; "u" -> 1e5; "m" -> 1e8; else -> 1e11
  }
  return ((n * mult) / 10).toLong()
}

fun lnurlDecode(lnurl: String): String {
  val (_, words) = Bech32.decode(lnurl)
  return Bech32.convertBits(words, 5, 8, false).toString(Charsets.UTF_8)
}

suspend fun ludToPayUrl(lud16: String, lud06: String): String {
  if (lud16.contains("@")) {
    val (u, d) = lud16.split("@", limit = 2)
    return "https://$d/.well-known/lnurlp/$u"
  }
  if (lud06.isNotEmpty()) return lnurlDecode(lud06)
  throw IllegalArgumentException("No Lightning address on this profile.")
}

/** NIP-57 zap via LNURL-pay + NWC. */
suspend fun zapProfile(toPk: String, toLud16: String, toLud06: String, sats: Long, comment: String = "", targetEvent: String? = null): JSONObject =
  withContext(Dispatchers.IO) {
    if (!Nwc.connected()) throw IllegalStateException("Connect an NWC wallet first (Wallet → sats).")
    val payUrl = ludToPayUrl(toLud16, toLud06)
    val lnurl = Net.getJson(payUrl)
    if (!lnurl.optBoolean("allowsNostr") || lnurl.optString("nostrPubkey", "").isEmpty())
      throw RuntimeException("That address doesn't accept Nostr zaps.")
    val msats = sats * 1000
    if (lnurl.has("minSendable") && msats < lnurl.getLong("minSendable"))
      throw RuntimeException("Minimum is ${Math.ceil(lnurl.getLong("minSendable") / 1000.0).toLong()} sats.")
    if (lnurl.has("maxSendable") && msats > lnurl.getLong("maxSendable"))
      throw RuntimeException("Maximum is ${Math.floor(lnurl.getLong("maxSendable") / 1000.0).toLong()} sats.")
    val tpl = Nip57.zapRequest(toPk, targetEvent, msats, RelayPool.relays.take(4), comment)
    val signed = Signer.signEvent(tpl.kind, tpl.content, tpl.tags)
    val cb = lnurl.getString("callback")
    val sep = if (cb.contains("?")) "&" else "?"
    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    var url = "$cb${sep}amount=$msats&nostr=${enc(Ev.toJsonArray(signed))}"
    if (comment.isNotEmpty()) url += "&comment=${enc(comment)}"
    val inv = Net.getJson(url)
    val pr = inv.optString("pr", "")
    if (pr.isEmpty()) throw RuntimeException("LNURL server didn't return an invoice.")
    Nwc.payInvoice(pr)
  }

/** Monero wallet-RPC adapter. Mirrors wallets.js. */
object Xmr {
  data class Cfg(var url: String, var user: String, var pass: String, var label: String)

  var cfg: Cfg
    private set
  var mode: String? = null // 'rpc' | 'external' | null

  init {
    val o = try { Store.getObj("xm.xmr.v1") } catch (_: Exception) { null }
    cfg = Cfg(o?.optString("url", "")?.ifEmpty { "http://127.0.0.1:18082/json_rpc" } ?: "http://127.0.0.1:18082/json_rpc",
      o?.optString("user", "") ?: "", o?.optString("pass", "") ?: "", o?.optString("label", "") ?: "")
    mode = Store.getStr("xm.xmr.mode.v1", "").ifEmpty { null }
  }

  fun saveCfg() {
    Store.setObj("xm.xmr.v1", JSONObject().put("url", cfg.url).put("user", cfg.user).put("pass", cfg.pass).put("label", cfg.label))
  }
  fun saveMode() { if (mode == null) Store.del("xm.xmr.mode.v1") else Store.setStr("xm.xmr.mode.v1", mode!!) }

  val ADDR = Regex("^4[0-9AB][1-9A-HJ-NP-Za-km-z]{93}$")
  fun piconeroToXmr(p: Long): Double = p / 1e12
  fun xmrToPiconero(x: Double): String = (Math.round(x * 1e12)).toString()

  suspend fun rpc(method: String, params: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
    val headers = mutableMapOf<String, String>()
    if (cfg.user.isNotEmpty()) {
      val basic = JBase64.getEncoder().encodeToString("${cfg.user}:${cfg.pass}".toByteArray())
      headers["Authorization"] = "Basic $basic"
    }
    val body = JSONObject().put("jsonrpc", "2.0").put("id", "xm").put("method", method).put("params", params)
    val j = try {
      Net.postJson(cfg.url, body, headers)
    } catch (e: Exception) {
      throw RuntimeException("RPC unreachable — is monero-wallet-rpc running with --rpc-bind-ip reachable?")
    }
    if (j.has("error") && !j.isNull("error")) {
      val err = j.opt("error")
      throw RuntimeException(if (err is JSONObject) err.optString("message", "wallet RPC error") else err.toString())
    }
    j.optJSONObject("result") ?: JSONObject()
  }

  suspend fun status(): JSONObject = rpc("get_version")
  suspend fun address(account: Int = 0): JSONObject = rpc("get_address", JSONObject().put("account_index", account))
  suspend fun balance(account: Int = 0): JSONObject = rpc("get_balance", JSONObject().put("account_index", account))
  suspend fun transfer(address: String, amountXmr: Double, account: Int = 0, priority: Int = 1): JSONObject =
    rpc("transfer", JSONObject().put("account_index", account)
      .put("destinations", JSONArray().put(JSONObject().put("amount", xmrToPiconero(amountXmr)).put("address", address)))
      .put("priority", priority).put("do_not_relay", false))
  suspend fun createWallet(filename: String, password: String, language: String = "English"): JSONObject =
    rpc("create_wallet", JSONObject().put("filename", filename).put("password", password).put("language", language))
  suspend fun openWallet(filename: String, password: String = ""): JSONObject =
    rpc("open_wallet", JSONObject().put("filename", filename).put("password", password))
  suspend fun tx(txid: String): JSONObject = rpc("get_transfer_by_txid", JSONObject().put("txid", txid))

  fun paymentUri(to: String, amt: Double): String = "monero:$to?tx_amount=$amt"
}
