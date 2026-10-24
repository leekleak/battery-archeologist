# Implementation status

## Working now

- Headless `battery_api36` AVD (API 36.1, Google APIs, swangle).
- Shizuku 13.6.0 ADB-mode server with a Shizuku user-service runner.
- Permission gate → tabbed content screen (Overview, Timeline, System, Apps,
  Reports), with saved-report loading.
- Privileged `dumpsys batterystats -c` capture, structural validation, raw
  preview, and on-device report storage.
- API 36 history parsing: 400+ timeline entries, discharge intervals, and a
  vertically scrolling Timeline tab with global clock times and a draggable
  inspection cursor that spells out every row's value.
- API 36 checkin parsing: 70+ apps, battery capacity/realtime system cards,
  top apps by CPU, with compatibility warnings for newer fields.
- Saved reports can be reopened and re-parsed; text/zip bugreports and raw
  checkin files can be imported through the system file picker.
- Apps are ranked by estimated device power and show percentages rounded to
  thousandths (for example `0.006%`); the detail sheet adds device/CPU mAh,
  capacity, CPU, foreground, wakeups, traffic, camera, audio, and flashlight.
- Overview/System includes a top-power-consumers card.
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
   `docs/session-handoff.md`. The Haze top-bar blur is a hard requirement and
   must be restored following Traffic Light's `HazeScaffold`/`PageTitle`
   implementation. Still open: expressive cards/hero shapes, animations, and a
   contrast audit. Colors and typography stay stock Material.
3. `parseutils`: low-power/RPM state machines and pretty-print helpers
   (connectivity and doze are now ported).
4. `checkinparse`: v36 `wl` `bp` group and `st` 7th field, enum values 17–22,
   more v22–36 field gates.
5. UI: report comparison and an animation pass.
6. Full test matrix: API 29/31/34/36 fixtures, release installation, cold-boot
   recovery.
