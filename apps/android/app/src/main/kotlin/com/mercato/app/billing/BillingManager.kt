package com.mercato.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.mercato_ffi.Game

/**
 * Play Billing integration for the single remove-ads entitlement (a
 * non-consumable one-time product; there is no shop and no other product).
 *
 * The store owns the truth about the purchase; this class mirrors it into
 * the shared core (`Game.setAdsRemoved`), which every ad slot already asks
 * through the gate. Persisted in Prefs only so a fresh launch starts with
 * the last known state before the store answers.
 *
 * Billing Library 9.x flow (see apps/android/README.md): connect once with
 * auto-reconnection, query the product, restore entitlements at launch and
 * on resume, acknowledge new purchases within the 3-day window.
 */
class BillingManager(
    context: Context,
    /** Deferred: the Rust core parses the dataset lazily off the main thread. */
    private val gameProvider: () -> Game,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    companion object {
        /** Same product id as the App Store side, per docs/MONETIZATION.md. */
        const val PRODUCT_ID = "mercato_remove_ads"
    }

    private val _adsRemoved = MutableStateFlow(prefs.adsRemovedBlocking())
    val adsRemoved: StateFlow<Boolean> = _adsRemoved.asStateFlow()

    /** Localized store price of the remove-ads product, once known. */
    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    /** Call once at app start. Reconnection is handled by the library. */
    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restore()
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection handles retries.
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsResult.productDetailsList.firstOrNull()
                _formattedPrice.value = productDetails
                    ?.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()
                    ?.formattedPrice
            }
        }
    }

    /** Open the Play purchase sheet. No-op until product details arrived. */
    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: return
        val offerToken = details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
            ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    /**
     * Re-sync the entitlement from the store (launch, resume, or the
     * Settings "restore purchases" row). Also revokes locally after a
     * refund, since the store stops reporting the purchase.
     */
    fun restore() {
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any {
                it.products.contains(PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            purchases.forEach(::acknowledgeIfNeeded)
            setEntitlement(owned)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) {
            return
        }
        purchases.forEach { purchase ->
            // PENDING purchases grant nothing yet; the PURCHASED transition
            // arrives through this same listener or the next restore().
            if (purchase.products.contains(PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            ) {
                acknowledgeIfNeeded(purchase)
                setEntitlement(true)
            }
        }
    }

    /** Unacknowledged purchases are auto-refunded after 3 days. */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED ||
            purchase.isAcknowledged
        ) {
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { /* retried by the next restore() on failure */ }
    }

    private fun setEntitlement(owned: Boolean) {
        gameProvider().setAdsRemoved(owned)
        _adsRemoved.value = owned
        scope.launch { prefs.setAdsRemoved(owned) }
    }
}
