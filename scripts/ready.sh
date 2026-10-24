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

# The app restores the last tab. Make sure the landing tab is Overview before
# waiting for the saved report to load.
for _ in $(seq 1 20); do
  layout="$("$ANDROID_CLI" layout --device="$SERIAL" --flat --full 2>/dev/null || true)"
  if echo "$layout" | grep -q '"resource-id":"tab_overview"'; then
    "$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_overview' >/dev/null 2>&1 || true
    break
  fi
  sleep 1
done

echo "Waiting for the latest saved report…"
capture_tapped=0
for _ in $(seq 1 90); do
  layout="$("$ANDROID_CLI" layout --device="$SERIAL" --flat --full 2>/dev/null || true)"

  # The report summary tag appears after the ViewModel has loaded the newest
  # saved report. There is intentionally no automatic capture anymore.
  if echo "$layout" | grep -q '"resource-id":"capture_summary"'; then
    echo "Battery Archeologist is ready on $SERIAL"
    exit 0
  fi

  if [[ "$capture_tapped" -eq 0 ]] && echo "$layout" | grep -q '"resource-id":"btn_capture"'; then
    echo "No saved report found; capturing once…"
    "$ANDROID_CLI" input --device="$SERIAL" tap 'id=btn_capture' >/dev/null 2>&1 || true
    capture_tapped=1
  fi

  sleep 1
done

echo "App launched but the report summary did not appear; inspect artifacts/screen.png"
"$ANDROID_CLI" screen capture --device="$SERIAL" --output="$ARTIFACTS/screen.png" 2>/dev/null || true
exit 1
