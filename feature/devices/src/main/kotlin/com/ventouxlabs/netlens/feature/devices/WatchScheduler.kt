package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.feature.devices.model.WatchCadence

sealed interface ScheduleAction {
    data class Enqueue(val cadence: WatchCadence) : ScheduleAction
    data object Cancel : ScheduleAction
}

/**
 * Pure scheduling decision: background watch runs only when the user is Pro AND the
 * master toggle is on. Any other combination cancels the periodic work.
 */
fun computeScheduleAction(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence): ScheduleAction =
    if (isPro && masterEnabled) ScheduleAction.Enqueue(cadence) else ScheduleAction.Cancel

interface WatchScheduler {
    /** Enqueues or cancels the periodic watch based on [computeScheduleAction]. */
    fun apply(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence)
}
