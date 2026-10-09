package com.reelcounter.feature.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcIconButton
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.design.theme.LocalReducedMotion

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onDeclined: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.next()
    }

    BackHandler(enabled = state.step != viewModel.steps.first()) { viewModel.back() }

    OnboardingScreen(
        state = state,
        steps = viewModel.steps,
        onBack = { viewModel.back() },
        actions = OnboardingActions(
            next = viewModel::next,
            agree = viewModel::agreeToDisclosure,
            decline = { viewModel.declineDisclosure(onDeclined) },
            openAccessibility = viewModel::openAccessibilitySettings,
            requestNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            setLimit = viewModel::setLimit,
            chooseGuess = viewModel::chooseGuess,
            chooseLook = viewModel::chooseLook,
            saveLimit = viewModel::saveLimitAndContinue,
            finish = { viewModel.finish(onFinished) },
        ),
    )
}

internal class OnboardingActions(
    val next: () -> Unit,
    val agree: () -> Unit,
    val decline: () -> Unit,
    val openAccessibility: (android.content.Context) -> Unit,
    val requestNotifications: () -> Unit,
    val setLimit: (Int) -> Unit,
    val chooseGuess: (Int?) -> Unit,
    val chooseLook: (com.reelcounter.core.model.AppLook) -> Unit,
    val saveLimit: () -> Unit,
    val finish: () -> Unit,
)

@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    steps: List<OnboardingStep>,
    onBack: () -> Unit,
    actions: OnboardingActions,
) {
    val reducedMotion = LocalReducedMotion.current
    val index = steps.indexOf(state.step).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp)) {
                if (index > 0) {
                    RcIconButton(RcIcons.Back, "Back", onBack, modifier = Modifier.align(Alignment.Center))
                }
            }
            StepProgress(
                current = index,
                total = steps.size,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Spacer(Modifier.size(48.dp))
        }

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = steps.indexOf(targetState) > steps.indexOf(initialState)
                if (reducedMotion) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                } else {
                    (slideInHorizontally(tween(320)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(280)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(180)))
                }
            },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep(actions.next)
                OnboardingStep.GUESS -> GuessStep(state.guess, actions.chooseGuess, actions.next)
                OnboardingStep.LOOK -> LookStep(state.look, actions.chooseLook, actions.next)
                OnboardingStep.DISCLOSURE -> DisclosureStep(onAgree = actions.agree, onDecline = actions.decline)
                OnboardingStep.ACCESSIBILITY -> AccessibilityStep(state.health, actions.openAccessibility, actions.next)
                OnboardingStep.NOTIFICATIONS -> NotificationsStep(onAllow = actions.requestNotifications, onSkip = actions.next)
                OnboardingStep.BATTERY -> BatteryStep(onContinue = actions.next)
                OnboardingStep.LIMIT -> LimitStep(state.dailyLimit, state.guess, actions.setLimit, actions.saveLimit)
                OnboardingStep.LIVE_TEST -> LiveTestStep(state.testProgress, state.testPassed, actions.finish)
            }
        }
    }
}

@Composable
private fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    val sticker = RcTheme.tokens.isSticker
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.semantics { contentDescription = "Step ${current + 1} of $total" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { i ->
            val done = i <= current
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .lookSurface(
                        RoundedCornerShape(2.dp),
                        if (done) (if (sticker) scheme.onSurface else scheme.primary) else (if (sticker) scheme.onSurface.copy(alpha = 0.15f) else scheme.surfaceContainerHighest),
                        outlined = false,
                    ),
            )
        }
    }
}

/** Common layout: scrollable content, primary actions pinned to the thumb zone. */
@Composable
internal fun StepLayout(
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (centered) Arrangement.Center else Arrangement.Top,
                content = content,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = actions,
        )
    }
}
