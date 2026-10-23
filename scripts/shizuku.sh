#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

RISH_DIR="$ROOT/tools/shizuku/rish"

ensure_apk() {
  if [[ ! -f "$SHIZUKU_APK" ]]; then
    echo "Downloading Shizuku 13.6.0"
    curl -fL --retry 2 \
      -o "$SHIZUKU_APK" \
      "https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk"
  fi
}

resolve_lib() {
  local apk_path
  apk_path="$("$ADB" -s "$SERIAL" shell pm path "$SHIZUKU_PACKAGE" | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk' | head -1)"
  [[ -n "$apk_path" ]] || { echo "Shizuku is not installed"; exit 1; }
  dirname "$apk_path" | sed 's|$|/lib/x86_64/libshizuku.so|'
}

install_apk() {
  ensure_apk
  "$ADB" -s "$SERIAL" install -r -g "$SHIZUKU_APK"
}

start_server() {
  local lib
  lib="$(resolve_lib)"
  echo "Starting Shizuku: $lib"
  "$ADB" -s "$SERIAL" shell "$lib" | tee "$ARTIFACTS/shizuku-start.log"
  for _ in $(seq 1 30); do
    if "$ADB" -s "$SERIAL" shell pidof shizuku_server >/dev/null 2>&1; then
      echo "shizuku_server is running"
      return 0
    fi
    sleep 0.5
  done
  echo "Shizuku server did not start"
  return 1
}

stop_server() {
  local pid
  pid="$("$ADB" -s "$SERIAL" shell pidof shizuku_server 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$pid" ]]; then
    "$ADB" -s "$SERIAL" shell kill -9 "$pid" || true
  fi
  echo "Stopped Shizuku server"
}

push_rish() {
  rm -rf "$RISH_DIR"
  mkdir -p "$RISH_DIR"
  unzip -o -q "$SHIZUKU_APK" assets/rish assets/rish_shizuku.dex -d "$RISH_DIR"
  "$ADB" -s "$SERIAL" shell mkdir -p /data/local/tmp/shizuku
  "$ADB" -s "$SERIAL" push "$RISH_DIR/assets/rish" /data/local/tmp/shizuku/rish >/dev/null
  "$ADB" -s "$SERIAL" push "$RISH_DIR/assets/rish_shizuku.dex" /data/local/tmp/shizuku/rish_shizuku.dex >/dev/null
  "$ADB" -s "$SERIAL" shell chmod 755 /data/local/tmp/shizuku/rish
  "$ADB" -s "$SERIAL" shell chmod 400 /data/local/tmp/shizuku/rish_shizuku.dex
}

rish_id() {
  "$ADB" -s "$SERIAL" shell \
    "RISH_APPLICATION_ID=$SHIZUKU_PACKAGE sh /data/local/tmp/shizuku/rish -c id" | tr -d '\r'
}

seed_permission() {
  require_emulator
  local uid json tmp="/data/local/tmp/battery-shizuku.json"
  uid="$("$ADB" -s "$SERIAL" shell pm list packages -U | tr -d '\r' | grep "package:$APP_PACKAGE " | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1)"
  [[ -n "$uid" ]] || { echo "Install $APP_PACKAGE first"; exit 1; }
  "$ADB" -s "$SERIAL" shell pm grant "$APP_PACKAGE" moe.shizuku.manager.permission.API_V23 >/dev/null 2>&1 || true
  json="{\"version\":2,\"packages\":[{\"uid\":$uid,\"flags\":2,\"packages\":[\"$APP_PACKAGE\"]}]}"
  stop_server
  printf '%s' "$json" | "$ADB" -s "$SERIAL" shell "cat > $tmp"
  "$ADB" -s "$SERIAL" shell "mkdir -p /data/user_de/0/com.android.shell; cp $tmp /data/user_de/0/com.android.shell/shizuku.json; chmod 600 /data/user_de/0/com.android.shell/shizuku.json"
  echo "Seeded Shizuku permission for $APP_PACKAGE (uid $uid)"
  start_server
}

status() {
  echo "installed: $("$ADB" -s "$SERIAL" shell pm path "$SHIZUKU_PACKAGE" | tr -d '\r' || true)"
  echo "server: $("$ADB" -s "$SERIAL" shell pidof shizuku_server | tr -d '\r' || true)"
  push_rish
  echo "rish: $(rish_id || true)"
}

case "${1:-}" in
  install) install_apk ;;
  start) start_server ;;
  stop) stop_server ;;
  restart) stop_server; start_server ;;
  rish) push_rish; rish_id ;;
  seed) seed_permission ;;
  status) status ;;
  *) echo "usage: $0 {install|start|stop|restart|rish|seed|status}"; exit 2 ;;
esac
