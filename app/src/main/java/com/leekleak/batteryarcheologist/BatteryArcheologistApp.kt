package com.leekleak.batteryarcheologist

import android.app.Application
import com.leekleak.shizuku.ShizukuGateway
import timber.log.Timber

class BatteryArcheologistApp : Application() {
    lateinit var gateway: ShizukuGateway
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        gateway = ShizukuGateway(
            context = this,
            debuggable = BuildConfig.DEBUG,
            versionCode = BuildConfig.VERSION_CODE,
        )
    }
}
