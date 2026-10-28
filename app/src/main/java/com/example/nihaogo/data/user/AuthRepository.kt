package com.example.nihaogo.data.user

import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Who is playing: an anonymous guest, or a guest that has been linked to a Google or email account. */
data class Account(
    val uid: String,
    val isGuest: Boolean,
    val name: String?,
    val email: String?,
    val photoUrl: String?,
    /** Signed in with Google rather than an email and password. */
    val isGoogle: Boolean = false,
)

/** Firebase sign-in. Players start anonymous; signing in links onto the same uid when it can. */
class AuthRepository(private val auth: FirebaseAuth = Firebase.auth) {
    private val _account = MutableStateFlow(auth.currentUser?.toAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        auth.addAuthStateListener { refresh() }
    }

    /** Returns the current user's id, signing in anonymously the first time (needs network once). */
    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid

    sealed interface SignInOutcome {
        /** The guest was upgraded in place; its uid and data are unchanged. */
        data object Linked : SignInOutcome

        /** The account already belonged to another user, who is now signed in instead. */
        data class Switched(val uid: String) : SignInOutcome
    }

    suspend fun signInWithGoogle(idToken: String): SignInOutcome =
        linkOrSwitch(GoogleAuthProvider.getCredential(idToken, null))

    /**
     * Signs in to an existing email account. This never links: linking an unknown email would quietly
     * create an account, which is [createEmailAccount]'s job.
     */
    suspend fun signInWithEmail(email: String, password: String): SignInOutcome =
        switchTo(EmailAuthProvider.getCredential(email, password))

    /**
     * Creates an email account, keeping a guest's uid and progress. Throws
     * [FirebaseAuthUserCollisionException] when the email already has an account.
     */
    suspend fun createEmailAccount(email: String, password: String): SignInOutcome {
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            current.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await()
            current.reload().await()
            refresh() // Linking doesn't fire the auth-state listener.
            return SignInOutcome.Linked
        }
        return SignInOutcome.Switched(checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user).uid)
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    private suspend fun linkOrSwitch(credential: AuthCredential): SignInOutcome {
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(credential).await()
                current.reload().await()
                refresh() // Linking doesn't fire the auth-state listener.
                return SignInOutcome.Linked
            } catch (e: FirebaseAuthUserCollisionException) {
                return switchTo(e.updatedCredential ?: credential)
            }
        }
        return switchTo(credential)
    }

    private suspend fun switchTo(credential: AuthCredential): SignInOutcome =
        SignInOutcome.Switched(checkNotNull(auth.signInWithCredential(credential).await().user).uid)

    fun signOut() = auth.signOut()

    private fun refresh() {
        _account.value = auth.currentUser?.toAccount()
    }

    private fun FirebaseUser.toAccount(): Account {
        // Linking a guest doesn't always copy the Google profile onto the user itself.
        val google = providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        val password = providerData.firstOrNull { it.providerId == EmailAuthProvider.PROVIDER_ID }
        val email = email?.takeIf { it.isNotBlank() } ?: google?.email ?: password?.email
        return Account(
            uid = uid,
            isGuest = isAnonymous && google == null && password == null,
            name = displayName?.takeIf { it.isNotBlank() } ?: google?.displayName ?: email?.substringBefore('@'),
            email = email,
            photoUrl = (photoUrl ?: google?.photoUrl)?.toString(),
            isGoogle = google != null,
        )
    }
}
