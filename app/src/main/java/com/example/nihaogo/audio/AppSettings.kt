package com.example.nihaogo.audio

import android.content.Context
import androidx.core.content.edit
import com.example.nihaogo.data.user.GameRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

/** Per-device preferences. Not synced: a quiet phone shouldn't silence the player's tablet. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _soundOn = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundOn: StateFlow<Boolean> = _soundOn.asStateFlow()

    private val _vibrateOn = MutableStateFlow(prefs.getBoolean(KEY_VIBRATE, true))
    val vibrateOn: StateFlow<Boolean> = _vibrateOn.asStateFlow()

    fun setSoundOn(on: Boolean) {
        _soundOn.value = on
        prefs.edit { putBoolean(KEY_SOUND, on) }
    }

    fun setVibrateOn(on: Boolean) {
        _vibrateOn.value = on
        prefs.edit { putBoolean(KEY_VIBRATE, on) }
    }

    /**
     * How many map categories were open when the player last saw the map, so a newly opened one
     * can be celebrated once. -1 until the map has been shown on this device.
     */
    var seenUnlockedCategories: Int
        get() = prefs.getInt(KEY_SEEN_UNLOCKED, -1)
        set(value) = prefs.edit { putInt(KEY_SEEN_UNLOCKED, value) }

    /** Whether the pinyin primer has been finished or skipped, so it only opens by itself once. */
    var pinyinIntroSeen: Boolean
        get() = prefs.getBoolean(KEY_PINYIN_INTRO_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_PINYIN_INTRO_SEEN, value) }

    /** Whether the sign-in page has been shown after the cover, so it only opens by itself once. */
    var loginSeen: Boolean
        get() = prefs.getBoolean(KEY_LOGIN_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_LOGIN_SEEN, value) }

    /** Days in a row the app has been opened, counting today once [markActiveToday] has run. */
    val streak: Int
        get() = if (prefs.getString(KEY_LAST_ACTIVE, null) in listOf(GameRules.today(), yesterday())) {
            prefs.getInt(KEY_STREAK, 0)
        } else {
            0
        }

    /** Counts today towards [streak]; a missed day starts it over. */
    fun markActiveToday() {
        val today = GameRules.today()
        val last = prefs.getString(KEY_LAST_ACTIVE, null)
        if (last == today) return
        val streak = if (last == yesterday()) prefs.getInt(KEY_STREAK, 0) + 1 else 1
        prefs.edit {
            putString(KEY_LAST_ACTIVE, today)
            putInt(KEY_STREAK, streak)
        }
    }

    private fun yesterday(): String = GameRules.today(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L))

    /** The email last signed in with when "จดจำฉัน" was ticked; null when not remembered. */
    var rememberedEmail: String?
        get() = prefs.getString(KEY_REMEMBERED_EMAIL, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_REMEMBERED_EMAIL) else putString(KEY_REMEMBERED_EMAIL, value) }

    /** Whether a special mode's story intro has been seen, so it only opens by itself once. */
    fun caseIntroSeen(modeId: String): Boolean = prefs.getBoolean(KEY_CASE_INTRO_SEEN + modeId, false)

    fun markCaseIntroSeen(modeId: String) = prefs.edit { putBoolean(KEY_CASE_INTRO_SEEN + modeId, true) }

    private companion object {
        const val KEY_SOUND = "sound_on"
        const val KEY_VIBRATE = "vibrate_on"
        const val KEY_SEEN_UNLOCKED = "seen_unlocked_categories"
        const val KEY_PINYIN_INTRO_SEEN = "pinyin_intro_seen"
        const val KEY_CASE_INTRO_SEEN = "case_intro_seen_"
        const val KEY_REMEMBERED_EMAIL = "remembered_email"
        const val KEY_LOGIN_SEEN = "login_seen"
        const val KEY_LAST_ACTIVE = "last_active_day"
        const val KEY_STREAK = "streak_days"
    }
}
