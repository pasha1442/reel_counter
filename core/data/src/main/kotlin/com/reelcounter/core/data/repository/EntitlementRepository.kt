package com.reelcounter.core.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.Period
import com.reelcounter.core.data.AppConfig
import com.reelcounter.core.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class BillingPeriod { MONTHLY, ANNUAL }

data class ProOffer(
    val id: String,
    val period: BillingPeriod,
    /** Formatted by Google Play in the buyer's currency, e.g. "₹999.00". */
    val price: String,
    val pricePerMonth: String,
    val trialDays: Int?,
)

sealed interface PurchaseResult {
    data object Success : PurchaseResult

    data object Cancelled : PurchaseResult

    data class Unavailable(val reason: String) : PurchaseResult
}

sealed interface RestoreResult {
    data object Restored : RestoreResult

    data object NothingToRestore : RestoreResult

    data class Failed(val reason: String) : RestoreResult
}

/** Who is Pro, and how to become Pro. */
interface EntitlementRepository {
    val isPro: Flow<Boolean>
    val offers: Flow<List<ProOffer>>

    /** False while payments aren't set up (test prices are shown, labelled as such). */
    val billingConnected: Boolean

    /** When the current free trial ends (epoch millis), or null when not in a trial. */
    val trialEndsAt: Flow<Long?>

    /** Starts Google Play's purchase sheet; needs the screen it's shown from. */
    suspend fun purchase(activity: Activity, offerId: String): PurchaseResult

    suspend fun restore(): RestoreResult

    /** Opens Google Play's page for managing or cancelling the subscription. */
    fun openManageSubscription(context: Context)

    /** Ties purchases to the signed-in account (so Pro follows the person to a new phone). */
    suspend fun onSignedIn(userId: String)

    suspend fun onSignedOut()
}

/** Google Play's subscriptions page, filtered to this app. Works with or without RevenueCat. */
private fun openPlaySubscriptions(context: Context, managementUrl: Uri? = null) {
    val uri = managementUrl ?: Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Stand-in while payments aren't set up: debug builds can switch Pro on locally ("Pretend to be
 * Pro"), release builds say payments aren't available. Prices are placeholders, labelled as test
 * prices on the paywall; real prices always come from Google Play.
 */
@Singleton
class LocalEntitlementRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val config: AppConfig,
) : EntitlementRepository {

    override val isPro: Flow<Boolean> = settings.settings.map { config.isDebug && it.debugPro }

    override val offers: Flow<List<ProOffer>> = flowOf(
        listOf(
            ProOffer(id = "pro_annual", period = BillingPeriod.ANNUAL, price = "$29.99", pricePerMonth = "$2.50", trialDays = 7),
            ProOffer(id = "pro_monthly", period = BillingPeriod.MONTHLY, price = "$4.99", pricePerMonth = "$4.99", trialDays = null),
        ),
    )

    override val billingConnected: Boolean = false

    override val trialEndsAt: Flow<Long?> = flowOf(null)

    override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult {
        if (!config.isDebug) return PurchaseResult.Unavailable("Payments aren't available in this version yet.")
        settings.setDebugPro(true)
        return PurchaseResult.Success
    }

    override suspend fun restore(): RestoreResult = RestoreResult.NothingToRestore

    override fun openManageSubscription(context: Context) = openPlaySubscriptions(context)

    override suspend fun onSignedIn(userId: String) = Unit

    override suspend fun onSignedOut() = Unit
}

/**
 * Real subscriptions: Google Play Billing through RevenueCat. Products, prices and the free trial
 * are set up in Play Console and grouped in RevenueCat's "current" offering; the app only knows
 * the entitlement ID. RevenueCat is configured at app start (see the Application class).
 */
@Singleton
class RevenueCatEntitlementRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val config: AppConfig,
) : EntitlementRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val customerInfo = MutableStateFlow<CustomerInfo?>(null)
    private val packages = MutableStateFlow<List<ProOffer>>(emptyList())

    init {
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info -> customerInfo.value = info }
        scope.launch { refresh() }
    }

    override val isPro: Flow<Boolean> = combine(customerInfo, settings.settings) { info, s ->
        info?.entitlements?.get(ENTITLEMENT_PRO)?.isActive == true || (config.isDebug && s.debugPro)
    }

    override val offers: Flow<List<ProOffer>> = packages

    override val billingConnected: Boolean = true

    override val trialEndsAt: Flow<Long?> = customerInfo.map { info ->
        info?.entitlements?.get(ENTITLEMENT_PRO)
            ?.takeIf { it.isActive && it.periodType == PeriodType.TRIAL }
            ?.expirationDate?.time
    }

    override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult {
        val pkg = try {
            Purchases.sharedInstance.awaitOfferings().current?.availablePackages?.firstOrNull { it.identifier == offerId }
        } catch (e: PurchasesException) {
            return PurchaseResult.Unavailable("Couldn't reach Google Play. Check your connection and try again.")
        } ?: return PurchaseResult.Unavailable("This plan isn't available right now. Please try again later.")
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            customerInfo.value = result.customerInfo
            PurchaseResult.Success
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseResult.Cancelled else PurchaseResult.Unavailable(e.error.message)
        }
    }

    override suspend fun restore(): RestoreResult = try {
        val info = Purchases.sharedInstance.awaitRestore()
        customerInfo.value = info
        if (info.entitlements[ENTITLEMENT_PRO]?.isActive == true) RestoreResult.Restored else RestoreResult.NothingToRestore
    } catch (e: PurchasesTransactionException) {
        RestoreResult.Failed(e.error.message)
    }

    override fun openManageSubscription(context: Context) = openPlaySubscriptions(context, customerInfo.value?.managementURL)

    override suspend fun onSignedIn(userId: String) {
        if (Purchases.sharedInstance.appUserID == userId) return
        runCatching { customerInfo.value = Purchases.sharedInstance.awaitLogIn(userId).customerInfo }
    }

    override suspend fun onSignedOut() {
        // RevenueCat refuses to log out an anonymous user; there is nothing to undo then.
        if (Purchases.sharedInstance.isAnonymous) return
        runCatching { customerInfo.value = Purchases.sharedInstance.awaitLogOut() }
    }

    private suspend fun refresh() {
        runCatching { customerInfo.value = Purchases.sharedInstance.awaitCustomerInfo() }
        runCatching {
            packages.value = Purchases.sharedInstance.awaitOfferings().current?.availablePackages.orEmpty().mapNotNull { pkg ->
                val period = when (pkg.packageType) {
                    PackageType.ANNUAL -> BillingPeriod.ANNUAL
                    PackageType.MONTHLY -> BillingPeriod.MONTHLY
                    else -> return@mapNotNull null
                }
                val product = pkg.product
                ProOffer(
                    id = pkg.identifier,
                    period = period,
                    price = product.price.formatted,
                    pricePerMonth = product.pricePerMonth()?.formatted ?: product.price.formatted,
                    trialDays = product.defaultOption?.freePhase?.billingPeriod?.let(::days),
                )
            }.sortedBy { it.period != BillingPeriod.ANNUAL }
        }
    }

    private fun days(period: Period): Int? = when (period.unit) {
        Period.Unit.DAY -> period.value
        Period.Unit.WEEK -> period.value * 7
        Period.Unit.MONTH -> period.value * 30
        Period.Unit.YEAR -> period.value * 365
        Period.Unit.UNKNOWN -> null
    }

    companion object {
        /** The entitlement set up in RevenueCat that unlocks every Pro feature. */
        const val ENTITLEMENT_PRO = "pro"
    }
}
