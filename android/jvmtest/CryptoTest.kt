import com.xmarcade.app.core.*
import org.json.JSONObject
import java.io.File

var PASS = 0
fun check(name: String, cond: Boolean, extra: String = "") {
  if (!cond) throw AssertionError("FAIL: $name $extra")
  PASS++
  println("ok: $name")
}
fun throws(name: String, fn: () -> Unit) {
  try { fn(); throw AssertionError("FAIL (no throw): $name") }
  catch (e: AssertionError) { throw e }
  catch (_: Exception) { PASS++; println("ok: $name (throws)") }
}

fun main(args: Array<String>) {
  val fix = JSONObject(File(args.getOrElse(0) { "/tmp/nostr-fixtures.json" }).readText())
  val SK1 = fix.getString("SK1"); val SK2 = fix.getString("SK2")
  val sk1 = Hex.decode(SK1); val sk2 = Hex.decode(SK2)
  val PUB1 = fix.getString("PUB1"); val PUB2 = fix.getString("PUB2")

  // --- primitives ---
  check("hex rt", Hex.encode(Hex.decode("00ff10ab")) == "00ff10ab")
  check("b64 Man", B64.encode("Man".toByteArray()) == "TWFu")
  check("b64 Ma", B64.encode("Ma".toByteArray()) == "TWE=")
  check("b64 M", B64.encode("M".toByteArray()) == "TQ==")
  check("b64 rt", B64.decode(B64.encode(ByteArray(100) { it.toByte() })).toList() == (0..99).map { it.toByte() })
  check("sha256 abc", Hex.encode(Sha.sha256("abc".toByteArray())) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
  check("hmac rfc4231#1", Hex.encode(Sha.hmacSha256(ByteArray(20) { 0x0b }, "Hi There".toByteArray())) == "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7")

  // --- secp256k1 / BIP-340 ---
  check("pubkey sk1", Hex.encode(Secp.pubkey(sk1)) == PUB1)
  check("pubkey sk2", Hex.encode(Secp.pubkey(sk2)) == PUB2)
  check("ecdh symmetry", Hex.encode(Secp.ecdhX(sk1, Hex.decode(PUB2))) == Hex.encode(Secp.ecdhX(sk2, Hex.decode(PUB1))))
  throws("ecdh bad pubkey") { Secp.ecdhX(sk1, Hex.decode("ff".repeat(32))) }
  val msg = Hex.decode(fix.getJSONObject("event").getString("id"))
  val jsSig = Hex.decode(fix.getJSONObject("event").getString("sig"))
  check("verify JS sig", Secp.verify(msg, jsSig, Hex.decode(PUB1)))
  val ktSig = Secp.sign(msg, sk1)
  check("verify KT sig", Secp.verify(msg, ktSig, Hex.decode(PUB1)))
  check("verify tampered", !Secp.verify(msg, ktSig.copyOf().also { it[0] = (it[0] + 1).toByte() }, Hex.decode(PUB1)))
  check("verify wrong key", !Secp.verify(msg, ktSig, Hex.decode(PUB2)))

  // --- events ---
  val tev = fix.getJSONObject("event").getJSONObject("tpl")
  val tags = mutableListOf<List<String>>()
  val ta = tev.getJSONArray("tags")
  for (i in 0 until ta.length()) tags.add((0 until ta.getJSONArray(i).length()).map { ta.getJSONArray(i).getString(it) })
  val tpl = UnsignedEvent(tev.getInt("kind"), tev.getString("content"), tags, tev.getLong("created_at"))
  val ev = Ev.finalize(tpl, sk1)
  check("event id == JS", ev.id == fix.getJSONObject("event").getString("id"))
  check("event verify", Ev.verify(ev))
  check("event verify JS sig", Ev.verify(ev.copy(sig = fix.getJSONObject("event").getString("sig"))))
  check("event tamper", !Ev.verify(ev.copy(content = ev.content + "!")))
  check("event json rt", Ev.fromJson(Ev.toJson(ev)) == ev)
  check("canonical escape", Ev.canonical("p", 1, 1, listOf(listOf("a", "x\"y\né")), "q\\") ==
    "[0,\"p\",1,1,[[\"a\",\"x\\\"y\\né\"]],\"q\\\\\"]")

  // --- NIP-44 ---
  val ck = Hex.decode(fix.getString("convKey"))
  check("convkey == JS", Hex.encode(Nip44.conversationKey(sk1, PUB2)) == fix.getString("convKey"))
  val ckDoc = Hex.decode(fix.getString("convKeyDoc"))
  check("convkey doc vector", Hex.encode(ckDoc) == "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d")
  val nonce01 = ByteArray(32).also { it[31] = 1 }
  check("nip44 doc payload", Nip44.encrypt("a", ckDoc, nonce01) ==
    "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb")
  check("nip44 decrypt JS payload", Nip44.decrypt(fix.getString("nip44cipher"), ck) == "cross-decrypt me ✉️")
  check("nip44 roundtrip", Nip44.decrypt(Nip44.encryptRandom("round ✓ trip 🔥 " + "z".repeat(500), ck), ck).startsWith("round ✓"))
  check("pad len 1", Nip44.calcPaddedLen(1) == 32L)
  check("pad len 32", Nip44.calcPaddedLen(32) == 32L)
  check("pad len 33", Nip44.calcPaddedLen(33) == 64L)
  // extended-prefix checksum vectors from NIP-44 doc
  val extCases = listOf(
    Triple(65535, "6e1bebca6a8229364a162a72ef064826c4cd7457bf54f190ef782bd9deff3e42", "6d8c2810d1e870fbaa1f0a0937126cca837a15f9260e27060c331d70a3c0bc84"),
    Triple(65536, "bf718b6f653bebc184e1479f1935b8da974d701b893afcf49e701f3e2f9f9c5a", "b7b4edb36ba92e267d322d56d9aebc22e7fa96ff52e3c12adc07f07a43cbc616"),
    Triple(65537, "008ffc88d3c96a9f307524eb361e47c5222a887fc45fa0c1fb8d429c5c23b430", "eeb7c7c5373894ea2c1547cfd3ccb15d5a0b2d619da852e5c79df792dcc9e435"),
  )
  for ((len, ptHash, payHash) in extCases) {
    val pt = "a".repeat(len)
    check("ext pt $len", Hex.encode(Sha.sha256(pt.toByteArray())) == ptHash)
    val pay = Nip44.encrypt(pt, ckDoc, nonce01)
    check("ext payload $len", Hex.encode(Sha.sha256(pay.toByteArray())) == payHash)
    check("ext decrypt $len", Nip44.decrypt(pay, ckDoc).length == len)
  }
  throws("nip44 short") { Nip44.decrypt("x".repeat(131), ck) }
  throws("nip44 hash-flag") { Nip44.decrypt("#" + "x".repeat(200), ck) }
  throws("nip44 bad version") {
    val raw = B64.decode(Nip44.encrypt("hi there, this pads out fine?", ck, nonce01))
    raw[0] = 3
    Nip44.decrypt(B64.encode(raw), ck)
  }
  throws("nip44 bad mac") {
    val p = Nip44.encrypt("tamper me please, longer text", ck, nonce01)
    Nip44.decrypt(p.dropLast(2) + if (p.last() == 'A') "BB" else "AA", ck)
  }
  throws("nip44 empty plain") { Nip44.encrypt("", ck, nonce01) }

  // --- NIP-19 ---
  val n19 = fix.getJSONObject("nip19")
  check("npub doc", n19.getString("npubOfPubDoc") == "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6")
  check("npub encode", Nip19.npubEncode("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d") == n19.getString("npubOfPubDoc"))
  check("npub e47e", Nip19.npubEncode("7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e") == n19.getString("npubOfE47e"))
  check("nsec doc", Nip19.nsecEncode(sk1) == n19.getString("nsecOfSk1"))
  check("nsec decode", Hex.encode(Nip19.decode(Nip19.nsecEncode(sk1))!!.let { Hex.decode(it.dataHex) }) == SK1.lowercase())
  check("npub rt", Nip19.decode(Nip19.npubEncode(PUB1))!!.dataHex == PUB1)
  val prof = Nip19.decode(n19.getString("nprofile"))!!
  check("nprofile", prof.type == "nprofile" && prof.dataHex == "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d" &&
    prof.relays == listOf("wss://r.x.com", "wss://djbas.sadkb.com"))
  check("nprofile enc rt", Nip19.decode(Nip19.nprofileEncode(PUB1, listOf("wss://x")))!!.let { it.dataHex == PUB1 && it.relays == listOf("wss://x") })
  check("nevent enc rt", Nip19.decode(Nip19.neventEncode(ev.id, listOf("wss://y"), PUB1, 1))!!.let { it.dataHex == ev.id && it.authorHex == PUB1 && it.kind == 1 })
  check("bad bech32 null", Nip19.decode("npub1qpzry9x8gf2tvdw0s3jn54khce6mua7lmqqqxw") == null)
  check("wrong type null", Nip19.decode("notakey") == null)
  check("parseKey nsec", Nip19.parseKeyInput(n19.getString("nsecOfSk1"))!!.let { Hex.encode(it) } == SK1.lowercase())
  check("parseKey hex", Nip19.parseKeyInput(SK1)!!.let { Hex.encode(it) } == SK1.lowercase())
  check("parseKey bad", Nip19.parseKeyInput("hello") == null)

  // --- NIP-57 ---
  val zap = Nip57.zapRequest(PUB2, "ev".padEnd(64, '0'), 210000, listOf("wss://a", "wss://b"), "hi")
  val fz = fix.getJSONObject("zap")
  check("zap kind/content", zap.kind == 9734 && zap.content == "hi")
  val fzt = mutableListOf<List<String>>()
  val za = fz.getJSONArray("tags")
  for (i in 0 until za.length()) fzt.add((0 until za.getJSONArray(i).length()).map { za.getJSONArray(i).getString(it) })
  check("zap tags == JS", zap.tags == fzt)

  // --- NIP-59 ---
  val sealJson = fix.getJSONObject("seal").toString()
  val seal = Ev.fromJson(JSONObject(sealJson))!!
  check("seal kind", seal.kind == 1059)
  val rumor = Nip59.unwrap(seal, sk2)!!
  check("unwrap rumor", rumor.kind == 1 && rumor.pubkey == PUB1 && rumor.content.contains("concord-key"))
  check("unwrap wrong key", Nip59.unwrap(seal, sk1) == null)
  val ktWrap = Nip59.wrap(1, "kotlin wrap ✓", listOf(listOf("p", PUB2)), sk1, PUB2)
  check("wrap kind", ktWrap.kind == 1059 && Ev.verify(ktWrap))
  check("wrap self-rt", Nip59.unwrap(ktWrap, sk2)!!.content == "kotlin wrap ✓")

  // --- models ---
  val sev = NostrEvent("a".repeat(64), PUB1, 1700000000, 22,
    listOf(listOf("url", "https://x/v.mp4"), listOf("title", "T"), listOf("t", "WebXDC"), listOf("imeta", "m video/mp4", "duration 12.5")),
    "hello", "s".repeat(64))
  val sh = parseShort(sev)
  check("parseShort", sh.url == "https://x/v.mp4" && sh.title == "T" && sh.tags == listOf("webxdc") && sh.dur == 12.5)
  val tev2 = NostrEvent("b".repeat(64), PUB1, 1700000000, 1, listOf(listOf("title", "Song")), "listen https://x/a.mp3", "s".repeat(64))
  check("parseTrack", parseTrack(tev2)!!.url == "https://x/a.mp3")
  check("parseTrack none", parseTrack(tev2.copy(content = "no audio")) == null)
  check("appCard", parseAppCard("play {\"type\":\"webxdc\",\"title\":\"G\",\"url\":\"https://g\",\"file\":\"g.xdc\"}")!!.title == "G")
  check("appCard none", parseAppCard("nothing") == null)
  check("profile", parseProfile(PUB1, "{\"display_name\":\"No\",\"lud16\":\"a@b\"}", 9).let { it.displayName == "No" && it.lud16 == "a@b" })

  // --- reverse file for node (JS decrypts/verifies OUR output) ---
  val rev = JSONObject()
    .put("nip44kt", Nip44.encryptRandom("kotlin→node ✓", ck))
    .put("sealKt", Ev.toJsonArray(ktWrap))
    .put("ktSig", Hex.encode(ktSig))
  File("/tmp/kt-reverse.json").writeText(rev.toString())
  println("reverse file written")
  println("ALL $PASS CHECKS PASSED")
}
