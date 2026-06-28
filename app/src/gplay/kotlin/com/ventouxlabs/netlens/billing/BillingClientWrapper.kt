package com.ventouxlabs.netlens.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

interface BillingClientWrapper {
    val isReady: Boolean
    fun startConnection(listener: BillingClientStateListener)
    fun queryPurchasesAsync(params: QueryPurchasesParams, listener: PurchasesResponseListener)
    fun queryProductDetailsAsync(
        params: QueryProductDetailsParams,
        listener: ProductDetailsResponseListener,
    )
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult
    fun acknowledgePurchase(
        params: AcknowledgePurchaseParams,
        listener: AcknowledgePurchaseResponseListener,
    )
}

fun interface BillingClientFactory {
    fun create(listener: PurchasesUpdatedListener): BillingClientWrapper
}

class PlayBillingClientWrapper(
    context: Context,
    listener: PurchasesUpdatedListener,
) : BillingClientWrapper {

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases()
        .build()

    override val isReady: Boolean get() = client.isReady

    override fun startConnection(listener: BillingClientStateListener) {
        client.startConnection(listener)
    }

    override fun queryPurchasesAsync(
        params: QueryPurchasesParams,
        listener: PurchasesResponseListener,
    ) {
        client.queryPurchasesAsync(params, listener)
    }

    override fun queryProductDetailsAsync(
        params: QueryProductDetailsParams,
        listener: ProductDetailsResponseListener,
    ) {
        client.queryProductDetailsAsync(params, listener)
    }

    override fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams,
    ): BillingResult {
        return client.launchBillingFlow(activity, params)
    }

    override fun acknowledgePurchase(
        params: AcknowledgePurchaseParams,
        listener: AcknowledgePurchaseResponseListener,
    ) {
        client.acknowledgePurchase(params, listener)
    }
}
