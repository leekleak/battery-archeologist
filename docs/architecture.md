# Architecture

## Modules

```
:app        Compose UI, navigation, state, report/capture repositories
:core       Pure Kotlin/JVM models and Battery Historian parser port
:shizuku    Android library: Shizuku binder state + user service shell bridge
```

`third_party/battery-historian` is source-only reference material and a
development oracle. It is not built into the app.

## Capture path

1. The app asks `ShizukuGateway` for permission state.
2. The Shizuku user service (`BatteryArcheologistService`) runs in a separate
   process with UID 2000 in ADB mode.
3. `ShizukuGateway.runShell` sends a command to the user service and receives
   stdout/stderr in 16 KiB callback chunks, avoiding binder transaction limits.
4. `captureCheckin()` runs `dumpsys batterystats -c`. The `-c` flag is required:
   it includes the `9,h,...` battery history lines, unlike a bare `--checkin`.
5. The app validates the capture with `CheckinInspector` and will hand it to the
   Kotlin Battery Historian parser.

## Parser pipeline (planned)

```
RawCapture
  -> BugreportExtractor / metadata + package list
  -> CheckinParser  -> BatteryStats model (system + per-app)
  -> HistoryParser  -> TimelineEntry rows + ActivitySummary
  -> Analytics      -> system cards, app stats, histograms
  -> BatteryReport  -> Compose UI
```

- The CSV-compatible `TimelineEntry(metric, type, startMs, endMs, value, opt)`
  is the interchange format.
- Parser outputs are typed Kotlin models; CSV serialization exists for golden
  tests and parity with the Go oracle.
- The schemas in `core/src/main/proto` are compiled to Kotlin by Wire
  (`javaInterop = true`). Wire has no descriptor API, so `FieldTables.kt`
  mirrors the `.proto` declaration order for the checkin parser's positional
  columns.
- Parser errors/warnings are data, shown in a diagnostics panel.

## Modern Android compatibility

The upstream parser supports checkin report versions 11–21. Android 16 reports
version 36. History lines are stable; system/app sections need a version adapter.
The parser must warn and degrade rather than crash on unknown sections.

## UI

- Navigation 3 host. `ReportsKey` renders `ReportsHomeScreen` as the root;
  `ReportViewerKey(name)` pushes the report viewer; `PermissionKey` is the
  on-demand Shizuku setup screen.
- Reports home: capture and import (both stay on the home), saved report cards
  with open/raw/share/delete, and a destructive system battery-stats reset
  behind a confirmation dialog. It has no bottom navigation bar and is usable
  without Shizuku.
- Report viewer: four tabs (Overview, Timeline, System, Apps), a back action,
  and the `content_screen` host. It has no capture action; report contents load
  only when the viewer destination opens.
- Capture or reset without Shizuku opens `PermissionKey`; once the state is
  Ready the deferred action runs and the app returns to the home.
- Raw reports open in a modal sheet from the home (`raw_capture`), for saved
  reports and the latest unsaved capture.
- Reports can be reopened and re-parsed; text/zip bugreports and raw checkin
  files can be imported through the system file picker.
- Timeline: Compose Canvas with synchronized pan/zoom, battery-level overlay,
  grouped event rows, report-taken marker, and a table fallback.
- Test tags are exposed as resource IDs for `android layout` / UIAutomator.
