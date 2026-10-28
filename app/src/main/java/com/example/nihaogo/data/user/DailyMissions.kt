package com.example.nihaogo.data.user

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Today's tasks on the missions page; each pays coins once it is done and claimed. */
enum class Mission(val titleTh: String, val target: Int, val reward: Int) {
    Lesson("เรียนบทเรียน 1 บท", 1, 10),
    Words("ฝึกคำศัพท์ 10 คำ", 10, 10),
    ListenSpeak("ฟัง-พูด 5 คำ", 5, 10),
    Exercise("ทำแบบฝึกหัดให้ครบ", 1, 20),
}

/** Missions counted for one day. */
data class MissionDay(
    /** Local date as yyyy-MM-dd (see [GameRules.today]). */
    val day: String,
    val counts: Map<Mission, Int> = emptyMap(),
    val claimed: Set<Mission> = emptySet(),
    val chestOpened: Boolean = false,
) {
    fun count(mission: Mission): Int = (counts[mission] ?: 0).coerceAtMost(mission.target)

    fun done(mission: Mission): Boolean = count(mission) >= mission.target

    /** Done but its coins not yet taken. */
    fun claimable(mission: Mission): Boolean = done(mission) && mission !in claimed

    val chestReady: Boolean get() = claimed.size == Mission.entries.size && !chestOpened
}

/**
 * Today's missions, kept on this device only (like the sound settings): they reset each day, so
 * syncing them would only add to the Firestore document. The coins they pay go into [progress].
 */
class DailyMissions(context: Context, private val progress: ProgressRepository) {
    private val prefs = context.applicationContext.getSharedPreferences("missions", Context.MODE_PRIVATE)
    private val _today = MutableStateFlow(load())

    /** Read through [current] so a day that ends while the app is open starts over. */
    val today: StateFlow<MissionDay> = _today.asStateFlow()

    fun current(): MissionDay {
        if (_today.value.day != GameRules.today()) _today.value = load()
        return _today.value
    }

    /** Counts [amount] more towards [mission]. */
    fun add(mission: Mission, amount: Int = 1) {
        val day = current()
        if (day.done(mission)) return
        save(day.copy(counts = day.counts + (mission to day.count(mission) + amount)))
    }

    /** Pays a finished mission's coins; returns what was paid (0 if it could not be claimed). */
    suspend fun claim(mission: Mission): Int {
        val day = current()
        if (!day.claimable(mission)) return 0
        save(day.copy(claimed = day.claimed + mission))
        progress.update { it.copy(coins = it.coins + mission.reward) }
        return mission.reward
    }

    /** Opens the chest once every mission is claimed; returns the coins it held (0 if not ready). */
    suspend fun openChest(): Int {
        val day = current()
        if (!day.chestReady) return 0
        save(day.copy(chestOpened = true))
        progress.update { it.copy(coins = it.coins + CHEST_REWARD) }
        return CHEST_REWARD
    }

    private fun load(): MissionDay {
        val today = GameRules.today()
        if (prefs.getString(KEY_DAY, null) != today) return MissionDay(today)
        return MissionDay(
            day = today,
            counts = Mission.entries.associateWith { prefs.getInt(KEY_COUNT + it.name, 0) },
            claimed = Mission.entries.filter { prefs.getBoolean(KEY_CLAIMED + it.name, false) }.toSet(),
            chestOpened = prefs.getBoolean(KEY_CHEST, false),
        )
    }

    private fun save(day: MissionDay) {
        _today.value = day
        prefs.edit {
            clear()
            putString(KEY_DAY, day.day)
            day.counts.forEach { (mission, count) -> putInt(KEY_COUNT + mission.name, count) }
            day.claimed.forEach { putBoolean(KEY_CLAIMED + it.name, true) }
            putBoolean(KEY_CHEST, day.chestOpened)
        }
    }

    companion object {
        const val CHEST_REWARD = 30
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count_"
        private const val KEY_CLAIMED = "claimed_"
        private const val KEY_CHEST = "chest"
    }
}
