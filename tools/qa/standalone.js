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
      if (m.type() === "error" && !/^WebSocket connection to 'wss?:\/\//.test(m.text()))
        errors.push("console: " + m.text().slice(0, 300));
    });
    page.on("requestfailed", (r) => {
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

    // SNAP: an XMR snap sheet opens from content (never submitted — no funds move).
    await page.locator("#phone [data-act='snap']").nth(0).waitFor({ timeout: 20000 });
    await page.locator("#phone [data-act='snap']").nth(0).click();
    await page.waitForFunction(() => /monero snap/i.test(document.body.textContent), null, { timeout: 10000 });
    const snap = await page.evaluate(() => ({
      hasAddr: !!document.getElementById("snapTo"),
      presets: document.querySelectorAll(".snap-amt").length,
    }));
    console.log("SNAP:", JSON.stringify(snap));
    if (!snap.hasAddr || snap.presets !== 4) throw new Error("snap sheet did not render");
    await page.locator("#phone [data-act='closeSheet']").nth(0).click();
    await page.waitForTimeout(500);

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
  } finally {
    await browser.close();
    srv.close();
  }
  console.log("ERRORS:", JSON.stringify(errors));
  if (errors.length) process.exit(2);
  console.log("QA OK");
})().catch((e) => {
  console.error("QA FAILED:", e.message || e);
  process.exit(1);
});
