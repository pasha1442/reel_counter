package com.reelcounter.feature.onboarding

import com.reelcounter.core.model.WatchTime
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontWeight
import com.reelcounter.core.design.components.ListDivider
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.components.lookLabel
import com.reelcounter.core.design.components.LookPicker
import com.reelcounter.core.design.components.AppIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.reelcounter.core.data.DeviceGuide
import com.reelcounter.core.data.SystemScreens
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.brain.BrainMood
import com.reelcounter.core.design.components.ConfettiBurst
import com.reelcounter.core.design.components.IconTile
import com.reelcounter.core.design.components.LimitPresetChips
import com.reelcounter.core.design.components.NumberStepper
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcButtonStyle
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.theme.LocalReducedMotion
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.core.model.LimitSettings
import com.reelcounter.core.model.ReelApp
import kotlinx.coroutines.delay

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun WelcomeStep(onNext: () -> Unit) {
    var pokes by remember { mutableIntStateOf(0) }
    StepLayout(
        centered = true,
        actions = {
            RcButton("Let's start", onClick = onNext, style = RcButtonStyle.Cta, modifier = Modifier.fillMaxWidth())
            Text("About 2 minutes · no account needed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.BottomCenter) {
            Brain(
                rot = 0f,
                glow = true,
                reactionKey = pokes,
                onPoke = { pokes++ },
                contentDescription = "Your brain, fresh and happy. Tap to poke it.",
                modifier = Modifier.size(270.dp),
            )
            HiThere(if (pokes == 0) "Hi there!" else "Hehe. Again!", Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(16.dp))
        Title("Meet your brain.")
        Spacer(Modifier.height(12.dp))
        Body("Every reel you scroll makes it a little foggier. We count them, so you can keep it bright.")
    }
}

/** The welcome speech bubble: a tilted sticker in either look. */
@Composable
private fun HiThere(text: String, modifier: Modifier = Modifier) {
    val sticker = RcTheme.tokens.isSticker
    Box(
        modifier
            .padding(end = 4.dp, top = 8.dp)
            .rotate(4f)
            .lookSurface(
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp),
                if (sticker) Color.White else MaterialTheme.colorScheme.inverseSurface,
                raised = true,
                shadowSize = 3.dp,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (sticker) Color(0xFF111111) else MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

private val GuessTiles = listOf("<20" to "a few", "20–50" to "some", "50–100" to "a lot", "100+" to "don't ask")

@Composable
internal fun GuessStep(selected: Int?, onChoose: (Int?) -> Unit, onNext: () -> Unit) {
    var noIdea by remember { mutableStateOf(false) }
    StepLayout(
        actions = {
            RcButton("Continue", onClick = onNext, enabled = selected != null || noIdea, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Title("How many reels a day?")
        Spacer(Modifier.height(10.dp))
        Body("Just guess. You'll see the real number by tomorrow.")
        Spacer(Modifier.height(24.dp))
        val scheme = MaterialTheme.colorScheme
        val sticker = RcTheme.tokens.isSticker
        GuessTiles.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, (value, label) ->
                    val index = row * 2 + col
                    val on = selected == index
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(96.dp)
                            .lookSurface(shape, if (on && sticker) scheme.primaryContainer else if (on) scheme.surfaceContainerHighest else scheme.surfaceContainer, raised = on)
                            .then(if (on && !sticker) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier)
                            .selectable(selected = on, role = Role.RadioButton) {
                                noIdea = false
                                onChoose(index)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(value, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(if (noIdea) 2.dp else 1.dp, if (noIdea) (if (sticker) scheme.onSurface else scheme.primary) else scheme.outline, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .selectable(selected = noIdea, role = Role.RadioButton) {
                    noIdea = true
                    onChoose(null)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("No idea", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun LookStep(look: AppLook, onChoose: (AppLook) -> Unit, onNext: () -> Unit) {
    StepLayout(
        actions = {
            RcButton("Use ${look.displayName}", onClick = onNext, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Title("Pick your look")
        Spacer(Modifier.height(10.dp))
        Body("Same app, two styles. Change it any time in your profile.")
        Spacer(Modifier.height(24.dp))
        LookPicker(selected = look, onSelect = onChoose)
    }
}

/** Play's prominent disclosure: what we read, what we never keep, and two clear, equal choices. */
@Composable
internal fun DisclosureStep(onAgree: () -> Unit, onDecline: () -> Unit) {
    StepLayout(
        actions = {
            RcButton("Agree and continue", onClick = onAgree, style = RcButtonStyle.Cta, modifier = Modifier.fillMaxWidth())
            RcButton("Not now", onClick = onDecline, style = RcButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Title("How counting works")
        Spacer(Modifier.height(12.dp))
        Body("Reel Counter uses Android's Accessibility permission only to count and block reels.")
        Spacer(Modifier.height(24.dp))
        RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
            DealRow(RcIcons.Seen, "What we see", "Which Instagram or YouTube screen is open, and when a new reel appears.")
            ListDivider()
            DealRow(RcIcons.Delete, "Gone in a moment", "Captions and names are read for a split second to tell reels apart. Never saved.")
            ListDivider()
            DealRow(RcIcons.Lock, "Never touched", "Messages, passwords, anything you type, and every other app.")
            ListDivider()
            DealRow(RcIcons.Phone, "Stays on your phone", "Your counts live here. Nothing is uploaded.")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DealRow(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AccessibilityStep(
    health: CounterHealth,
    onOpenSettings: (Context) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val guide = remember { DeviceGuide.guide() }
    val on = health == CounterHealth.HEALTHY

    LaunchedEffect(on) {
        if (on) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(1_100)
            onNext()
        }
    }

    StepLayout(
        actions = {
            if (on) {
                RcButton("Continue", onClick = onNext, modifier = Modifier.fillMaxWidth())
            } else {
                RcButton(
                    "Open Accessibility settings",
                    onClick = { onOpenSettings(context) },
                    style = RcButtonStyle.Cta,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Title("Switch on Reel Counter")
        Spacer(Modifier.height(20.dp))
        ToggleDemo(on = on)
        Spacer(Modifier.height(20.dp))
        AnimatedContent(targetState = on, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "a11y") { enabled ->
            if (enabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(RcIcons.CheckCircle, contentDescription = null, tint = RcTheme.extended.successText, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("The counter is on!", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                Column {
                    NumberedStep(1, "Tap the button below")
                    NumberedStep(2, "Find Reel Counter. On this phone: ${guide.accessibilityPath}")
                    NumberedStep(3, "Switch it on and tap Allow")
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Waiting for you to switch it on…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    RcCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text("Seeing “Restricted setting”?", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Android blocks this for apps installed outside the Play Store. Open App info, tap the ⋮ menu, choose Allow restricted settings, then try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        RcButton("Open App info", onClick = { SystemScreens.openAppDetails(context) }, style = RcButtonStyle.Secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(
        Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A looping mock of the settings switch, showing what to tap. */
@Composable
private fun ToggleDemo(on: Boolean) {
    val reducedMotion = LocalReducedMotion.current
    val phase = if (on || reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "demo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing)),
            label = "demoPhase",
        )
    }
    val p = phase?.value ?: 1f
    val checked = on || p in 0.45f..0.95f
    val tapScale = if (p in 0.3f..0.5f) 1f - kotlin.math.abs(p - 0.4f) * 5f else 0f

    RcCard(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Example: the Reel Counter switch in Accessibility settings" },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text("Accessibility", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(RcIcons.Accessibility)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Reel Counter", style = MaterialTheme.typography.titleMedium)
                Text(if (checked) "On" else "Off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(contentAlignment = Alignment.Center) {
                Switch(checked = checked, onCheckedChange = null)
                if (tapScale > 0f) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = 0.6f + tapScale * 0.6f
                                scaleY = 0.6f + tapScale * 0.6f
                                alpha = tapScale
                            }
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
internal fun NotificationsStep(onAllow: () -> Unit, onSkip: () -> Unit) {
    StepLayout(
        centered = true,
        actions = {
            RcButton("Allow notifications", onClick = onAllow, style = RcButtonStyle.Cta, modifier = Modifier.fillMaxWidth())
            RcButton("Skip", onClick = onSkip, style = RcButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Spacer(Modifier.height(24.dp))
        IconTile(RcIcons.Bell, size = 72.dp)
        Spacer(Modifier.height(24.dp))
        Title("Want a heads-up?")
        Spacer(Modifier.height(12.dp))
        Body("We'll nudge you at 50%, 80% and 100% of your limit, and tell you straight away if counting ever stops.")
        Spacer(Modifier.height(20.dp))
        RcCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("80% of your limit", style = MaterialTheme.typography.titleSmall)
            Text(
                "32 of 40. Cracks are showing. Pick your last few wisely.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun BatteryStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    val guide = remember { DeviceGuide.guide() }
    var unrestricted by remember { mutableStateOf(DeviceGuide.isBatteryUnrestricted(context)) }
    LifecycleResumeEffect(Unit) {
        unrestricted = DeviceGuide.isBatteryUnrestricted(context)
        onPauseOrDispose { }
    }

    StepLayout(
        actions = {
            if (!unrestricted) {
                RcButton(
                    "Open battery settings",
                    onClick = { SystemScreens.openBatterySettings(context) },
                    style = RcButtonStyle.Cta,
                    icon = RcIcons.Battery,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RcButton(
                if (unrestricted) "Continue" else "I've done it",
                onClick = onContinue,
                style = if (unrestricted) RcButtonStyle.Primary else RcButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Title("Keep the counter alive")
        Spacer(Modifier.height(12.dp))
        Body("Some phones stop background apps to save battery. That's why other counters “just stop”. Two minutes here prevents it.")
        Spacer(Modifier.height(20.dp))
        if (unrestricted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(RcIcons.CheckCircle, contentDescription = null, tint = RcTheme.extended.successText)
                Spacer(Modifier.width(8.dp))
                Text("Battery: Unrestricted", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
        RcCard(Modifier.fillMaxWidth()) {
            Text(guide.brandName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(guide.problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            guide.steps.forEachIndexed { index, step -> NumberedStep(index + 1, step) }
        }
    }
}

@Composable
internal fun LimitStep(limit: Int, guess: Int?, onChange: (Int) -> Unit, onSave: () -> Unit) {
    StepLayout(
        actions = {
            RcButton("Set $limit a day", onClick = onSave, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Title("Pick a daily limit")
        Spacer(Modifier.height(10.dp))
        val guessText = guess?.let { GuessTiles[it].first }
        Body(
            if (guessText != null) {
                "You guessed $guessText a day. Pick a number that feels a little hard. You can change it any time."
            } else {
                "Most people start around 40. Your brain's fog scales to this number, and you can change it any time."
            },
        )
        Spacer(Modifier.height(12.dp))
        // The brain at the end of a day spent exactly at this limit vs half of it: it reacts as you choose.
        val previewRot = when {
            limit <= 20 -> 10f
            limit <= 40 -> 22f
            limit <= 60 -> 38f
            else -> 55f
        }
        Brain(rot = previewRot, reactionKey = limit, modifier = Modifier.size(132.dp), contentDescription = "Brain preview")
        Text(
            when {
                limit <= 20 -> "At $limit a day, your brain stays fresh."
                limit <= 40 -> "At $limit a day, a little fog by night."
                limit <= 60 -> "At $limit a day, expect some cracks."
                else -> "At $limit a day, it'll get foggy. A start!"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        NumberStepper(
            value = limit,
            onValueChange = onChange,
            range = LimitSettings.MIN_LIMIT..LimitSettings.MAX_LIMIT,
            step = 5,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "About ${WatchTime.minutes(limit)} minutes of scrolling",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        LimitPresetChips(selected = limit, onSelect = onChange)
    }
}

/** The first "wow": prove counting works before the user leaves setup. */
@Composable
internal fun LiveTestStep(progress: Int, passed: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val installed = remember {
        ReelApp.entries.filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
    }
    LaunchedEffect(passed) {
        if (passed) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    Box(Modifier.fillMaxSize()) {
        StepLayout(
        centered = true,
            actions = {
                if (passed) {
                    RcButton("Take me home", onClick = onFinish, style = RcButtonStyle.Cta, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        installed.forEachIndexed { i, app ->
                            RcButton(
                                app.displayName,
                                onClick = { SystemScreens.launchApp(context, app.packageName) },
                                style = if (i == 0) RcButtonStyle.Primary else RcButtonStyle.Secondary,
                                leading = { AppIcon(app, size = 24.dp) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    RcButton("Skip the test", onClick = onFinish, style = RcButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
                }
            },
        ) {
            Spacer(Modifier.height(8.dp))
            Brain(
                rot = 0f,
                mood = if (passed) BrainMood.CELEBRATING else BrainMood.AUTO,
                celebrateKey = passed,
                reactionKey = progress,
                modifier = Modifier.size(200.dp),
                contentDescription = if (passed) "Your brain is celebrating" else "Your brain, waiting",
            )
            Spacer(Modifier.height(8.dp))
            Title(if (passed) "It works!" else "Scroll 3 reels!")
            Spacer(Modifier.height(12.dp))
            Body(
                if (passed) {
                    "Your counter is live. Every reel from now on counts."
                } else if (installed.isEmpty()) {
                    "Install Instagram or YouTube to try it. You can skip this for now."
                } else {
                    "Open Reels or Shorts and scroll ${OnboardingUiState.LIVE_TEST_REELS} times, then come back here."
                },
            )
            Spacer(Modifier.height(28.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$progress of ${OnboardingUiState.LIVE_TEST_REELS} reels counted"
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                repeat(OnboardingUiState.LIVE_TEST_REELS) { index ->
                    TestDot(done = index < progress, number = index + 1)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (passed) "3 of 3 counted" else "$progress of 3 counted",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ConfettiBurst(trigger = passed, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TestDot(done: Boolean, number: Int) {
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    val sticker = RcTheme.tokens.isSticker
    Box(
        Modifier
            .size(72.dp)
            .then(
                if (done) {
                    Modifier.lookSurface(CircleShape, extended.success, raised = true)
                } else {
                    Modifier.border(2.dp, if (sticker) scheme.onSurface else scheme.outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(RcIcons.Check, contentDescription = null, tint = extended.onSuccess, modifier = Modifier.size(32.dp))
        } else {
            Text("$number", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant)
        }
    }
}
