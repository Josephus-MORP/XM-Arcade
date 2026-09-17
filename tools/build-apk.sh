#!/usr/bin/env bash
# Builds the native XM Arcade debug APK.
# First run installs JDK 17 + Android SDK + Gradle into /var/tmp (outside the
# workspace snapshot, so it re-installs automatically if wiped).
# Usage: ./tools/build-apk.sh [setup]   # 'setup' stops after installing the toolchain
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK=/var/tmp/android-sdk
JDK=/var/tmp/jdk17
GRADLE_VER=8.7
GRADLE_HOME=/var/tmp/gradle-$GRADLE_VER
export TMPDIR=/var/tmp GRADLE_USER_HOME=/var/tmp/gradle-home JAVA_HOME=$JDK
export PATH="$JDK/bin:$PATH"
mkdir -p /var/tmp "$GRADLE_USER_HOME"

unzip_to() { # $1=zip $2=dest
  if command -v unzip >/dev/null; then unzip -q "$1" -d "$2"
  else python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$1" "$2"; fi
}

echo "==> JDK 17"
if [ ! -x "$JDK/bin/java" ]; then
  curl -sSL -o /var/tmp/jdk17.tar.gz "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz"
  rm -rf "$JDK" && mkdir -p "$JDK"
  tar xzf /var/tmp/jdk17.tar.gz -C "$JDK" --strip-components=1
fi
"$JDK/bin/java" -version 2>&1 | head -1 || true

echo "==> Android SDK"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  curl -sSL -o /var/tmp/cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  unzip_to /var/tmp/cmdtools.zip "$SDK/cmdline-tools/"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
if [ ! -d "$SDK/platforms/android-34" ]; then
  set +o pipefail # 'yes' always SIGPIPEs; keep sdkmanager's own exit code
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null
  set -o pipefail
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
fi
echo "sdk.dir=$SDK" > "$ROOT/android/local.properties"

echo "==> Gradle $GRADLE_VER"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  curl -sSL -o /var/tmp/gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
  rm -rf "$GRADLE_HOME"
  unzip_to /var/tmp/gradle.zip /var/tmp
fi

if [ "${1:-}" = "setup" ]; then echo SETUP_OK; exit 0; fi

echo "==> assembleDebug"
# Kill orphaned compiler daemons from killed builds (sandbox has ~2GB RAM).
pkill -9 -f KotlinCompileDaemon 2>/dev/null || true
pkill -9 -f GradleDaemon 2>/dev/null || true
sleep 1
cd "$ROOT/android"
"$GRADLE_HOME/bin/gradle" --no-daemon assembleDebug

echo "==> collect APK (build/ is not snapshotted)"
mkdir -p "$ROOT/dist"
cp app/build/outputs/apk/debug/app-debug.apk "$ROOT/dist/xm-arcade-native-debug.apk"
ls -la "$ROOT/dist/xm-arcade-native-debug.apk"
