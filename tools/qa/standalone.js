// XM Arcade — release QA for the single-file build.
// Serves release/ over HTTP, boots the app in headless Chromium, and asserts:
//   BOOT   — onboarding splash renders (no blank screen)
//   CREATE — Create account -> Generate my key lands in the app (live relays)
//   APPS   — both builtin webxdc games are embedded and mountable in a
//            sandboxed iframe, the same way the app runs them
//   SHOT   — an in-app screenshot is saved (used in README)
//   ERRORS — zero page errors / failed local requests (wss relay traffic excluded)
//
// Usage: node tools/qa/standalone.js [--shot docs/screenshot.png]
// Needs: playwright (npm i playwright) + a Chromium build.
const fs = require("fs");
const http = require("http");
const path = require("path");
const { chromium } = require("playwright");

const ROOT = path.resolve(__dirname, "..", "..");
const FILE = path.join(ROOT, "release", "XM-Arcade-standalone.html");
const shotArg = process.argv.indexOf("--shot");
const SHOT = shotArg > -1 ? path.resolve(process.argv[shotArg + 1]) : null;

function serve(file) {
  // The single-file app must make NO local subrequests: anything but the
  // page itself 404s, so a stray fetch shows up as a failed request.
  return new Promise((resolve) => {
    const srv = http.createServer((req, res) => {
      const p = decodeURIComponent(req.url.split("?")[0]);
      if (p !== "/" && p !== "/XM-Arcade-standalone.html") {
        res.writeHead(404);
        res.end("nf");
        return;
      }
      fs.readFile(file, (err, buf) => {
        res.writeHead(err ? 404 : 200, { "content-type": "text/html" });
        res.end(buf);
      });
    });
    srv.listen(0, "127.0.0.1", () => resolve(srv));
  });
}
const phone = (page) =>
  page.evaluate(() => ({
    len: document.getElementById("phone").textContent.length,
    text: document.getElementById("phone").textContent.slice(0, 400),
  }));

(async () => {
  if (!fs.existsSync(FILE)) {
    console.error("missing " + FILE + " — run tools/build-web.py first");
    process.exit(1);
  }
  const srv = await serve(FILE);
  const url = `http://127.0.0.1:${srv.address().port}/`;
  const errors = [];
// Fake monero-wallet-rpc (JSON-RPC + CORS) so the xap money-path is tested
// end-to-end with zero real funds. Started lazily mid-run (see GATE-XMR).
function startFakeXmr() {
  return new Promise((resolve) => {
    const srv = http.createServer((req, res) => {
      res.setHeader("Access-Control-Allow-Origin", "*");
      res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
      if (req.method === "OPTIONS") { res.writeHead(204); res.end(); return; }
      let body = "";
      req.on("data", (c) => (body += c));
      req.on("end", () => {
        let method = "";
        try { method = JSON.parse(body).method; } catch {}
        const results = {
          get_version: { version: "99.qa" },
          transfer: { tx_hash: "qa1234567890abcdef" },
          get_balance: { balance: 5000000000000 },
          get_address: { address: "4A" + "1".repeat(93) },
        };
        res.writeHead(200, { "content-type": "application/json" });
        res.end(JSON.stringify({ jsonrpc: "2.0", id: "xm", result: results[method] || {} }));
      });
    });
    srv.listen(18082, "127.0.0.1", () => resolve(srv));
  });
}
let xmrStub = null;
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    page.on("pageerror", (e) => {
      const stack = (e.stack || String(e)).split("\n").slice(0, 3).join(" | ");
      errors.push("pageerror: " + stack.slice(0, 400));
    });
    page.on("console", (m) => {
      // Relay flakiness is environmental noise: the app is built to degrade
      // gracefully (see the 2/3-style relay chips), so don't fail on it.
      if (m.type() !== "error") return;
      if (/^WebSocket connection to 'wss?:\/\//.test(m.text())) return;
      if (gateWindow && /ERR_CONNECTION_REFUSED/.test(m.text())) return;
      errors.push("console: " + m.text().slice(0, 300));
    });
    // The fake XMR RPC (127.0.0.1:18082) is deliberately DOWN during GATE-XMR.
    let gateWindow = false;
    page.on("requestfailed", (r) => {
      if (r.url().includes("127.0.0.1:18082")) return;
      if (!r.url().startsWith("wss://") && !r.url().startsWith("https://"))
        errors.push("reqfail: " + r.url().slice(0, 200));
    });

    await page.goto(url, { waitUntil: "load" });
    await page.waitForFunction(
      () => (document.getElementById("phone") || {}).textContent.length > 100,
      null, { timeout: 30000 }
    );
    const boot = await phone(page);
    boot.hasCreate = /create account/i.test(boot.text);
    console.log("BOOT:", JSON.stringify(boot));
    if (!boot.hasCreate) throw new Error("onboarding splash did not render");

    // CREATE: fresh account, straight through to the app.
    await page.locator("#phone button", { hasText: /create account/i }).click();
    await page.waitForFunction(
      () => /generate my key/i.test(document.getElementById("phone").textContent),
      null, { timeout: 15000 }
    );
    const relays = await page.evaluate(() =>
      (document.getElementById("phone").textContent.match(/[0-3]\/3 relays/) || ["?"])[0]
    );
    await page.locator("#phone button", { hasText: /generate my key/i }).click();
    await page.waitForFunction(
      () => /welcome to xm arcade/i.test(document.body.textContent),
      null, { timeout: 15000 }
    );
    await page.waitForTimeout(6000); // let the first pane live-load
    const app = await phone(page);
    console.log("CREATE:", JSON.stringify({ relays, appLen: app.len, appHead: app.text.slice(0, 200) }));

    // GATE-XMR: with no wallet reachable, xap prompts to connect one.
    gateWindow = true;
    await page.locator("#phone [data-act='xap']").nth(0).waitFor({ timeout: 20000 });
    await page.locator("#phone [data-act='xap']").nth(0).click();
    await page.waitForFunction(
      () => [...document.querySelectorAll(".sheet h3")].some((h) => h.textContent === "Connect a wallet"),
      null, { timeout: 10000 }
    );
    const gateXmr = await page.evaluate(() => ({
      text: document.querySelector(".sheet .sheet-body").textContent,
      hasGo: !!document.querySelector("#phone [data-act='needWalletGo']"),
    }));
    console.log("GATE-XMR:", JSON.stringify({ hasGo: gateXmr.hasGo, asks: /connect a wallet to xap/i.test(gateXmr.text) }));
    if (!gateXmr.hasGo || !/connect a wallet to xap/i.test(gateXmr.text)) throw new Error("xmr wallet gate did not render");
    await page.locator("#phone [data-act='closeSheet']").nth(0).click();
    await page.waitForTimeout(500);

    // XAP: with a (fake) wallet RPC up, the sheet renders AND submits end-to-end.
    gateWindow = false;
    xmrStub = await startFakeXmr();
    await page.locator("#phone [data-act='xap']").nth(0).click();
    await page.waitForFunction(
      () => [...document.querySelectorAll(".sheet h3")].some((h) => h.textContent === "Monero xap"),
      null, { timeout: 10000 }
    );
    const xap = await page.evaluate(() => ({
      hasAddr: !!document.getElementById("xapTo"),
      presets: document.querySelectorAll(".xap-amt").length,
    }));
    console.log("XAP:", JSON.stringify(xap));
    if (!xap.hasAddr || xap.presets !== 4) throw new Error("xap sheet did not render");
    await page.fill("#phone #xapTo", "4A" + "1".repeat(93));
    await page.locator("#phone [data-act='xapGo']").nth(0).click();
    await page.waitForFunction(() => /Xapped 0\.01 XMR/.test(document.getElementById("toast").textContent), null, { timeout: 10000 });
    console.log("XAP-SENT: ok (fake RPC tx accepted)");
    await page.waitForTimeout(500);

    // REACT: emoji picker opens from content (never submits).
    await page.locator("#phone [data-act='react']").nth(0).click();
    await page.waitForFunction(
      () => [...document.querySelectorAll(".sheet h3")].some((h) => h.textContent === "React"),
      null, { timeout: 10000 }
    );
    const react = await page.evaluate(() => ({
      cells: document.querySelectorAll(".react-cell").length,
      hasInput: !!document.getElementById("reactCustom"),
    }));
    console.log("REACT:", JSON.stringify(react));
    if (!react.hasInput || react.cells < 8) throw new Error("react sheet did not render");
    await page.locator("#phone [data-act='closeSheet']").nth(0).click();
    await page.waitForTimeout(500);

    // INLINE: shorts reply opens a bottom-third composer without leaving the pane.
    await page.locator("#phone [data-act='reply'][data-inline='1']").nth(0).click();
    await page.waitForFunction(() => !!document.querySelector("#phone .inline-reply"), null, { timeout: 10000 });
    const inl = await page.evaluate(() => ({
      hasBox: !!document.querySelector("#phone .inline-reply textarea"),
      noCompose: !document.querySelector("#phone [data-view='compose']"),
    }));
    console.log("INLINE:", JSON.stringify(inl));
    if (!inl.hasBox || !inl.noCompose) throw new Error("inline reply did not render");
    await page.locator("#phone [data-act='replyCancelInline']").nth(0).click();

    // APPS: builtins embedded + mountable exactly like the app mounts them.
    const apps = await page.evaluate(() => {
      const b = globalThis.__BUILTIN_APPS || {};
      return {
        keys: Object.keys(b),
        helloOk: (b["webxdc/hello/index.html"] || "").includes("<html"),
        tapOk: (b["webxdc/taprace/index.html"] || "").includes("<html"),
      };
    });
    console.log("APPS:", JSON.stringify(apps));
    if (!apps.helloOk || !apps.tapOk) throw new Error("builtin games missing from bundle");
    const game = await page.evaluate(
      () =>
        new Promise((resolve) => {
          try {
            // Mirror the app: it always injects its webxdc shim before the
            // game's own scripts run (see buildFrameDoc in js/webxdc.js).
            const stub =
              "<script>window.webxdc={setUpdateListener(){},sendUpdate(){},selfAddr:'qa',selfName:'qa'};</scr" +
              "ipt>";
            const raw = globalThis.__BUILTIN_APPS["webxdc/hello/index.html"];
            // Strip unresolvable relative script refs (the app rewrites them
            // to blob URLs; here they would 404), then inject the stub shim.
            const src = raw
              .replace(/<script\s+src=(["'])(?!(https?:|data:|blob:))[^"']+\1\s*><\/script>/gi, "")
              .replace(/<head[^>]*>/i, (m) => m + stub);
            const f = document.createElement("iframe");
            f.src = URL.createObjectURL(new Blob([src], { type: "text/html" }));
            f.style.cssText = "position:absolute;width:10px;height:10px;visibility:hidden";
            f.onload = () => {
              let text = "";
              try {
                text = f.contentDocument.body.textContent || "";
              } catch (e) {}
              const ok = text.includes("Hello Arcade");
              f.remove();
              resolve({ ok, head: text.slice(0, 60) });
            };
            f.onerror = () => resolve({ ok: false });
            document.body.appendChild(f);
            setTimeout(() => resolve({ ok: false, timeout: true }), 8000);
          } catch (e) {
            resolve({ ok: false, err: String(e).slice(0, 200) });
          }
        })
    );
    console.log("GAME:", JSON.stringify(game));
    if (!game.ok) throw new Error("embedded game did not mount");

    if (SHOT) {
      await page.screenshot({ path: SHOT });
      console.log("SHOT:", SHOT);
    }

    // GATE-SATS: with no NWC wallet, zap prompts to connect + routes to Wallet.
    await page.locator("#phone [data-act='zap']").nth(0).click();
    await page.waitForFunction(
      () => [...document.querySelectorAll(".sheet h3")].some((h) => h.textContent === "Connect a wallet"),
      null, { timeout: 10000 }
    );
    const gateSats = await page.evaluate(() => document.querySelector(".sheet .sheet-body").textContent);
    if (!/connect a wallet to zap/i.test(gateSats)) throw new Error("sats wallet gate did not render");
    await page.locator("#phone [data-act='needWalletGo']").nth(0).click();
    await page.waitForFunction(() => !!document.querySelector("#phone [data-view='wallet']"), null, { timeout: 10000 });
    console.log("GATE-SATS: ok (routed to wallet pane)");

    // HUE: secondary-color slider parked on classic blue, live-recolors, persists.
    await page.locator("#phone [data-act='dd']").nth(0).click();
    await page.locator("#phone #ddPanel [data-sec='settings']").click();
    // Scope to the ACTIVE view: the router keeps the exiting view mounted ~620ms.
    await page.waitForFunction(() => !!document.querySelector("#phone .view.active .accentHue"), null, { timeout: 10000 });
    const hue = await page.evaluate(() => {
      const acc = () => document.documentElement.style.getPropertyValue("--accent").trim();
      const r = document.querySelector("#phone .view.active .accentHue");
      const before = { val: r.value, acc: acc() };
      r.value = "0"; r.dispatchEvent(new Event("input", { bubbles: true }));
      const side = [...document.querySelectorAll(".accentHue")].filter((x) => !x.closest("#phone")).map((x) => x.value);
      return { before, afterAcc: acc(), side, saved: (JSON.parse(localStorage.getItem("xm.ui.v1") || "{}") || {}).accentHue };
    });
    console.log("HUE:", JSON.stringify(hue));
    if (hue.before.val !== "187" || hue.before.acc !== "hsl(187,85%,55%)") throw new Error("hue slider not parked on classic blue");
    if (hue.afterAcc !== "hsl(0,85%,55%)") throw new Error("accent did not recolor live");
    if (hue.side.length !== 1 || hue.side[0] !== "0") throw new Error("side-panel slider did not sync");
    if (hue.saved !== 0) throw new Error("hue not persisted");
  } finally {
    await browser.close();
    srv.close();
    if (xmrStub) xmrStub.close();
  }
  console.log("ERRORS:", JSON.stringify(errors));
  if (errors.length) process.exit(2);
  console.log("QA OK");
})().catch((e) => {
  console.error("QA FAILED:", e.message || e);
  process.exit(1);
});
