// Positional column tables for the checkin parser.
//
// Wire has no descriptor API, so the parser mirrors the .proto declaration order
// explicitly. Keep these lists in sync with core/src/main/proto/batterystats.proto:
// each list is the scalar field order of the corresponding message. Repeated,
// message, and enum fields are not parseLine columns and are omitted.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Apk.Service as BatteryAppService
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.AggregatedWakelock as BatteryAppAggregatedWakelock
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Audio as BatteryAppAudio
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.BluetoothMisc as BatteryAppBluetoothMisc
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Camera as BatteryAppCamera
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Cpu as BatteryAppCpu
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Flashlight as BatteryAppFlashlight
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Foreground as BatteryAppForeground
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.ForegroundService as BatteryAppForegroundService
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Network as BatteryAppNetwork
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Process as BatteryAppProcess
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.ScheduledJob as BatteryAppScheduledJob
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Sensor as BatteryAppSensor
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.StateTime as BatteryAppStateTime
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Sync as BatteryAppSync
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Vibrator as BatteryAppVibrator
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Video as BatteryAppVideo
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.WakeupAlarm as BatteryAppWakeupAlarm
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Wifi as BatteryAppWifi
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.WifiMulticast as BatteryAppWifiMulticast
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.Battery as BatterySystemBattery
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.BatteryDischarge as BatterySystemBatteryDischarge
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.BatteryLevel as BatterySystemBatteryLevel
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.ChargeTimeRemaining as BatterySystemChargeTimeRemaining
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.DischargeTimeRemaining as BatterySystemDischargeTimeRemaining
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.GlobalBluetooth as BatterySystemGlobalBluetooth
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.GlobalNetwork as BatterySystemGlobalNetwork
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.GlobalWifi as BatterySystemGlobalWifi
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.KernelWakelock as BatterySystemKernelWakelock
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.Misc as BatterySystemMisc
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.PowerUseSummary as BatterySystemPowerUseSummary
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.ResourcePowerManager as BatterySystemResourcePowerManager
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.SignalScanningTime as BatterySystemSignalScanningTime
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.WakeupReason as BatterySystemWakeupReason
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System.WifiMulticast as BatterySystemWifiMulticast

internal val BATTERY_APP_SERVICE_SPEC: MessageSpec<BatteryAppService.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("name", FieldType.STRING, { it.name }, { b, v -> b.name = v as String }),
        FieldSpec("start_time_msec", FieldType.FLOAT, { it.start_time_msec }, { b, v -> b.start_time_msec = v as Float }),
        FieldSpec("starts", FieldType.FLOAT, { it.starts }, { b, v -> b.starts = v as Float }),
        FieldSpec("launches", FieldType.FLOAT, { it.launches }, { b, v -> b.launches = v as Float }),
    ),
    factory = { BatteryAppService.Builder() },
)

internal val BATTERY_APP_AUDIO_SPEC: MessageSpec<BatteryAppAudio.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
    ),
    factory = { BatteryAppAudio.Builder() },
)

internal val BATTERY_APP_BLUETOOTH_MISC_SPEC: MessageSpec<BatteryAppBluetoothMisc.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("ble_scan_time_msec", FieldType.INT64, { it.ble_scan_time_msec }, { b, v -> b.ble_scan_time_msec = v as Long }),
        FieldSpec("ble_scan_count", FieldType.INT32, { it.ble_scan_count }, { b, v -> b.ble_scan_count = v as Int }),
        FieldSpec("ble_scan_count_bg", FieldType.INT32, { it.ble_scan_count_bg }, { b, v -> b.ble_scan_count_bg = v as Int }),
        FieldSpec("ble_scan_actual_time_msec", FieldType.INT64, { it.ble_scan_actual_time_msec }, { b, v -> b.ble_scan_actual_time_msec = v as Long }),
        FieldSpec("ble_scan_actual_time_msec_bg", FieldType.INT64, { it.ble_scan_actual_time_msec_bg }, { b, v -> b.ble_scan_actual_time_msec_bg = v as Long }),
        FieldSpec("ble_scan_result_count", FieldType.INT32, { it.ble_scan_result_count }, { b, v -> b.ble_scan_result_count = v as Int }),
        FieldSpec("ble_scan_result_count_bg", FieldType.INT32, { it.ble_scan_result_count_bg }, { b, v -> b.ble_scan_result_count_bg = v as Int }),
        FieldSpec("ble_scan_unoptimized_time_msec", FieldType.INT64, { it.ble_scan_unoptimized_time_msec }, { b, v -> b.ble_scan_unoptimized_time_msec = v as Long }),
        FieldSpec("ble_scan_unoptimized_time_msec_bg", FieldType.INT64, { it.ble_scan_unoptimized_time_msec_bg }, { b, v -> b.ble_scan_unoptimized_time_msec_bg = v as Long }),
        FieldSpec("ble_scan_unoptimized_max_time_msec", FieldType.INT64, { it.ble_scan_unoptimized_max_time_msec }, { b, v -> b.ble_scan_unoptimized_max_time_msec = v as Long }),
        FieldSpec("ble_scan_unoptimized_max_time_msec_bg", FieldType.INT64, { it.ble_scan_unoptimized_max_time_msec_bg }, { b, v -> b.ble_scan_unoptimized_max_time_msec_bg = v as Long }),
    ),
    factory = { BatteryAppBluetoothMisc.Builder() },
)

internal val BATTERY_APP_CAMERA_SPEC: MessageSpec<BatteryAppCamera.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
    ),
    factory = { BatteryAppCamera.Builder() },
)

internal val BATTERY_APP_CPU_SPEC: MessageSpec<BatteryAppCpu.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("user_time_ms", FieldType.FLOAT, { it.user_time_ms }, { b, v -> b.user_time_ms = v as Float }),
        FieldSpec("system_time_ms", FieldType.FLOAT, { it.system_time_ms }, { b, v -> b.system_time_ms = v as Float }),
        FieldSpec("power_ma_ms", FieldType.FLOAT, { it.power_ma_ms }, { b, v -> b.power_ma_ms = v as Float }),
    ),
    factory = { BatteryAppCpu.Builder() },
)

internal val BATTERY_APP_FLASHLIGHT_SPEC: MessageSpec<BatteryAppFlashlight.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
    ),
    factory = { BatteryAppFlashlight.Builder() },
)

internal val BATTERY_APP_FOREGROUND_SPEC: MessageSpec<BatteryAppForeground.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
    ),
    factory = { BatteryAppForeground.Builder() },
)

internal val BATTERY_APP_FOREGROUND_SERVICE_SPEC: MessageSpec<BatteryAppForegroundService.Builder> =
    MessageSpec(
        fields = listOf(
            FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
            FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
        ),
        factory = { BatteryAppForegroundService.Builder() },
    )

internal val BATTERY_APP_AGGREGATED_WAKELOCK_SPEC: MessageSpec<BatteryAppAggregatedWakelock.Builder> =
    MessageSpec(
        fields = listOf(
            FieldSpec("partial_time_msec", FieldType.FLOAT, { it.partial_time_msec }, { b, v -> b.partial_time_msec = v as Float }),
            FieldSpec("background_partial_time_msec", FieldType.FLOAT, { it.background_partial_time_msec }, { b, v -> b.background_partial_time_msec = v as Float }),
        ),
        factory = { BatteryAppAggregatedWakelock.Builder() },
    )

internal val BATTERY_APP_WIFI_MULTICAST_SPEC: MessageSpec<BatteryAppWifiMulticast.Builder> =
    MessageSpec(
        fields = listOf(
            FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
            FieldSpec("count", FieldType.INT32, { it.count }, { b, v -> b.count = v as Int }),
        ),
        factory = { BatteryAppWifiMulticast.Builder() },
    )

internal val BATTERY_APP_NETWORK_SPEC: MessageSpec<BatteryAppNetwork.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("mobile_bytes_rx", FieldType.FLOAT, { it.mobile_bytes_rx }, { b, v -> b.mobile_bytes_rx = v as Float }),
        FieldSpec("mobile_bytes_tx", FieldType.FLOAT, { it.mobile_bytes_tx }, { b, v -> b.mobile_bytes_tx = v as Float }),
        FieldSpec("wifi_bytes_rx", FieldType.FLOAT, { it.wifi_bytes_rx }, { b, v -> b.wifi_bytes_rx = v as Float }),
        FieldSpec("wifi_bytes_tx", FieldType.FLOAT, { it.wifi_bytes_tx }, { b, v -> b.wifi_bytes_tx = v as Float }),
        FieldSpec("mobile_packets_rx", FieldType.FLOAT, { it.mobile_packets_rx }, { b, v -> b.mobile_packets_rx = v as Float }),
        FieldSpec("mobile_packets_tx", FieldType.FLOAT, { it.mobile_packets_tx }, { b, v -> b.mobile_packets_tx = v as Float }),
        FieldSpec("wifi_packets_rx", FieldType.FLOAT, { it.wifi_packets_rx }, { b, v -> b.wifi_packets_rx = v as Float }),
        FieldSpec("wifi_packets_tx", FieldType.FLOAT, { it.wifi_packets_tx }, { b, v -> b.wifi_packets_tx = v as Float }),
        FieldSpec("mobile_active_time_msec", FieldType.FLOAT, { it.mobile_active_time_msec }, { b, v -> b.mobile_active_time_msec = v as Float }),
        FieldSpec("mobile_active_count", FieldType.FLOAT, { it.mobile_active_count }, { b, v -> b.mobile_active_count = v as Float }),
        FieldSpec("bt_bytes_rx", FieldType.INT64, { it.bt_bytes_rx }, { b, v -> b.bt_bytes_rx = v as Long }),
        FieldSpec("bt_bytes_tx", FieldType.INT64, { it.bt_bytes_tx }, { b, v -> b.bt_bytes_tx = v as Long }),
        FieldSpec("mobile_wakeup_count", FieldType.INT32, { it.mobile_wakeup_count }, { b, v -> b.mobile_wakeup_count = v as Int }),
        FieldSpec("wifi_wakeup_count", FieldType.INT32, { it.wifi_wakeup_count }, { b, v -> b.wifi_wakeup_count = v as Int }),
        FieldSpec("mobile_bytes_bg_rx", FieldType.INT64, { it.mobile_bytes_bg_rx }, { b, v -> b.mobile_bytes_bg_rx = v as Long }),
        FieldSpec("mobile_bytes_bg_tx", FieldType.INT64, { it.mobile_bytes_bg_tx }, { b, v -> b.mobile_bytes_bg_tx = v as Long }),
        FieldSpec("wifi_bytes_bg_rx", FieldType.INT64, { it.wifi_bytes_bg_rx }, { b, v -> b.wifi_bytes_bg_rx = v as Long }),
        FieldSpec("wifi_bytes_bg_tx", FieldType.INT64, { it.wifi_bytes_bg_tx }, { b, v -> b.wifi_bytes_bg_tx = v as Long }),
        FieldSpec("mobile_packets_bg_rx", FieldType.INT64, { it.mobile_packets_bg_rx }, { b, v -> b.mobile_packets_bg_rx = v as Long }),
        FieldSpec("mobile_packets_bg_tx", FieldType.INT64, { it.mobile_packets_bg_tx }, { b, v -> b.mobile_packets_bg_tx = v as Long }),
        FieldSpec("wifi_packets_bg_rx", FieldType.INT64, { it.wifi_packets_bg_rx }, { b, v -> b.wifi_packets_bg_rx = v as Long }),
        FieldSpec("wifi_packets_bg_tx", FieldType.INT64, { it.wifi_packets_bg_tx }, { b, v -> b.wifi_packets_bg_tx = v as Long }),
    ),
    factory = { BatteryAppNetwork.Builder() },
)

internal val BATTERY_APP_PROCESS_SPEC: MessageSpec<BatteryAppProcess.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("name", FieldType.STRING, { it.name }, { b, v -> b.name = v as String }),
        FieldSpec("user_time_msec", FieldType.FLOAT, { it.user_time_msec }, { b, v -> b.user_time_msec = v as Float }),
        FieldSpec("system_time_msec", FieldType.FLOAT, { it.system_time_msec }, { b, v -> b.system_time_msec = v as Float }),
        FieldSpec("foreground_time_msec", FieldType.FLOAT, { it.foreground_time_msec }, { b, v -> b.foreground_time_msec = v as Float }),
        FieldSpec("starts", FieldType.FLOAT, { it.starts }, { b, v -> b.starts = v as Float }),
        FieldSpec("anrs", FieldType.FLOAT, { it.anrs }, { b, v -> b.anrs = v as Float }),
        FieldSpec("crashes", FieldType.FLOAT, { it.crashes }, { b, v -> b.crashes = v as Float }),
    ),
    factory = { BatteryAppProcess.Builder() },
)

internal val BATTERY_APP_SCHEDULED_JOB_SPEC: MessageSpec<BatteryAppScheduledJob.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("name", FieldType.STRING, { it.name }, { b, v -> b.name = v as String }),
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
        FieldSpec("background_time_msec", FieldType.INT64, { it.background_time_msec }, { b, v -> b.background_time_msec = v as Long }),
        FieldSpec("background_count", FieldType.INT32, { it.background_count }, { b, v -> b.background_count = v as Int }),
    ),
    factory = { BatteryAppScheduledJob.Builder() },
)

internal val BATTERY_APP_SENSOR_SPEC: MessageSpec<BatteryAppSensor.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("number", FieldType.INT32, { it.number }, { b, v -> b.number = v as Int }),
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
        FieldSpec("background_count", FieldType.INT32, { it.background_count }, { b, v -> b.background_count = v as Int }),
        FieldSpec("actual_time_msec", FieldType.INT64, { it.actual_time_msec }, { b, v -> b.actual_time_msec = v as Long }),
        FieldSpec("background_actual_time_msec", FieldType.INT64, { it.background_actual_time_msec }, { b, v -> b.background_actual_time_msec = v as Long }),
    ),
    factory = { BatteryAppSensor.Builder() },
)

internal val BATTERY_APP_STATE_TIME_SPEC: MessageSpec<BatteryAppStateTime.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("foreground_time_msec", FieldType.FLOAT, { it.foreground_time_msec }, { b, v -> b.foreground_time_msec = v as Float }),
        FieldSpec("active_time_msec", FieldType.FLOAT, { it.active_time_msec }, { b, v -> b.active_time_msec = v as Float }),
        FieldSpec("cached_time_msec", FieldType.FLOAT, { it.cached_time_msec }, { b, v -> b.cached_time_msec = v as Float }),
        FieldSpec("top_time_msec", FieldType.INT64, { it.top_time_msec }, { b, v -> b.top_time_msec = v as Long }),
        FieldSpec("foreground_service_time_msec", FieldType.INT64, { it.foreground_service_time_msec }, { b, v -> b.foreground_service_time_msec = v as Long }),
        FieldSpec("top_sleeping_time_msec", FieldType.INT64, { it.top_sleeping_time_msec }, { b, v -> b.top_sleeping_time_msec = v as Long }),
        FieldSpec("background_time_msec", FieldType.INT64, { it.background_time_msec }, { b, v -> b.background_time_msec = v as Long }),
        FieldSpec("heavy_weight_time_msec", FieldType.INT64, { it.heavy_weight_time_msec }, { b, v -> b.heavy_weight_time_msec = v as Long }),
    ),
    factory = { BatteryAppStateTime.Builder() },
)

internal val BATTERY_APP_SYNC_SPEC: MessageSpec<BatteryAppSync.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("name", FieldType.STRING, { it.name }, { b, v -> b.name = v as String }),
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
        FieldSpec("background_time_msec", FieldType.INT64, { it.background_time_msec }, { b, v -> b.background_time_msec = v as Long }),
        FieldSpec("background_count", FieldType.INT32, { it.background_count }, { b, v -> b.background_count = v as Int }),
    ),
    factory = { BatteryAppSync.Builder() },
)

internal val BATTERY_APP_VIBRATOR_SPEC: MessageSpec<BatteryAppVibrator.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
    ),
    factory = { BatteryAppVibrator.Builder() },
)

internal val BATTERY_APP_VIDEO_SPEC: MessageSpec<BatteryAppVideo.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
    ),
    factory = { BatteryAppVideo.Builder() },
)

internal val BATTERY_APP_WAKEUP_ALARM_SPEC: MessageSpec<BatteryAppWakeupAlarm.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("name", FieldType.STRING, { it.name }, { b, v -> b.name = v as String }),
        FieldSpec("count", FieldType.INT32, { it.count }, { b, v -> b.count = v as Int }),
    ),
    factory = { BatteryAppWakeupAlarm.Builder() },
)

internal val BATTERY_APP_WIFI_SPEC: MessageSpec<BatteryAppWifi.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("full_wifi_lock_time_msec", FieldType.FLOAT, { it.full_wifi_lock_time_msec }, { b, v -> b.full_wifi_lock_time_msec = v as Float }),
        FieldSpec("scan_time_msec", FieldType.FLOAT, { it.scan_time_msec }, { b, v -> b.scan_time_msec = v as Float }),
        FieldSpec("running_time_msec", FieldType.FLOAT, { it.running_time_msec }, { b, v -> b.running_time_msec = v as Float }),
        FieldSpec("scan_count", FieldType.FLOAT, { it.scan_count }, { b, v -> b.scan_count = v as Float }),
        FieldSpec("idle_time_msec", FieldType.FLOAT, { it.idle_time_msec }, { b, v -> b.idle_time_msec = v as Float }),
        FieldSpec("rx_time_msec", FieldType.FLOAT, { it.rx_time_msec }, { b, v -> b.rx_time_msec = v as Float }),
        FieldSpec("tx_time_msec", FieldType.FLOAT, { it.tx_time_msec }, { b, v -> b.tx_time_msec = v as Float }),
        FieldSpec("scan_count_bg", FieldType.INT32, { it.scan_count_bg }, { b, v -> b.scan_count_bg = v as Int }),
        FieldSpec("scan_actual_time_msec", FieldType.INT64, { it.scan_actual_time_msec }, { b, v -> b.scan_actual_time_msec = v as Long }),
        FieldSpec("scan_actual_time_msec_bg", FieldType.INT64, { it.scan_actual_time_msec_bg }, { b, v -> b.scan_actual_time_msec_bg = v as Long }),
    ),
    factory = { BatteryAppWifi.Builder() },
)

internal val BATTERY_SYSTEM_BATTERY_SPEC: MessageSpec<BatterySystemBattery.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("start_count", FieldType.FLOAT, { it.start_count }, { b, v -> b.start_count = v as Float }),
        FieldSpec("battery_realtime_msec", FieldType.FLOAT, { it.battery_realtime_msec }, { b, v -> b.battery_realtime_msec = v as Float }),
        FieldSpec("battery_uptime_msec", FieldType.FLOAT, { it.battery_uptime_msec }, { b, v -> b.battery_uptime_msec = v as Float }),
        FieldSpec("total_realtime_msec", FieldType.FLOAT, { it.total_realtime_msec }, { b, v -> b.total_realtime_msec = v as Float }),
        FieldSpec("total_uptime_msec", FieldType.FLOAT, { it.total_uptime_msec }, { b, v -> b.total_uptime_msec = v as Float }),
        FieldSpec("start_clock_time_msec", FieldType.INT64, { it.start_clock_time_msec }, { b, v -> b.start_clock_time_msec = v as Long }),
        FieldSpec("screen_off_realtime_msec", FieldType.FLOAT, { it.screen_off_realtime_msec }, { b, v -> b.screen_off_realtime_msec = v as Float }),
        FieldSpec("screen_off_uptime_msec", FieldType.FLOAT, { it.screen_off_uptime_msec }, { b, v -> b.screen_off_uptime_msec = v as Float }),
        FieldSpec("estimated_battery_capacity_mah", FieldType.INT64, { it.estimated_battery_capacity_mah }, { b, v -> b.estimated_battery_capacity_mah = v as Long }),
        FieldSpec("min_learned_battery_capacity_uah", FieldType.INT64, { it.min_learned_battery_capacity_uah }, { b, v -> b.min_learned_battery_capacity_uah = v as Long }),
        FieldSpec("max_learned_battery_capacity_uah", FieldType.INT64, { it.max_learned_battery_capacity_uah }, { b, v -> b.max_learned_battery_capacity_uah = v as Long }),
        FieldSpec("screen_doze_realtime_msec", FieldType.FLOAT, { it.screen_doze_realtime_msec }, { b, v -> b.screen_doze_realtime_msec = v as Float }),
    ),
    factory = { BatterySystemBattery.Builder() },
)

internal val BATTERY_SYSTEM_BATTERY_DISCHARGE_SPEC: MessageSpec<BatterySystemBatteryDischarge.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("lower_bound", FieldType.FLOAT, { it.lower_bound }, { b, v -> b.lower_bound = v as Float }),
        FieldSpec("upper_bound", FieldType.FLOAT, { it.upper_bound }, { b, v -> b.upper_bound = v as Float }),
        FieldSpec("screen_on", FieldType.FLOAT, { it.screen_on }, { b, v -> b.screen_on = v as Float }),
        FieldSpec("screen_off", FieldType.FLOAT, { it.screen_off }, { b, v -> b.screen_off = v as Float }),
        FieldSpec("total_mah", FieldType.INT64, { it.total_mah }, { b, v -> b.total_mah = v as Long }),
        FieldSpec("total_mah_screen_off", FieldType.INT64, { it.total_mah_screen_off }, { b, v -> b.total_mah_screen_off = v as Long }),
        FieldSpec("screen_doze", FieldType.FLOAT, { it.screen_doze }, { b, v -> b.screen_doze = v as Float }),
        FieldSpec("screen_doze_count", FieldType.FLOAT, { it.screen_doze_count }, { b, v -> b.screen_doze_count = v as Float }),
        FieldSpec("light_doze_count", FieldType.FLOAT, { it.light_doze_count }, { b, v -> b.light_doze_count = v as Float }),
        FieldSpec("deep_doze_count", FieldType.FLOAT, { it.deep_doze_count }, { b, v -> b.deep_doze_count = v as Float }),
    ),
    factory = { BatterySystemBatteryDischarge.Builder() },
)

internal val BATTERY_SYSTEM_WIFI_MULTICAST_SPEC: MessageSpec<BatterySystemWifiMulticast.Builder> =
    MessageSpec(
        fields = listOf(
            FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
            FieldSpec("count", FieldType.INT32, { it.count }, { b, v -> b.count = v as Int }),
        ),
        factory = { BatterySystemWifiMulticast.Builder() },
    )

internal val BATTERY_SYSTEM_RESOURCE_POWER_MANAGER_SPEC: MessageSpec<BatterySystemResourcePowerManager.Builder> =
    MessageSpec(
        fields = listOf(
            FieldSpec("name", FieldType.STRING, { it.name }, { b, v -> b.name = v as String }),
            FieldSpec("total_time_msec", FieldType.FLOAT, { it.total_time_msec }, { b, v -> b.total_time_msec = v as Float }),
            FieldSpec("total_count", FieldType.INT32, { it.total_count }, { b, v -> b.total_count = v as Int }),
            FieldSpec("screen_off_time_msec", FieldType.FLOAT, { it.screen_off_time_msec }, { b, v -> b.screen_off_time_msec = v as Float }),
            FieldSpec("screen_off_count", FieldType.INT32, { it.screen_off_count }, { b, v -> b.screen_off_count = v as Int }),
        ),
        factory = { BatterySystemResourcePowerManager.Builder() },
    )

internal val BATTERY_SYSTEM_BATTERY_LEVEL_SPEC: MessageSpec<BatterySystemBatteryLevel.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("start_level", FieldType.FLOAT, { it.start_level }, { b, v -> b.start_level = v as Float }),
        FieldSpec("current_level", FieldType.FLOAT, { it.current_level }, { b, v -> b.current_level = v as Float }),
    ),
    factory = { BatterySystemBatteryLevel.Builder() },
)

internal val BATTERY_SYSTEM_CHARGE_TIME_REMAINING_SPEC: MessageSpec<BatterySystemChargeTimeRemaining.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("usec", FieldType.INT64, { it.usec }, { b, v -> b.usec = v as Long }),
    ),
    factory = { BatterySystemChargeTimeRemaining.Builder() },
)

internal val BATTERY_SYSTEM_DISCHARGE_TIME_REMAINING_SPEC: MessageSpec<BatterySystemDischargeTimeRemaining.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("usec", FieldType.INT64, { it.usec }, { b, v -> b.usec = v as Long }),
    ),
    factory = { BatterySystemDischargeTimeRemaining.Builder() },
)

internal val BATTERY_SYSTEM_GLOBAL_BLUETOOTH_SPEC: MessageSpec<BatterySystemGlobalBluetooth.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("bluetooth_idle_time_msec", FieldType.FLOAT, { it.bluetooth_idle_time_msec }, { b, v -> b.bluetooth_idle_time_msec = v as Float }),
        FieldSpec("bluetooth_rx_time_msec", FieldType.FLOAT, { it.bluetooth_rx_time_msec }, { b, v -> b.bluetooth_rx_time_msec = v as Float }),
        FieldSpec("bluetooth_tx_time_msec", FieldType.FLOAT, { it.bluetooth_tx_time_msec }, { b, v -> b.bluetooth_tx_time_msec = v as Float }),
        FieldSpec("bluetooth_power_mah", FieldType.FLOAT, { it.bluetooth_power_mah }, { b, v -> b.bluetooth_power_mah = v as Float }),
    ),
    factory = { BatterySystemGlobalBluetooth.Builder() },
)

internal val BATTERY_SYSTEM_GLOBAL_NETWORK_SPEC: MessageSpec<BatterySystemGlobalNetwork.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("mobile_bytes_rx", FieldType.FLOAT, { it.mobile_bytes_rx }, { b, v -> b.mobile_bytes_rx = v as Float }),
        FieldSpec("mobile_bytes_tx", FieldType.FLOAT, { it.mobile_bytes_tx }, { b, v -> b.mobile_bytes_tx = v as Float }),
        FieldSpec("wifi_bytes_rx", FieldType.FLOAT, { it.wifi_bytes_rx }, { b, v -> b.wifi_bytes_rx = v as Float }),
        FieldSpec("wifi_bytes_tx", FieldType.FLOAT, { it.wifi_bytes_tx }, { b, v -> b.wifi_bytes_tx = v as Float }),
        FieldSpec("mobile_packets_rx", FieldType.FLOAT, { it.mobile_packets_rx }, { b, v -> b.mobile_packets_rx = v as Float }),
        FieldSpec("mobile_packets_tx", FieldType.FLOAT, { it.mobile_packets_tx }, { b, v -> b.mobile_packets_tx = v as Float }),
        FieldSpec("wifi_packets_rx", FieldType.FLOAT, { it.wifi_packets_rx }, { b, v -> b.wifi_packets_rx = v as Float }),
        FieldSpec("wifi_packets_tx", FieldType.FLOAT, { it.wifi_packets_tx }, { b, v -> b.wifi_packets_tx = v as Float }),
        FieldSpec("bt_bytes_rx", FieldType.INT64, { it.bt_bytes_rx }, { b, v -> b.bt_bytes_rx = v as Long }),
        FieldSpec("bt_bytes_tx", FieldType.INT64, { it.bt_bytes_tx }, { b, v -> b.bt_bytes_tx = v as Long }),
    ),
    factory = { BatterySystemGlobalNetwork.Builder() },
)

internal val BATTERY_SYSTEM_GLOBAL_WIFI_SPEC: MessageSpec<BatterySystemGlobalWifi.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("wifi_on_time_msec", FieldType.FLOAT, { it.wifi_on_time_msec }, { b, v -> b.wifi_on_time_msec = v as Float }),
        FieldSpec("wifi_running_time_msec", FieldType.FLOAT, { it.wifi_running_time_msec }, { b, v -> b.wifi_running_time_msec = v as Float }),
        FieldSpec("wifi_idle_time_msec", FieldType.FLOAT, { it.wifi_idle_time_msec }, { b, v -> b.wifi_idle_time_msec = v as Float }),
        FieldSpec("wifi_rx_time_msec", FieldType.FLOAT, { it.wifi_rx_time_msec }, { b, v -> b.wifi_rx_time_msec = v as Float }),
        FieldSpec("wifi_tx_time_msec", FieldType.FLOAT, { it.wifi_tx_time_msec }, { b, v -> b.wifi_tx_time_msec = v as Float }),
        FieldSpec("wifi_power_mah", FieldType.FLOAT, { it.wifi_power_mah }, { b, v -> b.wifi_power_mah = v as Float }),
    ),
    factory = { BatterySystemGlobalWifi.Builder() },
)

internal val BATTERY_SYSTEM_KERNEL_WAKELOCK_SPEC: MessageSpec<BatterySystemKernelWakelock.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("name", FieldType.STRING, { it.name }, { b, v -> b.name = v as String }),
        FieldSpec("time_msec", FieldType.FLOAT, { it.time_msec }, { b, v -> b.time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
        FieldSpec("current_duration_msec", FieldType.INT64, { it.current_duration_msec }, { b, v -> b.current_duration_msec = v as Long }),
        FieldSpec("max_duration_msec", FieldType.INT64, { it.max_duration_msec }, { b, v -> b.max_duration_msec = v as Long }),
    ),
    factory = { BatterySystemKernelWakelock.Builder() },
)

internal val BATTERY_SYSTEM_MISC_SPEC: MessageSpec<BatterySystemMisc.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("screen_on_time_msec", FieldType.FLOAT, { it.screen_on_time_msec }, { b, v -> b.screen_on_time_msec = v as Float }),
        FieldSpec("screen_off_time_msec", FieldType.FLOAT, { it.screen_off_time_msec }, { b, v -> b.screen_off_time_msec = v as Float }),
        FieldSpec("phone_on_time_msec", FieldType.FLOAT, { it.phone_on_time_msec }, { b, v -> b.phone_on_time_msec = v as Float }),
        FieldSpec("wifi_on_time_msec", FieldType.FLOAT, { it.wifi_on_time_msec }, { b, v -> b.wifi_on_time_msec = v as Float }),
        FieldSpec("wifi_running_time_msec", FieldType.FLOAT, { it.wifi_running_time_msec }, { b, v -> b.wifi_running_time_msec = v as Float }),
        FieldSpec("bluetooth_on_time_msec", FieldType.FLOAT, { it.bluetooth_on_time_msec }, { b, v -> b.bluetooth_on_time_msec = v as Float }),
        FieldSpec("mobile_bytes_rx", FieldType.FLOAT, { it.mobile_bytes_rx }, { b, v -> b.mobile_bytes_rx = v as Float }),
        FieldSpec("mobile_bytes_tx", FieldType.FLOAT, { it.mobile_bytes_tx }, { b, v -> b.mobile_bytes_tx = v as Float }),
        FieldSpec("wifi_bytes_rx", FieldType.FLOAT, { it.wifi_bytes_rx }, { b, v -> b.wifi_bytes_rx = v as Float }),
        FieldSpec("wifi_bytes_tx", FieldType.FLOAT, { it.wifi_bytes_tx }, { b, v -> b.wifi_bytes_tx = v as Float }),
        FieldSpec("full_wakelock_time_msec", FieldType.FLOAT, { it.full_wakelock_time_msec }, { b, v -> b.full_wakelock_time_msec = v as Float }),
        FieldSpec("partial_wakelock_time_msec", FieldType.FLOAT, { it.partial_wakelock_time_msec }, { b, v -> b.partial_wakelock_time_msec = v as Float }),
        FieldSpec("mobile_active_time_msec", FieldType.FLOAT, { it.mobile_active_time_msec }, { b, v -> b.mobile_active_time_msec = v as Float }),
        FieldSpec("mobile_active_adjusted_time_msec", FieldType.FLOAT, { it.mobile_active_adjusted_time_msec }, { b, v -> b.mobile_active_adjusted_time_msec = v as Float }),
        FieldSpec("interactive_time_msec", FieldType.FLOAT, { it.interactive_time_msec }, { b, v -> b.interactive_time_msec = v as Float }),
        FieldSpec("low_power_mode_enabled_time_msec", FieldType.FLOAT, { it.low_power_mode_enabled_time_msec }, { b, v -> b.low_power_mode_enabled_time_msec = v as Float }),
        FieldSpec("connectivity_changes", FieldType.FLOAT, { it.connectivity_changes }, { b, v -> b.connectivity_changes = v as Float }),
        FieldSpec("device_idle_mode_enabled_time_msec", FieldType.FLOAT, { it.device_idle_mode_enabled_time_msec }, { b, v -> b.device_idle_mode_enabled_time_msec = v as Float }),
        FieldSpec("device_idle_mode_enabled_count", FieldType.FLOAT, { it.device_idle_mode_enabled_count }, { b, v -> b.device_idle_mode_enabled_count = v as Float }),
        FieldSpec("device_idling_time_msec", FieldType.FLOAT, { it.device_idling_time_msec }, { b, v -> b.device_idling_time_msec = v as Float }),
        FieldSpec("device_idling_count", FieldType.FLOAT, { it.device_idling_count }, { b, v -> b.device_idling_count = v as Float }),
        FieldSpec("mobile_active_count", FieldType.FLOAT, { it.mobile_active_count }, { b, v -> b.mobile_active_count = v as Float }),
        FieldSpec("mobile_active_unknown_time", FieldType.FLOAT, { it.mobile_active_unknown_time }, { b, v -> b.mobile_active_unknown_time = v as Float }),
        FieldSpec("device_light_idle_mode_enabled_time_msec", FieldType.INT64, { it.device_light_idle_mode_enabled_time_msec }, { b, v -> b.device_light_idle_mode_enabled_time_msec = v as Long }),
        FieldSpec("device_light_idle_mode_enabled_count", FieldType.INT32, { it.device_light_idle_mode_enabled_count }, { b, v -> b.device_light_idle_mode_enabled_count = v as Int }),
        FieldSpec("device_light_idling_time_msec", FieldType.INT64, { it.device_light_idling_time_msec }, { b, v -> b.device_light_idling_time_msec = v as Long }),
        FieldSpec("device_light_idling_count", FieldType.INT32, { it.device_light_idling_count }, { b, v -> b.device_light_idling_count = v as Int }),
        FieldSpec("max_device_light_idle_mode_enabled_time_msec", FieldType.INT64, { it.max_device_light_idle_mode_enabled_time_msec }, { b, v -> b.max_device_light_idle_mode_enabled_time_msec = v as Long }),
        FieldSpec("max_device_idle_mode_enabled_time_msec", FieldType.INT64, { it.max_device_idle_mode_enabled_time_msec }, { b, v -> b.max_device_idle_mode_enabled_time_msec = v as Long }),
    ),
    factory = { BatterySystemMisc.Builder() },
)

internal val BATTERY_SYSTEM_POWER_USE_SUMMARY_SPEC: MessageSpec<BatterySystemPowerUseSummary.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("battery_capacity_mah", FieldType.FLOAT, { it.battery_capacity_mah }, { b, v -> b.battery_capacity_mah = v as Float }),
        FieldSpec("computed_power_mah", FieldType.FLOAT, { it.computed_power_mah }, { b, v -> b.computed_power_mah = v as Float }),
        FieldSpec("min_drained_power_mah", FieldType.FLOAT, { it.min_drained_power_mah }, { b, v -> b.min_drained_power_mah = v as Float }),
        FieldSpec("max_drained_power_mah", FieldType.FLOAT, { it.max_drained_power_mah }, { b, v -> b.max_drained_power_mah = v as Float }),
    ),
    factory = { BatterySystemPowerUseSummary.Builder() },
)

internal val BATTERY_SYSTEM_SIGNAL_SCANNING_TIME_SPEC: MessageSpec<BatterySystemSignalScanningTime.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("time_msec", FieldType.FLOAT, { it.time_msec }, { b, v -> b.time_msec = v as Float }),
    ),
    factory = { BatterySystemSignalScanningTime.Builder() },
)

internal val BATTERY_SYSTEM_WAKEUP_REASON_SPEC: MessageSpec<BatterySystemWakeupReason.Builder> = MessageSpec(
    fields = listOf(
        FieldSpec("name", FieldType.STRING, { it.name }, { b, v -> b.name = v as String }),
        FieldSpec("time_msec", FieldType.FLOAT, { it.time_msec }, { b, v -> b.time_msec = v as Float }),
        FieldSpec("count", FieldType.FLOAT, { it.count }, { b, v -> b.count = v as Float }),
    ),
    factory = { BatterySystemWakeupReason.Builder() },
)
