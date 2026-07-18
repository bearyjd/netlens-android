package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScheduleActionTest {
    @Test
    fun `enqueue only when pro and enabled`() {
        assertEquals(ScheduleAction.Enqueue(WatchCadence.ONE_HOUR), computeScheduleAction(true, true, WatchCadence.ONE_HOUR))
        assertEquals(ScheduleAction.Cancel, computeScheduleAction(false, true, WatchCadence.ONE_HOUR))
        assertEquals(ScheduleAction.Cancel, computeScheduleAction(true, false, WatchCadence.ONE_HOUR))
        assertEquals(ScheduleAction.Cancel, computeScheduleAction(false, false, WatchCadence.ONE_HOUR))
    }
}
