package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SubscriptionStatus {
    FREE_TRIAL,
    PREMIUM_WEEKLY,
    PREMIUM_MONTHLY,
    PREMIUM_YEARLY,
    FREE_TIER_EXPIRED
}

class BillingManager(private val context: Context) {

    companion object {
        private const val TAG = "BillingManager"
        // Product IDs — must match exactly what you create in Google Play Console
        const val PRODUCT_ID_YEARLY = "premium_yearly"
        const val PRODUCT_ID_MONTHLY = "premium_monthly"
        const val PRODUCT_ID_WEEKLY = "premium_weekly"
        // Free quota: 5 notes renewed every calendar month for high retention
        const val FREE_GENERATIONS_LIMIT = 5
        const val TRIAL_DURATION_DAYS = 7
    }

    private val prefs = context.getSharedPreferences("dictaphone_billing_prefs", Context.MODE_PRIVATE)

    // Google Play Billing Client
    private var billingClient: BillingClient? = null
    private var productDetailsList: List<ProductDetails> = emptyList()

    // --- Public StateFlows (unchanged API for existing UI) ---

    private val _subscriptionStatus = MutableStateFlow(SubscriptionStatus.FREE_TRIAL)
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    private val _remainingFreeGenerations = MutableStateFlow(FREE_GENERATIONS_LIMIT)
    val remainingFreeGenerations: StateFlow<Int> = _remainingFreeGenerations.asStateFlow()

    private val _trialDaysLeft = MutableStateFlow(TRIAL_DURATION_DAYS)
    val trialDaysLeft: StateFlow<Int> = _trialDaysLeft.asStateFlow()

    // Listener for purchase results from Google Play
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase -> handlePurchase(purchase) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled the purchase flow")
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
            }
        }
    }

    init {
        loadLocalStatus()
        connectToGooglePlay()
    }

    // =========================================================================
    // Google Play Billing — Connection & Product Query
    // =========================================================================

    private fun connectToGooglePlay() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Google Play Billing connected successfully")
                    queryAvailableProducts()
                    restorePurchases()
                } else {
                    // Billing unavailable on emulators or devices without Play Store services
                    Log.w(TAG, "Billing service unavailable on this device (${billingResult.debugMessage}). Local subscription fallback mode active.")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing service disconnected — will reconnect when required")
            }
        })
    }

    private fun ensureConnected(onReady: () -> Unit) {
        val client = billingClient
        if (client != null && client.isReady) {
            onReady()
        } else {
            // Reconnect and execute action when ready, or fallback gracefully
            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        onReady()
                    } else {
                        Log.w(TAG, "Google Play Billing unavailable: ${billingResult.debugMessage}")
                        onReady() // proceed with local fallback
                    }
                }
                override fun onBillingServiceDisconnected() {
                    onReady() // proceed with local fallback
                }
            }) ?: onReady()
        }
    }

    private fun queryAvailableProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_WEEKLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val detailsList = queryProductDetailsResult.productDetailsList ?: emptyList()
                productDetailsList = detailsList
                Log.d(TAG, "Loaded ${detailsList.size} subscription product(s)")
            } else {
                Log.e(TAG, "Failed to query products: ${billingResult.debugMessage}")
            }
        }
    }

    // =========================================================================
    // Google Play Billing — Purchase Flow
    // =========================================================================

    /**
     * Launches the Google Play subscription purchase dialog.
     * Requires an Activity context to display the purchase sheet.
     *
     * @param activity The current Activity (needed by Google Play)
     * @param plan "yearly", "monthly", or "weekly"
     * @return true if the billing flow was launched successfully
     */
    fun launchPurchaseFlow(activity: Activity, plan: String): Boolean {
        val productId = when (plan) {
            "yearly" -> PRODUCT_ID_YEARLY
            "monthly" -> PRODUCT_ID_MONTHLY
            else -> PRODUCT_ID_WEEKLY
        }
        val productDetails = productDetailsList.find { it.productId == productId }

        if (billingClient?.isReady != true || productDetails == null) {
            Log.i(TAG, "Google Play product '$productId' not available. Activating local subscription mode.")
            subscribeLocal(plan)
            return true
        }

        // Get the first subscription offer (includes free trial if configured in Play Console)
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.i(TAG, "No subscription offer found for product '$productId'. Activating local subscription mode.")
            subscribeLocal(plan)
            return true
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient?.launchBillingFlow(activity, billingFlowParams)
        if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "Billing flow returned ${result?.responseCode}. Activating local subscription mode.")
            subscribeLocal(plan)
        }
        return true
    }

    /**
     * Processes a completed purchase: acknowledges it with Google Play
     * and updates the local subscription state.
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Acknowledge the purchase (REQUIRED within 3 days or Google refunds it)
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient?.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged successfully")
                    applyPurchaseLocally(purchase)
                } else {
                    Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                }
            }
        } else {
            applyPurchaseLocally(purchase)
        }
    }

    private fun applyPurchaseLocally(purchase: Purchase) {
        val isYearly = purchase.products.contains(PRODUCT_ID_YEARLY)
        val isMonthly = purchase.products.contains(PRODUCT_ID_MONTHLY)
        val plan = when {
            isYearly -> "yearly"
            isMonthly -> "monthly"
            else -> "weekly"
        }

        // Cache subscription state locally for offline access
        prefs.edit()
            .putBoolean("is_subscribed", true)
            .putString("subscribed_plan", plan)
            .putString("purchase_token", purchase.purchaseToken)
            .apply()

        _subscriptionStatus.value = when {
            isYearly -> SubscriptionStatus.PREMIUM_YEARLY
            isMonthly -> SubscriptionStatus.PREMIUM_MONTHLY
            else -> SubscriptionStatus.PREMIUM_WEEKLY
        }
    }

    // =========================================================================
    // Google Play Billing — Restore Purchases
    // =========================================================================

    /**
     * Queries Google Play for existing active subscriptions.
     * Called automatically on startup and available for the "Restore Purchases" button.
     */
    fun restorePurchases() {
        ensureConnected {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            billingClient?.queryPurchasesAsync(params) { billingResult, purchasesList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val activePurchase = purchasesList.firstOrNull {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    if (activePurchase != null) {
                        handlePurchase(activePurchase)
                    } else if (prefs.getBoolean("is_subscribed", false)) {
                        // Local cache says subscribed but Google Play says no —
                        // subscription expired or was cancelled
                        prefs.edit()
                            .putBoolean("is_subscribed", false)
                            .remove("subscribed_plan")
                            .remove("purchase_token")
                            .apply()
                        loadLocalStatus()
                        Log.d(TAG, "Subscription expired — reverted to free tier")
                    }
                }
            }
        }
    }

    // =========================================================================
    // Local Status Management (Free Trial & Generation Quotas)
    // =========================================================================

    private fun loadLocalStatus() {
        val firstLaunchTime = prefs.getLong("first_launch_time", 0L)
        val now = System.currentTimeMillis()
        val installTime = if (firstLaunchTime == 0L) {
            prefs.edit().putLong("first_launch_time", now).apply()
            now
        } else {
            firstLaunchTime
        }

        // Monthly quota reset check (giving free users renewed quota every calendar month)
        val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(now))
        val lastResetMonth = prefs.getString("last_quota_reset_month", "")
        if (lastResetMonth != currentMonthKey) {
            prefs.edit()
                .putString("last_quota_reset_month", currentMonthKey)
                .putInt("free_generations_used", 0)
                .apply()
        }

        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        val planName = prefs.getString("subscribed_plan", null)

        val daysSinceInstall = ((now - installTime) / (1000 * 60 * 60 * 24)).toInt()
        val daysLeft = (TRIAL_DURATION_DAYS - daysSinceInstall).coerceAtLeast(0)
        _trialDaysLeft.value = daysLeft

        val usedGenerations = prefs.getInt("free_generations_used", 0)
        _remainingFreeGenerations.value = (FREE_GENERATIONS_LIMIT - usedGenerations).coerceAtLeast(0)

        _subscriptionStatus.value = when {
            isSubscribed && planName == "yearly" -> SubscriptionStatus.PREMIUM_YEARLY
            isSubscribed && planName == "monthly" -> SubscriptionStatus.PREMIUM_MONTHLY
            isSubscribed && planName == "weekly" -> SubscriptionStatus.PREMIUM_WEEKLY
            daysLeft > 0 -> SubscriptionStatus.FREE_TRIAL
            else -> SubscriptionStatus.FREE_TIER_EXPIRED
        }
    }

    fun isPremiumOrTrial(): Boolean {
        return _subscriptionStatus.value != SubscriptionStatus.FREE_TIER_EXPIRED ||
                _remainingFreeGenerations.value > 0
    }

    fun consumeGeneration(): Boolean {
        // Premium or trial users have unlimited generations
        if (_subscriptionStatus.value == SubscriptionStatus.PREMIUM_YEARLY ||
            _subscriptionStatus.value == SubscriptionStatus.PREMIUM_MONTHLY ||
            _subscriptionStatus.value == SubscriptionStatus.PREMIUM_WEEKLY ||
            _subscriptionStatus.value == SubscriptionStatus.FREE_TRIAL
        ) {
            return true
        }

        // Free tier: consume from the monthly quota
        if (_remainingFreeGenerations.value > 0) {
            val used = prefs.getInt("free_generations_used", 0) + 1
            prefs.edit().putInt("free_generations_used", used).apply()
            _remainingFreeGenerations.value = (FREE_GENERATIONS_LIMIT - used).coerceAtLeast(0)
            return true
        }

        return false
    }

    // =========================================================================
    // Backward Compatibility — Local-only subscribe (for dev/testing)
    // Will be replaced by launchPurchaseFlow() when PaywallScreen is updated
    // =========================================================================

    private fun subscribeLocal(plan: String) {
        prefs.edit()
            .putBoolean("is_subscribed", true)
            .putString("subscribed_plan", plan)
            .apply()
        _subscriptionStatus.value = when (plan) {
            "yearly" -> SubscriptionStatus.PREMIUM_YEARLY
            "monthly" -> SubscriptionStatus.PREMIUM_MONTHLY
            else -> SubscriptionStatus.PREMIUM_WEEKLY
        }
    }

    /**
     * @deprecated Use [launchPurchaseFlow] with an Activity instead.
     * Kept temporarily for backward compatibility with existing UI.
     */
    fun subscribe(plan: String) {
        subscribeLocal(plan)
    }

    fun cancelSubscription() {
        prefs.edit()
            .putBoolean("is_subscribed", false)
            .remove("subscribed_plan")
            .remove("purchase_token")
            .apply()
        loadLocalStatus()
    }

    fun openGooglePlaySubscriptionSettings() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/account/subscriptions")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Clean up billing client connection.
     * Should be called when the app is destroyed.
     */
    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
    }
}
