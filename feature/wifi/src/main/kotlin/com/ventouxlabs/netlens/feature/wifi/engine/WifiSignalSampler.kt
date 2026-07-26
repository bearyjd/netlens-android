package com.ventouxlabs.netlens.feature.wifi.engine

import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import kotlinx.coroutines.flow.Flow

interface WifiSignalSampler {
    /**
     * Emits the live signal of the currently-associated AP every [intervalMs] until cancelled.
     * Ticks where the phone isn't associated emit nothing rather than a zero reading, so a walk
     * through a dead zone leaves a gap instead of a fake -0 dBm sample.
     */
    fun samples(intervalMs: Long): Flow<WifiSignalSample>
}
