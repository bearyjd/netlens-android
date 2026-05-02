package com.ventoux.netlens.billing

import android.app.Activity
import com.ventoux.netlens.core.billing.ProStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FossProStatus : ProStatus {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(true)
    override fun launchPurchase(activity: Activity) {}
}
