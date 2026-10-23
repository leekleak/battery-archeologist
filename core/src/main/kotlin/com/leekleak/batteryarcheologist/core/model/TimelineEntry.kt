package com.leekleak.batteryarcheologist.core.model

/**
 * One row of the Battery Historian V2 CSV stream:
 * metric,type,start_time,end_time,value,opt
 *
 * Keeping this shape makes the port directly comparable with the Go oracle and
 * the existing historian JS, while the Compose UI gets a typed representation.
 */
data class TimelineEntry(
    val metric: String,
    val type: String,
    val startMs: Long,
    val endMs: Long,
    val value: String,
    val opt: String = "",
)
