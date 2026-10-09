package com.reelcounter.feature.account

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.data.LegalPages
import com.reelcounter.core.data.account.AccountUser
import com.reelcounter.core.data.account.AuthMode
import com.reelcounter.core.data.account.SignInMethod
import com.reelcounter.core.design.components.FormMessage
import com.reelcounter.core.design.components.ListDivider
import com.reelcounter.core.design.components.NavRow
import com.reelcounter.core.design.components.Pill
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcTextField
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.components.SectionHeader
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.theme.RcTheme

/** Who is signed in, and the account's own actions: name, password, sign out, delete. */
@Composable
fun AccountRoute(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenLegal: (String) -> Unit,
    onFinishSetup: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val setupComplete by viewModel.setupComplete.collectAsStateWithLifecycle()
    val delete by viewModel.delete.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        viewModel.leave.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            RcTopBar(title = "Account", onBack = onBack)
            val account = user
            if (account == null) {
                Text(
                    "You're signed out.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(RcSpacing.gutter),
                )
                return@Column
            }
            Column(Modifier.padding(horizontal = RcSpacing.gutter)) {
                AccountHeader(account, testMode = viewModel.mode == AuthMode.TEST)

                if (!setupComplete) {
                    Spacer(Modifier.height(RcSpacing.item))
                    RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                        NavRow("Finish setting up your account", onClick = onFinishSetup, subtitle = "Your name and a quick age check", icon = RcIcons.Person)
                    }
                }

                SectionHeader("Details")
                RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    NavRow("Name", onClick = { renaming = true }, icon = RcIcons.Edit, trailingText = account.name ?: "Add")
                    ListDivider()
                    NavRow(
                        "Subscription",
                        onClick = { if (isPro) activity?.let(viewModel::openManageSubscription) else onOpenPaywall() },
                        icon = RcIcons.Card,
                        trailingText = if (isPro) "Pro" else "Free",
                    )
                    if (account.method == SignInMethod.EMAIL) {
                        ListDivider()
                        NavRow("Change password", onClick = viewModel::sendPasswordReset, subtitle = "We'll email you a link", icon = RcIcons.Key)
                    }
                }

                SectionHeader("Signing out and deleting")
                RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    NavRow("Sign out", onClick = { confirmSignOut = true }, icon = RcIcons.Logout)
                    ListDivider()
                    NavRow(
                        "Delete account",
                        onClick = viewModel::openDelete,
                        icon = RcIcons.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your reel counts stay on this phone. They aren't part of your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                TextButton(onClick = { onOpenLegal(LegalPages.DELETE_ACCOUNT) }) {
                    Text("What deleting removes", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(RcSpacing.section))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (renaming) {
        RenameDialog(current = user?.name.orEmpty(), onSave = { viewModel.rename(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your counts, limits and streaks stay on this phone. Pro comes back when you sign in again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    val account = user
    if (delete.open && account != null) {
        DeleteDialog(
            account = account,
            testMode = viewModel.mode == AuthMode.TEST,
            password = delete.password,
            busy = delete.busy,
            error = delete.error,
            onPassword = viewModel::setDeletePassword,
            onConfirm = { activity?.let(viewModel::confirmDelete) },
            onDismiss = viewModel::closeDelete,
        )
    }
}

@Composable
private fun AccountHeader(account: AccountUser, testMode: Boolean) {
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(64.dp)
                .lookSurface(CircleShape, if (sticker) extended.cta else extended.cta.copy(alpha = 0.18f), thin = true),
            contentAlignment = Alignment.Center,
        ) {
            Text(account.initial, style = MaterialTheme.typography.headlineSmall, color = if (sticker) extended.onCta else extended.cta)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(account.name ?: "No name yet", style = MaterialTheme.typography.titleLarge)
            account.email?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Pill(if (account.method == SignInMethod.GOOGLE) "Signed in with Google" else "Signed in with email")
            }
        }
    }
    if (testMode) {
        Spacer(Modifier.height(12.dp))
        TestModeNote()
    }
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            RcTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = "Name",
                supporting = "Friends see this in battles.",
                imeAction = ImeAction.Done,
                onImeAction = { onSave(name) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(
    account: AccountUser,
    testMode: Boolean,
    password: String,
    busy: Boolean,
    error: String?,
    onPassword: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val needsPassword = account.method == SignInMethod.EMAIL && !testMode
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text(
                    "This removes your account, its email and name. It can't be undone. Your reel counts on this phone aren't affected. " +
                        "If you have Pro, also cancel it in Google Play so you aren't charged again.",
                )
                if (needsPassword) {
                    Spacer(Modifier.height(16.dp))
                    RcTextField(
                        value = password,
                        onValueChange = onPassword,
                        label = "Your password",
                        password = true,
                        imeAction = ImeAction.Done,
                        onImeAction = onConfirm,
                        enabled = !busy,
                    )
                } else if (account.method == SignInMethod.GOOGLE && !testMode) {
                    Spacer(Modifier.height(12.dp))
                    Text("Google will ask you to confirm it's you.", style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    FormMessage(it, isError = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy && (!needsPassword || password.isNotEmpty())) {
                Text(if (busy) "Deleting…" else "Delete account", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
