package com.xmarcade.app.core

import java.security.MessageDigest

/** NIP-44 v2 encryption. Pure JVM. Spec: secp256k1 ECDH + HKDF + ChaCha20 + HMAC. */
object Nip44 {
  const val MIN_PLAINTEXT = 1L
  const val MAX_PLAINTEXT = 4294967295L
  const val EXT_THRESHOLD = 65536L
  private val SALT = "nip44-v2".toByteArray(Charsets.UTF_8)

  fun calcPaddedLen(n: Long): Long {
    if (n <= 32) return 32
    val nextPower = 1L shl (64 - java.lang.Long.numberOfLeadingZeros(n - 1))
    val chunk = if (nextPower <= 256) 32L else nextPower / 8
    return chunk * ((n - 1) / chunk + 1)
  }

  fun conversationKey(sk: ByteArray, pubHex: String): ByteArray {
    val sharedX = Secp.ecdhX(sk, Hex.decode(pubHex))
    return Hkdf.extract(SALT, sharedX)
  }

  data class MsgKeys(val chachaKey: ByteArray, val chachaNonce: ByteArray, val hmacKey: ByteArray)

  fun messageKeys(ck: ByteArray, nonce: ByteArray): MsgKeys {
    require(ck.size == 32) { "invalid conversation_key length" }
    require(nonce.size == 32) { "invalid nonce length" }
    val k = Hkdf.expand(ck, nonce, 76)
    return MsgKeys(k.copyOfRange(0, 32), k.copyOfRange(32, 44), k.copyOfRange(44, 76))
  }

  fun pad(plain: ByteArray): ByteArray {
    val n = plain.size.toLong()
    require(n in MIN_PLAINTEXT..MAX_PLAINTEXT) { "invalid plaintext length" }
    val prefix = if (n >= EXT_THRESHOLD) {
      byteArrayOf(0, 0, (n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    } else byteArrayOf((n shr 8).toByte(), n.toByte())
    val total = calcPaddedLen(n).toInt()
    val out = ByteArray(prefix.size + total)
    prefix.copyInto(out, 0)
    plain.copyInto(out, prefix.size)
    return out
  }

  fun unpad(padded: ByteArray): ByteArray {
    require(padded.size >= 2) { "invalid padding" }
    val firstTwo = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
    val (unpaddedLen, prefixLen) = if (firstTwo == 0) {
      require(padded.size >= 6) { "invalid padding" }
      val u = ((padded[2].toLong() and 0xff) shl 24) or ((padded[3].toLong() and 0xff) shl 16) or
        ((padded[4].toLong() and 0xff) shl 8) or (padded[5].toLong() and 0xff)
      require(u >= EXT_THRESHOLD) { "invalid padding" }
      u to 6
    } else firstTwo.toLong() to 2
    require(unpaddedLen != 0L) { "invalid padding" }
    require(prefixLen + unpaddedLen <= padded.size) { "invalid padding" }
    require(padded.size.toLong() == prefixLen + calcPaddedLen(unpaddedLen)) { "invalid padding" }
    return padded.copyOfRange(prefixLen, (prefixLen + unpaddedLen).toInt())
  }

  /** Deterministic encrypt with explicit nonce (test vectors use this). */
  fun encrypt(plain: String, ck: ByteArray, nonce: ByteArray): String {
    val k = messageKeys(ck, nonce)
    val ct = ChaCha20.xor(k.chachaKey, k.chachaNonce, pad(plain.toByteArray(Charsets.UTF_8)))
    val mac = Sha.hmacSha256(k.hmacKey, nonce + ct)
    return B64.encode(byteArrayOf(2) + nonce + ct + mac)
  }

  fun encryptRandom(plain: String, ck: ByteArray): String = encrypt(plain, ck, random32())

  fun decrypt(payload: String, ck: ByteArray): String {
    require(payload.isNotEmpty() && payload[0] != '#') { "unknown version" }
    require(payload.length >= 132) { "invalid payload size" }
    val data = B64.decode(payload)
    require(data.size >= 99) { "invalid data size" }
    require(data[0].toInt() == 2) { "unknown version ${data[0]}" }
    val nonce = data.copyOfRange(1, 33)
    val ct = data.copyOfRange(33, data.size - 32)
    val mac = data.copyOfRange(data.size - 32, data.size)
    val k = messageKeys(ck, nonce)
    val calc = Sha.hmacSha256(k.hmacKey, nonce + ct)
    require(MessageDigest.isEqual(calc, mac)) { "invalid MAC" }
    return unpad(ChaCha20.xor(k.chachaKey, k.chachaNonce, ct)).toString(Charsets.UTF_8)
  }
}
