# Emulator setup

## Image and AVD

The host uses API 36.1 Google APIs x86_64. The system image was re-downloaded
after cleanup:

```bash
android sdk install "system-images;android-36.1;google_apis;x86_64"
```

Create/start:

```bash
source scripts/env.sh
./scripts/emulator.sh create
./scripts/emulator.sh start
```

`scripts/emulator.sh` creates `battery_api36` with:

- Pixel 10 profile, 1080×2424, 420 dpi
- 4 GB RAM, 4 vCPUs, 8 GB data
- SwiftShader/ANGLE (`-gpu swangle`)
- cold boot, no window, no audio

Do not use `-gpu swiftshader_indirect`; emulator 37.2.12 segfaults after
initialization on this host with that renderer. `-gpu swangle` boots reliably.

## Shizuku

```bash
./scripts/shizuku.sh install
./scripts/shizuku.sh start
./scripts/shizuku.sh status
./scripts/shizuku.sh rish
```

The start command resolves the app's native `libshizuku.so`:

```bash
adb shell /data/app/.../moe.shizuku.privileged.api-.../lib/x86_64/libshizuku.so
```

`rish -c id` should print `uid=2000(shell)`.

The app is pre-authorized for agentic runs by:

```bash
./scripts/shizuku.sh seed
```

This grants `moe.shizuku.manager.permission.API_V23` to the app, writes the
Shizuku config entry with `flags: 2`, and restarts the server.

## Ready state

```bash
./scripts/ready.sh
```

This boots the emulator, installs/starts Shizuku, builds/installs the app, seeds
permission, launches the app, and waits for the reports home with at least one
saved report.

## Agent UI

```bash
./scripts/agent-ui.sh layout          # compact UI tree
./scripts/agent-ui.sh tap 'id=btn_capture'
./scripts/agent-ui.sh scroll 'txt=Saved reports'
./scripts/agent-ui.sh shot            # small JPEG
./scripts/agent-ui.sh logs
```

Prefer `android layout` / `android input` over pixel coordinates.

## Battery simulation

```bash
./scripts/emulator.sh battery 42 discharging
./scripts/emulator.sh battery 90 charging
```

The app captures whatever `dumpsys batterystats -c` currently reports.

## Troubleshooting

- **Emulator segfaults after "emulatorSetupEnvironment":** use `-gpu swangle`.
- **"Running multiple emulators with the same AVD":** stale lock from a crash.
  Ensure no emulator process is running, remove
  `~/.android/avd/battery_api36.avd/*.lock`, and start again.
- **Gradle cannot find Java:** use
  `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`.
- **App shows "Permission required" after seed:** ensure the app is installed,
  rerun `./scripts/shizuku.sh seed`, then force-stop/relaunch the app.
- **Shizuku server dies after reboot:** expected in ADB mode; rerun
  `./scripts/shizuku.sh start`.
