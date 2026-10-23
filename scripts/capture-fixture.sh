#!/usr/bin/env bash
# Capture a raw API 36 `dumpsys batterystats -c` fixture from the running emulator.
#
# Usage:
#   scripts/capture-fixture.sh [fixture-name] [--force] [--oracle]
#
# The default fixture name is `api36`. The capture is written to
# tools/fixtures/<name>/checkin.txt with metadata.json next to it.
#
# --oracle also runs tools/oracle/run.sh and writes expected/ outputs.
# Oracle generation is optional so a missing container engine never blocks a
# capture. The oracle is development-only and is never part of the APK.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/env.sh
source "$ROOT/scripts/env.sh"

NAME="api36"
FORCE=0
RUN_ORACLE=0
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
    --oracle) RUN_ORACLE=1 ;;
    --help|-h)
      sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    -*)
      echo "error: unknown option $arg" >&2
      exit 2
      ;;
    *)
      NAME="$arg"
      ;;
  esac
done

if [[ ! "$NAME" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "error: fixture name must match [A-Za-z0-9._-]+" >&2
  exit 2
fi

DIR="$ROOT/tools/fixtures/$NAME"
CHECKIN="$DIR/checkin.txt"
META="$DIR/metadata.json"
if [[ -e "$CHECKIN" && "$FORCE" != "1" ]]; then
  echo "error: $CHECKIN already exists; pass --force to overwrite" >&2
  exit 1
fi

if ! "$ADB" -s "$SERIAL" get-state >/dev/null 2>&1; then
  echo "error: device $SERIAL is not available; start it with ./scripts/emulator.sh start" >&2
  exit 1
fi

mkdir -p "$DIR"
TMP="$DIR/.checkin.txt.tmp"
trap 'rm -f "$TMP"' EXIT

SOURCE_COMMAND="adb -s $SERIAL exec-out dumpsys batterystats -c"
"$ADB" -s "$SERIAL" exec-out dumpsys batterystats -c > "$TMP"

if [[ ! -s "$TMP" ]]; then
  echo "error: capture is empty" >&2
  exit 1
fi
if ! grep -q ',i,vers,' "$TMP"; then
  echo "error: capture does not contain a batterystats vers line" >&2
  exit 1
fi
mv "$TMP" "$CHECKIN"
trap - EXIT

sdk="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ ! "$sdk" =~ ^[0-9]+$ ]]; then
  echo "error: could not read numeric ro.build.version.sdk (got '$sdk')" >&2
  exit 1
fi
release="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
fingerprint="$("$ADB" -s "$SERIAL" shell getprop ro.build.fingerprint | tr -d '\r')"
build_id="$("$ADB" -s "$SERIAL" shell getprop ro.build.id | tr -d '\r')"
build_type="$("$ADB" -s "$SERIAL" shell getprop ro.build.type | tr -d '\r')"
model="$("$ADB" -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
device="$("$ADB" -s "$SERIAL" shell getprop ro.product.device | tr -d '\r')"
captured_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
bytes="$(wc -c < "$CHECKIN" | tr -d ' ')"
lines="$(wc -l < "$CHECKIN" | tr -d ' ')"
sha256="$(sha256sum "$CHECKIN" | cut -d' ' -f1)"

jq -n \
  --arg fixture "$NAME" \
  --arg captured_at_utc "$captured_at" \
  --arg serial "$SERIAL" \
  --argjson sdk "$sdk" \
  --arg release "$release" \
  --arg fingerprint "$fingerprint" \
  --arg build_id "$build_id" \
  --arg build_type "$build_type" \
  --arg model "$model" \
  --arg device "$device" \
  --arg source_command "$SOURCE_COMMAND" \
  --argjson bytes "$bytes" \
  --argjson lines "$lines" \
  --arg sha256 "$sha256" \
  '{
    fixture: $fixture,
    captured_at_utc: $captured_at_utc,
    serial: $serial,
    sdk: $sdk,
    release: $release,
    build_fingerprint: $fingerprint,
    build_id: $build_id,
    build_type: $build_type,
    model: $model,
    device: $device,
    source_command: $source_command,
    bytes: $bytes,
    lines: $lines,
    sha256: $sha256,
    notes: "Raw dumpsys batterystats -c output including checkin aggregates and history. Captured via adb exec-out so line endings are preserved."
  }' > "$META"

echo "captured $CHECKIN"
echo "metadata $META"
echo "sdk=$sdk release=$release lines=$lines bytes=$bytes"
echo "sha256=$sha256"

if [[ "$RUN_ORACLE" == "1" ]]; then
  rm -rf "$DIR/expected"
  "$ROOT/tools/oracle/run.sh" \
    -input "tools/fixtures/$NAME/checkin.txt" \
    -out-dir "tools/fixtures/$NAME/expected"
fi
