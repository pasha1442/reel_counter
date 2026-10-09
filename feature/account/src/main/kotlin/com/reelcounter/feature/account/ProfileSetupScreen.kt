package com.reelcounter.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.data.account.AuthMode
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.components.FormMessage
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcTextField
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.theme.RcSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Finishing a new account: the name friends will see, and a date of birth to check the person is
 * 18 or over. The date is used once and never saved. Under 18, the new account is removed.
 */
@Composable
fun ProfileSetupRoute(
    onDone: () -> Unit,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }

    if (state.underage) {
        UnderageMessage(onDone)
        return
    }

    var picking by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    BackHandler { leaving = true }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        RcTopBar(title = "Finish your account", onBack = { leaving = true })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RcSpacing.gutter),
        ) {
            if (viewModel.mode == AuthMode.TEST) {
                TestModeNote()
                Spacer(Modifier.height(20.dp))
            }
            Text("What should friends see?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            RcTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = "Your name",
                leadingIcon = RcIcons.Person,
                error = state.nameError,
                supporting = "Shown in friend battles. You can change it later.",
                imeAction = ImeAction.Done,
                enabled = !state.busy,
            )
            Spacer(Modifier.height(24.dp))
            Text("Your date of birth", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Box {
                RcTextField(
                    value = state.birthDate?.format(DateTimeFormatter.ofPattern("d MMMM yyyy")).orEmpty(),
                    onValueChange = {},
                    label = "Date of birth",
                    leadingIcon = RcIcons.Cake,
                    error = state.birthError,
                    supporting = "Only to check you're 18 or over. It isn't saved.",
                    readOnly = true,
                    enabled = !state.busy,
                )
                // The field is read-only: a tap anywhere on it opens the date picker.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Choose your date of birth",
                        ) { picking = true },
                )
            }
            Spacer(Modifier.height(24.dp))
            state.error?.let {
                FormMessage(it, isError = true)
                Spacer(Modifier.height(12.dp))
            }
            RcButton(text = "Continue", onClick = viewModel::submit, loading = state.busy, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Leave without finishing?") },
            text = { Text("Your account needs a name and an age check before it can be used. If you leave now, you'll be signed out.") },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    viewModel.abandon()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep going") } },
        )
    }

    if (picking) {
        BirthDateDialog(
            initial = state.birthDate,
            onPick = {
                viewModel.setBirthDate(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        // Opens on a year a little in the past rather than today, so fewer taps for most people.
        initialDisplayedMonthMillis = (initial ?: today.minusYears(20)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = (today.year - 100)..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState, showModeToggle = true)
    }
}

@Composable
private fun UnderageMessage(onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = RcSpacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Brain(rot = 0f, modifier = Modifier.size(132.dp), contentDescription = null)
        Spacer(Modifier.height(20.dp))
        Text("Accounts are for 18 and over", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "We've removed the account we just made, and kept nothing from it. Everything else in Reel Counter still works without an account: counting, limits, streaks and your brain.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        RcButton(text = "Back to the app", onClick = onDone, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}
