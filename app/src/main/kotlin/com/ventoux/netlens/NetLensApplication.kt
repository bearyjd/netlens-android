package com.ventoux.netlens

import android.app.Application
import com.ventoux.netlens.widget.enqueuePeriodicWidgetRefresh
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NetLensApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Belt-and-suspenders for users who already have widgets placed:
        // onEnabled() only fires on first placement, so without this the
        // periodic refresh would only schedule after a fresh widget add.
        // ExistingPeriodicWorkPolicy.KEEP makes repeat calls idempotent.
        enqueuePeriodicWidgetRefresh(this)
    }
}
