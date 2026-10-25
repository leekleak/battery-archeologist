# Implementation status

## Working now

- Headless `battery_api36` AVD (API 36.1, Google APIs, swangle).
- Shizuku 13.6.0 ADB-mode server with a Shizuku user-service runner.
- Navigation 3 host with the reports home as the root (`ReportsKey`): capture,
  import, saved reports, raw/share/delete, and a system battery-stats reset all
  live there. Opening a saved report pushes the three-tab viewer
  (`ReportViewerKey`) with Timeline (the default), Apps, and Other.
- On-demand Shizuku permission screen (`PermissionKey`); reports stay viewable
  without Shizuku, and capture/reset request access only when needed.
- Privileged `dumpsys batterystats -c` capture, structural validation, raw
  preview, and on-device report storage. Capture and import stay on the reports
  home and add a saved report instead of opening the viewer.
- Destructive `dumpsys batterystats --reset` action behind an explicit
  confirmation dialog; saved reports are kept.
- API 36 history parsing: 400+ timeline entries and a vertically scrolling
  Timeline tab with one named row per metric, a persisted metric filter, a
  draggable inspection cursor, previous/next filtered data-change arrows, and a
  sticky cursor readout with a blurred pinned header, formatted values,
  second-precision timestamps, and long-press tooltips.
- API 36 checkin parsing: 70+ apps, battery capacity/realtime system cards,
  top apps by CPU, with compatibility warnings for newer fields.
- Saved reports can be reopened and re-parsed; text/zip bugreports and raw
  checkin files can be imported through the system file picker.
- Apps are ranked by estimated device power and show percentages rounded to
  thousandths (for example `0.006%`); the detail sheet adds device mAh, CPU
  time, foreground, wakeups, traffic, camera, audio, and flashlight. Per-app
  CPU power is intentionally omitted: Android 8.0+ hard-codes the checkin CPU
  power field to 0, and the device-power estimate already includes the CPU
  attribution.
- Other keeps the battery capacity/realtime card next to always-expanded report
  details (parser internals and file metadata).
- Saved reports can be shared as attachments through the system share sheet.
- Full debug and release builds pass, including R8 and Wire code generation.
- Agentic scripts: `ready`, `smoke`, `layout`, `tap`, `text`, `scroll`, `shot`,
  `logs`.

## Ported modules

- `historianutils`, `bugreportutils`, `packageutils`
- `history/csv`, `history/parseutils`, `checkin/sliceparse`,
  `checkin/checkinutil`, `checkin/checkinparse`
- Wire-generated Kotlin models from the upstream `.proto` files

## Remaining backlog

1. **System power breakdown** (pws/pwi totals and power-use items) and a
   dedicated Power screen.
2. **M3 Expressive UI shell** — see `docs/ui-expressive.md` and
   `docs/session-handoff.md`. The Haze top-bar blur is a hard requirement and is
   implemented following Traffic Light's `HazeScaffold`/`PageTitle` pattern.
   Still open: expressive cards, animations, and a contrast audit. The
   permission Sunny hero is done. Colors use Material You dynamic color on
   Android 12+ (stock Material schemes below that); typography stays stock
   Material.
3. `parseutils`: low-power/RPM state machines and pretty-print helpers
   (connectivity and doze are now ported).
4. `checkinparse`: future report versions beyond v36, plus the remaining
   low-power/RPM state fields shared with `parseutils`.
5. UI: report comparison and an animation pass.
6. Full test matrix: API 29/31/34/36 fixtures, release installation, cold-boot
   recovery.
