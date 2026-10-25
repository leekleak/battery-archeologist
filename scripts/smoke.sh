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

# First saved report card in the current layout JSON.
first_saved_report_name() {
  local layout="$1" tag
  tag="$(echo "$layout" | grep -o '"resource-id":"saved_report_[^"]*"' | head -1 || true)"
  tag="${tag#\"resource-id\":\"saved_report_}"
  tag="${tag%\"}"
  printf '%s' "$tag"
}

# All saved report cards visible in the current layout JSON.
saved_report_names() {
  local layout="$1"
  echo "$layout" \
    | grep -o '"resource-id":"saved_report_[^"]*"' \
    | sed 's/^"resource-id":"saved_report_//; s/"$//' \
    | sort -u
}

"$ADB" -s "$SERIAL" get-state >/dev/null 2>&1 || fail "emulator $SERIAL is not running"
require_emulator
[[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] || fail "boot not complete"

"$ADB" -s "$SERIAL" shell pm path "$APP_PACKAGE" >/dev/null 2>&1 || fail "app not installed"
"$ADB" -s "$SERIAL" shell pm path "$SHIZUKU_PACKAGE" >/dev/null 2>&1 || fail "Shizuku not installed"
"$ADB" -s "$SERIAL" shell pidof shizuku_server >/dev/null 2>&1 || fail "Shizuku server not running"

# Pinned apps persist across runs, so start the pin/unpin check from a clean
# slate. Reports and every other preference stay untouched.
"$ADB" -s "$SERIAL" shell run-as "$APP_PACKAGE" rm -f shared_prefs/pinned_apps.xml \
  >/dev/null 2>&1 || true

id_line="$("$ADB" -s "$SERIAL" shell "RISH_APPLICATION_ID=$SHIZUKU_PACKAGE sh /data/local/tmp/shizuku/rish -c id" 2>&1 | tr -d '\r')"
echo "$id_line" | grep -q 'uid=2000(shell)' || fail "rish is not running as uid 2000: $id_line"

# Restart the app. Navigation 3 lands on the reports home; capture is an
# explicit action there and must never run automatically.
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
"$ADB" -s "$SERIAL" shell am start -W -n "$APP_PACKAGE/.MainActivity" >/dev/null

layout="$(wait_for_pattern '"resource-id":"reports_home"' 20)" || fail "reports home did not appear"
layout="$(wait_for_pattern '"resource-id":"saved_report_' 10)" || fail "no saved report found on reports home"

for tag in btn_capture btn_import_report btn_reset_battery_stats saved_reports_header; do
  echo "$layout" | grep -q "\"resource-id\":\"$tag\"" || fail "$tag missing from reports home"
done

if echo "$layout" | grep -q '"resource-id":"tab_timeline"'; then
  fail "reports home should not show the viewer tab bar"
fi

mapfile -t saved_reports < <(saved_report_names "$layout")
[[ ${#saved_reports[@]} -gt 0 ]] || fail "no saved report found on reports home"

open_report() {
  local name="$1"
  "$ANDROID_CLI" input --device="$SERIAL" tap "id=saved_report_$name" >/dev/null 2>&1 \
    || fail "could not open saved report $name"
  wait_for_pattern '"resource-id":"content_screen"' 20 >/dev/null \
    || fail "report viewer did not open"
  wait_for_pattern '"resource-id":"timeline_screen"' 30 >/dev/null \
    || fail "saved report did not load"
}

open_timeline() {
  "$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_timeline' >/dev/null 2>&1 \
    || fail "could not open Timeline tab"
  wait_for_pattern '"resource-id":"timeline_screen"' 15 >/dev/null \
    || fail "timeline screen missing"
}

report_name="${saved_reports[0]}"
open_report "$report_name"
layout="$(wait_for_pattern '"resource-id":"timeline_screen"' 30)" || fail "saved report did not load"

for tag in tab_timeline tab_apps tab_other; do
  echo "$layout" | grep -q "\"resource-id\":\"$tag\"" || fail "viewer bottom navigation missing $tag"
done
for legacy in tab_overview tab_system; do
  if echo "$layout" | grep -q "\"resource-id\":\"$legacy\""; then
    fail "removed tab is still present: $legacy"
  fi
done

for legacy in 'Current capture' 'Ready to capture' 'Capture validated' 'Capture parsed' 'Live data from dumpsys batterystats'; do
  if echo "$layout" | grep -q "$legacy"; then
    fail "legacy header/content still visible: $legacy"
  fi
done

logs="$("$ADB" -s "$SERIAL" logcat -d -v brief | grep -E 'HistoryParser:|CheckinParser:' || true)"
echo "$logs" | grep -q 'HistoryParser: entries=' || fail "history parser did not log a result"
echo "$logs" | grep -q 'CheckinParser: apps=' || fail "checkin parser did not log a result"

# Timeline: one named row per metric, data-change arrows and a sticky readout.
open_timeline
layout="$(wait_for_pattern '"resource-id":"timeline_cursor_readout"' 10)" || fail "timeline cursor readout missing"
echo "$layout" | grep -q '"resource-id":"timeline_prev_change"' || fail "previous data-change arrow missing"
echo "$layout" | grep -q '"resource-id":"timeline_next_change"' || fail "next data-change arrow missing"
echo "$layout" | grep -q '"resource-id":"btn_timeline_filter"' || fail "timeline metric filter missing"
if echo "$layout" | grep -q 'timeline_zoom_in'; then
  fail "manual zoom controls should be removed"
fi
if echo "$layout" | grep -q 'discharge_intervals'; then
  fail "discharge intervals list should be removed"
fi

# Other: capacity lives here, and report details are always expanded.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_other' >/dev/null 2>&1 || fail "could not open Other tab"
layout="$(wait_for_pattern '"resource-id":"other_screen"' 10)" || fail "other screen missing"
echo "$layout" | grep -q '"resource-id":"system_summary"' || fail "system battery card missing"
echo "$layout" | grep -q 'Capacity' || fail "battery capacity missing"
layout="$(wait_for_pattern 'Checkin version' 10)" || fail "report details are not expanded by default"
if echo "$layout" | grep -q '"resource-id":"btn_report_details"'; then
  fail "report details should not have an expand toggle"
fi

# Apps: searchable rows, pinning, and capacity is not repeated here.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=tab_apps' >/dev/null 2>&1 || fail "could not open Apps tab"
layout="$(wait_for_pattern '"resource-id":"app_search"' 10)" || fail "app search missing"
echo "$layout" | grep -q '"resource-id":"app_list"' || fail "app list missing"
if echo "$layout" | grep -q 'Apps by power'; then
  fail "redundant Apps by power header is still visible"
fi
if echo "$layout" | grep -q 'Capacity'; then
  fail "battery capacity is repeated on the Apps tab"
fi
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=app_row_0' >/dev/null 2>&1 \
  || fail "could not open the first app"
wait_for_app_windows 2 10 || fail "app detail sheet did not open"
wait_for_pattern 'Pin app' 10 >/dev/null || fail "app detail pin action missing"
"$ANDROID_CLI" input --device="$SERIAL" tap 'desc=Pin app' >/dev/null 2>&1 \
  || fail "could not pin the first app"
"$ADB" -s "$SERIAL" shell input keyevent 4 >/dev/null 2>&1 || true
wait_for_app_windows 1 10 || fail "app detail sheet did not close"
wait_for_pattern '"resource-id":"pinned_apps_header"' 10 >/dev/null \
  || fail "pinned apps section missing"
wait_for_pattern '"resource-id":"pinned_separator"' 10 >/dev/null \
  || fail "pinned apps separator missing"
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=app_row_0' >/dev/null 2>&1 \
  || fail "could not reopen the pinned app"
wait_for_app_windows 2 10 || fail "pinned app detail sheet did not open"
wait_for_pattern 'Unpin app' 10 >/dev/null || fail "app detail unpin action missing"
"$ANDROID_CLI" input --device="$SERIAL" tap 'desc=Unpin app' >/dev/null 2>&1 \
  || fail "could not unpin the first app"
"$ADB" -s "$SERIAL" shell input keyevent 4 >/dev/null 2>&1 || true
wait_for_app_windows 1 10 || fail "pinned app detail sheet did not close"
sleep 1
if "$ANDROID_CLI" layout --device="$SERIAL" --flat --full 2>/dev/null \
  | grep -q '"resource-id":"pinned_apps_header"'; then
  fail "pinned apps section did not disappear after unpinning"
fi

# Back to the reports home. The viewer tab bar must not follow us there.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=btn_back' >/dev/null 2>&1 \
  || "$ADB" -s "$SERIAL" shell input keyevent 4 >/dev/null 2>&1 || true
layout="$(wait_for_pattern '"resource-id":"reports_home"' 10)" || fail "did not return to reports home"
for _ in $(seq 1 5); do
  if ! echo "$layout" | grep -q '"resource-id":"tab_timeline"'; then
    break
  fi
  sleep 1
  layout="$("$ANDROID_CLI" layout --device="$SERIAL" --flat --full 2>/dev/null || true)"
done
if echo "$layout" | grep -q '"resource-id":"tab_timeline"'; then
  fail "viewer tab bar should not be visible on reports home"
fi

# Raw report: the home exposes the raw sheet for every saved report.
report_name="$(first_saved_report_name "$layout")"
[[ -n "$report_name" ]] || fail "no saved report found after returning home"
"$ANDROID_CLI" input --device="$SERIAL" tap "id=btn_view_raw_$report_name" >/dev/null 2>&1 \
  || fail "could not open raw report $report_name"
wait_for_app_windows 2 10 || fail "raw report sheet did not open"
"$ANDROID_CLI" input --device="$SERIAL" tap "txt=Raw capture" >/dev/null 2>&1 \
  || fail "raw report sheet content missing"
"$ADB" -s "$SERIAL" shell input keyevent 4 >/dev/null 2>&1 || true
wait_for_app_windows 1 10 || fail "raw report sheet did not close"

# Reset: open the confirmation dialog and cancel it. Never confirm the
# destructive `dumpsys batterystats --reset`.
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=btn_reset_battery_stats' >/dev/null 2>&1 \
  || fail "could not open reset confirmation"
wait_for_app_windows 2 10 || fail "reset confirmation dialog did not open"
"$ANDROID_CLI" input --device="$SERIAL" tap 'id=btn_reset_cancel' >/dev/null 2>&1 \
  || "$ANDROID_CLI" input --device="$SERIAL" tap 'txt=Cancel' >/dev/null 2>&1 \
  || fail "could not cancel reset confirmation"
wait_for_app_windows 1 10 || fail "reset confirmation dialog did not close"
layout="$(wait_for_pattern '"resource-id":"btn_reset_battery_stats"' 10)" \
  || fail "reset action missing after cancel"

echo "OK: reports home, viewer tabs, timeline cursor, other details, apps, back, raw sheet, reset cancel"
