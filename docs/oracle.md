# Development golden-output oracle

The oracle runs the **vendored** Battery Historian Go parsers against a captured
`dumpsys batterystats -c` file (or a bug report) and writes deterministic golden
outputs for the Kotlin port. It is development-only: nothing under `tools/` or
`scripts/` is compiled into the Android app or referenced by Gradle.

Upstream source: `third_party/battery-historian/` (Apache 2.0, see `LICENSE`).
The harness itself is in `tools/oracle/harness/main.go`.

## Why an oracle

The Kotlin parser is a port of upstream Go code. Unit tests built from invented
inputs only prove that the port agrees with itself. The oracle records what
upstream actually does with real device data, including upstream bugs and
version incompatibilities, so the port can be compared byte-for-byte.

## Prerequisites

- Docker **or** rootless Podman. The image is pinned to `golang:1.22-bookworm`
  for the build stage and `debian:bookworm-slim` at runtime.
- No host Go installation is required.
- For capturing fixtures: a running emulator/device (`emulator-5554` by default)
  with adb access. Shizuku is not needed for a direct `adb` capture. The capture
  script also uses `jq` and `sha256sum`.

`tools/oracle/run.sh` selects `docker` when `docker info` succeeds, otherwise
`podman`. Force one with `ORACLE_ENGINE=docker|podman`.

## Quick start

```bash
# 1. Capture an API 36 fixture (writes checkin.txt + metadata.json).
./scripts/capture-fixture.sh api36

# 2. Build the oracle image and generate expected outputs.
./tools/oracle/run.sh \
  -input tools/fixtures/api36/checkin.txt \
  -out-dir tools/fixtures/api36/expected

# 3. Reuse the image without rebuilding.
ORACLE_SKIP_BUILD=1 ./tools/oracle/run.sh \
  -input tools/fixtures/api36/checkin.txt \
  -out-dir tools/fixtures/api36/expected
```

`capture-fixture.sh --oracle` performs steps 1 and 2 in one command.
It refuses to overwrite an existing `checkin.txt` unless `--force` is passed.

## Oracle CLI

```
oracle -input <file> [-kind auto|checkin|bugreport] [-out-dir <dir>]
       [-scrub=true|false] [-strict]
```

- `-input`: raw `dumpsys batterystats -c` output, a text bug report, or a zip
  containing either. Plain text is classified by content.
- `-kind auto` (default) detects a bug report via the usual dumpstate/build
  fingerprint/section markers, otherwise a checkin capture via its
  `,i,vers,` line.
- `-out-dir`: destination for the files below. Relative paths are resolved
  inside the repository by `run.sh` (the repo is mounted at `/work`).
- `-scrub`: passes the upstream PII scrub flag to `AnalyzeHistory` (default
  true, matching the upstream `history-parse` default).
- `-strict`: exit non-zero on a hard checkin parser error or when no history
  summary was produced. Warnings and per-line history errors are expected on
  unsupported report versions, so they do not fail strict mode; they remain in
  the JSON for comparison.

The command prints a compact JSON manifest to stdout and writes `manifest.json`
to the output directory. The manifest is the stable summary for CI.

### Output files

| File | Producer | Contents |
| --- | --- | --- |
| `checkin-summary.json` | `checkinparse.ParseBatteryStats` | Report version, aggregation type, build ID/fingerprint, app list, system battery/misc/power/wakelock data, warnings, parser errors. |
| `history-summary.json` | `parseutils.AnalyzeHistory` with `FormatBatteryLevel` | `ActivitySummary` values, history parser errors, mapping errors, timestamp-altered flag. |
| `history-level.csv` | `parseutils.BatteryLevelSummariesToCSV` | Upstream level-summary CSV (header plus one row per level drop). |
| `history-timeline.csv` | `parseutils.AnalyzeHistory` with `FormatTotalTime` | Upstream event CSV (`metric,type,start_time,end_time,value,opt`), with rows sorted deterministically. |
| `manifest.json` | Harness | Counts and file list; also printed to stdout. |

Units:

- `checkin-summary.json` uses the units in the upstream proto field names:
  `*_ms` milliseconds, `*_usec` microseconds, `*_mah` mAh.
- `history-summary.json` is a direct JSON encoding of the Go
  `parseutils.ActivitySummary` struct. Field names are Go names and every
  `time.Duration` is encoded as **integer nanoseconds** (Go's default JSON
  encoding). This is deliberate: it preserves upstream precision. Convert to
  milliseconds for Kotlin comparisons.
- Timeline and level CSV timestamps are Unix milliseconds, matching upstream.

## Determinism

`encoding/json` sorts map keys, and the harness adds the following ordering on
top of upstream:

- checkin apps are sorted by `(uid, name)`;
- nested wakelock/sensor/job/sync/process/alarm lists are sorted;
- warnings and parser errors are sorted;
- system repeated fields (kernel wakelocks, power-use items, signal states,
  etc.) are sorted by name/number;
- timeline CSV data rows are sorted by start time, end time, then all columns.

The timeline sort is required because upstream `csv.PrintAllReset` and
`PrintActiveEvent` iterate Go maps, so the same event set can be written in a
different row order on each run. The event set is unchanged; only row order is
normalized. Re-running the oracle over the same fixture must produce
byte-identical files:

```bash
ORACLE_SKIP_BUILD=1 ./tools/oracle/run.sh \
  -input tools/fixtures/api36/checkin.txt \
  -out-dir tools/fixtures/_oracle-check
diff -r tools/fixtures/api36/expected tools/fixtures/_oracle-check
rm -rf tools/fixtures/_oracle-check
```

`run.sh` only mounts the repository; to write outside it, add another `-v`
mount to the engine invocation.

## Fixtures

### `tools/fixtures/api36/`

Real capture from the headless API 36.1 Google APIs x86_64 emulator.

- serial: `emulator-5554`
- SDK 36, Android 16 (`ro.build.version.release=16`)
- fingerprint: `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys`
- command: `adb -s emulator-5554 exec-out dumpsys batterystats -c`
- size: 1,536 lines / 46,125 bytes; SHA-256 in `metadata.json`
- capture note: the development emulator was exercised with
  `dumpsys batterystats --history-create-events 100` and its battery was driven
  from 100 down to 50, so the history contains generated events and real
  level-drop summaries. It is still a development-emulator state, not a pristine
  boot capture.

`metadata.json` is written by the capture script and records SDK, release,
fingerprint, build ID, build type, model, device, capture time, byte/line counts,
SHA-256, and the exact source command.

Observed oracle status for this fixture:

- report version 36, aggregation `SINCE_CHARGED`;
- 70 apps in the checkin summary;
- 164 checkin warnings, 0 checkin parser errors;
- 73 history parser errors (unknown/new history keys);
- 4 history summaries: `RESET` 100→100, `LEVEL` 70→60, `LEVEL` 60→50,
  `END` 50→50;
- `history-level.csv` has 2 data rows (the two level drops);
- `history-timeline.csv` has 263 events.

To reproduce exactly, use the checked-in `checkin.txt`; a new capture of a live
emulator will differ because the device state changes.

### `tools/fixtures/synthetic/`

A small synthetic history extracted from the vendored upstream test
`parseutils.TestFixTimeline` (`report version 12`). It is not device data, but
it exercises the supported summary and level-CSV paths: 6 summaries, including
level drops 44→43 and 58→57, and 2 expected upstream parser errors
(`consecutive +r`, `two negative transitions for Audio`). Use it as a
known-good baseline while the API 36 incompatibilities are worked through.

## Known incompatibilities

The vendored parser supports checkin report versions **11–21**
(`checkinparse.minParseReportVersion` / `maxParseReportVersion`). The API 36
emulator reports **version 36**.

### Checkin parser (API 36)

`checkinparse.ParseBatteryStats` continues past the version check but records
`newer report version 36 found`. Field layouts changed in later Android
releases, so the golden output includes upstream's warnings and whatever values
it could recover. In the checked-in fixture the warnings include:

- `wl` records have 6 extra fields (current/max/total durations); `st`, `jbc`
  and user-activity records also have additional fields.
- Unknown sections: `awl`, `ctf`, `fgs`, `jbc`, `wmct`, `gcf`, `rpm`.
- Power-use item names unknown to the proto enum: `cpu`, `camera`, `audio`,
  `video`, `sensors`, `gnss`, `memory`, `ambi`.
- `bt`, `dc`, `dct`, and `dcc` lines have additional trailing fields.
- Some UIDs appear in `pwi` and `cpu` records that the package list does not
  contain.

Consequences visible in `api36/expected/checkin-summary.json`: many kernel
wakelocks have empty names and zero times, and app wakelocks often have only a
name with zero durations. Partial-wakelock values recovered from version 36 are
not reliable. **Do not treat these values as authoritative Kotlin port
requirements.** They are a record of upstream behavior on an unsupported report
version. The Kotlin parser must implement the Android 16 layouts explicitly and
be tested against its own fixtures.

### History parser (API 36)

The history section uses keys the parser does not know:

- `Mrc` (modem rail charge), `Wrc` (wifi rail charge);
- `Chtp` (charging time remaining / time-to-full);
- `Esc` (a new energy-related key in Android 16; the vendored parser has no
  mapping for it);
- `SubsystemPowerState` (a subsystem power-state event).

It also emits transition errors for repeated positives (`+Etp`, `+Ejb`, `+Etw`)
because history resets or new event semantics produce transitions the parser
does not expect. These are recorded in `history-summary.json` rather than
aborting, so the Kotlin port has a complete record of where upstream diverges.

Upstream only summarizes periods that are actively discharging
(`summary.Active=true`); charging periods are excluded. `BatteryLevelSummariesToCSV`
only writes rows where `InitialBatteryLevel > FinalBatteryLevel`. The checked-in
API 36 fixture contains two such drops (70→60 and 60→50) because its battery was
simulated down to 50, but a fixture captured while charging at a steady level
will legitimately have zero summaries.

### Bug reports

A bug report input must contain a `CHECKIN BATTERYSTATS` section. The harness
passes that section to both checkin and history parsing. Full bug reports also
provide the package list, which improves app names; raw `-c` captures rely on
the `uid`/`apk` lines in the checkin output. A bug report whose checkin section
has no `9,h,...` history lines will produce an empty `history-summary.json`.

## Upstream command binaries

The image also contains the untouched upstream commands at
`/opt/upstream/history-parse` and `/opt/upstream/checkin-parse` for manual
comparison. They expect a bug report (not a raw `-c` file):

```bash
podman run --rm --userns=keep-id -v "$PWD:/work:z" -w /work \
  --entrypoint /opt/upstream/checkin-parse \
  battery-archeologist-oracle:local -input=/work/report.txt -output=/work/checkin.proto
```

## This host: Docker blocker, Podman workaround

The Docker CLI is installed, but `/var/run/docker.sock` is owned by
`root:docker` with mode `0660` and the development user is not in the `docker`
group. `docker info` fails with:

```
permission denied while trying to connect to the Docker API at unix:///var/run/docker.sock
```

`sudo` requires a password, so the agent cannot use the system Docker daemon.
Rootless **Podman 5.8.9** works. `run.sh` detects this automatically and builds
the same Dockerfile with `podman build`; on this host, `--userns=keep-id` is
used so output files stay owned by the invoking user. The image was successfully
pulled and built, so the blocker is socket permissions only, not image
availability or network access.

## Licensing and scope

- The oracle is a development tool and must never ship in the Android app.
- The vendored Battery Historian source and the synthetic fixture extracted
  from its test suite remain Apache 2.0; retain upstream headers.
- No Gradle file, app source, shizuku source, or core parser source is changed
  by this tooling.
