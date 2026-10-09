package com.reelcounter.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontWeight
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.ListDivider
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.components.lookLabel
import com.reelcounter.core.design.components.Pill
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.data.repository.BillingPeriod
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.repository.ProOffer
import com.reelcounter.core.data.repository.PurchaseResult
import com.reelcounter.core.data.repository.RestoreResult
import com.reelcounter.core.data.LegalPages
import com.reelcounter.core.data.telemetry.Events
import com.reelcounter.core.data.telemetry.Telemetry
import android.app.Activity
import androidx.activity.compose.LocalActivity
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.components.ConfettiBurst
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcButtonStyle
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.theme.RcTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlements: EntitlementRepository,
    private val telemetry: Telemetry,
) : ViewModel() {
    val offers: StateFlow<List<ProOffer>> = entitlements.offers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Prices are placeholders until payments are connected; the screen says so. */
    val testPrices: Boolean = !entitlements.billingConnected

    fun viewed(fromOnboarding: Boolean) =
        telemetry.event(Events.PAYWALL_VIEW, mapOf("from" to if (fromOnboarding) "onboarding" else "app"))

    suspend fun purchase(activity: Activity, offer: ProOffer): PurchaseResult {
        val result = entitlements.purchase(activity, offer.id)
        when (result) {
            PurchaseResult.Success -> telemetry.event(Events.TRIAL_OR_PURCHASE, mapOf("plan" to offer.period.name.lowercase()))
            PurchaseResult.Cancelled -> telemetry.event(Events.PURCHASE_CANCELLED, mapOf("plan" to offer.period.name.lowercase()))
            is PurchaseResult.Unavailable -> Unit
        }
        return result
    }

    suspend fun restore(): RestoreResult {
        telemetry.event(Events.RESTORE)
        return entitlements.restore()
    }
}

/**
 * Honest paywall: real prices, trial terms in plain words, no fake countdowns,
 * and a clear way out.
 */
@Composable
fun PaywallRoute(
    fromOnboarding: Boolean,
    onClose: () -> Unit,
    onOpenLegal: (String) -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(Unit) { viewModel.viewed(fromOnboarding) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var celebrate by remember { mutableIntStateOf(0) }
    val selected = offers.firstOrNull { it.id == selectedId } ?: offers.firstOrNull { it.period == BillingPeriod.ANNUAL }

    LaunchedEffect(celebrate) {
        if (celebrate > 0) {
            delay(1_600)
            onClose()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onClose) { Icon(RcIcons.Close, contentDescription = "Close") }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Brain(
                    rot = 0f,
                    glow = true,
                    celebrateKey = celebrate,
                    modifier = Modifier.size(140.dp),
                    contentDescription = "A fresh, glowing brain",
                )
                Spacer(Modifier.height(8.dp))
                Text("Make your limit stick", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pro blocks Reels and Shorts the moment you hit your number. DMs, search and the rest of the app keep working.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Benefit("Block Reels & Shorts at your limit: gentle, firm or locked")
                Benefit("Separate limits for Instagram and YouTube")
                Benefit("Every past week and day, not just the last two")
                Spacer(Modifier.height(20.dp))
                if (viewModel.testPrices) {
                    Pill(
                        "Test prices · real prices come from Google Play",
                        color = RcTheme.extended.tintSky,
                        contentColor = RcTheme.extended.onTintSky,
                        icon = RcIcons.Info,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    offers.forEach { offer ->
                        PlanCard(offer = offer, selected = offer.id == selected?.id, onSelect = { selectedId = offer.id })
                    }
                }
                val trialDays = selected?.trialDays
                if (selected != null && trialDays != null) {
                    Spacer(Modifier.height(16.dp))
                    TrialTimeline(trialDays, selected.price)
                }
                Spacer(Modifier.height(12.dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RcButton(
                    text = when {
                        selected == null -> "Continue"
                        selected.trialDays != null -> "Start ${selected.trialDays}-day free trial"
                        else -> "Subscribe for ${selected.price}/month"
                    },
                    onClick = {
                        val offer = selected ?: return@RcButton
                        val host = activity ?: return@RcButton
                        busy = true
                        scope.launch {
                            val result = viewModel.purchase(host, offer)
                            // Stop the spinner first: showing the message waits until it is dismissed.
                            busy = false
                            when (result) {
                                PurchaseResult.Success -> celebrate++
                                PurchaseResult.Cancelled -> Unit
                                is PurchaseResult.Unavailable -> snackbar.showSnackbar(result.reason)
                            }
                        }
                    },
                    loading = busy,
                    style = RcButtonStyle.Cta,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = selected?.let { termsFor(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                // Equal weight on purpose: saying no should be as easy as saying yes.
                RcButton("Maybe later", onClick = onClose, style = RcButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        scope.launch {
                            val message = when (val result = viewModel.restore()) {
                                RestoreResult.Restored -> "Pro restored. Welcome back!"
                                RestoreResult.NothingToRestore -> "No Pro purchase found for this Google account."
                                is RestoreResult.Failed -> result.reason
                            }
                            snackbar.showSnackbar(message)
                        }
                    }) { Text("Restore", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onOpenLegal(LegalPages.TERMS) }) {
                        Text("Terms", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onOpenLegal(LegalPages.PRIVACY) }) {
                        Text("Privacy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        ConfettiBurst(trigger = celebrate, modifier = Modifier.fillMaxSize())
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

private fun termsFor(offer: ProOffer): String = if (offer.trialDays != null) {
    "Free for ${offer.trialDays} days, then ${offer.price} a year. We'll remind you 2 days before the trial ends. Cancel anytime in Google Play."
} else {
    "${offer.price} a month. Cancel anytime in Google Play."
}

@Composable
private fun Benefit(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(RcIcons.CheckCircle, contentDescription = null, tint = RcTheme.extended.successText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanCard(offer: ProOffer, selected: Boolean, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extended = RcTheme.extended
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .lookSurface(shape, if (selected && RcTheme.tokens.isSticker) scheme.primaryContainer else scheme.surfaceContainer, raised = selected)
            .then(if (selected && !RcTheme.tokens.isSticker) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (offer.period == BillingPeriod.ANNUAL) "Yearly" else "Monthly", style = MaterialTheme.typography.titleMedium)
                if (offer.trialDays != null) {
                    Spacer(Modifier.width(8.dp))
                    Pill("${offer.trialDays} days free", color = extended.cta, contentColor = extended.onCta)
                }
            }
            Text(
                if (offer.period == BillingPeriod.ANNUAL) "${offer.pricePerMonth} a month, billed yearly" else "Billed monthly, no trial",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            if (offer.period == BillingPeriod.ANNUAL) "${offer.price}/yr" else "${offer.price}/mo",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Today → reminder → first charge, with real dates, so the trial has no surprises. */
@Composable
private fun TrialTimeline(trialDays: Int, price: String) {
    val today = java.time.LocalDate.now()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")
    val steps = listOf(
        "Today" to "All of Pro, free",
        today.plusDays(trialDays - 2L).format(fmt) to "We remind you",
        today.plusDays(trialDays.toLong()).format(fmt) to "$price, unless you cancel",
    )
    RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        steps.forEachIndexed { i, (date, label) ->
            if (i > 0) ListDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(date, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), modifier = Modifier.width(72.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
