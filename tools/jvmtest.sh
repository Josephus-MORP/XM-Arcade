#!/usr/bin/env bash
# XM Arcade — JVM test for the native crypto core.
# Cross-validates the Kotlin (secp256k1/BIP-340/NIP-19/44/57/59/events) against
# the shipped JS implementation (vendored nostr-tools) in BOTH directions.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KOTLINC=""
for k in /var/tmp/kotlinc/kotlinc/bin/kotlinc "$(command -v kotlinc || true)"; do
  if [ -n "$k" ] && [ -x "$k" ]; then KOTLINC="$k"; break; fi
done
if [ -z "$KOTLINC" ]; then echo "ERROR: kotlinc not found." >&2; exit 1; fi
mkdir -p /var/tmp/jvmtest
JSON_JAR=/var/tmp/jvmtest/json.jar
if [ ! -f "$JSON_JAR" ]; then
  curl -sL -o "$JSON_JAR" "https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar"
fi
node "$ROOT/tools/fixtures.mjs"
"$KOTLINC" "$ROOT/android/app/src/main/java/com/xmarcade/app/core/"*.kt \
  "$ROOT/android/jvmtest/CryptoTest.kt" -cp "$JSON_JAR" -include-runtime \
  -d /var/tmp/jvmtest/cryptotest.jar 2>&1 | grep -v "^warning:" || true
java -cp "/var/tmp/jvmtest/cryptotest.jar:$JSON_JAR" CryptoTestKt /tmp/nostr-fixtures.json
node "$ROOT/tools/fixtures.mjs" /tmp/kt-reverse.json
