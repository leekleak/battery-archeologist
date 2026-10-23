#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

cmd="${1:-}"; shift || true
case "$cmd" in
  layout)
    "$ANDROID_CLI" layout --device="$SERIAL" --flat --pretty "$@"
    ;;
  tap)
    [[ $# -ge 1 ]] || { echo "usage: $0 tap <selector>"; exit 2; }
    "$ANDROID_CLI" input --device="$SERIAL" tap "$1"
    ;;
  text)
    [[ $# -ge 2 ]] || { echo "usage: $0 text <selector> <text>"; exit 2; }
    "$ANDROID_CLI" input --device="$SERIAL" text "$2" "$1"
    ;;
  key)
    [[ $# -ge 1 ]] || { echo "usage: $0 key <back|home|enter|...>"; exit 2; }
    "$ANDROID_CLI" input --device="$SERIAL" key "$1"
    ;;
  shot)
    out="${1:-$ARTIFACTS/screen.png}"
    "$ANDROID_CLI" screen capture --device="$SERIAL" --output="$out"
    jpg="${out%.png}.jpg"
    if command -v magick >/dev/null 2>&1; then
      magick "$out" -resize 720x -quality 80 "$jpg"
      echo "$jpg"
    else
      echo "$out"
    fi
    ;;
  rawshot)
    out="${1:-$ARTIFACTS/screen.png}"
    "$ANDROID_CLI" screen capture --device="$SERIAL" --output="$out"
    echo "$out"
    ;;
  scroll)
    [[ $# -ge 1 ]] || { echo "usage: $0 scroll <selector> [down|up]"; exit 2; }
    "$ANDROID_CLI" input --device="$SERIAL" scroll --match first \
      --dir="${2:-down}" --until="$1" "clz=android.widget.ScrollView"
    ;;
  logs)
    if [[ $# -eq 0 ]]; then
      "$ADB" -s "$SERIAL" logcat -d -v brief | grep -iE 'battery|shizuku|AndroidRuntime|FATAL' | tail -200
    else
      "$ADB" -s "$SERIAL" logcat -d -v threadtime "$@"
    fi
    ;;
  logcat-clear)
    "$ADB" -s "$SERIAL" logcat -c
    ;;
  launch)
    "$ADB" -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
    "$ADB" -s "$SERIAL" shell am start -W -n "$APP_PACKAGE/.MainActivity"
    ;;
  *)
    echo "usage: $0 {layout|tap|text|key|shot|rawshot|scroll|logs|logcat-clear|launch}"
    exit 2
    ;;
esac
