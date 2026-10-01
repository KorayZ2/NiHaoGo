package com.example.nihaogo.data.user

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Google sign-in and sign-out on top of [AuthRepository], keeping local progress and Firestore in step.
 *
 * When the Google account already has progress from another device and this device has guest
 * progress too, the player picks which one to keep ([SignInResult.NeedsChoice] → [resolveConflict]).
 * Syncing stays paused until then so neither side is overwritten early.
 */
class AccountManager(
    private val auth: AuthRepository,
    private val local: ProgressRepository,
    private val sync: ProgressSync,
) {
    val account: StateFlow<Account?> get() = auth.account

    sealed interface SignInResult {
        data object Done : SignInResult

        data class NeedsChoice(val device: UserProgress, val account: UserProgress) : SignInResult {
            /** Positive when the account has more progress than this device (more stars, then more coins). */
            private val lead: Int get() = compareValuesBy(account, device, { it.totalStars }, { it.coins })
            val accountIsAhead: Boolean get() = lead > 0
            val deviceIsAhead: Boolean get() = lead < 0
        }
    }

    suspend fun signInWithGoogle(idToken: String): SignInResult {
        sync.pause()
        val result = try {
            decide(auth.signInWithGoogle(idToken))
        } catch (e: Exception) {
            sync.resume()
            throw e
        }
        if (result is SignInResult.Done) sync.resume() // NeedsChoice stays paused until resolveConflict
        return result
    }

    private suspend fun decide(result: AuthRepository.GoogleResult): SignInResult {
        if (result !is AuthRepository.GoogleResult.Switched) return SignInResult.Done

        val remote = try {
            sync.fetch(result.uid)
        } catch (e: Exception) {
            // Without the account's progress we can't compare, and syncing now would overwrite it.
            // Fall back to a new guest (keeping this device's progress) so the player can retry.
            auth.signOut()
            throw e
        }
        val device = local.progress.first()
        return when {
            remote == null || remote.isFresh -> SignInResult.Done // this device's progress gets uploaded
            device.isFresh -> {
                local.update { remote.copy(devUnlockAll = it.devUnlockAll) }
                SignInResult.Done
            }
            else -> SignInResult.NeedsChoice(device, remote)
        }
    }

    /** Keeps [chosen] (one side of a [SignInResult.NeedsChoice]) and uploads it to the account. */
    suspend fun resolveConflict(chosen: UserProgress) {
        local.update { chosen.copy(devUnlockAll = it.devUnlockAll) }
        sync.resume()
    }

    /** Signs out of Google; this device starts over as a new guest. The account keeps its progress. */
    suspend fun signOut() {
        sync.pause()
        local.update { UserProgress(devUnlockAll = it.devUnlockAll) }
        auth.signOut()
        sync.resume()
    }
}
