package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.feature.devices.model.WatchCadence

class RecordingWatchScheduler : WatchScheduler {
    data class Call(val isPro: Boolean, val masterEnabled: Boolean, val cadence: WatchCadence)
    val calls = mutableListOf<Call>()
    override fun apply(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence) {
        calls.add(Call(isPro, masterEnabled, cadence))
    }
}
