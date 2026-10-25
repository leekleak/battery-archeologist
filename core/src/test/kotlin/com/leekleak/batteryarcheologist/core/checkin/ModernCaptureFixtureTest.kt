package com.leekleak.batteryarcheologist.core.checkin

import com.leekleak.batteryarcheologist.core.checkin.checkinparse.CheckinParser
import com.leekleak.batteryarcheologist.core.history.parseutils.HistoryMetrics
import com.leekleak.batteryarcheologist.core.history.parseutils.HistoryParser
import com.leekleak.batteryarcheologist.core.history.parseutils.HistorySummaryFormat
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trimmed regression capture from a real Android 16 device. It keeps one
 * example of every history/check-in tag and section that the pre-v36 parser
 * used to report as an error or warning.
 */
class ModernCaptureFixtureTest {
    private val raw: String by lazy {
        checkNotNull(javaClass.getResourceAsStream("/fixtures/android16-modern-checkin.txt")) {
            "android16-modern-checkin.txt fixture missing"
        }.bufferedReader().use { it.readText() }
    }

    @Test
    fun `trimmed android 16 capture has no parser errors or warnings`() {
        val history = HistoryParser.analyze(
            raw,
            HistorySummaryFormat.BATTERY_LEVEL,
            emptyList(),
        )
        assertEquals(emptyList<String>(), history.errors)
        assertEquals(emptySet<String>(), history.unsupportedKeys.keys)
        assertTrue(
            history.entries.any { it.metric == HistoryMetrics.CELLULAR_HIGH_TX_POWER },
        )
        assertTrue(
            history.entries.any { it.metric == HistoryMetrics.DISPLAY_STATE_EVENT },
        )

        val checkin = CheckinParser.parse(raw, emptyList())
        assertEquals(checkin.errors.toString(), emptyList<String>(), checkin.errors)
        assertEquals(checkin.warnings.toString(), emptyList<String>(), checkin.warnings)
        val stats = checkNotNull(checkin.stats)
        val app = stats.app.single { it.uid == 1000 }

        assertEquals(25972f, app.foreground_service!!.total_time_msec!!, 0f)
        assertEquals(2516596f, app.aggregated_wakelock!!.partial_time_msec!!, 0f)
        assertEquals(7, app.jobs_deferred!!.deferred_event_count!!)
        assertEquals(1, app.scheduled_job.single().stop_reason_count[1])
        assertEquals("A", app.cpu_times_at_freq.single().type)
        assertEquals(13245095f, app.wifi_multicast!!.total_time_msec!!, 0f)
        assertEquals(22, app.bluetooth_misc!!.ble_scan_result_count_bg!!)
        assertEquals(7, app.user_activity.size)
        assertEquals(60L, app.state_time!!.heavy_weight_time_msec!!)
        assertEquals(70f, app.state_time!!.cached_time_msec!!, 0f)
        assertEquals(1f, app.wakelock.single().background_partial_time_msec!!, 0f)

        val system = checkNotNull(stats.system)
        assertEquals(1793469f, system.battery!!.screen_doze_realtime_msec!!, 0f)
        assertEquals(1f, system.battery_discharge!!.screen_doze!!, 0f)
        assertEquals(64f, system.battery_discharge!!.screen_doze_count!!, 0f)
        assertEquals(23, system.data_connection.size)
        assertEquals(
            BatteryStats.System.DataConnection.Name.NR,
            system.data_connection[20].name,
        )
        assertEquals(listOf(364800L, 460800L, 556800L), system.global_cpu_freq!!.freq_khz)
        assertEquals(53152260f, system.global_wifi_multicast!!.total_time_msec!!, 0f)
        assertEquals("VDD_CX", system.resource_power_manager.single().name)
    }
}
