package com.reelcounter.core.data.account

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.data.settings.TestAccount
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

enum class SignInMethod { GOOGLE, EMAIL }

/** The signed-in person, as much as the app needs: no more is kept or shown. */
data class AccountUser(
    val id: String,
    val name: String?,
    val email: String?,
    val method: SignInMethod,
) {
    val initial: String get() = ((name?.firstOrNull() ?: email?.firstOrNull()) ?: '?').uppercase()

    /** A name was chosen (accounts finish setup by choosing one). */
    val hasName: Boolean get() = !name.isNullOrBlank()
}

/** Which kind of sign-in this build has. */
enum class AuthMode {
    /** Firebase is configured: real accounts. */
    LIVE,

    /** Debug build without Firebase: a pretend account, clearly labelled, to try the screens. */
    TEST,

    /** Release build without Firebase: sign-in is hidden. */
    OFF,
}

sealed interface AuthOutcome {
    data class SignedIn(val user: AccountUser, val isNew: Boolean) : AuthOutcome

    data object Done : AuthOutcome

    /** Deleting needs a fresh sign-in; ask for the password or Google again, then retry. */
    data object NeedsRecentSignIn : AuthOutcome

    data class Failed(val message: String) : AuthOutcome
}

/**
 * Optional accounts: for backups and friend battles. Everything else in the app works without
 * one, and reel counts never go to the account.
 */
interface AuthRepository {
    val mode: AuthMode
    val user: StateFlow<AccountUser?>

    /** [idToken] comes from Credential Manager's "Sign in with Google". */
    suspend fun signInWithGoogle(idToken: String): AuthOutcome

    suspend fun signInWithEmail(email: String, password: String): AuthOutcome

    suspend fun createAccount(email: String, password: String): AuthOutcome

    suspend fun sendPasswordReset(email: String): AuthOutcome

    suspend fun setName(name: String): AuthOutcome

    /** Re-checks who is there before deleting (email accounts). */
    suspend fun confirmWithPassword(password: String): AuthOutcome

    /** Re-checks who is there before deleting (Google accounts). */
    suspend fun confirmWithGoogle(idToken: String): AuthOutcome

    suspend fun deleteAccount(): AuthOutcome

    suspend fun signOut()
}

/** Real accounts through Firebase Authentication (Google and email + password). */
@Singleton
class FirebaseAuthRepository @Inject constructor() : AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val current = MutableStateFlow(auth.currentUser?.toAccount())

    init {
        auth.addAuthStateListener { current.value = it.currentUser?.toAccount() }
    }

    override val mode = AuthMode.LIVE
    override val user: StateFlow<AccountUser?> = current.asStateFlow()

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome = attempt {
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        signedIn(result.user, result.additionalUserInfo?.isNewUser == true)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthOutcome = attempt {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        signedIn(result.user, isNew = false)
    }

    override suspend fun createAccount(email: String, password: String): AuthOutcome = attempt {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        // Best effort: a missed verification email must not block the new account.
        runCatching { result.user?.sendEmailVerification()?.await() }
        signedIn(result.user, isNew = true)
    }

    override suspend fun sendPasswordReset(email: String): AuthOutcome = attempt {
        auth.sendPasswordResetEmail(email.trim()).await()
        AuthOutcome.Done
    }

    override suspend fun setName(name: String): AuthOutcome = attempt {
        val firebaseUser = auth.currentUser ?: return@attempt AuthOutcome.Failed("You're signed out. Sign in again.")
        firebaseUser.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        firebaseUser.reload().await()
        current.value = auth.currentUser?.toAccount()
        AuthOutcome.Done
    }

    override suspend fun confirmWithPassword(password: String): AuthOutcome = attempt {
        val firebaseUser = auth.currentUser ?: return@attempt AuthOutcome.Failed("You're signed out. Sign in again.")
        val email = firebaseUser.email ?: return@attempt AuthOutcome.Failed("This account has no email address.")
        firebaseUser.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        AuthOutcome.Done
    }

    override suspend fun confirmWithGoogle(idToken: String): AuthOutcome = attempt {
        val firebaseUser = auth.currentUser ?: return@attempt AuthOutcome.Failed("You're signed out. Sign in again.")
        firebaseUser.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        AuthOutcome.Done
    }

    override suspend fun deleteAccount(): AuthOutcome = attempt {
        val firebaseUser = auth.currentUser ?: return@attempt AuthOutcome.Done
        firebaseUser.delete().await()
        AuthOutcome.Done
    }

    override suspend fun signOut() = auth.signOut()

    private fun signedIn(firebaseUser: FirebaseUser?, isNew: Boolean): AuthOutcome {
        val account = firebaseUser?.toAccount() ?: return AuthOutcome.Failed("Sign-in didn't finish. Please try again.")
        current.value = account
        return AuthOutcome.SignedIn(account, isNew)
    }

    private suspend fun attempt(block: suspend () -> AuthOutcome): AuthOutcome = try {
        block()
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        AuthOutcome.NeedsRecentSignIn
    } catch (e: FirebaseAuthUserCollisionException) {
        AuthOutcome.Failed("There's already an account with this email. Sign in instead.")
    } catch (e: FirebaseAuthWeakPasswordException) {
        AuthOutcome.Failed("Use a longer password: at least 8 characters.")
    } catch (e: FirebaseAuthInvalidUserException) {
        AuthOutcome.Failed("No account uses this email. Check it, or create an account.")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        AuthOutcome.Failed("That email and password don't match.")
    } catch (e: FirebaseNetworkException) {
        AuthOutcome.Failed("No internet connection. Check it and try again.")
    } catch (e: FirebaseTooManyRequestsException) {
        AuthOutcome.Failed("Too many tries. Wait a few minutes, then try again.")
    } catch (e: FirebaseAuthException) {
        AuthOutcome.Failed("Sign-in didn't work (${e.errorCode}). Please try again.")
    }

    private fun FirebaseUser.toAccount() = AccountUser(
        id = uid,
        name = displayName,
        email = email,
        method = if (providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) SignInMethod.GOOGLE else SignInMethod.EMAIL,
    )
}

/**
 * Debug builds without Firebase: a pretend account stored on the phone, so every account screen
 * can be tried. The screens label it as test mode. Nothing is sent anywhere.
 */
@Singleton
class TestAuthRepository @Inject constructor(
    private val settings: SettingsRepository,
) : AuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val mode = AuthMode.TEST
    override val user: StateFlow<AccountUser?> = settings.settings
        .map { it.testAccount?.toAccount() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome {
        delay(FAKE_NETWORK_MS)
        val existing = settings.settings.first().testAccount
        if (existing != null && existing.viaGoogle) return AuthOutcome.SignedIn(existing.toAccount(), isNew = false)
        val account = TestAccount(id = "test-google", name = null, email = "test.user@example.com", viaGoogle = true)
        settings.setTestAccount(account)
        return AuthOutcome.SignedIn(account.toAccount(), isNew = true)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthOutcome {
        delay(FAKE_NETWORK_MS)
        if (password.isEmpty()) return AuthOutcome.Failed("That email and password don't match.")
        val name = email.substringBefore('@').replaceFirstChar { it.uppercase() }
        val account = TestAccount(id = "test-email", name = name, email = email.trim(), viaGoogle = false)
        settings.setTestAccount(account)
        return AuthOutcome.SignedIn(account.toAccount(), isNew = false)
    }

    override suspend fun createAccount(email: String, password: String): AuthOutcome {
        delay(FAKE_NETWORK_MS)
        if (password.length < MIN_PASSWORD) return AuthOutcome.Failed("Use a longer password: at least 8 characters.")
        val account = TestAccount(id = "test-email", name = null, email = email.trim(), viaGoogle = false)
        settings.setTestAccount(account)
        return AuthOutcome.SignedIn(account.toAccount(), isNew = true)
    }

    override suspend fun sendPasswordReset(email: String): AuthOutcome {
        delay(FAKE_NETWORK_MS)
        return AuthOutcome.Done
    }

    override suspend fun setName(name: String): AuthOutcome {
        val account = settings.settings.first().testAccount ?: return AuthOutcome.Failed("You're signed out. Sign in again.")
        settings.setTestAccount(account.copy(name = name.trim()))
        return AuthOutcome.Done
    }

    override suspend fun confirmWithPassword(password: String): AuthOutcome =
        if (password.length >= MIN_PASSWORD) AuthOutcome.Done else AuthOutcome.Failed("That password isn't right.")

    override suspend fun confirmWithGoogle(idToken: String): AuthOutcome = AuthOutcome.Done

    override suspend fun deleteAccount(): AuthOutcome {
        delay(FAKE_NETWORK_MS)
        settings.setTestAccount(null)
        return AuthOutcome.Done
    }

    override suspend fun signOut() = settings.setTestAccount(null)

    private fun TestAccount.toAccount() =
        AccountUser(id = id, name = name, email = email, method = if (viaGoogle) SignInMethod.GOOGLE else SignInMethod.EMAIL)

    private companion object {
        const val FAKE_NETWORK_MS = 500L
        const val MIN_PASSWORD = 8
    }
}

/** Release builds without Firebase: no accounts. The UI hides sign-in in this mode. */
@Singleton
class DisabledAuthRepository @Inject constructor() : AuthRepository {
    private val unavailable = AuthOutcome.Failed("Sign-in isn't available in this version yet.")

    override val mode = AuthMode.OFF
    override val user: StateFlow<AccountUser?> = MutableStateFlow(null)

    override suspend fun signInWithGoogle(idToken: String) = unavailable

    override suspend fun signInWithEmail(email: String, password: String) = unavailable

    override suspend fun createAccount(email: String, password: String) = unavailable

    override suspend fun sendPasswordReset(email: String) = unavailable

    override suspend fun setName(name: String) = unavailable

    override suspend fun confirmWithPassword(password: String) = unavailable

    override suspend fun confirmWithGoogle(idToken: String) = unavailable

    override suspend fun deleteAccount() = unavailable

    override suspend fun signOut() = Unit
}
