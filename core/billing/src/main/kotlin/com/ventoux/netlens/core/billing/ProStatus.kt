package com.ventoux.netlens.core.billing

import android.app.Activity
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ProStatus {
    val isPro: StateFlow<Boolean>
    fun launchPurchase(activity: Activity)
}

val LocalProStatus = staticCompositionLocalOf<ProStatus> {
    object : ProStatus {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
        override fun launchPurchase(activity: Activity) {}
    }
}
