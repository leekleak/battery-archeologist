#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }

wait_for_text() {
  local needle="$1" tries="${2:-10}"
  local out=""
  for _ in $(seq 1 "$tries"); do
    out="$("$ANDROID_CLI" layout --device="$SERIAL" --flat 2>/dev/null || true)"
    if echo "$out" | grep -q "$needle"; then
      printf '%s' "$out"
      return 0
    fi
    sleep 1
  done
  printf '%s' "$out"
  return 1
}

"$ADB" -s "$SERIAL" get-state >/dev/null 2>&1 || fail "emulator $SERIAL is not running"
require_emulator
[[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] || fail "boot not complete"

"$ADB" -s "$SERIAL" shell pm path "$APP_PACKAGE" >/dev/null 2>&1 || fail "app not installed"
"$ADB" -s "$SERIAL" shell pm path "$SHIZUKU_PACKAGE" >/dev/null 2>&1 || fail "Shizuku not installed"
"$ADB" -s "$SERIAL" shell pidof shizuku_server >/dev/null 2>&1 || fail "Shizuku server not running"

id_line="$("$ADB" -s "$SERIAL" shell "RISH_APPLICATION_ID=$SHIZUKU_PACKAGE sh /data/local/tmp/shizuku/rish -c id" 2>&1 | tr -d '\r')"
echo "$id_line" | grep -q 'uid=2000(shell)' || fail "rish is not running as uid 2000: $id_line"

# Restart the app and wait for the automatic capture to finish.
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
"$ADB" -s "$SERIAL" shell am start -W -n "$APP_PACKAGE/.MainActivity" >/dev/null
layout=""
for _ in $(seq 1 30); do
  layout="$("$ANDROID_CLI" layout --device="$SERIAL" --flat 2>/dev/null || true)"
  if echo "$layout" | grep -q 'Capture validated'; then
    break
  fi
  sleep 1
done

echo "$layout" | grep -q 'Capture validated' || fail "capture summary not visible"
echo "$layout" | grep -q 'Checkin version' || fail "checkin summary missing"
echo "$layout" | grep -q 'Overview' || fail "content tabs missing"

logs="$("$ADB" -s "$SERIAL" logcat -d -v brief | grep -E 'HistoryParser:|CheckinParser:' || true)"
echo "$logs" | grep -q 'HistoryParser: entries=' || fail "history parser did not log a result"
echo "$logs" | grep -q 'CheckinParser: apps=' || fail "checkin parser did not log a result"

# Exercise the tab navigation and both parser-backed screens.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_timeline' >/dev/null 2>&1 || fail "could not open Timeline tab"
layout="$(wait_for_text 'Timeline' 10)" || fail "timeline screen missing"

"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_system' >/dev/null 2>&1 || fail "could not open System tab"
layout="$(wait_for_text 'System stats' 10)" || fail "system stats missing"

"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_apps' >/dev/null 2>&1 || fail "could not open Apps tab"
layout="$(wait_for_text 'Apps by power' 10)" || fail "app list missing"

echo "OK: emulator, Shizuku, capture, parsers, timeline, system, and apps tabs"
