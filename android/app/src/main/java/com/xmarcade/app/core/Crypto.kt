package com.xmarcade.app.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Pure-JVM crypto primitives. No Android APIs — unit-tested on the JVM. */
object Hex {
  private const val D = "0123456789abcdef"
  fun encode(b: ByteArray): String {
    val sb = StringBuilder(b.size * 2)
    for (x in b) { sb.append(D[(x.toInt() shr 4) and 15]); sb.append(D[x.toInt() and 15]) }
    return sb.toString()
  }
  fun decode(s: String): ByteArray {
    val t = s.trim()
    require(t.length % 2 == 0 && t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "bad hex" }
    return ByteArray(t.length / 2) { i ->
      ((t[i * 2].digitToInt(16) shl 4) or t[i * 2 + 1].digitToInt(16)).toByte()
    }
  }
}

object B64 {
  private const val E = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  private val DV = IntArray(128) { -1 }.also { for (i in E.indices) it[E[i].code] = i }
  fun encode(b: ByteArray): String {
    val sb = StringBuilder(((b.size + 2) / 3) * 4)
    var i = 0
    while (i < b.size) {
      val n = minOf(3, b.size - i)
      val v = ((b[i].toInt() and 0xff) shl 16) or
        ((if (n > 1) b[i + 1].toInt() and 0xff else 0) shl 8) or
        (if (n > 2) b[i + 2].toInt() and 0xff else 0)
      sb.append(E[(v shr 18) and 63]).append(E[(v shr 12) and 63])
      sb.append(if (n > 1) E[(v shr 6) and 63] else '=')
      sb.append(if (n > 2) E[v and 63] else '=')
      i += 3
    }
    return sb.toString()
  }
  fun decode(s: String): ByteArray {
    val t = s.trim()
    require(t.isNotEmpty() && t.length % 4 == 0) { "bad base64 length" }
    val out = ByteArray((t.length / 4) * 3)
    var o = 0
    var i = 0
    while (i < t.length) {
      val a = dv(t[i]); val b = dv(t[i + 1])
      val c = if (t[i + 2] == '=') 0 else dv(t[i + 2])
      val d = if (t[i + 3] == '=') 0 else dv(t[i + 3])
      require(a >= 0 && b >= 0 && (t[i + 2] == '=' || c >= 0) && (t[i + 3] == '=' || d >= 0)) { "bad base64 char" }
      val v = (a shl 18) or (b shl 12) or (c shl 6) or d
      out[o++] = (v shr 16).toByte()
      if (t[i + 2] != '=') out[o++] = (v shr 8).toByte()
      if (t[i + 3] != '=') out[o++] = v.toByte()
      i += 4
    }
    return out.copyOf(o)
  }
  private fun dv(c: Char): Int = if (c.code < 128) DV[c.code] else -1
}

object Sha {
  fun sha256(vararg parts: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    parts.forEach { md.update(it) }
    return md.digest()
  }
  fun hmacSha256(key: ByteArray, msg: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(msg)
  }
}

object Hkdf {
  /** RFC 5869 extract: PRK = HMAC-SHA256(key=salt, msg=IKM). */
  fun extract(salt: ByteArray, ikm: ByteArray): ByteArray = Sha.hmacSha256(salt, ikm)
  /** RFC 5869 expand. */
  fun expand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
    val out = ByteArray(len)
    var prev = ByteArray(0)
    var o = 0
    var ctr = 1
    while (o < len) {
      val t = Sha.hmacSha256(prk, prev + info + ctr.toByte())
      val n = minOf(t.size, len - o)
      t.copyInto(out, o, 0, n)
      o += n; prev = t; ctr++
    }
    return out
  }
}

/** ChaCha20 stream cipher, RFC 8439 (32-byte key, 12-byte nonce, counter 0). */
object ChaCha20 {
  private fun rotl(v: Int, n: Int) = (v shl n) or (v ushr (32 - n))
  private fun qr(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
    x[a] += x[b]; x[d] = rotl(x[d] xor x[a], 16)
    x[c] += x[d]; x[b] = rotl(x[b] xor x[c], 12)
    x[a] += x[b]; x[d] = rotl(x[d] xor x[a], 8)
    x[c] += x[d]; x[b] = rotl(x[b] xor x[c], 7)
  }
  private fun le(b: ByteArray, o: Int): Int =
    (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
      ((b[o + 2].toInt() and 0xff) shl 16) or (b[o + 3].toInt() shl 24)

  fun xor(key: ByteArray, nonce12: ByteArray, data: ByteArray, counter: Int = 0): ByteArray {
    require(key.size == 32 && nonce12.size == 12) { "bad chacha key/nonce" }
    val out = ByteArray(data.size)
    var ctr = counter
    var off = 0
    while (off < data.size) {
      val s = IntArray(16)
      s[0] = 0x61707865; s[1] = 0x3320646e; s[2] = 0x79622d32; s[3] = 0x6b206574
      for (i in 0..7) s[4 + i] = le(key, i * 4)
      s[12] = ctr
      for (i in 0..2) s[13 + i] = le(nonce12, i * 4)
      val w = s.copyOf()
      repeat(10) {
        qr(w, 0, 4, 8, 12); qr(w, 1, 5, 9, 13); qr(w, 2, 6, 10, 14); qr(w, 3, 7, 11, 15)
        qr(w, 0, 5, 10, 15); qr(w, 1, 6, 11, 12); qr(w, 2, 7, 8, 13); qr(w, 3, 4, 9, 14)
      }
      for (i in 0..15) w[i] += s[i]
      val n = minOf(64, data.size - off)
      for (i in 0 until n) {
        val ks = ((w[i shr 2] ushr ((i and 3) * 8)) and 0xff).toByte()
        out[off + i] = (data[off + i].toInt() xor ks.toInt()).toByte()
      }
      off += n; ctr++
    }
    return out
  }
}

private val CSPRNG = SecureRandom()
fun random32(): ByteArray = ByteArray(32).also { CSPRNG.nextBytes(it) }
