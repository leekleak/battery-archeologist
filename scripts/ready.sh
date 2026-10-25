#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

"$ROOT/scripts/emulator.sh" start
require_emulator
"$ROOT/scripts/shizuku.sh" install
"$ROOT/scripts/shizuku.sh" start

"$ROOT/gradlew" -p "$ROOT" :app:installDebug --console=plain

"$ROOT/scripts/shizuku.sh" seed
"$ADB" -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
"$ADB" -s "$SERIAL" shell am start -W -n "$APP_PACKAGE/.MainActivity" >/dev/null

# Navigation 3 starts on the reports home. Capture is an explicit action that
# stays on that screen and adds a saved report; it never opens the viewer.
echo "Waiting for the reports home…"
home_visible=0
for _ in $(seq 1 20); do
  layout="$("$ANDROID_CLI" layout --device="$SERIAL" --flat --full 2>/dev/null || true)"
  if echo "$layout" | grep -q '"resource-id":"reports_home"'; then
    home_visible=1
    break
  fi
  sleep 1
done

if [[ "$home_visible" -eq 0 ]]; then
  echo "App launched but the reports home did not appear; inspect artifacts/screen.png"
  "$ANDROID_CLI" screen capture --device="$SERIAL" --output="$ARTIFACTS/screen.png" 2>/dev/null || true
  exit 1
fi

if ! echo "$layout" | grep -q '"resource-id":"saved_report_'; then
  echo "No saved report found; capturing once…"
  "$ANDROID_CLI" input --device="$SERIAL" tap 'id=btn_capture' >/dev/null 2>&1 || true
fi

echo "Waiting for a saved report…"
for _ in $(seq 1 60); do
  layout="$("$ANDROID_CLI" layout --device="$SERIAL" --flat --full 2>/dev/null || true)"
  if echo "$layout" | grep -q '"resource-id":"saved_report_'; then
    echo "Battery Archeologist is ready on $SERIAL"
    exit 0
  fi
  sleep 1
done

echo "App launched but no saved report appeared; inspect artifacts/screen.png"
"$ANDROID_CLI" screen capture --device="$SERIAL" --output="$ARTIFACTS/screen.png" 2>/dev/null || true
exit 1
