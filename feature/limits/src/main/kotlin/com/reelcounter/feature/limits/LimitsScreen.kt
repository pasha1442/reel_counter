package com.reelcounter.feature.limits

import com.reelcounter.core.model.WatchTime
import com.reelcounter.core.model.Strictness
import com.reelcounter.core.design.components.SegmentedToggle
import com.reelcounter.core.design.components.RcIconButton
import com.reelcounter.core.design.components.AppIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.semantics
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.components.ScreenHeader
import com.reelcounter.core.design.components.ListDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.brain.nameRes
import com.reelcounter.core.design.components.IconTile
import com.reelcounter.core.design.components.LimitPresetChips
import com.reelcounter.core.design.components.NavRow
import com.reelcounter.core.design.components.NumberStepper
import com.reelcounter.core.design.components.ProBadge
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.components.SectionHeader
import com.reelcounter.core.design.components.StageChip
import com.reelcounter.core.design.components.ToggleRow
import com.reelcounter.core.design.components.staggeredEntrance
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.limits.UnlockRules
import com.reelcounter.core.model.BrainStage
import com.reelcounter.core.model.LimitSettings
import com.reelcounter.core.model.ReelApp

@Composable
fun LimitsRoute(
    contentPadding: PaddingValues,
    onOpenPaywall: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: LimitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingApp by rememberSaveable { mutableStateOf<ReelApp?>(null) }

    LimitsScreen(
        state = state,
        contentPadding = contentPadding,
        onDailyLimit = viewModel::setDailyLimit,
        onBlockAtLimit = { if (!viewModel.setBlockAtLimit(it)) onOpenPaywall() },
        onNudges = viewModel::setNudges,
        onEditApp = { app -> if (state.isPro) editingApp = app else onOpenPaywall() },
        onStrictness = { if (!viewModel.setStrictness(it)) onOpenPaywall() },
        onOpenProfile = onOpenProfile,
    )

    editingApp?.let { app ->
        PerAppLimitDialog(
            app = app,
            current = state.perAppLimits[app],
            dailyLimit = state.settings.dailyLimit,
            onDismiss = { editingApp = null },
            onSave = { limit ->
                viewModel.setPerAppLimit(app, limit)
                editingApp = null
            },
        )
    }
}

@Composable
internal fun LimitsScreen(
    state: LimitsUiState,
    contentPadding: PaddingValues,
    onDailyLimit: (Int) -> Unit,
    onBlockAtLimit: (Boolean) -> Unit,
    onNudges: (Boolean) -> Unit,
    onEditApp: (ReelApp) -> Unit,
    onStrictness: (Strictness) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val limit = state.dailyLimit
    val scheme = MaterialTheme.colorScheme
    if (state.loading) {
        Box(Modifier.fillMaxSize())
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = RcSpacing.gutter),
    ) {
        ScreenHeader("Limits") {
            RcIconButton(RcIcons.Person, "Profile and settings", onOpenProfile)
        }
        RcCard(Modifier.fillMaxWidth().staggeredEntrance(0)) {
            Text("Daily limit", style = MaterialTheme.typography.titleMedium)
            Text("Instagram and YouTube together", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            NumberStepper(
                value = limit,
                onValueChange = onDailyLimit,
                range = LimitSettings.MIN_LIMIT..LimitSettings.MAX_LIMIT,
                step = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "About ${WatchTime.minutes(limit)} minutes of scrolling",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            LimitPresetChips(selected = limit, onSelect = onDailyLimit)
            Spacer(Modifier.height(20.dp))
            StagePreview(state.previewRot, state.todayTotal)
        }

        SectionHeader("At your limit")
        RcCard(Modifier.fillMaxWidth().staggeredEntrance(1), contentPadding = PaddingValues(vertical = 6.dp)) {
            ToggleRow(
                title = "Block Reels & Shorts",
                subtitle = "DMs, search and the rest of the app keep working",
                checked = state.isPro && state.settings.blockAtLimit,
                onCheckedChange = onBlockAtLimit,
                icon = RcIcons.Shield,
                badge = if (!state.isPro) {
                    { ProBadge() }
                } else {
                    null
                },
            )
            ListDivider()
            ToggleRow(
                title = "Nudges",
                subtitle = "At 50%, 80% and 100% of your limit",
                checked = state.settings.nudgesEnabled,
                onCheckedChange = onNudges,
                icon = RcIcons.Bell,
            )
        }

        SectionHeader("How strict", trailing = if (!state.isPro) {
            { ProBadge() }
        } else {
            null
        })
        val strictness = state.settings.strictness
        SegmentedToggle(
            options = listOf("Gentle", "Firm", "Locked"),
            selected = Strictness.entries.indexOf(strictness),
            onSelect = { onStrictness(Strictness.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
            equalWidths = true,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when (strictness) {
                Strictness.GENTLE -> "Wait ${UnlockRules.GENTLE_WAIT_SECONDS} seconds to unlock ${UnlockRules.EXTRA_REELS} more."
                Strictness.FIRM -> "Tap a short promise, word by word, to unlock ${UnlockRules.EXTRA_REELS} more. Up to ${UnlockRules.FIRM_DAILY_UNLOCKS} times a day."
                Strictness.LOCKED -> "No unlocks. Reels stay closed until midnight."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SectionHeader("Per app")
        RcCard(Modifier.fillMaxWidth().staggeredEntrance(2), contentPadding = PaddingValues(vertical = 6.dp)) {
            ReelApp.entries.forEachIndexed { index, app ->
                if (index > 0) ListDivider()
                NavRow(
                    title = app.displayName,
                    subtitle = app.surfaceName,
                    onClick = { onEditApp(app) },
                    leading = { AppIcon(app, size = 32.dp) },
                    trailingText = state.perAppLimits[app]?.let { "$it a day" } ?: "No limit",
                    badge = if (!state.isPro) {
                        { ProBadge() }
                    } else {
                        null
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Coming soon: schedules and per-sitting caps.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(RcSpacing.section))
    }
}

/** One line under the limit: what today's count does to the brain at this limit. Change the number, watch it change. */
@Composable
private fun StagePreview(rot: Float, todayTotal: Int) {
    val stage = BrainStage.fromRot(rot)
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "At this limit, today's $todayTotal reels mean",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        StageChip(stage)
    }
}

@Composable
private fun PerAppLimitDialog(
    app: ReelApp,
    current: Int?,
    dailyLimit: Int,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    var value by rememberSaveable { mutableIntStateOf(current ?: (dailyLimit / 2).coerceAtLeast(LimitSettings.MIN_LIMIT)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${app.displayName} ${app.surfaceName} limit") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Blocks ${app.surfaceName} once you reach this many today, even if your daily limit isn't used up.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                NumberStepper(value = value, onValueChange = { value = it }, range = LimitSettings.MIN_LIMIT..LimitSettings.MAX_LIMIT, step = 5)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = {
            TextButton(onClick = { onSave(null) }) { Text(if (current != null) "Remove limit" else "Cancel") }
        },
    )
}
