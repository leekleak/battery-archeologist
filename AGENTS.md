# Environment

- Android SDK: the project uses `$HOME/Android/Sdk`.
- JDK: `JAVA_HOME` defaults to the Gradle-provisioned Temurin 21 in
  `~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`. The system
  `/usr/lib/jvm/java-21-openjdk` is broken.
- Emulator: `battery_api36`, API 36.1 Google APIs x86_64, headless with
  `-gpu swangle`. Do not use `-gpu swiftshader_indirect`; it segfaults on this
  host with emulator 37.2.12.
- Target serial: `emulator-5554`. Always pass `-s emulator-5554` or set
  `ANDROID_SERIAL`; an offline physical device may also appear in `adb devices`.

# Quick start

```bash
source scripts/env.sh
./scripts/emulator.sh start
./scripts/shizuku.sh install
./scripts/shizuku.sh start
./gradlew :app:installDebug
./scripts/shizuku.sh seed          # grants the app and seeds its Shizuku entry
adb shell am start -W -n com.leekleak.batteryarcheologist/.MainActivity
```

`./scripts/ready.sh` runs the whole sequence.

# Agentic UI

Use the Android CLI first:

```bash
android layout --device=emulator-5554 --flat --pretty
android input --device=emulator-5554 tap "txt=Capture battery stats"
android input --device=emulator-5554 text "hello" "txt=Search"
android screen capture --device=emulator-5554 --output=artifacts/screen.png
# For scrollable Compose content, prefer the accessibility scroll command:
android input --device=emulator-5554 scroll --match first --dir=down \
  --until="txt=System stats" "clz=android.widget.ScrollView"
```

Screenshots are large; downscale them before reading:

```bash
magick artifacts/screen.png -resize 720x -quality 80 artifacts/screen.jpg
```

The app exposes Compose test tags as resource IDs (`btn_grant`, `btn_capture`,
`capture_summary`, `raw_preview`, etc.).

# Shizuku

- ADB mode only. Start the server with
  `adb shell $(adb shell pm path moe.shizuku.privileged.api | ...)/lib/x86_64/libshizuku.so`.
- The user-service runner is preferred over `Shizuku.newProcess`; the latter is
  private in Shizuku API 13.1.5.
- The app declares `moe.shizuku.manager.permission.API_V23` through the provider
  manifest, so `pm grant` plus a server restart can pre-authorize the app.
- Shizuku must be restarted after every emulator cold boot.

# Rules

- No commits; leave changes for overseer review.
- Traffic Light is GPL-3.0 and this project is GPL-3.0. Copying and adapting
  Traffic Light code and design is allowed; preserve attribution and the GPL
  notice. Do not import code with an incompatible license.
- Keep parser code in `:core` free of Android dependencies.
- Ported Battery Historian files retain their Apache 2.0 headers; Apache 2.0 is
  compatible with this GPL-3.0 project.
- Before any manual Gradle install/connected-test command, source
  `scripts/env.sh` or set `ANDROID_SERIAL=emulator-5554`. `installDebug` will
  otherwise deploy to every connected device, including physical phones.

# Verification

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
./scripts/ready.sh
./scripts/smoke.sh
./scripts/shizuku.sh status
```
