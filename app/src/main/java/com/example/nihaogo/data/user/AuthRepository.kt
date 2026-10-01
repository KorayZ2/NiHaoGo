package com.example.nihaogo.data.user

import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Who is playing: an anonymous guest, or a guest that has been linked to a Google account. */
data class Account(
    val uid: String,
    val isGuest: Boolean,
    val name: String?,
    val email: String?,
    val photoUrl: String?,
)

/** Firebase sign-in. Players start anonymous; Google sign-in links onto the same uid when it can. */
class AuthRepository(private val auth: FirebaseAuth = Firebase.auth) {
    private val _account = MutableStateFlow(auth.currentUser?.toAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        auth.addAuthStateListener { refresh() }
    }

    /** Returns the current user's id, signing in anonymously the first time (needs network once). */
    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid

    sealed interface GoogleResult {
        /** The guest was upgraded in place; its uid and data are unchanged. */
        data object Linked : GoogleResult

        /** The Google account already belonged to another user, who is now signed in instead. */
        data class Switched(val uid: String) : GoogleResult
    }

    suspend fun signInWithGoogle(idToken: String): GoogleResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(credential).await()
                current.reload().await()
                refresh() // Linking doesn't fire the auth-state listener.
                return GoogleResult.Linked
            } catch (e: FirebaseAuthUserCollisionException) {
                return switchTo(e.updatedCredential ?: credential)
            }
        }
        return switchTo(credential)
    }

    private suspend fun switchTo(credential: AuthCredential): GoogleResult =
        GoogleResult.Switched(checkNotNull(auth.signInWithCredential(credential).await().user).uid)

    fun signOut() = auth.signOut()

    private fun refresh() {
        _account.value = auth.currentUser?.toAccount()
    }

    private fun FirebaseUser.toAccount(): Account {
        // Linking a guest doesn't always copy the Google profile onto the user itself.
        val google = providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        return Account(
            uid = uid,
            isGuest = isAnonymous && google == null,
            name = displayName?.takeIf { it.isNotBlank() } ?: google?.displayName,
            email = email?.takeIf { it.isNotBlank() } ?: google?.email,
            photoUrl = (photoUrl ?: google?.photoUrl)?.toString(),
        )
    }
}
