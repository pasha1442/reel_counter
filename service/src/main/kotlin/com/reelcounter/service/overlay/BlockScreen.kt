package com.reelcounter.service.overlay

import com.reelcounter.core.model.WatchTime
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.design.components.Pill
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.brain.brainDescription
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcButtonStyle
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.limits.BlockReason
import com.reelcounter.core.limits.UnlockMethod
import com.reelcounter.core.limits.UnlockOffer
import com.reelcounter.core.limits.UnlockRules
import com.reelcounter.core.model.ReelApp
import kotlinx.coroutines.delay

data class BlockUiState(
    val app: ReelApp,
    val reason: BlockReason,
    val todayTotal: Int,
    val limit: Int,
    val appLimit: Int?,
    val offer: UnlockOffer,
    val rot: Float,
    val look: AppLook = AppLook.MIDNIGHT,
)

@Composable
internal fun BlockScreen(state: BlockUiState, callbacks: BlockCallbacks) {
    val scheme = MaterialTheme.colorScheme
    val sticker = RcTheme.tokens.isSticker
    // Both looks block on their own calm page: the brain and the one big button carry the message.
    val page = scheme.background
    // A window added by a service has no Surface above it: set the text colour here.
    Surface(color = page, contentColor = scheme.onBackground) {
    Box(
        Modifier
            .fillMaxSize()
            .background(page),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Brain(
                rot = state.rot,
                reactionKey = state.todayTotal,
                modifier = Modifier.size(220.dp),
                contentDescription = brainDescription(state.rot),
            )
            Spacer(Modifier.height(8.dp))
            Pill(
                text = "${state.app.displayName} ${state.app.surfaceName}",
                color = scheme.surfaceContainer,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (sticker) "Brain break!" else "That's ${state.limit}.",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle(state),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(28.dp))
            RcButton(
                text = "Leave ${state.app.surfaceName}",
                onClick = callbacks::onLeave,
                style = RcButtonStyle.Cta,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(16.dp))
            UnlockSection(state.offer, callbacks)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Your count resets at midnight.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
    }
}

private fun subtitle(state: BlockUiState): String = when (state.reason) {
    BlockReason.APP_LIMIT ->
        "That's ${state.appLimit ?: state.todayTotal} ${state.app.displayName} ${state.app.surfaceName} today. The rest of the app still works."
    BlockReason.DAILY_LIMIT ->
        "${state.todayTotal} reels today, about ${WatchTime.minutes(state.todayTotal)} minutes. Your brain tapped out. DMs and search still work."
}

@Composable
private fun UnlockSection(offer: UnlockOffer, callbacks: BlockCallbacks) {
    when (offer.method) {
        UnlockMethod.BUILD_PHRASE -> PhraseUnlock(offer, callbacks)
        UnlockMethod.WAIT -> WaitUnlock(offer, callbacks)
        UnlockMethod.NONE -> Text(
            text = if (offer.unlocksLeft == 0) "No unlocks left today. Your brain says thanks." else "Locked until midnight.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhraseUnlock(offer: UnlockOffer, callbacks: BlockCallbacks) {
    var open by remember { mutableStateOf(false) }
    AnimatedContent(targetState = open, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "unlock") { expanded ->
        if (!expanded) {
            RcButton(
                text = "Unlock ${offer.extraReels} more (${offer.unlocksLeft} left today)",
                onClick = { open = true },
                style = RcButtonStyle.Ghost,
                icon = RcIcons.Lock,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            )
        } else {
            PromiseBuilder(offer = offer, onDone = callbacks::onUnlock)
        }
    }
}

/**
 * Firm-mode friction without a keyboard: tap the promise's words in order. A wrong word shakes;
 * the right one moves into the sentence. Enough effort to break autopilot, no typing in an overlay.
 */
@Composable
private fun PromiseBuilder(offer: UnlockOffer, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val target = UnlockRules.PHRASE_WORDS
    val words = remember { UnlockRules.shuffledWords() }
    val picked = remember { mutableStateListOf<Int>() }
    var shaking by remember { mutableStateOf<Int?>(null) }
    val shake = remember { Animatable(0f) }
    val done = picked.size == target.size

    LaunchedEffect(shaking) {
        if (shaking == null) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 320
                -12f at 50
                10f at 120
                -6f at 190
                3f at 250
            },
        )
        shaking = null
    }

    RcCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
    ) {
        Text("Tap the words in order to unlock ${offer.extraReels} more", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            text = buildAnnotatedString {
                target.forEachIndexed { i, word ->
                    withStyle(SpanStyle(color = if (i < picked.size) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.4f))) {
                        append(word)
                    }
                    if (i < target.lastIndex) append(" ")
                }
            },
            style = MaterialTheme.typography.titleLarge,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.semantics { contentDescription = "Promise: ${UnlockRules.PHRASE}. ${picked.size} of ${target.size} words placed." },
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            words.forEachIndexed { index, word ->
                val used = index in picked
                Surface(
                    onClick = {
                        if (word == target[picked.size]) {
                            picked += index
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        } else {
                            shaking = index
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        }
                    },
                    enabled = !used && !done,
                    shape = CircleShape,
                    color = if (used) scheme.surfaceContainerHighest.copy(alpha = 0.35f) else scheme.surfaceContainerHighest,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .graphicsLayer { translationX = if (shaking == index) shake.value.dp.toPx() else 0f },
                ) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (used) scheme.onSurfaceVariant.copy(alpha = 0.4f) else scheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        RcButton(
            text = if (done) "Unlock ${offer.extraReels} reels" else "${picked.size} / ${target.size}",
            onClick = onDone,
            style = RcButtonStyle.Primary,
            enabled = done,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WaitUnlock(offer: UnlockOffer, callbacks: BlockCallbacks) {
    var secondsLeft by remember { mutableIntStateOf(offer.waitSeconds) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    RcButton(
        text = if (secondsLeft > 0) "Take a breath… ${secondsLeft}s" else "Continue for ${offer.extraReels} more",
        onClick = callbacks::onUnlock,
        enabled = secondsLeft == 0,
        style = RcButtonStyle.Ghost,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
    )
}
