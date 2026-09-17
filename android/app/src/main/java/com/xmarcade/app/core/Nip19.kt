package com.xmarcade.app.core

/** Bech32 + NIP-19 entities. Pure JVM. */
object Bech32 {
  const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
  private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

  fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): ByteArray {
    var acc = 0; var bits = 0
    val out = ArrayList<Byte>()
    for (b in data) {
      acc = (acc shl from) or (b.toInt() and ((1 shl from) - 1)); bits += from
      while (bits >= to) { bits -= to; out.add(((acc shr bits) and ((1 shl to) - 1)).toByte()) }
    }
    if (pad && bits > 0) out.add(((acc shl (to - bits)) and ((1 shl to) - 1)).toByte())
    return out.toByteArray()
  }

  private fun polymod(values: ByteArray): Int {
    var chk = 1
    for (v in values) {
      val b = chk shr 25
      chk = ((chk and 0x1ffffff) shl 5) xor (v.toInt() and 31)
      for (i in 0..4) if ((b shr i) and 1 == 1) chk = chk xor GEN[i]
    }
    return chk
  }

  private fun hrpExpand(hrp: String): ByteArray {
    val out = ByteArray(hrp.length * 2 + 1)
    hrp.forEachIndexed { i, c -> out[i] = (c.code shr 5).toByte() }
    out[hrp.length] = 0
    hrp.forEachIndexed { i, c -> out[hrp.length + 1 + i] = (c.code and 31).toByte() }
    return out
  }

  fun encode(hrp: String, data5: ByteArray): String {
    val h = hrp.lowercase()
    val pm = polymod(hrpExpand(h) + data5 + ByteArray(6)) xor 1
    val sb = StringBuilder(h).append('1')
    data5.forEach { sb.append(CHARSET[it.toInt()]) }
    for (i in 0..5) sb.append(CHARSET[(pm shr (5 * (5 - i))) and 31])
    return sb.toString()
  }

  /** Returns (hrp, data words without checksum). Throws on any violation. */
  fun decode(str: String): Pair<String, ByteArray> {
    val s = str.trim()
    require(s.length in 8..5000 && s.none { it.code !in 33..126 }) { "bad bech32" }
    require(s == s.lowercase() || s == s.uppercase()) { "mixed case" }
    val low = s.lowercase()
    val pos = low.lastIndexOf('1')
    require(pos >= 1 && pos + 7 <= low.length) { "bad bech32 separator" }
    val hrp = low.substring(0, pos)
    val words = ByteArray(low.length - pos - 1) { i ->
      val v = CHARSET.indexOf(low[pos + 1 + i])
      require(v >= 0) { "bad bech32 char" }
      v.toByte()
    }
    require(polymod(hrpExpand(hrp) + words) == 1) { "bad bech32 checksum" }
    return hrp to words.copyOfRange(0, words.size - 6)
  }
}

object Nip19 {
  data class Decoded(
    val type: String, val dataHex: String = "",
    val relays: List<String> = emptyList(),
    val authorHex: String = "", val kind: Int = 0, val identifier: String = "",
  )

  fun npubEncode(pubHex: String): String = Bech32.encode("npub", Bech32.convertBits(Hex.decode(pubHex), 8, 5, true))
  fun nsecEncode(sk: ByteArray): String = Bech32.encode("nsec", Bech32.convertBits(sk, 8, 5, true))
  fun noteEncode(idHex: String): String = Bech32.encode("note", Bech32.convertBits(Hex.decode(idHex), 8, 5, true))

  private fun tlv(type: Int, v: ByteArray): ByteArray = byteArrayOf(type.toByte(), v.size.toByte()) + v
  fun nprofileEncode(pubHex: String, relays: List<String> = emptyList()): String {
    var b = tlv(0, Hex.decode(pubHex))
    relays.forEach { b += tlv(1, it.toByteArray(Charsets.US_ASCII)) }
    return Bech32.encode("nprofile", Bech32.convertBits(b, 8, 5, true))
  }
  fun neventEncode(idHex: String, relays: List<String> = emptyList(), authorHex: String = "", kind: Int = -1): String {
    var b = tlv(0, Hex.decode(idHex))
    relays.forEach { b += tlv(1, it.toByteArray(Charsets.US_ASCII)) }
    if (authorHex.isNotEmpty()) b += tlv(2, Hex.decode(authorHex))
    if (kind >= 0) b += tlv(3, byteArrayOf((kind shr 24).toByte(), (kind shr 16).toByte(), (kind shr 8).toByte(), kind.toByte()))
    return Bech32.encode("nevent", Bech32.convertBits(b, 8, 5, true))
  }

  fun decode(s: String): Decoded? {
    return try {
      val (hrp, words) = Bech32.decode(s)
      val raw = Bech32.convertBits(words, 5, 8, false)
      when (hrp) {
        "npub", "nsec", "note" -> {
          require(raw.size == 32) { "bad length" }
          Decoded(hrp, Hex.encode(raw))
        }
        "nprofile", "nevent", "naddr" -> {
          var special = ByteArray(0); val relays = mutableListOf<String>()
          var author = ByteArray(0); var kind = 0; var ident = ""
          var i = 0
          while (i + 2 <= raw.size) {
            val t = raw[i].toInt() and 0xff; val l = raw[i + 1].toInt() and 0xff
            if (i + 2 + l > raw.size) break
            val v = raw.copyOfRange(i + 2, i + 2 + l)
            when (t) {
              0 -> if (hrp == "naddr") ident = v.toString(Charsets.UTF_8) else special = v
              1 -> relays.add(v.toString(Charsets.US_ASCII))
              2 -> author = v
              3 -> if (v.size == 4) kind = ((v[0].toInt() and 0xff) shl 24) or ((v[1].toInt() and 0xff) shl 16) or ((v[2].toInt() and 0xff) shl 8) or (v[3].toInt() and 0xff)
            }
            i += 2 + l
          }
          Decoded(hrp, Hex.encode(special), relays, Hex.encode(author).takeIf { author.isNotEmpty() } ?: "", kind, ident)
        }
        else -> null
      }
    } catch (_: Exception) { null }
  }

  /** nsec1… or 64-char hex → secret key bytes, else null. */
  fun parseKeyInput(s: String): ByteArray? {
    val t = s.trim()
    if (t.isEmpty()) return null
    if (t.startsWith("nsec1")) {
      val d = decode(t)
      if (d != null && d.type == "nsec") return Hex.decode(d.dataHex)
      return null
    }
    if (Regex("^[0-9a-fA-F]{64}$").matches(t)) return Hex.decode(t.lowercase())
    return null
  }
}
