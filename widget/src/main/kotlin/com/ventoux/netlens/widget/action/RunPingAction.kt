package com.ventoux.netlens.widget.action

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.ventoux.netlens.widget.FourByTwoWidget
import com.ventoux.netlens.widget.WidgetStateDefinition
import com.ventoux.netlens.widget.util.PingMeasurement
import kotlinx.coroutines.withTimeoutOrNull

class RunPingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val dataStore = WidgetStateDefinition.getDataStore(context, "")
        dataStore.edit { it[WidgetStateDefinition.CHIP_PING_RESULT] = "running" }
        FourByTwoWidget().updateAll(context)

        val result = try {
            withTimeoutOrNull(5000L) { PingMeasurement.measure() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        val resultStr = result?.toString() ?: "fail"
        dataStore.edit { it[WidgetStateDefinition.CHIP_PING_RESULT] = resultStr }
        FourByTwoWidget().updateAll(context)
    }
}
