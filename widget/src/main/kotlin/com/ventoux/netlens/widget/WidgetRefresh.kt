package com.ventoux.netlens.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

suspend fun refreshAllWidgets(context: Context) {
    NetLensWidget().updateAll(context)
}

suspend fun resetCarouselAndRefreshWidgets(context: Context) {
    val dataStore = IpWidgetStateDefinition.getDataStore(context, "")
    dataStore.edit { prefs ->
        prefs[IpWidgetStateDefinition.CAROUSEL_PAGE_KEY] = 0
    }
    NetLensWidget().updateAll(context)
}

private const val PERIODIC_WORK_NAME = "netlens_widget_periodic_refresh"

fun enqueueWidgetRefresh(context: Context) {
    val workRequest = OneTimeWorkRequestBuilder<IpWidgetRefreshWorker>().build()
    WorkManager.getInstance(context).enqueue(workRequest)
}

fun schedulePeriodicWidgetRefresh(context: Context) {
    val periodicWork = PeriodicWorkRequestBuilder<IpWidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        periodicWork,
    )
}

fun cancelPeriodicWidgetRefresh(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
}
