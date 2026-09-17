// Generate cross-validation fixtures from the SHIPPED crypto (vendored nostr-tools).
// The Kotlin port must reproduce / interoperate with every value here.
import * as nt from "../js/vendor/nostr-tools.mjs";
import { writeFileSync } from "fs";

const hx = (b) => Buffer.from(b).toString("hex");
const SK1 = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"; // NIP-19 nsec example
const SK2 = "0000000000000000000000000000000000000000000000000000000000000002";
const sk1 = Buffer.from(SK1, "hex");
const sk2 = Buffer.from(SK2, "hex");
const PUB1 = nt.getPublicKey(sk1);
const PUB2 = nt.getPublicKey(sk2);

const tpl = { kind: 1, created_at: 1700000000, tags: [["t", "webxdc"], ["r", "https://x"]], content: "hello 🔥" };
const ev = nt.finalizeEvent(tpl, sk1);

const ck = nt.nip44.v2.utils.getConversationKey(sk1, PUB2);
const ckHex = hx(ck);
// NIP-44 doc vector uses sec1=01/sec2=02; also verify ours against the doc's:
const ckDoc = hx(nt.nip44.v2.utils.getConversationKey(Buffer.alloc(32, 0).fill(1, 31), nt.getPublicKey(Buffer.alloc(32, 0).fill(2, 31))));

const nip44cipher = nt.nip44.v2.encrypt("cross-decrypt me ✉️", ck); // random nonce; Kotlin must decrypt
const seal = nt.nip59.wrapEvent(
  { kind: 1, created_at: 1700000001, pubkey: PUB1, tags: [["p", PUB2]], content: JSON.stringify({ app: "xm-arcade", type: "concord-key", channel: "c", key: "ab".repeat(32) }) },
  sk1, PUB2,
);
const zap = nt.nip57.makeZapRequest({ profile: PUB2, event: "ev".padEnd(64, "0"), amount: 210000, relays: ["wss://a", "wss://b"], comment: "hi" });

const fix = {
  SK1, SK2, PUB1, PUB2,
  event: { tpl, id: ev.id, sig: ev.sig },
  convKey: ckHex, convKeyDoc: ckDoc,
  nip44cipher,
  seal,
  zap: { kind: zap.kind, content: zap.content, tags: zap.tags },
  nip19: {
    npubOfPubDoc: nt.nip19.npubEncode("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"),
    npubOfE47e: nt.nip19.npubEncode("7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"),
    nsecOfSk1: nt.nip19.nsecEncode(sk1),
    nprofile: "nprofile1qqsrhuxx8l9ex335q7he0f09aej04zpazpl0ne2cgukyawd24mayt8gpp4mhxue69uhhytnc9e3k7mgpz4mhxue69uhkg6nzv9ejuumpv34kytnrdaksjlyr9p",
  },
};
writeFileSync("/tmp/nostr-fixtures.json", JSON.stringify(fix, null, 1));
console.log("fixtures: ok", "PUB1=" + PUB1.slice(0, 12) + "…", "id=" + ev.id.slice(0, 12) + "…");

// Reverse direction: decrypt payloads produced by Kotlin (argv[2] = json file with {payloads, sealJson, sig}).
if (process.argv[2]) {
  const k = JSON.parse((await import("fs")).readFileSync(process.argv[2], "utf8"));
  const out = { nip44back: nt.nip44.v2.decrypt(k.nip44kt, ck) };
  out.rumorBack = nt.nip59.unwrapEvent(JSON.parse(k.sealKt), sk2);
  out.ktSigValid = nt.verifyEvent({ ...ev, sig: k.ktSig });
  console.log("REVERSE:" + JSON.stringify(out));
}
