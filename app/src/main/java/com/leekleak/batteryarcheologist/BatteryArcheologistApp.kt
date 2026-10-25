package com.leekleak.batteryarcheologist

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.leekleak.batteryarcheologist.ui.apps.AppIconFetcher
import com.leekleak.batteryarcheologist.ui.apps.AppIconKeyer
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
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(AppIconFetcher.Factory())
                    add(AppIconKeyer())
                }
                .build()
        }
        gateway = ShizukuGateway(
            context = this,
            debuggable = BuildConfig.DEBUG,
            versionCode = BuildConfig.VERSION_CODE,
        )
    }
}
