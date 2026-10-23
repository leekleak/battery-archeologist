#!/usr/bin/env bash
# Shared environment for Battery Archeologist development.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ROOT

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Prefer a working JDK 21. /usr/lib/jvm/java-21-openjdk is broken on this host;
# Gradle's provisioned Temurin 21 is the reliable fallback.
if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x "$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java" ]]; then
    export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2"
  elif [[ -x /usr/lib/jvm/java-25-openjdk/bin/java ]]; then
    export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
  fi
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

export ANDROID_CLI="$ANDROID_HOME/cmdline-tools/latest/bin/android"
export ADB="$ANDROID_HOME/platform-tools/adb"
export EMULATOR="$ANDROID_HOME/emulator/emulator"
export AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"

export AVD_NAME="${AVD_NAME:-battery_api36}"
export SERIAL="${SERIAL:-emulator-5554}"
export ANDROID_SERIAL="$SERIAL"

export APP_PACKAGE="com.leekleak.batteryarcheologist"
export SHIZUKU_PACKAGE="moe.shizuku.privileged.api"
export SHIZUKU_APK="$ROOT/tools/shizuku/shizuku-13.6.0.apk"
export ARTIFACTS="$ROOT/artifacts"

require_emulator() {
  local qemu
  qemu="$("$ADB" -s "$SERIAL" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r' || true)"
  if [[ "$qemu" != "1" ]]; then
    echo "Refusing to continue: $SERIAL is not an Android emulator (ro.kernel.qemu='$qemu')" >&2
    exit 1
  fi
}

mkdir -p "$ARTIFACTS"
