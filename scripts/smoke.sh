#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }

wait_for_pattern() {
  local needle="$1" tries="${2:-10}"
  local out=""
  for _ in $(seq 1 "$tries"); do
    out="$("$ANDROID_CLI" layout --device="$SERIAL" --flat --full 2>/dev/null || true)"
    if echo "$out" | grep -q "$needle"; then
      printf '%s' "$out"
      return 0
    fi
    sleep 1
  done
  printf '%s' "$out"
  return 1
}

# `android layout` only exposes the status bar while a Compose
# ModalBottomSheet is open, so sheets are detected by the extra window they
# create. Their titles are confirmed through the input selector, which does
# see dialog content.
app_window_count() {
  local count
  count="$("$ADB" -s "$SERIAL" shell dumpsys window windows 2>/dev/null \
    | grep -c "Window{.* $APP_PACKAGE/" || true)"
  printf '%s' "${count:-0}"
}

wait_for_app_windows() {
  local want="$1" tries="${2:-10}"
  for _ in $(seq 1 "$tries"); do
    if [[ "$(app_window_count)" -eq "$want" ]]; then
      return 0
    fi
    sleep 1
  done
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

# Restart the app. The ViewModel loads the newest saved report on launch; it
# must never capture automatically.
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
"$ADB" -s "$SERIAL" shell am start -W -n "$APP_PACKAGE/.MainActivity" >/dev/null

layout="$(wait_for_pattern '"resource-id":"tab_overview"' 20)" || fail "main screen did not appear"
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_overview' >/dev/null 2>&1 || true

layout="$(wait_for_pattern '"resource-id":"capture_summary"' 30)" || fail "saved report did not load"

echo "$layout" | grep -q 'Report' || fail "report summary missing"
echo "$layout" | grep -q 'Captured' || fail "captured timestamp missing"
echo "$layout" | grep -q 'tab_timeline' || fail "bottom navigation missing"

for legacy in 'Current capture' 'Ready to capture' 'Capture validated' 'Capture parsed' 'Live data from dumpsys batterystats'; do
  if echo "$layout" | grep -q "$legacy"; then
    fail "legacy header/content still visible: $legacy"
  fi
done

logs="$("$ADB" -s "$SERIAL" logcat -d -v brief | grep -E 'HistoryParser:|CheckinParser:' || true)"
echo "$logs" | grep -q 'HistoryParser: entries=' || fail "history parser did not log a result"
echo "$logs" | grep -q 'CheckinParser: apps=' || fail "checkin parser did not log a result"

# Timeline: the chart is a vertically scrolling page item, the axis uses
# global clock time, and the draggable cursor has a textual readout.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_timeline' >/dev/null 2>&1 || fail "could not open Timeline tab"
layout="$(wait_for_pattern '"resource-id":"timeline_screen"' 15)" || fail "timeline screen missing"
layout="$(wait_for_pattern '"resource-id":"timeline_cursor_readout"' 10)" || fail "timeline cursor readout missing"
echo "$layout" | grep -q 'Selected' || fail "timeline cursor time missing"
echo "$layout" | grep -q 'Drag across the chart to inspect a moment.' || fail "timeline hint missing"
if echo "$layout" | grep -q 'timeline_zoom_in'; then
  fail "manual zoom controls should be removed"
fi
layout="$(wait_for_pattern '"resource-id":"discharge_intervals"' 10)" || fail "discharge intervals card missing"

# System: capacity lives here, and parser internals are collapsed by default.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_system' >/dev/null 2>&1 || fail "could not open System tab"
layout="$(wait_for_pattern '"resource-id":"system_summary"' 10)" || fail "system battery card missing"
echo "$layout" | grep -q 'Capacity' || fail "battery capacity missing"
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=btn_report_details' >/dev/null 2>&1 || fail "could not expand report details"
layout="$(wait_for_pattern 'Checkin version' 10)" || fail "report details did not expand"

# Apps: rows are power-first and capacity is not repeated here.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_apps' >/dev/null 2>&1 || fail "could not open Apps tab"
layout="$(wait_for_pattern 'Apps by power' 10)" || fail "app list missing"
if echo "$layout" | grep -q 'Capacity'; then
  fail "battery capacity is repeated on the Apps tab"
fi

# Reports: current report plus saved list; raw text opens in a sheet.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_reports' >/dev/null 2>&1 || fail "could not open Reports tab"
layout="$(wait_for_pattern 'Saved reports' 10)" || fail "saved reports list missing"
echo "$layout" | grep -q '"resource-id":"btn_view_raw"' || fail "raw report button missing"
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=btn_view_raw' >/dev/null 2>&1 || fail "could not open raw report"
wait_for_app_windows 2 10 || fail "raw report sheet did not open"
"$ANDROID_CLI" input --device="$SERIAL" tap "txt=Raw capture" >/dev/null 2>&1 \
  || fail "raw report sheet content missing"
"$ADB" -s "$SERIAL" shell input keyevent 4 >/dev/null 2>&1 || true
wait_for_app_windows 1 10 || fail "raw report sheet did not close"

echo "OK: saved report load, navbar, timeline cursor, system details, apps, reports, raw sheet"
