package com.reelcounter.feature.account

import android.app.Activity
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.data.account.AccountUser
import com.reelcounter.core.data.account.AuthMode
import com.reelcounter.core.data.account.AuthOutcome
import com.reelcounter.core.data.account.AuthRepository
import com.reelcounter.core.data.account.SignInMethod
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.data.telemetry.Events
import com.reelcounter.core.data.telemetry.Telemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a finished sign-in should go next. */
sealed interface AuthNav {
    /** [needsSetup]: new account, or one without a name yet; finish it on the profile screen. */
    data class SignedIn(val needsSetup: Boolean) : AuthNav
}

/** Setup is done once the account has a name and passed the 18+ check on this phone. */
private suspend fun AuthOutcome.SignedIn.needsSetup(settings: SettingsRepository) =
    isNew || !user.hasName || user.id !in settings.settings.first().ageConfirmedAccounts

/** "Continue with Google" / "Continue with email". */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val telemetry: Telemetry,
) : ViewModel() {
    val mode: AuthMode = auth.mode

    private val busyState = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = busyState.asStateFlow()

    private val errorState = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = errorState.asStateFlow()

    private val navChannel = Channel<AuthNav>(Channel.BUFFERED)
    val nav: Flow<AuthNav> = navChannel.receiveAsFlow()

    fun continueWithGoogle(activity: Activity) {
        if (busyState.value) return
        viewModelScope.launch {
            busyState.value = true
            errorState.value = null
            val token = if (mode == AuthMode.TEST) {
                "test-mode"
            } else {
                when (val result = GoogleSignIn.requestIdToken(activity)) {
                    is GoogleToken.Ok -> result.idToken
                    GoogleToken.Cancelled -> null
                    is GoogleToken.Failed -> {
                        errorState.value = result.message
                        null
                    }
                }
            }
            if (token != null) {
                when (val outcome = auth.signInWithGoogle(token)) {
                    is AuthOutcome.SignedIn -> {
                        telemetry.event(if (outcome.isNew) Events.SIGN_UP else Events.SIGN_IN, mapOf("method" to "google"))
                        navChannel.send(AuthNav.SignedIn(outcome.needsSetup(settings)))
                    }
                    is AuthOutcome.Failed -> errorState.value = outcome.message
                    else -> Unit
                }
            }
            busyState.value = false
        }
    }
}

data class EmailFormState(
    val creating: Boolean,
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val error: String? = null,
    val notice: String? = null,
)

/** Email and password: sign in, create an account, or get a reset link. */
@HiltViewModel
class EmailAuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val telemetry: Telemetry,
) : ViewModel() {
    val mode: AuthMode = auth.mode

    private val form = MutableStateFlow(EmailFormState(creating = false))
    val state: StateFlow<EmailFormState> = form.asStateFlow()

    private val navChannel = Channel<AuthNav>(Channel.BUFFERED)
    val nav: Flow<AuthNav> = navChannel.receiveAsFlow()

    fun start(creating: Boolean) {
        if (form.value.email.isEmpty() && form.value.password.isEmpty()) form.update { it.copy(creating = creating) }
    }

    fun setCreating(creating: Boolean) = form.update { it.copy(creating = creating, error = null, notice = null, passwordError = null) }

    fun setEmail(value: String) = form.update { it.copy(email = value, emailError = null, error = null) }

    fun setPassword(value: String) = form.update { it.copy(password = value, passwordError = null, error = null) }

    fun submit() {
        val current = form.value
        if (current.busy) return
        val emailError = if (Patterns.EMAIL_ADDRESS.matcher(current.email.trim()).matches()) null else "Enter a valid email address."
        val passwordError = when {
            current.password.isEmpty() && current.creating -> "Choose a password: at least $MIN_PASSWORD characters."
            current.password.isEmpty() -> "Enter your password."
            current.creating && current.password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters."
            else -> null
        }
        if (emailError != null || passwordError != null) {
            form.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            form.update { it.copy(busy = true, error = null, notice = null) }
            val outcome = if (current.creating) {
                auth.createAccount(current.email, current.password)
            } else {
                auth.signInWithEmail(current.email, current.password)
            }
            when (outcome) {
                is AuthOutcome.SignedIn -> {
                    telemetry.event(if (outcome.isNew) Events.SIGN_UP else Events.SIGN_IN, mapOf("method" to "email"))
                    navChannel.send(AuthNav.SignedIn(outcome.needsSetup(settings)))
                }
                is AuthOutcome.Failed -> form.update { it.copy(error = outcome.message) }
                else -> Unit
            }
            form.update { it.copy(busy = false) }
        }
    }

    fun sendReset() {
        val email = form.value.email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            form.update { it.copy(emailError = "Enter your email address first, then tap “Forgot password?” again.") }
            return
        }
        viewModelScope.launch {
            form.update { it.copy(busy = true, error = null, notice = null) }
            when (val outcome = auth.sendPasswordReset(email)) {
                is AuthOutcome.Failed -> form.update { it.copy(error = outcome.message) }
                else -> form.update { it.copy(notice = "If an account uses $email, a reset link is on its way. Check your inbox.") }
            }
            form.update { it.copy(busy = false) }
        }
    }

    private companion object {
        const val MIN_PASSWORD = 8
    }
}

data class ProfileSetupState(
    val name: String = "",
    val birthDate: LocalDate? = null,
    val busy: Boolean = false,
    val nameError: String? = null,
    val birthError: String? = null,
    val error: String? = null,
    /** Under 18: the new account was removed and the screen explains why. */
    val underage: Boolean = false,
)

/** Finishing a new account: a name friends will see, and an age check (the date isn't saved). */
@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val mode: AuthMode = auth.mode

    private val form = MutableStateFlow(ProfileSetupState(name = auth.user.value?.name.orEmpty()))
    val state: StateFlow<ProfileSetupState> = form.asStateFlow()

    private val doneChannel = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    fun setName(value: String) = form.update { it.copy(name = value.take(MAX_NAME), nameError = null) }

    fun setBirthDate(value: LocalDate) = form.update { it.copy(birthDate = value, birthError = null) }

    fun submit() {
        val current = form.value
        if (current.busy) return
        val name = current.name.trim()
        val nameError = if (name.isEmpty()) "Enter a name." else null
        val birthError = if (current.birthDate == null) "Choose your date of birth." else null
        if (nameError != null || birthError != null) {
            form.update { it.copy(nameError = nameError, birthError = birthError) }
            return
        }
        viewModelScope.launch {
            form.update { it.copy(busy = true, error = null) }
            val age = Period.between(current.birthDate, LocalDate.now()).years
            if (age < ADULT_AGE) {
                // Remove the account just made: accounts are 18+ for now. Nothing about it is kept.
                auth.deleteAccount()
                auth.signOut()
                GoogleSignIn.clear(context)
                form.update { it.copy(busy = false, underage = true) }
                return@launch
            }
            when (val outcome = auth.setName(name)) {
                is AuthOutcome.Failed -> form.update { it.copy(busy = false, error = outcome.message) }
                else -> {
                    auth.user.value?.id?.let { settings.confirmAge(it) }
                    doneChannel.send(Unit)
                }
            }
        }
    }

    /** Leaving before setup is finished signs out: an account can't be used without the age check. */
    fun abandon() {
        viewModelScope.launch {
            auth.signOut()
            GoogleSignIn.clear(context)
            doneChannel.send(Unit)
        }
    }

    private companion object {
        const val ADULT_AGE = 18
        const val MAX_NAME = 30
    }
}

data class DeleteState(
    val open: Boolean = false,
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/** The Account screen: name, sign out, delete. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val entitlements: EntitlementRepository,
    private val settings: SettingsRepository,
    private val telemetry: Telemetry,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val mode: AuthMode = auth.mode
    val user: StateFlow<AccountUser?> = auth.user

    /** The account has a name and passed the 18+ check on this phone. */
    val setupComplete: StateFlow<Boolean> = combine(auth.user, settings.settings) { user, s ->
        user != null && user.hasName && user.id in s.ageConfirmedAccounts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val isPro: StateFlow<Boolean> = entitlements.isPro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val deleteForm = MutableStateFlow(DeleteState())
    val delete: StateFlow<DeleteState> = deleteForm.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    /** Sign-out or deletion finished: leave the screen. */
    private val leaveChannel = Channel<String>(Channel.BUFFERED)
    val leave: Flow<String> = leaveChannel.receiveAsFlow()

    fun rename(name: String) {
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val outcome = auth.setName(trimmed)) {
                is AuthOutcome.Failed -> messageChannel.send(outcome.message)
                else -> messageChannel.send("Name updated")
            }
        }
    }

    fun sendPasswordReset() {
        val email = user.value?.email ?: return
        viewModelScope.launch {
            when (val outcome = auth.sendPasswordReset(email)) {
                is AuthOutcome.Failed -> messageChannel.send(outcome.message)
                else -> messageChannel.send("We sent a link to $email to set a new password.")
            }
        }
    }

    fun openManageSubscription(activityContext: Context) = entitlements.openManageSubscription(activityContext)

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            GoogleSignIn.clear(context)
            telemetry.event(Events.SIGN_OUT)
            leaveChannel.send("Signed out. Your counts are still on this phone.")
        }
    }

    fun openDelete() = deleteForm.update { DeleteState(open = true) }

    fun closeDelete() = deleteForm.update { DeleteState() }

    fun setDeletePassword(value: String) = deleteForm.update { it.copy(password = value, error = null) }

    fun confirmDelete(activity: Activity) {
        val account = user.value ?: return
        if (deleteForm.value.busy) return
        viewModelScope.launch {
            deleteForm.update { it.copy(busy = true, error = null) }
            // Deleting needs a fresh check of who is there: the password, or Google again.
            val confirmed = when {
                mode == AuthMode.TEST -> AuthOutcome.Done
                account.method == SignInMethod.EMAIL -> auth.confirmWithPassword(deleteForm.value.password)
                else -> when (val token = GoogleSignIn.requestIdToken(activity)) {
                    is GoogleToken.Ok -> auth.confirmWithGoogle(token.idToken)
                    GoogleToken.Cancelled -> null
                    is GoogleToken.Failed -> AuthOutcome.Failed(token.message)
                }
            }
            when (confirmed) {
                null -> deleteForm.update { it.copy(busy = false) }
                is AuthOutcome.Failed -> deleteForm.update { it.copy(busy = false, error = confirmed.message) }
                else -> when (val deleted = auth.deleteAccount()) {
                    is AuthOutcome.Failed -> deleteForm.update { it.copy(busy = false, error = deleted.message) }
                    AuthOutcome.NeedsRecentSignIn -> deleteForm.update {
                        it.copy(busy = false, error = "For your safety, sign out and sign in again, then delete.")
                    }
                    else -> {
                        settings.forgetAccount(account.id)
                        auth.signOut()
                        GoogleSignIn.clear(context)
                        telemetry.event(Events.ACCOUNT_DELETED)
                        deleteForm.update { DeleteState() }
                        leaveChannel.send("Your account was deleted.")
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_NAME = 30
    }
}
