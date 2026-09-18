#!/usr/bin/env bash
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SECRETS_DIR="${XM_SECRETS_DIR:-/home/user/xm-arcade-release-secrets}"
PASS_FILE="$SECRETS_DIR/passwords.sh"
if [ -f "$PASS_FILE" ]; then
  echo "→ Loading signing secrets from $PASS_FILE"
  set -a; source "$PASS_FILE"; set +a
fi
if [ -n "$XM_KEYSTORE_BASE64" ] && [ ! -f "$XM_STORE_FILE" ]; then
  echo "→ Decoding XM_KEYSTORE_BASE64 to /tmp/xm-arcade-release.keystore"
  echo "$XM_KEYSTORE_BASE64" | base64 -d > /tmp/xm-arcade-release.keystore
  export XM_STORE_FILE=/tmp/xm-arcade-release.keystore
  export ORG_GRADLE_PROJECT_XM_STORE_FILE=/tmp/xm-arcade-release.keystore
fi
BUILD_TYPE="${1:-debug}"
echo "→ Building XM Arcade ($BUILD_TYPE) with signing ${XM_STORE_FILE:-debug keystore}..."
cd "$ROOT"
if [ "$BUILD_TYPE" = "release" ]; then
  ./gradlew :app:assembleRelease --stacktrace
  APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
else
  ./gradlew :app:assembleDebug --stacktrace
  APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
fi
if [ -f "$APK" ]; then
  echo "✅ Built: $APK"
  ls -lh "$APK"
  mkdir -p "$ROOT/docs/releases"
  cp "$APK" "$ROOT/XM-Arcade-v1.1.0.apk" 2>/dev/null || true
  cp "$APK" "$ROOT/docs/releases/XM-Arcade-v1.1.0.apk" 2>/dev/null || true
  if command -v apksigner >/dev/null 2>&1; then
    apksigner verify --print-certs "$APK" | head -n 20 || true
  fi
else
  echo "❌ APK not found at $APK"
  exit 1
fi
