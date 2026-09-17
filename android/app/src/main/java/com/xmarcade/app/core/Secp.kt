package com.xmarcade.app.core

import java.math.BigInteger

/** secp256k1 (affine BigInteger math) + BIP-340 Schnorr. Pure JVM. */
object Secp {
  val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
  val N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
  private val GX = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
  private val GY = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
  private val G = Pt(GX, GY)
  private val TWO = BigInteger.valueOf(2)
  private val THREE = BigInteger.valueOf(3)

  data class Pt(val x: BigInteger, val y: BigInteger)

  private fun add(p: Pt?, q: Pt?): Pt? {
    if (p == null) return q
    if (q == null) return p
    val lam: BigInteger
    if (p.x == q.x) {
      if (p.y != q.y || p.y.signum() == 0) return null
      lam = p.x.pow(2).multiply(THREE).multiply(p.y.multiply(TWO).mod(P).modInverse(P)).mod(P)
    } else {
      lam = q.y.subtract(p.y).mod(P).multiply(q.x.subtract(p.x).mod(P).modInverse(P)).mod(P)
    }
    val xr = lam.pow(2).subtract(p.x).subtract(q.x).mod(P)
    val yr = lam.multiply(p.x.subtract(xr)).subtract(p.y).mod(P)
    return Pt(xr, yr)
  }

  fun mul(k: BigInteger, p: Pt = G): Pt? {
    var n = k.mod(N)
    var r: Pt? = null
    var a: Pt? = p
    while (n.signum() > 0) {
      if (n.testBit(0)) r = add(r, a)
      a = add(a, a)
      n = n.shiftRight(1)
    }
    return r
  }

  fun bytes32(n: BigInteger): ByteArray {
    val raw = n.toByteArray()
    val out = ByteArray(32)
    if (raw.size <= 32) raw.copyInto(out, 32 - raw.size)
    else raw.copyInto(out, 0, raw.size - 32, raw.size)
    return out
  }
  private fun intOf(b: ByteArray): BigInteger = BigInteger(1, b)

  private fun validSk(sk: ByteArray): BigInteger {
    require(sk.size == 32) { "bad secret key length" }
    val d = intOf(sk)
    require(d.signum() > 0 && d < N) { "secret key out of range" }
    return d
  }

  /** X-only public key (Nostr/BIP-340 format). */
  fun pubkey(sk: ByteArray): ByteArray = bytes32(mul(validSk(sk))!!.x)

  /** Lift an x-only pubkey to the even-Y curve point, or null if invalid. */
  fun liftX(xBytes: ByteArray): Pt? {
    if (xBytes.size != 32) return null
    val x = intOf(xBytes)
    if (x >= P) return null
    val y2 = x.pow(3).add(BigInteger.valueOf(7)).mod(P)
    var y = y2.modPow(P.add(BigInteger.ONE).shiftRight(2), P)
    if (y.pow(2).mod(P) != y2) return null
    if (y.testBit(0)) y = P.subtract(y)
    return Pt(x, y)
  }

  /** Raw ECDH shared x-coordinate (unhashed, per NIP-44). */
  fun ecdhX(sk: ByteArray, pubX: ByteArray): ByteArray {
    val p = liftX(pubX) ?: throw IllegalArgumentException("invalid public key")
    val s = mul(validSk(sk), p) ?: throw IllegalArgumentException("ECDH failed")
    return bytes32(s.x)
  }

  private fun taggedHash(tag: String, vararg msgs: ByteArray): ByteArray {
    val h = Sha.sha256(tag.toByteArray(Charsets.UTF_8))
    return Sha.sha256(h, h, *msgs)
  }

  /** BIP-340 sign. Deterministic (zero aux) unless auxRand given. */
  fun sign(msg32: ByteArray, sk32: ByteArray, aux32: ByteArray = ByteArray(32)): ByteArray {
    require(msg32.size == 32 && aux32.size == 32) { "bad sign input" }
    var d = validSk(sk32)
    val p = mul(d)!!
    if (p.y.testBit(0)) d = N.subtract(d)
    val t = bytes32(d.xor(intOf(taggedHash("BIP0340/aux", aux32))))
    var k0 = intOf(taggedHash("BIP0340/nonce", t, bytes32(p.x), msg32)).mod(N)
    require(k0.signum() != 0) { "nonce failure" }
    val rPt = mul(k0)!!
    val k = if (rPt.y.testBit(0)) N.subtract(k0) else k0
    val e = intOf(taggedHash("BIP0340/challenge", bytes32(rPt.x), bytes32(p.x), msg32)).mod(N)
    val s = k.add(e.multiply(d)).mod(N)
    require(s.signum() != 0) { "sig failure" }
    return bytes32(rPt.x) + bytes32(s)
  }

  /** BIP-340 verify. Never throws on hostile input — returns false. */
  fun verify(msg32: ByteArray, sig64: ByteArray, pubX32: ByteArray): Boolean {
    try {
      if (msg32.size != 32 || sig64.size != 64 || pubX32.size != 32) return false
      val r = intOf(sig64.copyOfRange(0, 32))
      val s = intOf(sig64.copyOfRange(32, 64))
      if (r >= P || s >= N) return false
      val p = liftX(pubX32) ?: return false
      val e = intOf(taggedHash("BIP0340/challenge", sig64.copyOfRange(0, 32), pubX32, msg32)).mod(N)
      val rPt = add(mul(s)!!, mul(N.subtract(e), p) ?: return false) ?: return false
      if (rPt.y.testBit(0)) return false
      return rPt.x == r
    } catch (_: Exception) { return false }
  }
}
