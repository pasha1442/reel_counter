package com.reelcounter.feature.account

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

internal sealed interface GoogleToken {
    data class Ok(val idToken: String) : GoogleToken

    data object Cancelled : GoogleToken

    data class Failed(val message: String) : GoogleToken
}

/** Google's "Sign in with Google" sheet, through Android's Credential Manager. */
internal object GoogleSignIn {
    /** The Firebase project's web client ID; generated from google-services.json, absent without it. */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    suspend fun requestIdToken(activity: Activity): GoogleToken {
        val clientId = webClientId(activity) ?: return GoogleToken.Failed("Google sign-in isn't set up in this version yet. Use email instead.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleToken.Ok(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleToken.Failed("Google didn't return an account. Please try again.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleToken.Cancelled
        } catch (e: NoCredentialException) {
            GoogleToken.Failed("There's no Google account on this phone. Add one in Settings, or use email.")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleToken.Failed("Google sign-in didn't work. Please try again.")
        } catch (e: GetCredentialException) {
            GoogleToken.Failed("Google sign-in didn't work. Please try again.")
        }
    }

    /** Forgets the chosen Google account, so the next sign-in asks again. */
    suspend fun clear(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}

/** Google's four-colour "G", drawn from the official sign-in button artwork. */
private val GoogleG: ImageVector by lazy {
    fun path(d: String) = PathParser().parsePathString(d).toNodes()
    ImageVector.Builder(name = "GoogleG", defaultWidth = 48.dp, defaultHeight = 48.dp, viewportWidth = 48f, viewportHeight = 48f)
        .addPath(
            path("M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"),
            fill = SolidColor(Color(0xFFEA4335)),
        )
        .addPath(
            path("M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"),
            fill = SolidColor(Color(0xFF4285F4)),
        )
        .addPath(
            path("M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"),
            fill = SolidColor(Color(0xFFFBBC05)),
        )
        .addPath(
            path("M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"),
            fill = SolidColor(Color(0xFF34A853)),
        )
        .build()
}

@Composable
internal fun GoogleLogo(modifier: Modifier = Modifier) {
    Icon(GoogleG, contentDescription = null, tint = Color.Unspecified, modifier = modifier.size(20.dp))
}
