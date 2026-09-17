package com.xmarcade.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** NIP-01 events + NIP-57 request builder + NIP-59 gift wrap. Pure JVM (+org.json). */
data class NostrEvent(
  val id: String, val pubkey: String, val createdAt: Long, val kind: Int,
  val tags: List<List<String>>, val content: String, val sig: String,
)

data class UnsignedEvent(val kind: Int, val content: String, val tags: List<List<String>>, val createdAt: Long = nowSec())

fun nowSec(): Long = System.currentTimeMillis() / 1000

object Ev {
  private const val HEXD = "0123456789abcdef"

  /** JSON.stringify-compatible string escaping for canonical serialization. */
  fun esc(s: String): String {
    val sb = StringBuilder(s.length + 8)
    for (c in s) when (c) {
      '"' -> sb.append("\\\"")
      '\\' -> sb.append("\\\\")
      '\b' -> sb.append("\\b")
      '\u000C' -> sb.append("\\f")
      '\n' -> sb.append("\\n")
      '\r' -> sb.append("\\r")
      '\t' -> sb.append("\\t")
      else -> if (c.code < 0x20) sb.append("\\u00").append(HEXD[c.code shr 4]).append(HEXD[c.code and 15]) else sb.append(c)
    }
    return sb.toString()
  }

  fun canonical(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
    val sb = StringBuilder("[0,\"").append(pubkey).append("\",").append(createdAt).append(",").append(kind).append(",[")
    tags.forEachIndexed { i, t ->
      if (i > 0) sb.append(',')
      sb.append('[')
      t.forEachIndexed { j, x -> if (j > 0) sb.append(','); sb.append('"').append(esc(x)).append('"') }
      sb.append(']')
    }
    return sb.append("],\"").append(esc(content)).append("\"]").toString()
  }

  fun computeId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String =
    Hex.encode(Sha.sha256(canonical(pubkey, createdAt, kind, tags, content).toByteArray(Charsets.UTF_8)))

  fun finalize(t: UnsignedEvent, sk: ByteArray): NostrEvent {
    val pub = Hex.encode(Secp.pubkey(sk))
    val id = computeId(pub, t.createdAt, t.kind, t.tags, t.content)
    val sig = Hex.encode(Secp.sign(Hex.decode(id), sk))
    return NostrEvent(id, pub, t.createdAt, t.kind, t.tags, t.content, sig)
  }

  fun verify(e: NostrEvent): Boolean {
    if (!Regex("^[0-9a-f]{64}$").matches(e.id) || !Regex("^[0-9a-f]{64}$").matches(e.pubkey)) return false
    if (computeId(e.pubkey, e.createdAt, e.kind, e.tags, e.content) != e.id) return false
    return try { Secp.verify(Hex.decode(e.id), Hex.decode(e.sig), Hex.decode(e.pubkey)) } catch (_: Exception) { false }
  }

  fun toJson(e: NostrEvent): JSONObject = JSONObject()
    .put("id", e.id).put("pubkey", e.pubkey).put("created_at", e.createdAt)
    .put("kind", e.kind).put("content", e.content).put("sig", e.sig)
    .put("tags", JSONArray(e.tags.map { JSONArray(it) }))

  fun toJsonArray(e: NostrEvent): String = toJson(e).toString()

  fun fromJson(o: JSONObject): NostrEvent? {
    return try {
      val tags = mutableListOf<List<String>>()
      val ta = o.optJSONArray("tags") ?: JSONArray()
      for (i in 0 until ta.length()) {
        val row = ta.optJSONArray(i) ?: continue
        tags.add((0 until row.length()).map { row.optString(it, "") })
      }
      NostrEvent(
        o.getString("id"), o.getString("pubkey"), o.getLong("created_at"),
        o.getInt("kind"), tags, o.optString("content", ""), o.optString("sig", ""),
      )
    } catch (_: Exception) { null }
  }

  fun tagVal(e: NostrEvent, name: String): String = e.tags.firstOrNull { it.isNotEmpty() && it[0] == name }?.getOrNull(1) ?: ""
  fun tagVals(e: NostrEvent, name: String): List<String> = e.tags.filter { it.isNotEmpty() && it[0] == name }.mapNotNull { it.getOrNull(1) }
}

object Nip57 {
  /** Unsigned kind-9734 zap request (mirrors nostr-tools makeZapRequest). */
  fun zapRequest(profile: String, eventId: String?, amountMsats: Long, relays: List<String>, comment: String = ""): UnsignedEvent {
    require(amountMsats > 0) { "amount not given" }
    require(profile.isNotEmpty()) { "profile not given" }
    val tags = mutableListOf(listOf("p", profile), listOf("amount", amountMsats.toString()), listOf("relays") + relays)
    if (!eventId.isNullOrEmpty()) tags.add(listOf("e", eventId))
    return UnsignedEvent(kind = 9734, content = comment, tags = tags)
  }
}

private val FUZZ = SecureRandom()

object Nip59 {
  private fun fuzzed(): Long = nowSec() - FUZZ.nextInt(172801)

  /** Gift-wrap a rumor for one recipient (mirrors nostr-tools wrapEvent). */
  fun wrap(rumorKind: Int, rumorContent: String, rumorTags: List<List<String>>, senderSk: ByteArray, recipientPub: String): NostrEvent {
    val senderPub = Hex.encode(Secp.pubkey(senderSk))
    val rCreated = nowSec()
    val rId = Ev.computeId(senderPub, rCreated, rumorKind, rumorTags, rumorContent)
    val rumorJson = JSONObject().put("id", rId).put("pubkey", senderPub)
      .put("created_at", rCreated).put("kind", rumorKind)
      .put("tags", JSONArray(rumorTags.map { JSONArray(it) })).put("content", rumorContent).toString()
    val seal = Ev.finalize(
      UnsignedEvent(13, Nip44.encryptRandom(rumorJson, Nip44.conversationKey(senderSk, recipientPub)), emptyList(), fuzzed()),
      senderSk,
    )
    val esk = random32()
    return Ev.finalize(
      UnsignedEvent(1059, Nip44.encryptRandom(Ev.toJsonArray(seal), Nip44.conversationKey(esk, recipientPub)), listOf(listOf("p", recipientPub)), fuzzed()),
      esk,
    )
  }

  /** Unwrap a kind-1059 with the recipient secret key → rumor, or null. */
  fun unwrap(wrap: NostrEvent, recipientSk: ByteArray): NostrEvent? {
    return try {
      val recipientPub = Hex.encode(Secp.pubkey(recipientSk))
      val sealJson = Nip44.decrypt(wrap.content, Nip44.conversationKey(recipientSk, wrap.pubkey))
      val seal = Ev.fromJson(JSONObject(sealJson)) ?: return null
      val rumorJson = Nip44.decrypt(seal.content, Nip44.conversationKey(recipientSk, seal.pubkey))
      val rumor = Ev.fromJson(JSONObject(rumorJson)) ?: return null
      if (seal.pubkey != rumor.pubkey) return null
      rumor
    } catch (_: Exception) { null }
  }
}
