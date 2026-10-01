package com.example.nihaogo.data.user

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.nihaogo.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Asks Credential Manager for a Google ID token to hand to Firebase. */
object GoogleCredentials {
    sealed interface Result {
        data class Token(val idToken: String) : Result
        data object Cancelled : Result
        data object NoAccount : Result
    }

    /** [activity] must be an Activity context: Credential Manager shows its account picker over it. */
    suspend fun request(activity: Context): Result {
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                "Unexpected credential type ${credential.type}"
            }
            Result.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } catch (_: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (_: NoCredentialException) {
            Result.NoAccount
        }
    }
}
