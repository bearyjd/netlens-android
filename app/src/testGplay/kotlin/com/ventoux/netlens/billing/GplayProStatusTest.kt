package com.ventoux.netlens.billing

import android.app.Activity
import android.content.SharedPreferences
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GplayProStatusTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var fakeClient: FakeBillingClientWrapper
    private lateinit var status: GplayProStatus

    @BeforeEach
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        fakeClient = FakeBillingClientWrapper()
        status = GplayProStatus(fakePrefs, BillingClientFactory { fakeClient })
    }

    @Test
    fun `initial state defaults to false`() {
        assertFalse(status.isPro.value)
    }

    @Test
    fun `initial state reads true from prefs`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putBoolean(GplayProStatus.KEY_PRO_UNLOCKED, true).apply()
        val client = FakeBillingClientWrapper()
        val s = GplayProStatus(prefs, BillingClientFactory { client })
        assertTrue(s.isPro.value)
    }

    @Test
    fun `updateProStatus sets flow and prefs`() {
        status.updateProStatus(true)
        assertTrue(status.isPro.value)
        assertTrue(fakePrefs.getBoolean(GplayProStatus.KEY_PRO_UNLOCKED, false))

        status.updateProStatus(false)
        assertFalse(status.isPro.value)
        assertFalse(fakePrefs.getBoolean(GplayProStatus.KEY_PRO_UNLOCKED, true))
    }

    @Test
    fun `onPurchasesUpdated OK with purchased state sets pro`() {
        val result = billingResult(BillingClient.BillingResponseCode.OK)
        val purchase = createPurchase(state = Purchase.PurchaseState.PURCHASED)
        status.onPurchasesUpdated(result, listOf(purchase))
        assertTrue(status.isPro.value)
    }

    @Test
    fun `onPurchasesUpdated OK with null purchases does not set pro`() {
        val result = billingResult(BillingClient.BillingResponseCode.OK)
        status.onPurchasesUpdated(result, null)
        assertFalse(status.isPro.value)
    }

    @Test
    fun `onPurchasesUpdated OK with empty list does not set pro`() {
        val result = billingResult(BillingClient.BillingResponseCode.OK)
        status.onPurchasesUpdated(result, emptyList())
        assertFalse(status.isPro.value)
    }

    @Test
    fun `onPurchasesUpdated ITEM_ALREADY_OWNED sets pro`() {
        fakeClient.purchasesToReturn = listOf(
            createPurchase(state = Purchase.PurchaseState.PURCHASED),
        )
        val result = billingResult(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
        status.onPurchasesUpdated(result, null)
        assertTrue(status.isPro.value)
    }

    @Test
    fun `onPurchasesUpdated USER_CANCELED does not change state`() {
        val result = billingResult(BillingClient.BillingResponseCode.USER_CANCELED)
        status.onPurchasesUpdated(result, null)
        assertFalse(status.isPro.value)
    }

    @Test
    fun `onPurchasesUpdated error does not change state`() {
        val result = billingResult(BillingClient.BillingResponseCode.ERROR)
        status.onPurchasesUpdated(result, null)
        assertFalse(status.isPro.value)
    }

    @Test
    fun `reconnect stops after max attempts`() {
        // init calls connectAndQueryPurchases → startConnection (attempt 1)
        assertEquals(1, fakeClient.startConnectionCalls)

        // Simulate 2 more non-OK setup results
        fakeClient.triggerSetupFinished(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        assertEquals(2, fakeClient.startConnectionCalls) // attempt 2

        fakeClient.triggerSetupFinished(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        assertEquals(3, fakeClient.startConnectionCalls) // attempt 3

        // Next non-OK should NOT trigger another attempt (counter = 3 >= MAX)
        fakeClient.triggerSetupFinished(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        assertEquals(3, fakeClient.startConnectionCalls) // still 3
    }

    @Test
    fun `successful setup resets reconnect counter`() {
        assertEquals(1, fakeClient.startConnectionCalls)

        // Fail once
        fakeClient.triggerSetupFinished(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        assertEquals(2, fakeClient.startConnectionCalls)

        // Succeed — resets counter
        fakeClient.triggerSetupFinished(BillingClient.BillingResponseCode.OK)

        // Simulate disconnect — should start fresh attempts from counter 0
        fakeClient.triggerDisconnected()
        assertEquals(3, fakeClient.startConnectionCalls)
    }

    @Test
    fun `acknowledge is called for unacknowledged purchased items`() {
        val result = billingResult(BillingClient.BillingResponseCode.OK)
        val purchase = createPurchase(
            state = Purchase.PurchaseState.PURCHASED,
            acknowledged = false,
            token = "test_token_123",
        )
        status.onPurchasesUpdated(result, listOf(purchase))
        assertTrue(fakeClient.acknowledgedTokens.contains("test_token_123"))
    }

    private fun billingResult(code: Int): BillingResult =
        BillingResult.newBuilder().setResponseCode(code).build()

    private fun createPurchase(
        productId: String = GplayProStatus.PRODUCT_ID,
        state: Int = Purchase.PurchaseState.PURCHASED,
        acknowledged: Boolean = false,
        token: String = "test_token",
    ): Purchase {
        val json = """{"productId":"$productId","purchaseToken":"$token","purchaseState":$state,"acknowledged":$acknowledged}"""
        return Purchase(json, "test_signature")
    }
}

private class FakeBillingClientWrapper : BillingClientWrapper {
    var startConnectionCalls = 0
    var lastConnectionListener: BillingClientStateListener? = null
    val acknowledgedTokens = mutableListOf<String>()
    var purchasesToReturn: List<Purchase> = emptyList()
    override var isReady: Boolean = false

    override fun startConnection(listener: BillingClientStateListener) {
        startConnectionCalls++
        lastConnectionListener = listener
    }

    override fun queryPurchasesAsync(
        params: QueryPurchasesParams,
        listener: PurchasesResponseListener,
    ) {
        listener.onQueryPurchasesResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build(),
            purchasesToReturn,
        )
    }

    override fun queryProductDetailsAsync(
        params: QueryProductDetailsParams,
        listener: ProductDetailsResponseListener,
    ) {}

    override fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams,
    ): BillingResult =
        BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()

    override fun acknowledgePurchase(
        params: AcknowledgePurchaseParams,
        listener: AcknowledgePurchaseResponseListener,
    ) {
        acknowledgedTokens.add(params.purchaseToken)
        listener.onAcknowledgePurchaseResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build(),
        )
    }

    fun triggerSetupFinished(responseCode: Int) {
        lastConnectionListener?.onBillingSetupFinished(
            BillingResult.newBuilder().setResponseCode(responseCode).build(),
        )
    }

    fun triggerDisconnected() {
        lastConnectionListener?.onBillingServiceDisconnected()
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = data.toMap()
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST") (data[key] as? Set<String> ?: defValues)
    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data, listeners)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) { listener?.let { listeners.add(it) } }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) { listener?.let { listeners.remove(it) } }

    private class FakeEditor(
        private val data: MutableMap<String, Any?>,
        private val listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>,
    ) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var cleared = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor { key?.let { pending[it] = values }; return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
        override fun remove(key: String?): SharedPreferences.Editor { key?.let { removals.add(it) }; return this }
        override fun clear(): SharedPreferences.Editor { cleared = true; return this }

        override fun commit(): Boolean { flush(); return true }
        override fun apply() { flush() }

        private fun flush() {
            if (cleared) data.clear()
            removals.forEach { data.remove(it) }
            data.putAll(pending)
        }
    }
}
