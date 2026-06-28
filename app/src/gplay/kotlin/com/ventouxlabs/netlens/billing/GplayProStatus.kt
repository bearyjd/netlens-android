package com.ventouxlabs.netlens.billing

import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.ventouxlabs.netlens.R
import com.ventouxlabs.netlens.core.billing.ProStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GplayProStatus @Inject constructor(
    @BillingPrefs private val prefs: SharedPreferences,
    billingClientFactory: BillingClientFactory,
) : ProStatus, PurchasesUpdatedListener {

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_PRO_UNLOCKED, false))
    override val isPro: StateFlow<Boolean> = _isPro

    private val reconnectAttempts = AtomicInteger(0)

    private val billingClient: BillingClientWrapper = billingClientFactory.create(this)

    init {
        connectAndQueryPurchases()
    }

    private fun connectAndQueryPurchases() {
        if (reconnectAttempts.get() >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached, giving up")
            return
        }
        reconnectAttempts.incrementAndGet()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectAttempts.set(0)
                    queryExistingPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    connectAndQueryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                connectAndQueryPurchases()
            }
        })
    }

    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any {
                    it.products.contains(PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                updateProStatus(owned)
                purchases.filter { !it.isAcknowledged }.forEach(::acknowledge)
            }
        }
    }

    override fun launchPurchase(activity: Activity) {
        if (!billingClient.isReady) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.billing_connecting),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            if (reconnectAttempts.get() == 0) {
                connectAndQueryPurchases()
            }
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val productDetails = details.firstOrNull() ?: return@queryProductDetailsAsync

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build(),
                    ),
                )
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            updateProStatus(true)
                            if (!purchase.isAcknowledged) acknowledge(purchase)
                        }
                        Purchase.PurchaseState.PENDING ->
                            Log.i(TAG, "Purchase pending — waiting for completion")
                        else -> {}
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryExistingPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> Log.w(TAG, "Purchase failed: ${result.debugMessage}")
        }
    }

    internal fun updateProStatus(pro: Boolean) {
        _isPro.value = pro
        prefs.edit().putBoolean(KEY_PRO_UNLOCKED, pro).apply()
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    companion object {
        private const val TAG = "GplayProStatus"
        internal const val KEY_PRO_UNLOCKED = "pro_unlocked"
        internal const val MAX_RECONNECT_ATTEMPTS = 3
        internal const val PRODUCT_ID = "pro_unlock"
    }
}
