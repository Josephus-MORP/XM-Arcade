package com.xmarcade.utils

import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom

object NostrCrypto {
    private val secp = Secp256k1.get()

    fun generatePrivateKey(): ByteArray {
        val rng = SecureRandom()
        var key: ByteArray
        do {
            key = ByteArray(32).also { rng.nextBytes(it) }
        } while (!isValidPrivateKey(key))
        return key
    }

    private fun isValidPrivateKey(key: ByteArray): Boolean {
        if (key.size != 32) return false
        if (key.all { it == 0.toByte() }) return false
        return try {
            secp.pubkeyCreate(key) != null
        } catch (_: Exception) { false }
    }

    fun pubkeyFromPrivate(priv: ByteArray): ByteArray = secp.pubkeyCreate(priv)

    fun pubkeyHex(privHex: String): String {
        val priv = hexToBytes(privHex)
        return bytesToHex(pubkeyFromPrivate(priv))
    }

    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun hexToBytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // Bech32 encoding for npub / nsec (simplified placeholder – production uses bitcoinj/bech32)
    fun toNpub(pubHex: String): String = "npub1${pubHex.take(10)}..." // placeholder; replace with real bech32
    fun toNsec(privHex: String): String = "nsec1${privHex.take(10)}..."
}
