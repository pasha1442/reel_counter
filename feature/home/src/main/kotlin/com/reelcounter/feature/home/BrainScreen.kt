package com.reelcounter.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.brain.nameRes
import com.reelcounter.core.design.brain.pokeQuipsRes
import com.reelcounter.core.design.brain.taglineRes
import com.reelcounter.core.design.components.ListDivider
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.components.SpeechBubble
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.BrainStage
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Preview rot for each stage: the middle of its range. */
private val PreviewRot = mapOf(
    BrainStage.FRESH to 8f,
    BrainStage.FOGGY to 30f,
    BrainStage.CRACKING to 50f,
    BrainStage.MELTING to 70f,
    BrainStage.ROTTEN to 94f,
)

private const val NOW = -1
private const val GLOWING = 5

/** "Your brain": what it looks like now, every stage you can tap through, and how it heals. */
@Composable
fun BrainRoute(onBack: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) return
    // NOW = today's brain; 0..4 = a stage; GLOWING = the streak reward.
    var selected by rememberSaveable { mutableIntStateOf(NOW) }
    var poke by rememberSaveable { mutableStateOf<String?>(null) }
    val nowStage = state.brain.stage
    val stage = if (selected in 0..4) BrainStage.entries[selected] else nowStage
    val rot = when (selected) {
        NOW -> state.brain.rot
        GLOWING -> 0f
        else -> PreviewRot.getValue(stage)
    }
    val quips = stringArrayResource(stage.pokeQuipsRes())
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        RcTopBar(title = "Your brain", onBack = onBack)
        Column(Modifier.padding(horizontal = RcSpacing.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(248.dp), contentAlignment = Alignment.BottomCenter) {
                Brain(
                    rot = rot,
                    glow = selected == GLOWING,
                    celebrateKey = selected,
                    onPoke = { poke = quips.filter { it != poke }.randomOrNull() ?: quips.first() },
                    modifier = Modifier.size(232.dp),
                )
                SpeechBubble(poke, Modifier.align(Alignment.TopCenter))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (selected == GLOWING) "Glowing" else stringResource(stage.nameRes()),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (selected) {
                    NOW -> "Now · ${state.todayTotal} of ${state.limit} reels"
                    GLOWING -> "The morning after a day under your limit"
                    else -> "Preview · ${rangeText(stage, state.limit)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (selected == GLOWING) "“Look at me. Shiny.”" else "“${stringResource(stage.taglineRes())}”",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(RcSpacing.section))
            StageSelector(
                selected = if (selected in 0..4) selected else if (selected == NOW) nowStage.ordinal else -1,
                nowStage = nowStage,
                onSelect = { selected = if (it == nowStage.ordinal && selected != NOW) NOW else it },
            )
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                if (selected != NOW) {
                    TextButton(onClick = { selected = NOW }) {
                        Icon(RcIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back to now", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Text("Tap a stage to preview it", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(RcSpacing.item))
            RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                InfoRow(
                    icon = RcIcons.Sparkle,
                    iconTint = if (RcTheme.tokens.isSticker) scheme.onSurface else RcTheme.extended.cta,
                    title = "Wake up glowing",
                    body = "Finish today at or under ${state.limit} and it glows tomorrow.",
                    action = if (selected != GLOWING) "Preview" else null,
                    onAction = { selected = GLOWING },
                )
                ListDivider()
                val left = state.brain.morningResidual.roundToInt()
                InfoRow(
                    icon = RcIcons.Refresh,
                    iconTint = scheme.onSurfaceVariant,
                    title = "It heals overnight",
                    body = if (left > 0) {
                        "$left% of today's fog is left from earlier days. A day under your limit clears most of it."
                    } else {
                        "A day under your limit means a fresh brain tomorrow morning."
                    },
                )
            }
            Spacer(Modifier.height(RcSpacing.section))
        }
    }
}

/** Five equal segments, one per stage, each with its colour dot; "now" marks today's stage. */
@Composable
private fun StageSelector(selected: Int, nowStage: BrainStage, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    Row(
        Modifier
            .fillMaxWidth()
            .lookSurface(RoundedCornerShape(16.dp), scheme.surfaceContainer, thin = true)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BrainStage.entries.forEachIndexed { i, s ->
            val on = selected == i
            val fill by animateColorAsState(
                when {
                    !on -> Color.Transparent
                    sticker -> extended.stage(s)
                    else -> scheme.surfaceContainerHighest
                },
                label = "stageFill",
            )
            val text = if (on && sticker) extended.onStage(s) else if (on) scheme.onSurface else scheme.onSurfaceVariant
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .then(if (on) Modifier.lookSurface(RoundedCornerShape(12.dp), fill, outlined = sticker, thin = true) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (on && sticker) extended.onStage(s).copy(alpha = 0.8f) else extended.stage(s)),
                )
                Spacer(Modifier.height(6.dp))
                // Shrinks to fit: "Cracking" is wider than a fifth of a small phone.
                BasicText(
                    stringResource(s.nameRes()),
                    style = MaterialTheme.typography.labelMedium.copy(color = text, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                if (s == nowStage) {
                    Text("now", style = MaterialTheme.typography.labelSmall, color = text.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = RcSpacing.card, end = if (action != null) 8.dp else RcSpacing.card, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

private fun rangeText(stage: BrainStage, limit: Int): String {
    val from = ceil(stage.range.first * limit / 100f).toInt()
    return if (stage == BrainStage.ROTTEN) {
        "$from+ reels at your limit of $limit"
    } else {
        val to = (ceil((stage.range.last + 1) * limit / 100f).toInt() - 1).coerceAtLeast(from)
        "$from–$to reels at your limit of $limit"
    }
}
