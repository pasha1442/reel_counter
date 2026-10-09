package com.reelcounter.feature.account

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.data.LegalPages
import com.reelcounter.core.data.account.AuthMode
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.brain.BrainMood
import com.reelcounter.core.design.components.FormMessage
import com.reelcounter.core.design.components.LinkMinHeight
import com.reelcounter.core.design.components.Pill
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcButtonStyle
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcTextField
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.components.SegmentedToggle
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.theme.RcTheme

/**
 * Sign in or create an account: Google or email, as equal choices, with "Not now" just as easy.
 * Accounts are optional; the screen says so.
 */
@Composable
fun SignInRoute(
    onBack: () -> Unit,
    onEmail: () -> Unit,
    onSignedIn: (needsSetup: Boolean) -> Unit,
    onOpenLegal: (String) -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(Unit) {
        viewModel.nav.collect { if (it is AuthNav.SignedIn) onSignedIn(it.needsSetup) }
    }
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        RcTopBar(title = "", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RcSpacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Brain(rot = 0f, mood = BrainMood.CELEBRATING, modifier = Modifier.size(132.dp), contentDescription = null)
            Spacer(Modifier.height(20.dp))
            Text("Sign in to Reel Counter", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Keep Pro on a new phone and get ready for friend battles. Counting, limits and streaks work without an account.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (viewModel.mode == AuthMode.TEST) {
                Spacer(Modifier.height(16.dp))
                TestModeNote()
            }
            Spacer(Modifier.height(32.dp))
            RcButton(
                text = "Continue with Google",
                onClick = { activity?.let(viewModel::continueWithGoogle) },
                style = RcButtonStyle.Secondary,
                leading = { GoogleLogo() },
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            RcButton(
                text = "Continue with email",
                onClick = onEmail,
                style = RcButtonStyle.Secondary,
                icon = RcIcons.Mail,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(16.dp))
                FormMessage(it, isError = true)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = LinkMinHeight)) {
                Text("Not now", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
        }
        LegalFooter(onOpenLegal, Modifier.padding(horizontal = RcSpacing.gutter, vertical = 16.dp))
    }
}

/** Email and password, with "Create account" and "Sign in" as two tabs of one form. */
@Composable
fun EmailAuthRoute(
    startCreating: Boolean,
    onBack: () -> Unit,
    onSignedIn: (needsSetup: Boolean) -> Unit,
    onOpenLegal: (String) -> Unit,
    viewModel: EmailAuthViewModel = hiltViewModel(),
) {
    LaunchedEffect(startCreating) { viewModel.start(startCreating) }
    LaunchedEffect(Unit) {
        viewModel.nav.collect { if (it is AuthNav.SignedIn) onSignedIn(it.needsSetup) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        RcTopBar(title = if (state.creating) "Create account" else "Sign in with email", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RcSpacing.gutter),
        ) {
            Spacer(Modifier.height(8.dp))
            SegmentedToggle(
                options = listOf("Sign in", "Create account"),
                selected = if (state.creating) 1 else 0,
                onSelect = { viewModel.setCreating(it == 1) },
                equalWidths = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (viewModel.mode == AuthMode.TEST) {
                Spacer(Modifier.height(16.dp))
                TestModeNote()
            }
            Spacer(Modifier.height(24.dp))
            RcTextField(
                value = state.email,
                onValueChange = viewModel::setEmail,
                label = "Email",
                leadingIcon = RcIcons.Mail,
                error = state.emailError,
                keyboardType = KeyboardType.Email,
                enabled = !state.busy,
            )
            Spacer(Modifier.height(12.dp))
            RcTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = "Password",
                leadingIcon = RcIcons.Lock,
                password = true,
                error = state.passwordError,
                supporting = if (state.creating) "At least 8 characters" else null,
                imeAction = ImeAction.Done,
                onImeAction = viewModel::submit,
                enabled = !state.busy,
            )
            if (!state.creating) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = viewModel::sendReset, enabled = !state.busy, modifier = Modifier.heightIn(min = LinkMinHeight)) {
                        Text("Forgot password?", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            state.error?.let {
                FormMessage(it, isError = true)
                Spacer(Modifier.height(12.dp))
            }
            state.notice?.let {
                FormMessage(it, isError = false)
                Spacer(Modifier.height(12.dp))
            }
            RcButton(
                text = if (state.creating) "Create account" else "Sign in",
                onClick = viewModel::submit,
                loading = state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.creating) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Accounts are for people aged 18 and over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LegalFooter(onOpenLegal, Modifier.padding(horizontal = RcSpacing.gutter, vertical = 16.dp))
    }
}

/** "By continuing, you agree to our Terms of Use and Privacy Policy", with both as links. */
@Composable
internal fun LegalFooter(onOpenLegal: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val links = TextLinkStyles(SpanStyle(color = scheme.onSurface, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        append("By continuing, you agree to our ")
        withLink(LinkAnnotation.Clickable(LegalPages.TERMS, links) { onOpenLegal(LegalPages.TERMS) }) { append("Terms of Use") }
        append(" and ")
        withLink(LinkAnnotation.Clickable(LegalPages.PRIVACY, links) { onOpenLegal(LegalPages.PRIVACY) }) { append("Privacy Policy") }
        append(".")
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Debug builds without Firebase: make it obvious that no real account is involved. */
@Composable
internal fun TestModeNote(modifier: Modifier = Modifier) {
    val extended = RcTheme.extended
    Pill(
        text = "Test mode · no real account is made",
        color = extended.tintSky,
        contentColor = extended.onTintSky,
        icon = RcIcons.Info,
        modifier = modifier,
    )
}
