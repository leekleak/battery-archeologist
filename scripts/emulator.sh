#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

SYSTEM_IMAGE="system-images;android-36.1;google_apis;x86_64"
AVD_DIR="$HOME/.android/avd/$AVD_NAME.avd"

create_avd() {
  if "$EMULATOR" -list-avds | grep -qx "$AVD_NAME"; then
    echo "AVD $AVD_NAME already exists"
  else
    echo "Creating AVD $AVD_NAME from $SYSTEM_IMAGE"
    echo "no" | "$AVDMANAGER" create avd \
      -n "$AVD_NAME" \
      -k "$SYSTEM_IMAGE" \
      -d pixel_10 \
      --force
  fi

  local config="$AVD_DIR/config.ini"
  [[ -f "$config" ]] || { echo "Missing $config"; exit 1; }

  set_config() {
    local key="$1" value="$2"
    if grep -qE "^${key}=" "$config"; then
      sed -i "s|^${key}=.*|${key}=${value}|" "$config"
    else
      printf '%s=%s\n' "$key" "$value" >> "$config"
    fi
  }

  set_config hw.ramSize 4096
  set_config hw.cpu.ncore 4
  set_config vm.heapSize 512
  set_config disk.dataPartition.size 8G
  set_config hw.gpu.enabled yes
  set_config hw.gpu.mode swangle
  set_config hw.audioInput no
  set_config hw.audioOutput no
  set_config hw.keyboard yes
  set_config showDeviceFrame no
  set_config fastboot.forceColdBoot yes
  set_config fastboot.forceFastBoot no
  set_config PlayStore.enabled false
  echo "Configured $config"
}

start_avd() {
  if "$ADB" -s "$SERIAL" get-state >/dev/null 2>&1; then
    echo "Emulator already running on $SERIAL"
  else
    # The Android CLI's start command overrides the GPU mode on this host and
    # crashes with the SwiftShader renderer. Launch directly with swangle.
    rm -f "$AVD_DIR"/*.lock
    echo "Starting $AVD_NAME (headless, cold boot, swangle)"
    nohup "$EMULATOR" \
      -avd "$AVD_NAME" \
      -no-window \
      -gpu swangle \
      -no-audio \
      -no-boot-anim \
      -no-snapshot \
      -accel on \
      -port 5554 \
      >"$ARTIFACTS/emulator.log" 2>&1 &
    disown || true
    wait_boot
  fi
  provision
}

wait_boot() {
  "$ADB" -s "$SERIAL" wait-for-device
  local tries=0
  until [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    tries=$((tries + 1))
    if (( tries > 180 )); then
      echo "Timed out waiting for boot"
      exit 1
    fi
    sleep 2
  done
  echo "Boot completed on $SERIAL"
}

provision() {
  "$ADB" -s "$SERIAL" shell settings put global device_provisioned 1 >/dev/null
  "$ADB" -s "$SERIAL" shell settings put secure user_setup_complete 1 >/dev/null
  "$ADB" -s "$SERIAL" shell settings put global window_animation_scale 0 >/dev/null
  "$ADB" -s "$SERIAL" shell settings put global transition_animation_scale 0 >/dev/null
  "$ADB" -s "$SERIAL" shell settings put global animator_duration_scale 0 >/dev/null
  "$ADB" -s "$SERIAL" shell settings put system screen_off_timeout 2147483647 >/dev/null
  "$ADB" -s "$SERIAL" shell svc power stayon true >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  echo "Provisioned $SERIAL"
}

stop_avd() {
  "$ANDROID_CLI" emulator stop "$AVD_NAME" 2>/dev/null || "$ADB" -s "$SERIAL" emu kill || true
}

status() {
  "$ADB" devices -l
  echo "---"
  if "$ADB" -s "$SERIAL" get-state >/dev/null 2>&1; then
    echo "serial: $SERIAL"
    echo "boot: $("$ADB" -s "$SERIAL" shell getprop sys.boot_completed | tr -d '\r')"
    echo "sdk: $("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "model: $("$ADB" -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
  else
    echo "No emulator on $SERIAL"
  fi
}

battery() {
  local level="${1:-50}"
  local mode="${2:-discharging}"
  "$ADB" -s "$SERIAL" emu power capacity "$level"
  if [[ "$mode" == "charging" ]]; then
    "$ADB" -s "$SERIAL" emu power ac on
    "$ADB" -s "$SERIAL" emu power status charging
  else
    "$ADB" -s "$SERIAL" emu power ac off
    "$ADB" -s "$SERIAL" emu power status discharging
  fi
  "$ADB" -s "$SERIAL" shell dumpsys battery | grep -E 'level|status|AC powered|USB powered' || true
}

case "${1:-}" in
  create) create_avd ;;
  start) create_avd; start_avd ;;
  stop) stop_avd ;;
  wait) wait_boot ;;
  provision) provision ;;
  status) status ;;
  battery) shift; battery "$@" ;;
  *) echo "usage: $0 {create|start|stop|wait|provision|status|battery <level> <charging|discharging>}"; exit 2 ;;
esac
