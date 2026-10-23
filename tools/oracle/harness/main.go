// Copyright 2026 The Battery Archeologist Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Command oracle runs the vendored Battery Historian parsers over a raw
// `dumpsys batterystats -c` capture or a bugreport and writes deterministic
// golden outputs for the Kotlin port.
//
// The harness deliberately does not reimplement parsing: it calls
// checkinparse.ParseBatteryStats and parseutils.AnalyzeHistory directly.
// Only presentation is ours: map-derived app order is sorted, warnings are
// sorted, and timeline CSV rows are sorted so that repeated runs are
// byte-identical even where upstream iterates Go maps.
package main

import (
	"bytes"
	"encoding/csv"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"

	"github.com/golang/protobuf/proto"
	"github.com/google/battery-historian/bugreportutils"
	"github.com/google/battery-historian/checkinparse"
	"github.com/google/battery-historian/checkinutil"
	"github.com/google/battery-historian/packageutils"
	"github.com/google/battery-historian/parseutils"
	bspb "github.com/google/battery-historian/pb/batterystats_proto"
	sessionpb "github.com/google/battery-historian/pb/session_proto"
	usagepb "github.com/google/battery-historian/pb/usagestats_proto"
)

const oracleVersion = "1"

func main() {
	var (
		inputPath = flag.String("input", "", "Raw `dumpsys batterystats -c` file, bugreport, or zip containing either.")
		kind      = flag.String("kind", "auto", "Input kind: auto, checkin, or bugreport.")
		outDir    = flag.String("out-dir", ".", "Directory to write golden files to.")
		scrubPII  = flag.Bool("scrub", true, "Whether upstream ScrubPII is applied to addresses.")
		strict    = flag.Bool("strict", false, "Exit non-zero on checkin parser errors or when no history summaries were produced.")
	)
	flag.Parse()

	if *inputPath == "" {
		fmt.Fprintln(os.Stderr, "oracle: -input is required")
		flag.Usage()
		os.Exit(2)
	}

	data, err := os.ReadFile(*inputPath)
	if err != nil {
		fatalf("read input: %v", err)
	}
	in, err := loadInput(*inputPath, data, *kind)
	if err != nil {
		fatalf("%v", err)
	}
	if err := os.MkdirAll(*outDir, 0o755); err != nil {
		fatalf("create output directory: %v", err)
	}

	manifest := &manifestJSON{
		OracleVersion: oracleVersion,
		InputKind:     in.kind,
		Files:         []string{},
	}

	if in.checkinText != "" {
		if err := writeCheckinSummary(in, *outDir, manifest); err != nil {
			manifest.CheckinError = err.Error()
		}
	}
	if in.historyText != "" {
		if err := writeHistoryOutputs(in, *outDir, *scrubPII, manifest); err != nil {
			manifest.HistoryError = err.Error()
		}
	}

	if len(manifest.Files) == 0 {
		fatalf("no outputs produced: checkin_error=%q history_error=%q", manifest.CheckinError, manifest.HistoryError)
	}
	sort.Strings(manifest.Files)
	if err := writeJSONFile(filepath.Join(*outDir, "manifest.json"), manifest); err != nil {
		fatalf("write manifest: %v", err)
	}

	enc := json.NewEncoder(os.Stdout)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(manifest); err != nil {
		fatalf("write manifest to stdout: %v", err)
	}

	// Warnings and per-line history errors are expected on unsupported report
	// versions and are part of the golden data, so strict mode only fails on
	// hard checkin errors or when no history summary was produced at all.
	if *strict && (manifest.CheckinError != "" || manifest.CheckinParserErrorCount > 0 || manifest.HistorySummaryCount == 0) {
		os.Exit(1)
	}
}

type oracleInput struct {
	kind             string
	checkinText      string
	historyText      string
	reportVersion    int32
	buildFingerprint string
	buildID          string
	meta             *bugreportutils.MetaInfo
	pkgs             []*usagepb.PackageInfo
	loadWarnings     []string
}

func loadInput(path string, data []byte, kind string) (*oracleInput, error) {
	switch kind {
	case "checkin":
		return newCheckinInput(string(data)), nil
	case "bugreport":
		return newBugreportInput(string(data))
	case "auto", "":
	default:
		return nil, fmt.Errorf("unknown -kind %q (want auto, checkin, or bugreport)", kind)
	}

	if bugreportutils.IsBugReport(data) {
		return newBugreportInput(string(data))
	}
	if looksLikeCheckin(data) {
		return newCheckinInput(string(data)), nil
	}

	// A zip may contain a bugreport or a checkin capture.
	contents, err := bugreportutils.Contents(path, data)
	if err != nil {
		return nil, fmt.Errorf("input is neither a bug report nor a batterystats checkin capture: %v", err)
	}
	names := make([]string, 0, len(contents))
	for name := range contents {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		if bugreportutils.IsBugReport(contents[name]) {
			return newBugreportInput(string(contents[name]))
		}
	}
	for _, name := range names {
		if looksLikeCheckin(contents[name]) {
			return newCheckinInput(string(contents[name])), nil
		}
	}
	return nil, errors.New("archive did not contain a bug report or batterystats checkin capture")
}

func newCheckinInput(text string) *oracleInput {
	version, buildID := parseVersionLine(text)
	return &oracleInput{
		kind:          "checkin",
		checkinText:   text,
		historyText:   text,
		reportVersion: version,
		buildID:       buildID,
	}
}

func newBugreportInput(br string) (*oracleInput, error) {
	checkin := bugreportutils.ExtractBatterystatsCheckin(br)
	if strings.TrimSpace(checkin) == "" {
		return nil, errors.New("bug report has no CHECKIN BATTERYSTATS section")
	}
	meta, metaErr := bugreportutils.ParseMetaInfo(br)
	pkgs, pkgErrs := packageutils.ExtractAppsFromBugReport(br)
	in := &oracleInput{
		kind:         "bugreport",
		checkinText:  checkin,
		historyText:  checkin,
		meta:         meta,
		pkgs:         pkgs,
		loadWarnings: errorStrings(pkgErrs),
	}
	if meta != nil {
		in.buildFingerprint = meta.BuildFingerprint
	}
	if metaErr != nil {
		in.loadWarnings = append(in.loadWarnings, metaErr.Error())
	}
	if version, buildID := parseVersionLine(checkin); version >= 0 {
		in.reportVersion = version
		in.buildID = buildID
	}
	return in, nil
}

func looksLikeCheckin(b []byte) bool {
	text := string(b)
	return strings.Contains(text, ",i,vers,") && strings.Contains(text, "\n9,")
}

// parseVersionLine extracts the checkin report version and build ID from the
// aggregate `vers` line, e.g.:
//
//	9,0,i,vers,36,216,BE4B.251210.005,BE4B.251210.005
func parseVersionLine(checkin string) (reportVersion int32, buildID string) {
	reportVersion = -1
	for _, line := range strings.Split(checkin, "\n") {
		line = strings.TrimSpace(line)
		if !strings.Contains(line, ",vers,") {
			continue
		}
		r := csv.NewReader(strings.NewReader(line))
		r.FieldsPerRecord = -1
		fields, err := r.Read()
		if err != nil {
			continue
		}
		for i, f := range fields {
			if f != "vers" || i+1 >= len(fields) {
				continue
			}
			if v, err := strconv.ParseInt(fields[i+1], 10, 32); err == nil {
				reportVersion = int32(v)
			}
			if i+2 < len(fields) {
				buildID = strings.TrimSpace(fields[len(fields)-1])
			}
			return reportVersion, buildID
		}
	}
	return reportVersion, buildID
}

func writeCheckinSummary(in *oracleInput, outDir string, manifest *manifestJSON) error {
	session := &sessionpb.Checkin{
		Checkin:          proto.String(in.checkinText),
		BuildFingerprint: proto.String(in.buildFingerprint),
		Packages:         in.pkgs,
	}
	if in.meta != nil && in.meta.SdkVersion >= 0 {
		session.SystemInfo = &usagepb.SystemInfo{
			SdkVersion:       proto.Int32(int32(in.meta.SdkVersion)),
			BuildFingerprint: proto.String(in.meta.BuildFingerprint),
		}
	}

	var ctr checkinutil.IntCounter
	stats, warnings, errs := checkinparse.ParseBatteryStats(
		&ctr, checkinparse.CreateBatteryReport(session), in.pkgs)

	summary := &checkinSummaryJSON{
		Kind:          in.kind,
		ReportVersion: in.reportVersion,
		BuildID:       in.buildID,
		Warnings:      sortedStrings(append(append([]string{}, warnings...), in.loadWarnings...)),
		ParseErrs:     errorStrings(errs),
	}
	if stats != nil {
		summary.ReportVersion = stats.GetReportVersion()
		summary.AggregationType = stats.GetAggregationType().String()
		summary.SDKVersion = stats.GetSdkVersion()
		summary.GMSVersion = stats.GetGmsVersion()
		summary.StartTimeUsec = stats.GetStartTimeUsec()
		summary.EndTimeUsec = stats.GetEndTimeUsec()
		summary.BuildFingerprint = stats.GetBuild().GetFingerprint()
		if summary.BuildFingerprint == "" {
			summary.BuildFingerprint = in.buildFingerprint
		}
		summary.Build = buildJSONFromProto(stats.GetBuild())
		summary.System = summarizeSystem(stats.GetSystem())
		summary.Apps = summarizeApps(stats.GetApp())
		summary.AppCount = len(summary.Apps)
	}
	manifest.ReportVersion = summary.ReportVersion
	manifest.CheckinAppCount = summary.AppCount
	manifest.CheckinWarningCount = len(summary.Warnings)
	manifest.CheckinParserErrorCount = len(summary.ParseErrs)

	if err := writeJSONFile(filepath.Join(outDir, "checkin-summary.json"), summary); err != nil {
		return err
	}
	manifest.Files = append(manifest.Files, "checkin-summary.json")
	return nil
}

func writeHistoryOutputs(in *oracleInput, outDir string, scrubPII bool, manifest *manifestJSON) error {
	upm, mapErrs := parseutils.UIDAndPackageNameMapping(in.checkinText, in.pkgs)

	var levelCSV bytes.Buffer
	levelReport := parseutils.AnalyzeHistory(&levelCSV, in.historyText, parseutils.FormatBatteryLevel, upm, scrubPII)

	var timelineCSV bytes.Buffer
	parseutils.AnalyzeHistory(&timelineCSV, in.historyText, parseutils.FormatTotalTime, upm, scrubPII)

	timelineBytes, timelineErr := normalizeTimelineCSV(timelineCSV.Bytes())
	timelineNote := ""
	if timelineErr != nil {
		timelineBytes = timelineCSV.Bytes()
		timelineNote = timelineErr.Error()
	}

	summary := &historySummaryJSON{
		Kind:              in.kind,
		ReportVersion:     levelReport.ReportVersion,
		TimestampsAltered: levelReport.TimestampsAltered,
		SummaryCount:      len(levelReport.Summaries),
		Summaries:         levelReport.Summaries,
		ParserErrors:      errorStrings(levelReport.Errs),
		MappingErrors:     errorStrings(mapErrs),
		TimelineNote:      timelineNote,
	}

	if err := writeJSONFile(filepath.Join(outDir, "history-summary.json"), summary); err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(outDir, "history-level.csv"), levelCSV.Bytes(), 0o644); err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(outDir, "history-timeline.csv"), timelineBytes, 0o644); err != nil {
		return err
	}

	manifest.ReportVersion = summary.ReportVersion
	manifest.HistorySummaryCount = summary.SummaryCount
	manifest.HistoryParserErrorCount = len(summary.ParserErrors) + len(summary.MappingErrors)
	manifest.Files = append(manifest.Files, "history-summary.json", "history-level.csv", "history-timeline.csv")
	return nil
}

// normalizeTimelineCSV sorts data rows by start/end time and then by all
// columns. Upstream writes active events while iterating a Go map in
// PrintAllReset/PrintActiveEvent, so row order is not stable across runs even
// though the event set is. Sorting makes the golden file byte-comparable.
func normalizeTimelineCSV(raw []byte) ([]byte, error) {
	if len(bytes.TrimSpace(raw)) == 0 {
		return raw, nil
	}
	r := csv.NewReader(bytes.NewReader(raw))
	r.FieldsPerRecord = -1
	rows, err := r.ReadAll()
	if err != nil {
		return nil, fmt.Errorf("parse timeline csv: %v", err)
	}
	if len(rows) == 0 {
		return raw, nil
	}
	header, body := rows[0], rows[1:]
	sort.SliceStable(body, func(i, j int) bool {
		return timelineRowLess(body[i], body[j])
	})

	var b bytes.Buffer
	w := csv.NewWriter(&b)
	if err := w.Write(header); err != nil {
		return nil, err
	}
	if err := w.WriteAll(body); err != nil {
		return nil, err
	}
	w.Flush()
	if err := w.Error(); err != nil {
		return nil, err
	}
	return b.Bytes(), nil
}

func timelineRowLess(a, b []string) bool {
	// metric,type,start_time,end_time,value,opt
	if len(a) >= 4 && len(b) >= 4 {
		as, aerr := strconv.ParseInt(a[2], 10, 64)
		bs, berr := strconv.ParseInt(b[2], 10, 64)
		if aerr == nil && berr == nil && as != bs {
			return as < bs
		}
		ae, aerr := strconv.ParseInt(a[3], 10, 64)
		be, berr := strconv.ParseInt(b[3], 10, 64)
		if aerr == nil && berr == nil && ae != be {
			return ae < be
		}
	}
	for i := 0; i < len(a) && i < len(b); i++ {
		if a[i] != b[i] {
			return a[i] < b[i]
		}
	}
	return len(a) < len(b)
}

type manifestJSON struct {
	OracleVersion           string   `json:"oracle_version"`
	InputKind               string   `json:"input_kind"`
	ReportVersion           int32    `json:"report_version"`
	CheckinAppCount         int      `json:"checkin_app_count"`
	CheckinWarningCount     int      `json:"checkin_warning_count"`
	CheckinParserErrorCount int      `json:"checkin_parser_error_count"`
	HistorySummaryCount     int      `json:"history_summary_count"`
	HistoryParserErrorCount int      `json:"history_parser_error_count"`
	Files                   []string `json:"files"`
	CheckinError            string   `json:"checkin_error,omitempty"`
	HistoryError            string   `json:"history_error,omitempty"`
}

type checkinSummaryJSON struct {
	Kind             string     `json:"kind"`
	ReportVersion    int32      `json:"report_version"`
	AggregationType  string     `json:"aggregation_type,omitempty"`
	SDKVersion       int32      `json:"sdk_version,omitempty"`
	GMSVersion       int32      `json:"gms_version,omitempty"`
	BuildID          string     `json:"build_id,omitempty"`
	BuildFingerprint string     `json:"build_fingerprint,omitempty"`
	Build            *buildJSON `json:"build,omitempty"`
	StartTimeUsec    int64      `json:"start_time_usec,omitempty"`
	EndTimeUsec      int64      `json:"end_time_usec,omitempty"`
	AppCount         int        `json:"app_count"`
	System           systemJSON `json:"system"`
	Apps             []appJSON  `json:"apps"`
	Warnings         []string   `json:"warnings,omitempty"`
	ParseErrs        []string   `json:"parser_errors,omitempty"`
}

type historySummaryJSON struct {
	Kind              string                       `json:"kind"`
	ReportVersion     int32                        `json:"report_version"`
	TimestampsAltered bool                         `json:"timestamps_altered"`
	SummaryCount      int                          `json:"summary_count"`
	Summaries         []parseutils.ActivitySummary `json:"summaries"`
	ParserErrors      []string                     `json:"parser_errors,omitempty"`
	MappingErrors     []string                     `json:"mapping_errors,omitempty"`
	TimelineNote      string                       `json:"timeline_note,omitempty"`
}

type buildJSON struct {
	Fingerprint string   `json:"fingerprint,omitempty"`
	Brand       string   `json:"brand,omitempty"`
	Product     string   `json:"product,omitempty"`
	Device      string   `json:"device,omitempty"`
	Release     string   `json:"release,omitempty"`
	BuildID     string   `json:"build_id,omitempty"`
	Incremental string   `json:"incremental,omitempty"`
	Type        string   `json:"type,omitempty"`
	Tags        []string `json:"tags,omitempty"`
}

func buildJSONFromProto(b interface {
	GetFingerprint() string
	GetBrand() string
	GetProduct() string
	GetDevice() string
	GetRelease() string
	GetBuildId() string
	GetIncremental() string
	GetType() string
	GetTags() []string
}) *buildJSON {
	if b == nil || b.GetFingerprint() == "" {
		return nil
	}
	return &buildJSON{
		Fingerprint: b.GetFingerprint(),
		Brand:       b.GetBrand(),
		Product:     b.GetProduct(),
		Device:      b.GetDevice(),
		Release:     b.GetRelease(),
		BuildID:     b.GetBuildId(),
		Incremental: b.GetIncremental(),
		Type:        b.GetType(),
		Tags:        b.GetTags(),
	}
}

type appJSON struct {
	UID                 int32              `json:"uid"`
	Name                string             `json:"name"`
	VersionCode         int32              `json:"version_code,omitempty"`
	VersionName         string             `json:"version_name,omitempty"`
	Children            []string           `json:"children,omitempty"`
	ComputedPowerMah    float32            `json:"computed_power_mah,omitempty"`
	CPU                 *cpuJSON           `json:"cpu,omitempty"`
	Foreground          *countMsJSON       `json:"foreground,omitempty"`
	Audio               *countMsJSON       `json:"audio,omitempty"`
	Camera              *countMsJSON       `json:"camera,omitempty"`
	Flashlight          *countMsJSON       `json:"flashlight,omitempty"`
	Video               *countMsJSON       `json:"video,omitempty"`
	Vibrator            *countMsJSON       `json:"vibrator,omitempty"`
	Wakelocks           []wakelockJSON     `json:"wakelocks,omitempty"`
	WakeupAlarms        []nameCountJSON    `json:"wakeup_alarms,omitempty"`
	Sensors             []sensorJSON       `json:"sensors,omitempty"`
	ScheduledJobs       []nameDurationJSON `json:"scheduled_jobs,omitempty"`
	Syncs               []nameDurationJSON `json:"syncs,omitempty"`
	Processes           []processJSON      `json:"processes,omitempty"`
	Network             *networkJSON       `json:"network,omitempty"`
	StateTime           *stateTimeJSON     `json:"state_time,omitempty"`
	ModemController     *controllerJSON    `json:"modem_controller,omitempty"`
	WifiController      *controllerJSON    `json:"wifi_controller,omitempty"`
	BluetoothController *controllerJSON    `json:"bluetooth_controller,omitempty"`
}

type cpuJSON struct {
	UserMs    float32 `json:"user_ms"`
	SystemMs  float32 `json:"system_ms"`
	PowerMaMs float32 `json:"power_ma_ms"`
}

type countMsJSON struct {
	TotalMs float32 `json:"total_ms"`
	Count   float32 `json:"count"`
}

type wakelockJSON struct {
	Name                   string  `json:"name"`
	FullMs                 float32 `json:"full_ms,omitempty"`
	FullCount              float32 `json:"full_count,omitempty"`
	PartialMs              float32 `json:"partial_ms,omitempty"`
	PartialCount           float32 `json:"partial_count,omitempty"`
	WindowMs               float32 `json:"window_ms,omitempty"`
	WindowCount            float32 `json:"window_count,omitempty"`
	PartialMaxDurationMs   int64   `json:"partial_max_duration_ms,omitempty"`
	PartialTotalDurationMs int64   `json:"partial_total_duration_ms,omitempty"`
}

type nameCountJSON struct {
	Name  string `json:"name"`
	Count int32  `json:"count"`
}

type sensorJSON struct {
	Number   int32   `json:"number"`
	TotalMs  float32 `json:"total_ms"`
	Count    float32 `json:"count"`
	ActualMs int64   `json:"actual_ms,omitempty"`
}

type nameDurationJSON struct {
	Name    string  `json:"name"`
	TotalMs float32 `json:"total_ms"`
	Count   float32 `json:"count"`
}

type processJSON struct {
	Name         string  `json:"name"`
	UserMs       float32 `json:"user_ms"`
	SystemMs     float32 `json:"system_ms"`
	ForegroundMs float32 `json:"foreground_ms"`
	Starts       float32 `json:"starts"`
}

type networkJSON struct {
	MobileBytesRx     float32 `json:"mobile_bytes_rx,omitempty"`
	MobileBytesTx     float32 `json:"mobile_bytes_tx,omitempty"`
	WifiBytesRx       float32 `json:"wifi_bytes_rx,omitempty"`
	WifiBytesTx       float32 `json:"wifi_bytes_tx,omitempty"`
	MobilePacketsRx   float32 `json:"mobile_packets_rx,omitempty"`
	MobilePacketsTx   float32 `json:"mobile_packets_tx,omitempty"`
	WifiPacketsRx     float32 `json:"wifi_packets_rx,omitempty"`
	WifiPacketsTx     float32 `json:"wifi_packets_tx,omitempty"`
	MobileActiveMs    float32 `json:"mobile_active_ms,omitempty"`
	MobileActiveCount float32 `json:"mobile_active_count,omitempty"`
	BtBytesRx         int64   `json:"bt_bytes_rx,omitempty"`
	BtBytesTx         int64   `json:"bt_bytes_tx,omitempty"`
	MobileWakeupCount int32   `json:"mobile_wakeup_count,omitempty"`
	WifiWakeupCount   int32   `json:"wifi_wakeup_count,omitempty"`
}

type stateTimeJSON struct {
	ForegroundMs        float32 `json:"foreground_ms,omitempty"`
	ActiveMs            float32 `json:"active_ms,omitempty"`
	CachedMs            float32 `json:"cached_ms,omitempty"`
	TopMs               int64   `json:"top_ms,omitempty"`
	ForegroundServiceMs int64   `json:"foreground_service_ms,omitempty"`
	TopSleepingMs       int64   `json:"top_sleeping_ms,omitempty"`
	BackgroundMs        int64   `json:"background_ms,omitempty"`
}

type controllerJSON struct {
	IdleMs   int64         `json:"idle_ms,omitempty"`
	RxMs     int64         `json:"rx_ms,omitempty"`
	PowerMah int64         `json:"power_mah,omitempty"`
	Tx       []txLevelJSON `json:"tx,omitempty"`
}

type txLevelJSON struct {
	Level  int32 `json:"level"`
	TimeMs int64 `json:"time_ms"`
}

type systemJSON struct {
	Battery                   *batteryJSON         `json:"battery,omitempty"`
	BatteryDischarge          *dischargeJSON       `json:"battery_discharge,omitempty"`
	BatteryLevel              *batteryLevelJSON    `json:"battery_level,omitempty"`
	Misc                      *miscJSON            `json:"misc,omitempty"`
	KernelWakelocks           []kernelWakelockJSON `json:"kernel_wakelocks,omitempty"`
	PowerUseSummary           *powerUseSummaryJSON `json:"power_use_summary,omitempty"`
	PowerUseItems             []powerUseItemJSON   `json:"power_use_items,omitempty"`
	GlobalWifi                *globalWifiJSON      `json:"global_wifi,omitempty"`
	GlobalWifiController      *controllerJSON      `json:"global_wifi_controller,omitempty"`
	GlobalModemController     *controllerJSON      `json:"global_modem_controller,omitempty"`
	GlobalBluetoothController *controllerJSON      `json:"global_bluetooth_controller,omitempty"`
	GlobalNetwork             *globalNetworkJSON   `json:"global_network,omitempty"`
	WakeupReasons             []nameDurationJSON   `json:"wakeup_reasons,omitempty"`
	DataConnections           []nameTimeCountJSON  `json:"data_connections,omitempty"`
	SignalStrengths           []nameTimeCountJSON  `json:"signal_strengths,omitempty"`
	WifiSignalStrengths       []nameTimeCountJSON  `json:"wifi_signal_strengths,omitempty"`
	WifiStates                []nameTimeCountJSON  `json:"wifi_states,omitempty"`
	WifiSupplicantStates      []nameTimeCountJSON  `json:"wifi_supplicant_states,omitempty"`
	ScreenBrightness          []nameTimeCountJSON  `json:"screen_brightness,omitempty"`
}

type nameTimeCountJSON struct {
	Name   string  `json:"name"`
	TimeMs float32 `json:"time_ms"`
	Count  float32 `json:"count"`
}

type batteryJSON struct {
	StartCount           float32 `json:"start_count"`
	BatteryRealtimeMs    float32 `json:"battery_realtime_ms"`
	BatteryUptimeMs      float32 `json:"battery_uptime_ms"`
	TotalRealtimeMs      float32 `json:"total_realtime_ms"`
	TotalUptimeMs        float32 `json:"total_uptime_ms"`
	StartClockTimeMs     int64   `json:"start_clock_time_ms"`
	ScreenOffRealtimeMs  float32 `json:"screen_off_realtime_ms"`
	ScreenOffUptimeMs    float32 `json:"screen_off_uptime_ms"`
	EstimatedCapacityMah int64   `json:"estimated_capacity_mah,omitempty"`
}

type dischargeJSON struct {
	LowerBound        float32 `json:"lower_bound"`
	UpperBound        float32 `json:"upper_bound"`
	ScreenOn          float32 `json:"screen_on"`
	ScreenOff         float32 `json:"screen_off"`
	TotalMah          int64   `json:"total_mah,omitempty"`
	TotalMahScreenOff int64   `json:"total_mah_screen_off,omitempty"`
}

type batteryLevelJSON struct {
	StartLevel   float32 `json:"start_level"`
	CurrentLevel float32 `json:"current_level"`
}

type miscJSON struct {
	ScreenOnTimeMs                      float32 `json:"screen_on_time_ms,omitempty"`
	ScreenOffTimeMs                     float32 `json:"screen_off_time_ms,omitempty"`
	PhoneOnTimeMs                       float32 `json:"phone_on_time_ms,omitempty"`
	FullWakelockTimeMs                  float32 `json:"full_wakelock_time_ms,omitempty"`
	PartialWakelockTimeMs               float32 `json:"partial_wakelock_time_ms,omitempty"`
	MobileActiveTimeMs                  float32 `json:"mobile_active_time_ms,omitempty"`
	MobileActiveAdjustedTimeMs          float32 `json:"mobile_active_adjusted_time_ms,omitempty"`
	InteractiveTimeMs                   float32 `json:"interactive_time_ms,omitempty"`
	LowPowerModeEnabledTimeMs           float32 `json:"low_power_mode_enabled_time_ms,omitempty"`
	ConnectivityChanges                 float32 `json:"connectivity_changes,omitempty"`
	DeviceIdleModeEnabledTimeMs         float32 `json:"device_idle_mode_enabled_time_ms,omitempty"`
	DeviceIdleModeEnabledCount          float32 `json:"device_idle_mode_enabled_count,omitempty"`
	DeviceIdlingTimeMs                  float32 `json:"device_idling_time_ms,omitempty"`
	DeviceIdlingCount                   float32 `json:"device_idling_count,omitempty"`
	MobileActiveCount                   float32 `json:"mobile_active_count,omitempty"`
	MobileActiveUnknownTime             float32 `json:"mobile_active_unknown_time,omitempty"`
	DeviceLightIdleModeEnabledTimeMs    int64   `json:"device_light_idle_mode_enabled_time_ms,omitempty"`
	DeviceLightIdleModeEnabledCount     int32   `json:"device_light_idle_mode_enabled_count,omitempty"`
	DeviceLightIdlingTimeMs             int64   `json:"device_light_idling_time_ms,omitempty"`
	DeviceLightIdlingCount              int32   `json:"device_light_idling_count,omitempty"`
	MaxDeviceLightIdleModeEnabledTimeMs int64   `json:"max_device_light_idle_mode_enabled_time_ms,omitempty"`
	MaxDeviceIdleModeEnabledTimeMs      int64   `json:"max_device_idle_mode_enabled_time_ms,omitempty"`
}

type kernelWakelockJSON struct {
	Name   string  `json:"name"`
	TimeMs float32 `json:"time_ms"`
	Count  float32 `json:"count"`
	MaxMs  int64   `json:"max_ms,omitempty"`
}

type powerUseSummaryJSON struct {
	BatteryCapacityMah float32 `json:"battery_capacity_mah"`
	ComputedPowerMah   float32 `json:"computed_power_mah"`
	MinDrainedPowerMah float32 `json:"min_drained_power_mah"`
	MaxDrainedPowerMah float32 `json:"max_drained_power_mah"`
}

type powerUseItemJSON struct {
	Name             string  `json:"name"`
	ComputedPowerMah float32 `json:"computed_power_mah"`
}

type globalWifiJSON struct {
	OnTimeMs      float32 `json:"on_time_ms,omitempty"`
	RunningTimeMs float32 `json:"running_time_ms,omitempty"`
}

type globalNetworkJSON struct {
	MobileBytesRx   float32 `json:"mobile_bytes_rx,omitempty"`
	MobileBytesTx   float32 `json:"mobile_bytes_tx,omitempty"`
	WifiBytesRx     float32 `json:"wifi_bytes_rx,omitempty"`
	WifiBytesTx     float32 `json:"wifi_bytes_tx,omitempty"`
	MobilePacketsRx float32 `json:"mobile_packets_rx,omitempty"`
	MobilePacketsTx float32 `json:"mobile_packets_tx,omitempty"`
	WifiPacketsRx   float32 `json:"wifi_packets_rx,omitempty"`
	WifiPacketsTx   float32 `json:"wifi_packets_tx,omitempty"`
	BtBytesRx       int64   `json:"bt_bytes_rx,omitempty"`
	BtBytesTx       int64   `json:"bt_bytes_tx,omitempty"`
}

func summarizeApps(apps []*bspb.BatteryStats_App) []appJSON {
	out := make([]appJSON, 0, len(apps))
	for _, app := range apps {
		a := appJSON{
			UID:         app.GetUid(),
			Name:        app.GetName(),
			VersionCode: app.GetVersionCode(),
			VersionName: app.GetVersionName(),
		}
		if a.Name == "" {
			a.Name = fmt.Sprintf("uid:%d", a.UID)
		}
		for _, child := range app.GetChild() {
			a.Children = append(a.Children, child.GetName())
		}
		sort.Strings(a.Children)
		if p := app.GetPowerUseItem(); p != nil {
			a.ComputedPowerMah = p.GetComputedPowerMah()
		}
		if c := app.GetCpu(); c != nil {
			a.CPU = &cpuJSON{UserMs: c.GetUserTimeMs(), SystemMs: c.GetSystemTimeMs(), PowerMaMs: c.GetPowerMaMs()}
		}
		if f := app.GetForeground(); f != nil {
			a.Foreground = &countMsJSON{TotalMs: f.GetTotalTimeMsec(), Count: f.GetCount()}
		}
		a.Audio = countMsFrom(app.GetAudio().GetTotalTimeMsec(), app.GetAudio().GetCount())
		a.Camera = countMsFrom(app.GetCamera().GetTotalTimeMsec(), app.GetCamera().GetCount())
		a.Flashlight = countMsFrom(app.GetFlashlight().GetTotalTimeMsec(), app.GetFlashlight().GetCount())
		a.Video = countMsFrom(app.GetVideo().GetTotalTimeMsec(), app.GetVideo().GetCount())
		a.Vibrator = countMsFrom(app.GetVibrator().GetTotalTimeMsec(), app.GetVibrator().GetCount())
		for _, w := range app.GetWakelock() {
			a.Wakelocks = append(a.Wakelocks, wakelockJSON{
				Name:                   w.GetName(),
				FullMs:                 w.GetFullTimeMsec(),
				FullCount:              w.GetFullCount(),
				PartialMs:              w.GetPartialTimeMsec(),
				PartialCount:           w.GetPartialCount(),
				WindowMs:               w.GetWindowTimeMsec(),
				WindowCount:            w.GetWindowCount(),
				PartialMaxDurationMs:   w.GetPartialMaxDurationMsec(),
				PartialTotalDurationMs: w.GetPartialTotalDurationMsec(),
			})
		}
		for _, w := range app.GetWakeupAlarm() {
			a.WakeupAlarms = append(a.WakeupAlarms, nameCountJSON{Name: w.GetName(), Count: w.GetCount()})
		}
		for _, s := range app.GetSensor() {
			a.Sensors = append(a.Sensors, sensorJSON{
				Number: s.GetNumber(), TotalMs: s.GetTotalTimeMsec(), Count: s.GetCount(), ActualMs: s.GetActualTimeMsec(),
			})
		}
		for _, j := range app.GetScheduledJob() {
			a.ScheduledJobs = append(a.ScheduledJobs, nameDurationJSON{Name: j.GetName(), TotalMs: j.GetTotalTimeMsec(), Count: j.GetCount()})
		}
		for _, s := range app.GetSync() {
			a.Syncs = append(a.Syncs, nameDurationJSON{Name: s.GetName(), TotalMs: s.GetTotalTimeMsec(), Count: s.GetCount()})
		}
		for _, p := range app.GetProcess() {
			a.Processes = append(a.Processes, processJSON{
				Name: p.GetName(), UserMs: p.GetUserTimeMsec(), SystemMs: p.GetSystemTimeMsec(),
				ForegroundMs: p.GetForegroundTimeMsec(), Starts: p.GetStarts(),
			})
		}
		if n := app.GetNetwork(); n != nil {
			a.Network = &networkJSON{
				MobileBytesRx: n.GetMobileBytesRx(), MobileBytesTx: n.GetMobileBytesTx(),
				WifiBytesRx: n.GetWifiBytesRx(), WifiBytesTx: n.GetWifiBytesTx(),
				MobilePacketsRx: n.GetMobilePacketsRx(), MobilePacketsTx: n.GetMobilePacketsTx(),
				WifiPacketsRx: n.GetWifiPacketsRx(), WifiPacketsTx: n.GetWifiPacketsTx(),
				MobileActiveMs: n.GetMobileActiveTimeMsec(), MobileActiveCount: n.GetMobileActiveCount(),
				BtBytesRx: n.GetBtBytesRx(), BtBytesTx: n.GetBtBytesTx(),
				MobileWakeupCount: n.GetMobileWakeupCount(), WifiWakeupCount: n.GetWifiWakeupCount(),
			}
		}
		if st := app.GetStateTime(); st != nil {
			a.StateTime = &stateTimeJSON{
				ForegroundMs: st.GetForegroundTimeMsec(), ActiveMs: st.GetActiveTimeMsec(),
				CachedMs: st.GetCachedTimeMsec(), TopMs: st.GetTopTimeMsec(),
				ForegroundServiceMs: st.GetForegroundServiceTimeMsec(),
				TopSleepingMs:       st.GetTopSleepingTimeMsec(), BackgroundMs: st.GetBackgroundTimeMsec(),
			}
		}
		a.ModemController = controllerFromProto(app.GetModemController())
		a.WifiController = controllerFromProto(app.GetWifiController())
		a.BluetoothController = controllerFromProto(app.GetBluetoothController())

		sort.Slice(a.Wakelocks, func(i, j int) bool { return a.Wakelocks[i].Name < a.Wakelocks[j].Name })
		sort.Slice(a.WakeupAlarms, func(i, j int) bool { return a.WakeupAlarms[i].Name < a.WakeupAlarms[j].Name })
		sort.Slice(a.Sensors, func(i, j int) bool { return a.Sensors[i].Number < a.Sensors[j].Number })
		sort.Slice(a.ScheduledJobs, func(i, j int) bool { return a.ScheduledJobs[i].Name < a.ScheduledJobs[j].Name })
		sort.Slice(a.Syncs, func(i, j int) bool { return a.Syncs[i].Name < a.Syncs[j].Name })
		sort.Slice(a.Processes, func(i, j int) bool { return a.Processes[i].Name < a.Processes[j].Name })
		out = append(out, a)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].UID != out[j].UID {
			return out[i].UID < out[j].UID
		}
		return out[i].Name < out[j].Name
	})
	return out
}

func countMsFrom(totalMs, count float32) *countMsJSON {
	if totalMs == 0 && count == 0 {
		return nil
	}
	return &countMsJSON{TotalMs: totalMs, Count: count}
}

func controllerFromProto(c interface {
	GetIdleTimeMsec() int64
	GetRxTimeMsec() int64
	GetPowerMah() int64
	GetTx() []*bspb.BatteryStats_ControllerActivity_TxLevel
}) *controllerJSON {
	if c == nil {
		return nil
	}
	out := &controllerJSON{IdleMs: c.GetIdleTimeMsec(), RxMs: c.GetRxTimeMsec(), PowerMah: c.GetPowerMah()}
	for _, tx := range c.GetTx() {
		out.Tx = append(out.Tx, txLevelJSON{Level: tx.GetLevel(), TimeMs: tx.GetTimeMsec()})
	}
	if out.IdleMs == 0 && out.RxMs == 0 && out.PowerMah == 0 && len(out.Tx) == 0 {
		return nil
	}
	sort.Slice(out.Tx, func(i, j int) bool { return out.Tx[i].Level < out.Tx[j].Level })
	return out
}

func summarizeSystem(s *bspb.BatteryStats_System) systemJSON {
	out := systemJSON{}
	if s == nil {
		return out
	}
	if b := s.GetBattery(); b != nil {
		out.Battery = &batteryJSON{
			StartCount: b.GetStartCount(), BatteryRealtimeMs: b.GetBatteryRealtimeMsec(),
			BatteryUptimeMs: b.GetBatteryUptimeMsec(), TotalRealtimeMs: b.GetTotalRealtimeMsec(),
			TotalUptimeMs: b.GetTotalUptimeMsec(), StartClockTimeMs: b.GetStartClockTimeMsec(),
			ScreenOffRealtimeMs: b.GetScreenOffRealtimeMsec(), ScreenOffUptimeMs: b.GetScreenOffUptimeMsec(),
			EstimatedCapacityMah: b.GetEstimatedBatteryCapacityMah(),
		}
	}
	if d := s.GetBatteryDischarge(); d != nil {
		out.BatteryDischarge = &dischargeJSON{
			LowerBound: d.GetLowerBound(), UpperBound: d.GetUpperBound(),
			ScreenOn: d.GetScreenOn(), ScreenOff: d.GetScreenOff(),
			TotalMah: d.GetTotalMah(), TotalMahScreenOff: d.GetTotalMahScreenOff(),
		}
	}
	if l := s.GetBatteryLevel(); l != nil {
		out.BatteryLevel = &batteryLevelJSON{StartLevel: l.GetStartLevel(), CurrentLevel: l.GetCurrentLevel()}
	}
	if m := s.GetMisc(); m != nil {
		out.Misc = &miscJSON{
			ScreenOnTimeMs: m.GetScreenOnTimeMsec(), ScreenOffTimeMs: m.GetScreenOffTimeMsec(),
			PhoneOnTimeMs: m.GetPhoneOnTimeMsec(), FullWakelockTimeMs: m.GetFullWakelockTimeMsec(),
			PartialWakelockTimeMs:       m.GetPartialWakelockTimeMsec(),
			MobileActiveTimeMs:          m.GetMobileActiveTimeMsec(),
			MobileActiveAdjustedTimeMs:  m.GetMobileActiveAdjustedTimeMsec(),
			InteractiveTimeMs:           m.GetInteractiveTimeMsec(),
			LowPowerModeEnabledTimeMs:   m.GetLowPowerModeEnabledTimeMsec(),
			ConnectivityChanges:         m.GetConnectivityChanges(),
			DeviceIdleModeEnabledTimeMs: m.GetDeviceIdleModeEnabledTimeMsec(),
			DeviceIdleModeEnabledCount:  m.GetDeviceIdleModeEnabledCount(),
			DeviceIdlingTimeMs:          m.GetDeviceIdlingTimeMsec(), DeviceIdlingCount: m.GetDeviceIdlingCount(),
			MobileActiveCount: m.GetMobileActiveCount(), MobileActiveUnknownTime: m.GetMobileActiveUnknownTime(),
			DeviceLightIdleModeEnabledTimeMs:    m.GetDeviceLightIdleModeEnabledTimeMsec(),
			DeviceLightIdleModeEnabledCount:     m.GetDeviceLightIdleModeEnabledCount(),
			DeviceLightIdlingTimeMs:             m.GetDeviceLightIdlingTimeMsec(),
			DeviceLightIdlingCount:              m.GetDeviceLightIdlingCount(),
			MaxDeviceLightIdleModeEnabledTimeMs: m.GetMaxDeviceLightIdleModeEnabledTimeMsec(),
			MaxDeviceIdleModeEnabledTimeMs:      m.GetMaxDeviceIdleModeEnabledTimeMsec(),
		}
	}
	for _, k := range s.GetKernelWakelock() {
		out.KernelWakelocks = append(out.KernelWakelocks, kernelWakelockJSON{
			Name: k.GetName(), TimeMs: k.GetTimeMsec(), Count: k.GetCount(), MaxMs: k.GetMaxDurationMsec(),
		})
	}
	sort.Slice(out.KernelWakelocks, func(i, j int) bool { return out.KernelWakelocks[i].Name < out.KernelWakelocks[j].Name })
	if p := s.GetPowerUseSummary(); p != nil {
		out.PowerUseSummary = &powerUseSummaryJSON{
			BatteryCapacityMah: p.GetBatteryCapacityMah(), ComputedPowerMah: p.GetComputedPowerMah(),
			MinDrainedPowerMah: p.GetMinDrainedPowerMah(), MaxDrainedPowerMah: p.GetMaxDrainedPowerMah(),
		}
	}
	for _, p := range s.GetPowerUseItem() {
		out.PowerUseItems = append(out.PowerUseItems, powerUseItemJSON{
			Name: p.GetName().String(), ComputedPowerMah: p.GetComputedPowerMah(),
		})
	}
	sort.Slice(out.PowerUseItems, func(i, j int) bool { return out.PowerUseItems[i].Name < out.PowerUseItems[j].Name })
	if g := s.GetGlobalWifi(); g != nil {
		out.GlobalWifi = &globalWifiJSON{OnTimeMs: g.GetWifiOnTimeMsec(), RunningTimeMs: g.GetWifiRunningTimeMsec()}
	}
	out.GlobalWifiController = controllerFromProto(s.GetGlobalWifiController())
	out.GlobalModemController = controllerFromProto(s.GetGlobalModemController())
	out.GlobalBluetoothController = controllerFromProto(s.GetGlobalBluetoothController())
	if g := s.GetGlobalNetwork(); g != nil {
		out.GlobalNetwork = &globalNetworkJSON{
			MobileBytesRx: g.GetMobileBytesRx(), MobileBytesTx: g.GetMobileBytesTx(),
			WifiBytesRx: g.GetWifiBytesRx(), WifiBytesTx: g.GetWifiBytesTx(),
			MobilePacketsRx: g.GetMobilePacketsRx(), MobilePacketsTx: g.GetMobilePacketsTx(),
			WifiPacketsRx: g.GetWifiPacketsRx(), WifiPacketsTx: g.GetWifiPacketsTx(),
			BtBytesRx: g.GetBtBytesRx(), BtBytesTx: g.GetBtBytesTx(),
		}
	}
	for _, w := range s.GetWakeupReason() {
		out.WakeupReasons = append(out.WakeupReasons, nameDurationJSON{Name: w.GetName(), TotalMs: w.GetTimeMsec(), Count: w.GetCount()})
	}
	sort.Slice(out.WakeupReasons, func(i, j int) bool { return out.WakeupReasons[i].Name < out.WakeupReasons[j].Name })
	for _, d := range s.GetDataConnection() {
		out.DataConnections = append(out.DataConnections, nameTimeCountJSON{Name: d.GetName().String(), TimeMs: d.GetTimeMsec(), Count: d.GetCount()})
	}
	for _, d := range s.GetSignalStrength() {
		out.SignalStrengths = append(out.SignalStrengths, nameTimeCountJSON{Name: d.GetName().String(), TimeMs: d.GetTimeMsec(), Count: d.GetCount()})
	}
	for _, d := range s.GetWifiSignalStrength() {
		out.WifiSignalStrengths = append(out.WifiSignalStrengths, nameTimeCountJSON{Name: d.GetName().String(), TimeMs: d.GetTimeMsec(), Count: d.GetCount()})
	}
	for _, d := range s.GetWifiState() {
		out.WifiStates = append(out.WifiStates, nameTimeCountJSON{Name: d.GetName().String(), TimeMs: d.GetTimeMsec(), Count: d.GetCount()})
	}
	for _, d := range s.GetWifiSupplicantState() {
		out.WifiSupplicantStates = append(out.WifiSupplicantStates, nameTimeCountJSON{Name: d.GetName().String(), TimeMs: d.GetTimeMsec(), Count: d.GetCount()})
	}
	for _, d := range s.GetScreenBrightness() {
		out.ScreenBrightness = append(out.ScreenBrightness, nameTimeCountJSON{Name: d.GetName().String(), TimeMs: d.GetTimeMsec()})
	}
	sortNameTimeCount(out.DataConnections)
	sortNameTimeCount(out.SignalStrengths)
	sortNameTimeCount(out.WifiSignalStrengths)
	sortNameTimeCount(out.WifiStates)
	sortNameTimeCount(out.WifiSupplicantStates)
	sortNameTimeCount(out.ScreenBrightness)
	return out
}

func sortNameTimeCount(s []nameTimeCountJSON) {
	sort.Slice(s, func(i, j int) bool { return s[i].Name < s[j].Name })
}

func errorStrings(errs []error) []string {
	out := make([]string, 0, len(errs))
	for _, err := range errs {
		if err != nil {
			out = append(out, err.Error())
		}
	}
	return sortedStrings(out)
}

func sortedStrings(in []string) []string {
	out := append([]string{}, in...)
	sort.Strings(out)
	return out
}

func writeJSONFile(path string, v interface{}) error {
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	b = append(b, '\n')
	return os.WriteFile(path, b, 0o644)
}

func fatalf(format string, args ...interface{}) {
	fmt.Fprintf(os.Stderr, "oracle: "+format+"\n", args...)
	os.Exit(1)
}
