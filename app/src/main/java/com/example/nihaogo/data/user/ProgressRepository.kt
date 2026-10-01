package com.example.nihaogo.data.user

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Where player progress lives. The local store is the source of truth; [ProgressSync]
 * mirrors it to Firestore.
 */
interface ProgressRepository {
    val progress: Flow<UserProgress>

    /** Atomically transforms the stored progress and returns the new value. */
    suspend fun update(transform: (UserProgress) -> UserProgress): UserProgress
}

private val Context.progressStore by preferencesDataStore(name = "progress")

class LocalProgressRepository(context: Context) : ProgressRepository {
    private val store = context.applicationContext.progressStore
    private val key = stringPreferencesKey("progress_json")
    private val json = Json { ignoreUnknownKeys = true }

    override val progress: Flow<UserProgress> = store.data.map { prefs -> decode(prefs[key]) }

    override suspend fun update(transform: (UserProgress) -> UserProgress): UserProgress {
        var result = UserProgress()
        store.edit { prefs ->
            result = transform(decode(prefs[key]))
            prefs[key] = json.encodeToString(UserProgress.serializer(), result)
        }
        return result
    }

    private fun decode(raw: String?): UserProgress =
        raw?.let { runCatching { json.decodeFromString(UserProgress.serializer(), it) }.getOrNull() }
            ?: UserProgress()
}
