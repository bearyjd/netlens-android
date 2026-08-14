package com.ventouxlabs.netlens.widget

import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression coverage for the bug [refreshAllWidgets]'s doc comment describes: a corrupted
 * `providerToReceiver` map used to let `updateAll()` push one receiver's widget onto another
 * receiver's placed instance (observed on a Pixel 10, appwidget id 2). [refreshWidgets] is the
 * pulled-out, framework-independent version of that dispatch logic — this file asserts the
 * invariant directly with fakes, no Android/Glance runtime required.
 */
class WidgetRefreshTest {

    private class FakeGlanceId(val label: String) : GlanceId

    @Test
    fun `WIDGET_RECEIVERS pairs each receiver with its own widget class`() {
        assertEquals(4, WIDGET_RECEIVERS.size)
        assertEquals(CompactWidgetReceiver::class.java, WIDGET_RECEIVERS[0].first)
        assertEquals(StandardWidgetReceiver::class.java, WIDGET_RECEIVERS[1].first)
        assertEquals(DashboardWidgetReceiver::class.java, WIDGET_RECEIVERS[2].first)
        assertEquals(FourByTwoWidgetReceiver::class.java, WIDGET_RECEIVERS[3].first)
    }

    // The reason this test file exists: a receiver's widget ids must never be dispatched to a
    // different receiver's widget instance. That crossing is exactly what the historical
    // updateAll() bug did.
    @Test
    fun `each receiver's ids only ever reach that receiver's own widget instance`() = runTest {
        val idsByReceiver: Map<Class<out GlanceAppWidgetReceiver>, IntArray> = mapOf(
            CompactWidgetReceiver::class.java to intArrayOf(1, 2),
            StandardWidgetReceiver::class.java to intArrayOf(3),
            DashboardWidgetReceiver::class.java to intArrayOf(),
            FourByTwoWidgetReceiver::class.java to intArrayOf(4, 5, 6),
        )
        val calls = mutableListOf<Pair<GlanceAppWidget, Int>>()

        refreshWidgets(
            idsFor = { receiver -> idsByReceiver.getValue(receiver) },
            glanceIdFor = { id -> FakeGlanceId("id-$id") },
            update = { widget, glanceId -> calls.add(widget to (glanceId as FakeGlanceId).label.removePrefix("id-").toInt()) },
        )

        // Every recorded call's widget class matches the receiver whose idsFor produced that id.
        idsByReceiver.forEach { (receiverClass, ids) ->
            val expectedWidgetClass = WIDGET_RECEIVERS.first { it.first == receiverClass }.second().javaClass
            ids.forEach { id ->
                val actualCall = calls.first { it.second == id }
                assertEquals(
                    expectedWidgetClass,
                    actualCall.first.javaClass,
                    "id $id (belongs to $receiverClass) was dispatched to the wrong widget class",
                )
            }
        }
        assertEquals(6, calls.size)
    }

    @Test
    fun `a receiver with no ids never has its widget factory invoked`() = runTest {
        var factoryCalled = false
        val onlyReceiver = WIDGET_RECEIVERS.first().first

        refreshWidgets(
            receivers = listOf(onlyReceiver to { factoryCalled = true; WIDGET_RECEIVERS.first().second() }),
            idsFor = { intArrayOf() },
            glanceIdFor = { FakeGlanceId("unused") },
            update = { _, _ -> },
        )

        assertTrue(!factoryCalled)
    }
}
