package com.example.nihaogo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RawRes
import com.example.nihaogo.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Short game sound effects (made by `tools/gen_sfx.py`) plus the buzz on a wrong answer.
 * Both respect the switches in [AppSettings]. Safe to call from any thread.
 */
class Sfx(context: Context, private val settings: AppSettings) {

    /**
     * [volume] keeps any two sounds that overlap (stars, win, coin on the result screen) below
     * full scale together. [ringMillis] is how long the sound is loud enough to clip speech.
     */
    enum class Sound(@param:RawRes val res: Int, val volume: Float, val ringMillis: Long) {
        Tap(R.raw.sfx_tap, 0.5f, 0),
        Correct(R.raw.sfx_correct, 0.7f, 350),
        Wrong(R.raw.sfx_wrong, 0.6f, 400),
        Star(R.raw.sfx_star, 0.6f, 250),
        Coin(R.raw.sfx_coin, 0.5f, 350),
        Win(R.raw.sfx_win, 0.65f, 900),
        Lose(R.raw.sfx_lose, 0.6f, 900),
        Unlock(R.raw.sfx_unlock, 0.65f, 600),
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // How many sounds have finished loading, whether or not they loaded cleanly.
    private val loadedCount = MutableStateFlow(0)

    init {
        pool.setOnLoadCompleteListener { _, _, _ -> loadedCount.update { it + 1 } }
    }

    // Loading is async; a sound played before it finishes is silently skipped.
    private val ids = Sound.entries.associateWith { pool.load(context.applicationContext, it.res, 1) }

    /** Suspends until every sound has finished loading (the cover waits on this). */
    suspend fun awaitLoaded() {
        loadedCount.first { it >= Sound.entries.size }
    }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    // Uptime when the loudest sound played so far has died down.
    @Volatile private var quietAt = 0L

    /** [rate] shifts the pitch (0.5–2.0), e.g. to make each star ping a little higher. */
    fun play(sound: Sound, rate: Float = 1f) {
        if (!settings.soundOn.value) return
        val id = ids.getValue(sound)
        val r = rate.coerceIn(0.5f, 2f)
        pool.play(id, sound.volume, sound.volume, 1, 0, r)
        quietAt = maxOf(quietAt, SystemClock.uptimeMillis() + (sound.ringMillis / r).toLong())
    }

    /** How long until the sounds playing now have died down, so speech can wait for them. */
    fun millisUntilQuiet(): Long = (quietAt - SystemClock.uptimeMillis()).coerceAtLeast(0)

    fun tap() = play(Sound.Tap)

    fun correct() = play(Sound.Correct)

    fun wrong() {
        play(Sound.Wrong)
        buzz()
    }

    /** Plays [Sound.Correct] or [wrong] depending on [ok]. */
    fun answer(ok: Boolean) = if (ok) correct() else wrong()

    /** The short double buzz of a wrong answer, if vibration is switched on. */
    fun buzz() {
        val v = vibrator ?: return
        if (!settings.vibrateOn.value || !v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 70, 45), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 35, 70, 45), -1)
        }
    }
}
