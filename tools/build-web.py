#!/usr/bin/env python3
"""XM Arcade — single-file web build.

Bundles js/app.js with esbuild (IIFE), inlines css/*.css, and embeds the
built-in webxdc games so the app runs from ONE html file with no server
and no network (except Nostr relays / Blossom at runtime).

Reads:  index.html, css/app.css, css/live.css, js/**, webxdc/*/index.html, VERSION
Writes: release/XM-Arcade-standalone.html

esbuild resolution order:
  1. tools/esbuild-linux-x64 (vendored, works on Linux x86_64 + CI)
  2. `esbuild` on PATH
  3. `npx --yes esbuild` (downloads on first use, needs network + node)
"""
import json
import re
import shutil
import subprocess
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENTRY = ROOT / "js" / "app.js"
CSS_FILES = [ROOT / "css" / "app.css", ROOT / "css" / "live.css"]
BUILTINS = {
    "webxdc/hello/index.html": ROOT / "webxdc" / "hello" / "index.html",
    "webxdc/taprace/index.html": ROOT / "webxdc" / "taprace" / "index.html",
}
OUT = ROOT / "release" / "XM-Arcade-standalone.html"
VENDORED_ESBUILD = ROOT / "tools" / "esbuild-linux-x64"


def find_esbuild():
    if VENDORED_ESBUILD.exists():
        return [str(VENDORED_ESBUILD)]
    if shutil.which("esbuild"):
        return ["esbuild"]
    if shutil.which("npx"):
        return ["npx", "--yes", "esbuild"]
    sys.exit("ERROR: no esbuild found. Install node + esbuild, or restore tools/esbuild-linux-x64.")


def js_safe(text):
    """Escape sequences that would break out of a <script> block.

    `<\\/` === `/` inside JS strings, regexes, and comments, so this is
    behavior-preserving everywhere in the bundle.
    """
    text = re.sub(r"</script", r"<\\/script", text, flags=re.IGNORECASE)
    text = text.replace("<!--", r"<\!--")
    return text


def main():
    version = (ROOT / "VERSION").read_text().strip()
    for p in [ENTRY, ROOT / "index.html", *CSS_FILES, *BUILTINS.values()]:
        if not p.exists():
            sys.exit(f"ERROR: missing input {p.relative_to(ROOT)}")

    # 1. bundle
    cmd = find_esbuild() + [
        str(ENTRY), "--bundle", "--format=iife", "--target=es2022",
        "--minify", "--log-level=warning",
    ]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f"ERROR: esbuild failed:\n{r.stderr[-3000:]}")
    bundle = js_safe(r.stdout)
    print(f"bundle: {len(r.stdout)} bytes")

    # 2. css
    css = "\n".join(p.read_text() for p in CSS_FILES)
    if re.search(r"</style", css, re.IGNORECASE):
        sys.exit("ERROR: CSS contains </style — cannot inline safely.")
    print(f"css: {len(css)} bytes")

    # 3. builtin games
    builtins = {k: p.read_text() for k, p in BUILTINS.items()}
    blob = js_safe(json.dumps(builtins, ensure_ascii=False))
    print(f"builtins: {len(blob)} bytes ({', '.join(builtins)})")

    # 4. assemble
    html = (ROOT / "index.html").read_text()
    for href in ("css/app.css", "css/live.css"):
        tag = f'<link rel="stylesheet" href="{href}">'
        if tag not in html:
            sys.exit(f"ERROR: expected tag missing from index.html: {tag}")
        html = html.replace(tag, "")
    style_tag = "<style>\n" + css + "\n</style>"
    if "</head>" not in html:
        sys.exit("ERROR: index.html has no </head>.")
    html = html.replace("</head>", style_tag + "\n</head>")

    entry_tag = '<script type="module" src="js/app.js"></script>'
    if entry_tag not in html:
        sys.exit("ERROR: index.html entry script tag changed; update build-web.py.")
    inject = (
        f'<script>/* XM Arcade standalone v{version} — built {date.today().isoformat()} */\n'
        f"globalThis.__BUILTIN_APPS={blob};</script>\n"
        f"<script>\n{bundle}\n</script>"
    )
    html = html.replace(entry_tag, inject)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(html)
    print(f"wrote: {OUT.relative_to(ROOT)} ({len(html)} bytes)")


if __name__ == "__main__":
    main()
