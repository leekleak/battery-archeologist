# Battery Archeologist

An on-device recreation of [Android Battery Historian](https://github.com/google/battery-historian),
using Shizuku for privileged battery-stat capture and a native Compose UI.

## Current state

This repository is at the first working vertical slice:

- Gradle project with `:app`, `:core`, and `:shizuku`.
- Shizuku permission gate and a working ADB-mode user-service capture path.
- Quick capture of `dumpsys batterystats -c` with structural validation and
  on-device storage.
- Permission gate, then a tabbed content host: Overview, Timeline, System,
  Apps, Reports.
- Ported history parser producing a real Compose timeline, discharge
  intervals, doze, and network-connectivity events; ported checkin parser and
  per-app power predictions rounded to thousandths, with sortable app/power
  lists and detail sheets.
- Full debug/release builds pass, including R8 and protobuf.
- Go oracle fixtures for regression testing.
- Headless emulator + agentic scripts.
- Saved reports can be reopened, shared, and re-parsed; text/zip bugreports
  and raw checkin files can be imported through the system file picker.
- M3 Expressive first pass: tonal gradient, large shapes, Google Sans Flex,
  app detail bottom sheet, and a floating bottom toolbar with icons.
- GPL-3.0; Traffic Light code/design reuse is allowed by the same author.

Full app/system drill-down, import, and comparison are the next waves. See
`docs/implementation-status.md`, `docs/architecture.md`, `docs/ui-expressive.md`,
and `AGENTS.md`.

## Requirements

- JDK 21 (the Gradle-provisioned Temurin 21 on this host works).
- Android SDK with platform 37.2, build-tools 36, and the API 36.1 Google APIs
  x86_64 system image.
- Shizuku 13.6.0 installed in the emulator/device; ADB mode only.

## Quick start

```bash
source scripts/env.sh
./scripts/ready.sh
```

Manual:

```bash
./scripts/emulator.sh start
./scripts/shizuku.sh install
./scripts/shizuku.sh start
./gradlew :app:installDebug
./scripts/shizuku.sh seed
adb shell am start -W -n com.leekleak.batteryarcheologist/.MainActivity
```

## Agentic controls

```bash
./scripts/agent-ui.sh layout
./scripts/agent-ui.sh tap "txt=Capture battery stats"
./scripts/agent-ui.sh shot
./scripts/agent-ui.sh logs
```

## Licensing

Battery Archeologist is licensed under **GPL-3.0** (see `LICENSE`).
It includes a Kotlin port of Battery Historian under Apache 2.0; those files
retain their upstream headers, and Apache 2.0 is compatible with GPL-3.0.
Traffic Light is also GPL-3.0 and by the same author; its code and design may be
reused here with attribution. See `THIRD_PARTY_NOTICES.md`.
