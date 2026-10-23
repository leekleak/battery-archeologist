#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

"$ROOT/scripts/emulator.sh" start
require_emulator
"$ROOT/scripts/shizuku.sh" install
"$ROOT/scripts/shizuku.sh" start

if ! "$ADB" -s "$SERIAL" shell pm path "$APP_PACKAGE" >/dev/null 2>&1; then
  "$ROOT/gradlew" -p "$ROOT" :app:installDebug --console=plain
else
  "$ROOT/gradlew" -p "$ROOT" :app:installDebug --console=plain
fi

"$ROOT/scripts/shizuku.sh" seed
"$ADB" -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
"$ADB" -s "$SERIAL" shell am start -W -n "$APP_PACKAGE/.MainActivity" >/dev/null
echo "Waiting for capture summary…"
for _ in $(seq 1 60); do
  if "$ANDROID_CLI" layout --device="$SERIAL" --flat 2>/dev/null | grep -qE 'Capture (parsed|validated)'; then
    echo "Battery Archeologist is ready on $SERIAL"
    exit 0
  fi
  sleep 1
done
echo "App launched but capture summary did not appear; inspect artifacts/screen.png"
"$ANDROID_CLI" screen capture --device="$SERIAL" --output="$ARTIFACTS/screen.png" 2>/dev/null || true
exit 1
