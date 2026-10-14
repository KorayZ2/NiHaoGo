package com.example.nihaogo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RawRes
import com.example.nihaogo.R

/**
 * Short game sound effects (made by `tools/gen_sfx.py`) plus the buzz on a wrong answer.
 * Both respect the switches in [AppSettings]. Safe to call from any thread.
 */
class Sfx(context: Context, private val settings: AppSettings) {

    enum class Sound(@param:RawRes val res: Int, val volume: Float) {
        Tap(R.raw.sfx_tap, 0.5f),
        Correct(R.raw.sfx_correct, 0.8f),
        Wrong(R.raw.sfx_wrong, 0.7f),
        Star(R.raw.sfx_star, 0.8f),
        Coin(R.raw.sfx_coin, 0.6f),
        Win(R.raw.sfx_win, 0.9f),
        Lose(R.raw.sfx_lose, 0.7f),
        Unlock(R.raw.sfx_unlock, 0.8f),
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

    // Loading is async; a sound played before it finishes is silently skipped.
    private val ids = Sound.entries.associateWith { pool.load(context.applicationContext, it.res, 1) }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** [rate] shifts the pitch (0.5–2.0), e.g. to make each star ping a little higher. */
    fun play(sound: Sound, rate: Float = 1f) {
        if (!settings.soundOn.value) return
        val id = ids.getValue(sound)
        pool.play(id, sound.volume, sound.volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

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
