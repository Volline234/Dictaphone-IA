package com.example.billing

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SubscriptionStatus {
    FREE_TRIAL,
    PREMIUM_WEEKLY,
    PREMIUM_MONTHLY,
    FREE_TIER_EXPIRED
}

class BillingManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("dictaphone_billing_prefs", Context.MODE_PRIVATE)

    companion object {
        const val FREE_GENERATIONS_LIMIT = 5
        const val TRIAL_DURATION_DAYS = 7
        // When true, allows all users to generate without fake purchase blockers
        const val ENABLE_COMMUNITY_FREE_MODE = true
    }

    private val _subscriptionStatus = MutableStateFlow(SubscriptionStatus.FREE_TRIAL)
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    private val _remainingFreeGenerations = MutableStateFlow(FREE_GENERATIONS_LIMIT)
    val remainingFreeGenerations: StateFlow<Int> = _remainingFreeGenerations.asStateFlow()

    private val _trialDaysLeft = MutableStateFlow(TRIAL_DURATION_DAYS)
    val trialDaysLeft: StateFlow<Int> = _trialDaysLeft.asStateFlow()

    init {
        loadStatus()
    }

    private fun loadStatus() {
        val firstLaunchTime = prefs.getLong("first_launch_time", 0L)
        val now = System.currentTimeMillis()
        val installTime = if (firstLaunchTime == 0L) {
            prefs.edit().putLong("first_launch_time", now).apply()
            now
        } else {
            firstLaunchTime
        }

        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        val planName = prefs.getString("subscribed_plan", null)

        val daysSinceInstall = ((now - installTime) / (1000 * 60 * 60 * 24)).toInt()
        val daysLeft = (TRIAL_DURATION_DAYS - daysSinceInstall).coerceAtLeast(0)
        _trialDaysLeft.value = daysLeft

        val usedGenerations = prefs.getInt("free_generations_used", 0)
        _remainingFreeGenerations.value = (FREE_GENERATIONS_LIMIT - usedGenerations).coerceAtLeast(0)

        _subscriptionStatus.value = when {
            isSubscribed && planName == "weekly" -> SubscriptionStatus.PREMIUM_WEEKLY
            isSubscribed && planName == "monthly" -> SubscriptionStatus.PREMIUM_MONTHLY
            daysLeft > 0 -> SubscriptionStatus.FREE_TRIAL
            else -> SubscriptionStatus.FREE_TIER_EXPIRED
        }
    }

    fun isPremiumOrTrial(): Boolean {
        return _subscriptionStatus.value != SubscriptionStatus.FREE_TIER_EXPIRED || _remainingFreeGenerations.value > 0
    }

    fun consumeGeneration(): Boolean {
        if (ENABLE_COMMUNITY_FREE_MODE) {
            return true
        }

        if (_subscriptionStatus.value == SubscriptionStatus.PREMIUM_WEEKLY ||
            _subscriptionStatus.value == SubscriptionStatus.PREMIUM_MONTHLY ||
            _subscriptionStatus.value == SubscriptionStatus.FREE_TRIAL
        ) {
            return true
        }

        if (_remainingFreeGenerations.value > 0) {
            val used = prefs.getInt("free_generations_used", 0) + 1
            prefs.edit().putInt("free_generations_used", used).apply()
            _remainingFreeGenerations.value = (FREE_GENERATIONS_LIMIT - used).coerceAtLeast(0)
            return true
        }

        return false
    }

    fun subscribe(plan: String) {
        prefs.edit()
            .putBoolean("is_subscribed", true)
            .putString("subscribed_plan", plan)
            .apply()
        _subscriptionStatus.value = if (plan == "weekly") SubscriptionStatus.PREMIUM_WEEKLY else SubscriptionStatus.PREMIUM_MONTHLY
    }

    fun cancelSubscription() {
        prefs.edit()
            .putBoolean("is_subscribed", false)
            .remove("subscribed_plan")
            .apply()
        loadStatus()
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
}
