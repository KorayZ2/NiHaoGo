package com.example.nihaogo.data.user

import android.util.Log
import com.example.nihaogo.data.content.Step
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Mirrors local progress to Firestore at `users/{uid}`. Local storage stays the source of truth so
 * the game works offline; Firestore queues writes on the device until it is back online.
 *
 * On first sign-in, progress already played on this device is uploaded. If this device has no
 * progress yet but the account does (e.g. signing in on a new phone), the account's progress is
 * pulled down instead. Syncing follows the signed-in user and can be paused while switching accounts.
 */
class ProgressSync(
    private val local: ProgressRepository,
    private val auth: AuthRepository,
    private val db: FirebaseFirestore = Firebase.firestore,
) {
    private val paused = MutableStateFlow(false)

    fun pause() {
        paused.value = true
    }

    fun resume() {
        paused.value = false
    }

    fun start(scope: CoroutineScope) {
        // Keeps a user signed in: at first launch, and again as a new guest after signing out.
        scope.launch {
            auth.account.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                if (uid == null) retrying { auth.ensureSignedIn() }
            }
        }
        scope.launch {
            combine(auth.account, paused) { account, isPaused -> account?.uid?.takeUnless { isPaused } }
                .distinctUntilChanged()
                .collectLatest { uid -> if (uid != null) mirror(userDoc(uid)) }
        }
    }

    /** Reads an account's saved progress, or null if it has none yet. */
    suspend fun fetch(uid: String): UserProgress? {
        val snapshot = userDoc(uid).get().await()
        return if (snapshot.exists()) decode(snapshot.data.orEmpty()) else null
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private suspend fun mirror(doc: DocumentReference) {
        retrying { reconcile(doc) }
        local.progress.map(::encode).distinctUntilChanged().collect { fields ->
            // Not awaited: the task only completes once the server acks, which never happens offline.
            // mergeFields replaces each progress field whole but keeps createdAt.
            doc.set(fields + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.mergeFields(SYNCED_FIELDS))
                .addOnFailureListener { Log.w(TAG, "Upload failed", it) }
        }
    }

    private suspend fun reconcile(doc: DocumentReference) {
        val snapshot = doc.get().await()
        if (!snapshot.exists()) {
            doc.set(mapOf("createdAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            return
        }
        if (local.progress.first().isFresh) {
            val remote = decode(snapshot.data.orEmpty())
            local.update { remote.copy(devUnlockAll = it.devUnlockAll) }
        }
    }

    /** Retries with backoff (5 s → 5 min) until [block] succeeds. */
    private suspend fun <T> retrying(block: suspend () -> T): T {
        var wait = 5_000L
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sync not ready, retrying in ${wait / 1000}s", e)
                delay(wait)
                wait = (wait * 2).coerceAtMost(300_000L)
            }
        }
    }

    private fun encode(p: UserProgress): Map<String, Any> = mapOf(
        "coins" to p.coins,
        "premium" to p.premium,
        "progress" to p.categories.mapValues { (_, c) -> c.stars.mapKeys { it.key.name.lowercase() } },
        "unlocked" to p.unlocked.sorted(),
        "special" to p.special,
    )

    private fun decode(data: Map<String, Any?>): UserProgress = UserProgress(
        coins = data["coins"].asInt(),
        premium = data["premium"] as? Boolean ?: false,
        categories = data["progress"].asMap().entries.associate { (id, steps) ->
            id.toString() to CategoryProgress(
                stars = steps.asMap().entries.mapNotNull { (name, stars) ->
                    Step.entries.firstOrNull { it.name.lowercase() == name }?.let { it to stars.asInt() }
                }.toMap()
            )
        },
        unlocked = (data["unlocked"] as? List<*>).orEmpty().map { it.toString() }.toSet(),
        special = data["special"].asMap().entries.associate { (key, stars) -> key.toString() to stars.asInt() },
    )

    private fun Any?.asMap(): Map<*, *> = this as? Map<*, *> ?: emptyMap<String, Any>()

    private fun Any?.asInt(): Int = (this as? Number)?.toInt() ?: 0

    private companion object {
        const val TAG = "ProgressSync"
        val SYNCED_FIELDS = listOf("coins", "premium", "progress", "unlocked", "special", "updatedAt")
    }
}
