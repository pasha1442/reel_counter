package com.reelcounter.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.data.SystemScreens
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.brain.BrainMood
import com.reelcounter.core.design.brain.brainDescription
import com.reelcounter.core.design.brain.nameRes
import com.reelcounter.core.design.brain.pokeQuipsRes
import com.reelcounter.core.design.components.AnimatedCounter
import com.reelcounter.core.design.components.AppIcon
import com.reelcounter.core.design.components.HealthPill
import com.reelcounter.core.design.components.IconTile
import com.reelcounter.core.design.components.LimitBar
import com.reelcounter.core.design.components.ListDivider
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcButtonStyle
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIconButton
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.ScreenHeader
import com.reelcounter.core.design.components.SpeechBubble
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.components.staggeredEntrance
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.limits.Milestones
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.core.model.CountingStatus
import com.reelcounter.core.model.ReelApp
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HomeRoute(
    onReady: () -> Unit,
    contentPadding: PaddingValues,
    onOpenPaywall: () -> Unit,
    onSetUpCounting: () -> Unit,
    onOpenBrain: () -> Unit,
    onOpenProfile: () -> Unit,
    showHealthRequest: Boolean,
    onHealthRequestHandled: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showHealth by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) onReady() }

    LaunchedEffect(showHealthRequest) {
        if (showHealthRequest) {
            showHealth = true
            onHealthRequestHandled()
        }
    }

    HomeScreen(
        state = state,
        contentPadding = contentPadding,
        onHealthClick = { showHealth = true },
        onFixCounter = {
            if (state.needsConsent) onSetUpCounting() else viewModel.openAccessibilitySettings(context)
        },
        onOpenPaywall = onOpenPaywall,
        onOpenBrain = onOpenBrain,
        onOpenProfile = onOpenProfile,
        onPrivacyChoice = viewModel::setPrivacyChoice,
    )

    if (showHealth) {
        CounterHealthSheet(
            health = state.health,
            onDismiss = { showHealth = false },
            onOpenAccessibility = {
                showHealth = false
                if (state.needsConsent) onSetUpCounting() else viewModel.openAccessibilitySettings(context)
            },
            onOpenBattery = { SystemScreens.openBatterySettings(context) },
        )
    }
}

/**
 * Today. One focal point (the brain and today's number), then three quiet blocks: per app,
 * streaks, and blocking. Explanations live behind the info button, not on the screen.
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onHealthClick: () -> Unit,
    onFixCounter: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenBrain: () -> Unit,
    onOpenProfile: () -> Unit,
    onPrivacyChoice: (Boolean) -> Unit,
) {
    // Stored data arrives within a frame or two. Showing defaults first would make the brain and
    // counters "animate" from zero on every open, a change that never happened.
    if (state.loading) {
        Box(Modifier.fillMaxSize())
        return
    }
    var showStreakInfo by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = RcSpacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader("Today") {
            HealthPill(health = state.health, onClick = onHealthClick)
            RcIconButton(RcIcons.Person, "Profile and settings", onOpenProfile)
        }
        CounterBanners(state, onFixCounter)
        BrainHero(state, onOpenBrain)
        CountBlock(state)
        AnimatedVisibility(visible = state.askPrivacyChoice, exit = fadeOut() + shrinkVertically()) {
            PrivacyChoiceCard(onPrivacyChoice, Modifier.padding(top = RcSpacing.section))
        }
        Spacer(Modifier.height(RcSpacing.section))
        AppsCard(state, Modifier.staggeredEntrance(0))
        Spacer(Modifier.height(RcSpacing.item))
        StreakCard(state, onInfo = { showStreakInfo = true }, modifier = Modifier.staggeredEntrance(1))
        Spacer(Modifier.height(RcSpacing.item))
        BlockingRow(state, onOpenPaywall, Modifier.staggeredEntrance(2))
        Spacer(Modifier.height(RcSpacing.section))
    }

    if (showStreakInfo) StreakInfoDialog(state, onDismiss = { showStreakInfo = false })
}

@Composable
private fun CounterBanners(state: HomeUiState, onFixCounter: () -> Unit) {
    val extended = RcTheme.extended
    val paused = state.statuses.filterValues { it == CountingStatus.PAUSED }.keys
    val estimated = state.statuses.filterValues { it == CountingStatus.ESTIMATED }.keys

    AnimatedVisibility(
        visible = state.health != CounterHealth.HEALTHY,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val stopped = state.health == CounterHealth.ENABLED_NOT_RUNNING
        Banner(
            icon = if (stopped) RcIcons.Warning else RcIcons.Accessibility,
            tint = extended.warning,
            title = when {
                state.needsConsent -> "Counting needs your OK"
                stopped -> "Counting stopped"
                else -> "Counting is off"
            },
            body = when {
                state.needsConsent -> "Reel Counter can't count until you agree to how it works."
                stopped -> "Your phone paused the counter. Switch it off and on again to fix it."
                else -> "Turn on Reel Counter in Accessibility settings to start counting."
            },
            action = when {
                state.needsConsent -> "Set up"
                stopped -> "Fix it"
                else -> "Turn on"
            },
            onAction = onFixCounter,
        )
    }
    if (paused.isNotEmpty()) {
        Banner(
            icon = RcIcons.Info,
            tint = extended.warning,
            title = "Counting paused for ${paused.joinToString { it.displayName }}",
            body = "An app update changed its screens. A fix usually lands within hours; we won't show wrong numbers.",
        )
    } else if (estimated.isNotEmpty()) {
        Banner(
            icon = RcIcons.Info,
            tint = MaterialTheme.colorScheme.onSurface,
            title = "Estimated counts",
            body = "${estimated.joinToString { it.displayName }} updated recently, so counts are estimates for now.",
        )
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    RcCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RcSpacing.item),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(icon, tint = tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            RcButton(text = action, onClick = onAction, style = RcButtonStyle.Cta, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BrainHero(state: HomeUiState, onOpenBrain: () -> Unit) {
    val stage = state.brain.stage
    val quips = stringArrayResource(stage.pokeQuipsRes())
    var quip by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(quip) {
        if (quip != null) {
            delay(2_600)
            quip = null
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(232.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Brain(
            rot = state.brain.rot,
            reactionKey = state.todayTotal,
            glow = state.streak >= 2 && state.todayTotal <= state.limit,
            mood = if (state.lateNight) BrainMood.SLEEPY else BrainMood.AUTO,
            onPoke = { quip = quips.filter { it != quip }.randomOrNull() ?: quips.first() },
            contentDescription = brainDescription(state.brain.rot),
            modifier = Modifier.size(216.dp),
        )
        SpeechBubble(
            text = quip,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    StageLink(
        name = stringResource(stage.nameRes()),
        dot = RcTheme.extended.stage(stage),
        onClick = onOpenBrain,
    )
}

/** "● Fresh ›": the brain's stage, and the way into the brain screen. */
@Composable
private fun StageLink(name: String, dot: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .lookSurface(CircleShape, scheme.surfaceContainer, thin = true)
            .clickable(role = Role.Button, onClickLabel = "Open your brain", onClick = onClick)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge)
        Icon(RcIcons.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** "13 / 20", what that means, and the bar. */
@Composable
private fun CountBlock(state: HomeUiState) {
    val scheme = MaterialTheme.colorScheme
    val extended = RcTheme.extended
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        AnimatedCounter(value = state.todayTotal, style = MaterialTheme.typography.displayLarge)
        Text(
            " / ${state.limit}",
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
    Text("reels today", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    LimitBar(count = state.todayTotal, limit = state.limit)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = when {
                state.remaining > 1 -> "${state.remaining} left today"
                state.remaining == 1 -> "1 left. Make it a good one."
                state.remaining == 0 -> "Limit reached. Nicely stopped."
                else -> "${-state.remaining} over · fresh start at midnight"
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (state.remaining < 0) extended.warningText else scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text("≈ ${state.minutes} min", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}

/** Instagram and YouTube as two rows of one card. */
@Composable
private fun AppsCard(state: HomeUiState, modifier: Modifier = Modifier) {
    RcCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        ReelApp.entries.forEachIndexed { i, app ->
            if (i > 0) ListDivider()
            AppRow(app, state.perApp[app] ?: 0, state.statuses[app])
        }
    }
}

@Composable
private fun AppRow(app: ReelApp, count: Int, status: CountingStatus?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RcSpacing.card, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, size = 32.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.displayName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            val note = when (status) {
                CountingStatus.ESTIMATED -> "Estimated"
                CountingStatus.PAUSED -> "Paused"
                else -> null
            }
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = RcTheme.extended.warningText)
        }
        AnimatedCounter(value = count, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
    }
}

/**
 * Day streak and week streak, this week's days, and one line on what today means for them.
 * The rules (a week counts when 5 of 7 days stay under) are one tap away, behind the info button.
 */
@Composable
private fun StreakCard(state: HomeUiState, onInfo: () -> Unit, modifier: Modifier = Modifier) {
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    val sticker = RcTheme.tokens.isSticker
    RcCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = RcSpacing.card, end = 8.dp, top = 12.dp, bottom = RcSpacing.card)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakStat(
                icon = RcIcons.Flame,
                iconTint = if (sticker) scheme.onSurface else extended.cta,
                value = "${state.streak}",
                label = "day streak",
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(Modifier.height(36.dp), color = scheme.outlineVariant)
            Spacer(Modifier.width(16.dp))
            StreakStat(
                icon = RcIcons.Calendar,
                iconTint = scheme.onSurfaceVariant,
                value = "${state.weekStreak.current}",
                label = "week streak",
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "How streaks work", onClick = onInfo)
                    .semantics { contentDescription = "How streaks work" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(RcIcons.Info, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            state.week.forEach { day -> WeekDot(day, state.limit) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            streakLine(state),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

private fun streakLine(state: HomeUiState): String {
    val badge = state.nextBadge
    return when {
        state.remaining < 0 -> "Over the limit today. Your streak starts again tomorrow."
        state.streak == 0 -> "Stay at or under ${state.limit} today to start a streak."
        badge != null -> {
            val left = badge - state.streak
            "Stay at or under ${state.limit} today. $left more ${if (left == 1) "day" else "days"} to your $badge-day badge."
        }
        else -> "Stay at or under ${state.limit} today to keep it going."
    }
}

@Composable
private fun StreakStat(icon: ImageVector, iconTint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekDot(day: WeekDay, limit: Int) {
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    val sticker = RcTheme.tokens.isSticker
    val letter = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    val description = when (day.result) {
        DayResult.UNDER -> "under the limit"
        DayResult.OVER -> "over the limit"
        DayResult.TODAY -> "today, ${day.total} so far"
        DayResult.FUTURE -> "still to come"
        DayResult.NO_DATA -> "before counting started"
    }
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: $description"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val base = Modifier.size(30.dp)
        val dot = when (day.result) {
            DayResult.UNDER -> base.lookSurface(CircleShape, extended.success, thin = true)
            DayResult.OVER -> base.lookSurface(CircleShape, extended.warning, thin = true)
            // Today's ring turns to the warning colour once it is past the limit.
            DayResult.TODAY -> base.border(
                2.dp,
                when {
                    day.total > limit -> extended.warningText
                    sticker -> scheme.onSurface
                    else -> scheme.onSurfaceVariant
                },
                CircleShape,
            )
            DayResult.FUTURE, DayResult.NO_DATA ->
                if (sticker) {
                    base.border(1.dp, scheme.onSurface.copy(alpha = 0.25f), CircleShape)
                } else {
                    base.clip(CircleShape).background(extended.track)
                }
        }
        Box(dot, contentAlignment = Alignment.Center) {
            when (day.result) {
                DayResult.UNDER -> Icon(RcIcons.Check, null, tint = extended.onSuccess, modifier = Modifier.size(16.dp))
                DayResult.OVER -> Text("${day.total}", style = MaterialTheme.typography.labelSmall, color = extended.onWarning, maxLines = 1)
                DayResult.TODAY -> Text("${day.total}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                else -> Unit
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            letter,
            style = MaterialTheme.typography.labelSmall,
            color = if (day.result == DayResult.TODAY) scheme.onSurface else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StreakInfoDialog(state: HomeUiState, onDismiss: () -> Unit) {
    val ws = state.weekStreak
    val thisWeek = when {
        ws.goodDaysNeeded == 0 -> "This week already counts."
        ws.stillPossible -> "This week: ${ws.goodDaysNeeded} more good ${if (ws.goodDaysNeeded == 1) "day" else "days"} and it counts."
        else -> "This week can't reach 5 good days any more. A new week starts Monday."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How streaks work") },
        text = {
            Text(
                "Day streak: every day you finish at or under your limit.\n\n" +
                    "Week streak: a week counts when 5 of its 7 days stay under your limit.\n\n" +
                    "Badges at ${Milestones.DAY_BADGES.joinToString()} days.\n\n" +
                    thisWeek,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

/** Asked once, after setup: both answers are one tap, and Profile can change it any time. */
@Composable
private fun PrivacyChoiceCard(onChoice: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    RcCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(RcIcons.Bug, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Help us fix bugs faster?", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(
                    "Share crash reports and anonymous usage stats. Never your reel counts. You can change this in Profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RcButton("No thanks", onClick = { onChoice(false) }, style = RcButtonStyle.Secondary, modifier = Modifier.weight(1f))
            RcButton("Share", onClick = { onChoice(true) }, style = RcButtonStyle.Secondary, modifier = Modifier.weight(1f))
        }
    }
}

/** One quiet row: the Pro offer, or (for Pro) whether blocking is on. */
@Composable
private fun BlockingRow(state: HomeUiState, onOpenPaywall: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (title, subtitle) = when {
        !state.isPro -> "Block Reels at your limit" to "Try Pro free for 7 days"
        state.blockAtLimit -> "Blocking at ${state.limit}" to "Reels and Shorts close when you hit your limit"
        else -> "Blocking is off" to "You'll get nudges only. Turn it on in Limits."
    }
    RcCard(
        modifier.fillMaxWidth(),
        onClick = if (!state.isPro) onOpenPaywall else null,
        contentPadding = PaddingValues(horizontal = RcSpacing.card, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                RcIcons.Shield,
                contentDescription = null,
                tint = if (state.isPro && state.blockAtLimit) RcTheme.extended.successText else scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            if (!state.isPro) Icon(RcIcons.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}
