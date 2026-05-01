package com.ventoux.netlens.core.billing

import android.app.Activity
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

interface ProStatus {
    val isPro: StateFlow<Boolean>
    fun launchPurchase(activity: Activity)
}

val LocalProStatus = staticCompositionLocalOf<ProStatus> {
    error("No ProStatus provided")
}
