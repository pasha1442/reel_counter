package com.reelcounter.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.data.LegalPages
import com.reelcounter.core.data.SystemScreens
import com.reelcounter.core.data.account.AccountUser
import com.reelcounter.core.data.account.AuthMode
import com.reelcounter.core.design.components.ListDivider
import com.reelcounter.core.design.components.LookPicker
import com.reelcounter.core.design.components.NavRow
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.components.SectionHeader
import com.reelcounter.core.design.components.ToggleRow
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.core.model.ReelApp

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    onAddWidget: () -> Unit,
    onSignIn: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLegal: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    LaunchedEffect(Unit) { viewModel.message.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        SettingsScreen(
            state = state,
            actions = SettingsActions(
                back = onBack,
                openPaywall = onOpenPaywall,
                addWidget = onAddWidget,
                signIn = onSignIn,
                openAccount = onOpenAccount,
                openLegal = onOpenLegal,
                manageSubscription = { viewModel.openManageSubscription(activity ?: context) },
                restorePurchases = viewModel::restorePurchases,
                counterStatus = { viewModel.openAccessibilitySettings(context) },
                battery = { SystemScreens.openBatterySettings(context) },
                notifications = { SystemScreens.openNotificationSettings(context) },
                liveNotification = viewModel::setLiveNotification,
                look = viewModel::setLook,
                shareCrashReports = viewModel::setShareCrashReports,
                shareUsageStats = viewModel::setShareUsageStats,
                showDialog = { dialog = it },
                help = { viewModel.contactSupport(context) },
                rate = { activity?.let(viewModel::rate) },
                share = { viewModel.shareApp(context) },
                debugPro = viewModel::setDebugPro,
                debugRecording = viewModel::setDebugRecording,
                debugDemoFriends = viewModel::setDebugDemoFriends,
                replayOnboarding = viewModel::replayOnboarding,
                simulateReel = viewModel::simulateReel,
            ),
        )
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    when (dialog) {
        SettingsDialog.WHAT_WE_STORE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("What we store") },
            text = {
                Text(
                    "On this phone: one entry per reel (which app, the time, which sitting) for 90 days, then only daily totals. " +
                        "Your limit and settings.\n\nNever stored or sent: captions, usernames, video content, messages, " +
                        "or anything from other apps.\n\nYour counts never leave your phone. Only if you sign in, buy Pro, " +
                        "or switch on crash reports or usage stats is a little information sent, as the Privacy Policy explains.",
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Got it") } },
            dismissButton = {
                TextButton(onClick = {
                    dialog = null
                    onOpenLegal(LegalPages.PRIVACY)
                }) { Text("Privacy Policy") }
            },
        )
        SettingsDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete all reel history?") },
            text = { Text("Every counted reel and daily total on this phone will be erased. Your limit and settings stay. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllData()
                    dialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}

enum class SettingsDialog { WHAT_WE_STORE, DELETE }

internal class SettingsActions(
    val back: () -> Unit,
    val openPaywall: () -> Unit,
    val addWidget: () -> Unit,
    val signIn: () -> Unit,
    val openAccount: () -> Unit,
    val openLegal: (String) -> Unit,
    val manageSubscription: () -> Unit,
    val restorePurchases: () -> Unit,
    val counterStatus: () -> Unit,
    val battery: () -> Unit,
    val notifications: () -> Unit,
    val liveNotification: (Boolean) -> Unit,
    val look: (AppLook) -> Unit,
    val shareCrashReports: (Boolean) -> Unit,
    val shareUsageStats: (Boolean) -> Unit,
    val showDialog: (SettingsDialog) -> Unit,
    val help: () -> Unit,
    val rate: () -> Unit,
    val share: () -> Unit,
    val debugPro: (Boolean) -> Unit,
    val debugRecording: (Boolean) -> Unit,
    val debugDemoFriends: (Boolean) -> Unit,
    val replayOnboarding: () -> Unit,
    val simulateReel: (ReelApp) -> Unit,
)

@Composable
internal fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        RcTopBar(title = "Profile", onBack = actions.back)
        Column(Modifier.padding(horizontal = RcSpacing.gutter)) {
            Spacer(Modifier.height(8.dp))
            RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                AccountRow(state.user, state.accountSetupComplete, state.authMode, actions.signIn, actions.openAccount)
                ListDivider()
                ProRow(state.isPro, actions.openPaywall, actions.manageSubscription)
                if (!state.isPro && state.billingConnected) {
                    ListDivider()
                    NavRow("Restore purchases", onClick = actions.restorePurchases, subtitle = "Bought Pro before? Get it back here", icon = RcIcons.Restore)
                }
            }

            SectionHeader("Look")
            LookPicker(selected = state.settings.look, onSelect = actions.look)

            SectionHeader("Counter")
            RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                NavRow(
                    title = "Counter status",
                    onClick = actions.counterStatus,
                    icon = RcIcons.Accessibility,
                    trailingText = when (state.health) {
                        CounterHealth.HEALTHY -> "Running"
                        CounterHealth.NOT_ENABLED -> "Off"
                        CounterHealth.ENABLED_NOT_RUNNING -> "Stopped"
                    },
                    trailingColor = if (state.health == CounterHealth.HEALTHY) extended.successText else extended.warningText,
                )
                ListDivider()
                NavRow("Battery", onClick = actions.battery, subtitle = "Set to Unrestricted so counting never stops", icon = RcIcons.Battery)
                ListDivider()
                ToggleRow(
                    title = "Live count in status bar",
                    subtitle = "A quiet notification with today's count",
                    checked = state.settings.liveNotification,
                    onCheckedChange = actions.liveNotification,
                    icon = RcIcons.Widgets,
                )
                ListDivider()
                NavRow("Notifications", onClick = actions.notifications, icon = RcIcons.Bell)
                ListDivider()
                NavRow("Home screen widget", onClick = actions.addWidget, icon = RcIcons.Widgets)
            }

            SectionHeader("Privacy")
            RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                NavRow("What we store", onClick = { actions.showDialog(SettingsDialog.WHAT_WE_STORE) }, subtitle = "Your counts stay on this phone", icon = RcIcons.Policy)
                if (state.telemetryAvailable) {
                    ListDivider()
                    ToggleRow(
                        title = "Crash reports",
                        subtitle = "Send a report if the app crashes, so we can fix it",
                        checked = state.settings.shareCrashReports,
                        onCheckedChange = actions.shareCrashReports,
                        icon = RcIcons.Bug,
                    )
                    ListDivider()
                    ToggleRow(
                        title = "Usage stats",
                        subtitle = "Anonymous app events. Never your counts",
                        checked = state.settings.shareUsageStats,
                        onCheckedChange = actions.shareUsageStats,
                        icon = RcIcons.History,
                    )
                }
                ListDivider()
                NavRow("Privacy Policy", onClick = { actions.openLegal(LegalPages.PRIVACY) }, icon = RcIcons.Shield)
                ListDivider()
                NavRow("Terms of Use", onClick = { actions.openLegal(LegalPages.TERMS) }, icon = RcIcons.Document)
                ListDivider()
                NavRow(
                    "Delete all reel history",
                    onClick = { actions.showDialog(SettingsDialog.DELETE) },
                    icon = RcIcons.Delete,
                    iconTint = scheme.error,
                    titleColor = scheme.error,
                )
            }

            SectionHeader("Support")
            RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                NavRow("Help & feedback", onClick = actions.help, subtitle = "Questions, bugs or ideas", icon = RcIcons.Help)
                ListDivider()
                NavRow("Rate Reel Counter", onClick = actions.rate, icon = RcIcons.Star)
                ListDivider()
                NavRow("Share Reel Counter", onClick = actions.share, icon = RcIcons.Share)
            }

            if (state.isDebug) {
                SectionHeader("Debug")
                RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    ReelApp.entries.forEach { app ->
                        NavRow("Add a test ${app.displayName} reel", onClick = { actions.simulateReel(app) }, icon = RcIcons.Add)
                        ListDivider()
                    }
                    ToggleRow("Pretend to be Pro", checked = state.settings.debugPro, onCheckedChange = actions.debugPro, subtitle = "For trying Pro features", icon = RcIcons.Bug)
                    ListDivider()
                    ToggleRow(
                        "Sample battle on Friends",
                        checked = state.settings.debugDemoFriends,
                        onCheckedChange = actions.debugDemoFriends,
                        subtitle = "Until battles are live",
                        icon = RcIcons.Friends,
                    )
                    ListDivider()
                    ToggleRow(
                        "Record detection sessions",
                        checked = state.settings.debugRecordDetection,
                        onCheckedChange = actions.debugRecording,
                        subtitle = "Hashed text only, to Android/data/…/recordings",
                        icon = RcIcons.Bug,
                    )
                    ListDivider()
                    NavRow("Replay onboarding", onClick = actions.replayOnboarding, icon = RcIcons.Refresh)
                }
            }

            Spacer(Modifier.height(RcSpacing.section))
            Text(
                "Reel Counter ${state.versionName}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "Fonts: Bricolage Grotesque, Figtree, Lexend Mega, Public Sans (OFL)",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(RcSpacing.section))
        }
    }
}

/** Signed in: who, and the way into Account. Signed out: the way to sign in. Off: a quiet note. */
@Composable
private fun AccountRow(user: AccountUser?, setupComplete: Boolean, mode: AuthMode, onSignIn: () -> Unit, onOpenAccount: () -> Unit) {
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    when {
        user != null -> NavRow(
            title = if (setupComplete) user.name.orEmpty() else "Finish setting up your account",
            subtitle = user.email ?: "Signed in",
            onClick = onOpenAccount,
            leading = {
                Box(
                    Modifier
                        .size(44.dp)
                        .lookSurface(CircleShape, if (sticker) extended.cta else extended.cta.copy(alpha = 0.18f), thin = true),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(user.initial, style = MaterialTheme.typography.titleMedium, color = if (sticker) extended.onCta else extended.cta)
                }
            },
        )
        mode == AuthMode.OFF -> Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RcSpacing.card, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarPlaceholder()
            Spacer(Modifier.width(14.dp))
            Column {
                Text("No account yet", style = MaterialTheme.typography.bodyLarge)
                Text("Optional. Backup and battles are coming", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> NavRow(
            title = "Sign in or create an account",
            subtitle = "Keep Pro on a new phone · get ready for battles",
            onClick = onSignIn,
            leading = { AvatarPlaceholder() },
        )
    }
}

@Composable
private fun AvatarPlaceholder() {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .lookSurface(CircleShape, scheme.surfaceContainerHighest, thin = true),
        contentAlignment = Alignment.Center,
    ) {
        Icon(RcIcons.Person, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ProRow(isPro: Boolean, onOpenPaywall: () -> Unit, onManage: () -> Unit) {
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    NavRow(
        title = if (isPro) "You're Pro" else "Reel Counter Pro",
        subtitle = if (isPro) "Manage or cancel in Google Play" else "Blocking, per-app limits and more",
        onClick = if (isPro) onManage else onOpenPaywall,
        icon = RcIcons.Star,
        iconTint = if (sticker) MaterialTheme.colorScheme.onSurface else extended.cta,
        trailingText = if (isPro) "Manage" else "Try free",
        trailingColor = if (sticker) MaterialTheme.colorScheme.onSurface else extended.cta,
    )
}
