package com.ventouxlabs.netlens.feature.wifi.engine

import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import kotlinx.coroutines.flow.Flow

interface WifiSignalSampler {
    /**
     * Emits the live signal of the currently-associated AP every [intervalMs] until cancelled.
     *
     * A tick where the phone isn't associated emits **null** rather than a zero reading — a walk
     * into a dead zone must not look like a real -0 dBm sample. Null rather than simply skipping
     * the tick because the two are not the same to a caller: skipping is indistinguishable from
     * "still working on it", which would leave a capture waiting forever for samples that can no
     * longer arrive. An explicit null says the poll happened and there was nothing there.
     */
    fun samples(intervalMs: Long): Flow<WifiSignalSample?>
}
