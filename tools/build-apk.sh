#!/usr/bin/env bash
# XM Arcade — build an installable Android APK.
#
#   ./tools/build-apk.sh         # debug build  -> release/XM-Arcade-debug.apk
#   ./tools/build-apk.sh release # signed build -> release/XM-Arcade-vX.Y.Z.apk
#
# The release build needs a signing key. NEVER commit keys or passwords —
# pass them as environment variables (Gradle maps ORG_GRADLE_PROJECT_*
# to project properties automatically):
#
#   export ORG_GRADLE_PROJECT_XM_STORE_FILE=/path/to/xm-arcade-release.keystore
#   export ORG_GRADLE_PROJECT_XM_STORE_PASSWORD='...'
#   export ORG_GRADLE_PROJECT_XM_KEY_ALIAS=xmarcade
#   export ORG_GRADLE_PROJECT_XM_KEY_PASSWORD='...'
#
# Prereqs: JDK 17+, Android SDK (platform-34 + build-tools 34.0.0),
# Gradle 8.7+, python3. ANDROID_HOME / GRADLE_HOME are honored, and these
# fallback locations are probed: /var/tmp/toolchain, /opt, /tmp.
set -euo pipefail

MODE="${1:-debug}"
if [ "$MODE" != "debug" ] && [ "$MODE" != "release" ]; then
  echo "usage: $0 [debug|release]" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION")"

if [ -z "${ANDROID_HOME:-}" ]; then
  for d in /var/tmp/toolchain/asdk /opt/asdk /tmp/asdk "$HOME/.android-sdk" \
           "$HOME/Android/Sdk" /usr/lib/android-sdk; do
    if [ -d "$d" ]; then ANDROID_HOME="$d"; break; fi
  done
fi
if [ -z "${ANDROID_HOME:-}" ]; then
  echo "ERROR: Android SDK not found. Set ANDROID_HOME." >&2
  exit 1
fi
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ -z "${GRADLE_USER_HOME:-}" ]; then
  for d in /var/tmp/toolchain/gradle-home /opt/gradle-home /tmp/gradle-home; do
    if [ -d "$d" ]; then GRADLE_USER_HOME="$d"; break; fi
  done
  GRADLE_USER_HOME="${GRADLE_USER_HOME:-${HOME}/.gradle}"
fi
export GRADLE_USER_HOME

GRADLE_BIN=""
for g in "${GRADLE_HOME:-}/bin/gradle" /var/tmp/toolchain/gradle-8.7/bin/gradle \
         /opt/gradle-8.7/bin/gradle /tmp/gradle-8.7/bin/gradle; do
  if [ -n "$g" ] && [ -x "$g" ]; then GRADLE_BIN="$g"; break; fi
done
if [ -z "$GRADLE_BIN" ]; then GRADLE_BIN="$(command -v gradle || true)"; fi
if [ -z "$GRADLE_BIN" ]; then
  echo "ERROR: Gradle not found. Set GRADLE_HOME or put gradle on PATH." >&2
  exit 1
fi

if [ "$MODE" = "release" ]; then
  for v in ORG_GRADLE_PROJECT_XM_STORE_FILE ORG_GRADLE_PROJECT_XM_STORE_PASSWORD \
           ORG_GRADLE_PROJECT_XM_KEY_ALIAS ORG_GRADLE_PROJECT_XM_KEY_PASSWORD; do
    if [ -z "${!v:-}" ]; then echo "ERROR: $v is not set (see header of this script)." >&2; exit 1; fi
  done
fi

# 1. Rebuild the single-file web app, stage it as the APK asset.
python3 "$ROOT/tools/build-web.py"
mkdir -p "$ROOT/android/app/src/main/assets/www"
cp "$ROOT/release/XM-Arcade-standalone.html" "$ROOT/android/app/src/main/assets/www/index.html"
echo "asset: $(stat -c%s "$ROOT/android/app/src/main/assets/www/index.html") bytes"

# 2. Build.
cd "$ROOT/android"
if [ "$MODE" = "release" ]; then
  "$GRADLE_BIN" --no-daemon assembleRelease
  SRC="$ROOT/android/app/build/outputs/apk/release/app-release.apk"
  OUT="$ROOT/release/XM-Arcade-v${VERSION}.apk"
else
  "$GRADLE_BIN" --no-daemon assembleDebug
  SRC="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
  OUT="$ROOT/release/XM-Arcade-debug.apk"
fi

# 3. Copy + verify.
cp "$SRC" "$OUT"
BT="$ANDROID_HOME/build-tools/34.0.0"
if [ ! -x "$BT/aapt" ]; then BT="$ANDROID_HOME/build-tools/"*"/aapt"; BT="$(dirname "$BT")"; fi
echo "apk: $OUT ($(stat -c%s "$OUT") bytes)"
"$BT/aapt" dump badging "$OUT" | head -3
"$BT/apksigner" verify --print-certs "$OUT" | head -2
"$BT/zipalign" -c 4 "$OUT" && echo "zipalign: OK"
echo "BUILD OK ($MODE)"
