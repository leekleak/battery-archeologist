package com.leekleak.batteryarcheologist.core.model

data class PackageInfoModel(
    val name: String = "",
    val uid: Int = 0,
    val versionCode: Long = 0L,
    val versionName: String = "",
    val sharedUserId: String = "",
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
)
