# Porting matrix

Source: `third_party/battery-historian` (Apache 2.0, commit 6da43a3).
Status values: `todo`, `in progress`, `ported`, `adapted`, `skipped`.

| Upstream | Target Kotlin package | Status | Notes |
|---|---|---|---|
| `historianutils` | `core.historianutils` | ported | named regex groups, helpers |
| `bugreportutils` | `core.bugreport` | ported | zip/text, metadata, checkin extraction |
| `packageutils` | `core.packageinfo` | ported | UID/package mapping, bugreport extraction |
| `csv` | `core.history.csv` | ported | state machine + CSV |
| `sliceparse` | `core.checkin.sliceparse` | ported | colon-delimited slices |
| `checkinutil` | `core.checkin.checkinutil` | ported | counters, report parser |
| `parseutils` | `core.history.parseutils` | ported (vertical slice; backlog) | history state machine; largest port |
| `checkinparse` | `core.checkin.checkinparse` | ported (vertical slice; backlog) | version 11–21 plus modern adapter |
| `aggregated` | `core.analytics` | todo | system stats |
| `presenter` | `core.analytics` | todo | data only, no HTML |
| `activity` | `core.logs` | later | event/system/last logcat |
| `broadcasts` | `core.logs` | later | broadcast CSV |
| `dmesg` | `core.logs` | later | kernel dmesg |
| `wakeupreason` | `core.logs` | later | suspend/resume |
| `kernel` | `core.logs` | later | kernel trace |
| `powermonitor` | `core.logs` | later | power monitor file |
| `wearable` | `core.logs` | later | wearable section |
| `analyzer` | n/a | skipped | HTTP/template orchestration replaced by `ReportParser` |
| `js`, `templates`, `static` | n/a | skipped | replaced by Compose |
| `scripts/historian.py` | n/a | skipped | legacy plot; V2 CSV path is used |

## Test sources

- 79 Go tests total. `parseutils` has 54.
- Port pure Go tests to JUnit alongside the Kotlin code.
- The Go oracle (tools/oracle) provides golden outputs for end-to-end fixtures.
