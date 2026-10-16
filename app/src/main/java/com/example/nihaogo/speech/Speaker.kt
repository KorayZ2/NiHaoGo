package com.example.nihaogo.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** App-wide Chinese text-to-speech, tuned for learners: unhurried and said in full. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    enum class State { Initializing, Ready, MissingChinese, Unavailable }

    private val _state = MutableStateFlow(State.Initializing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val tts = TextToSpeech(context.applicationContext, this)

    // No loudness boost on top: the voice is already near full scale, so any gain crackles.
    private val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _state.value = State.Unavailable
            return
        }
        // Media, so it follows the volume the player hears games and videos at.
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        _state.value = if (result >= TextToSpeech.LANG_AVAILABLE) State.Ready else State.MissingChinese
    }

    fun speak(text: String, slow: Boolean = false) {
        if (_state.value != State.Ready) return
        // Same rate for single words as for sentences: stretching a word further smears glides
        // like "ian" until 天 tiān sounds like "ting".
        tts.setSpeechRate(if (slow) SlowRate else NormalRate)
        // A moment of silence first, so an output that is just waking up doesn't swallow the
        // start of the first syllable.
        tts.playSilentUtterance(LeadInMillis, TextToSpeech.QUEUE_FLUSH, null)
        tts.speak(asSentence(text), TextToSpeech.QUEUE_ADD, params, "nihaogo")
    }

    fun stop() {
        tts.stop()
    }

    private companion object {
        const val NormalRate = 0.75f
        const val SlowRate = 0.45f
        const val LeadInMillis = 150L

        /**
         * Ends bare Chinese with a full stop, so the voice says it as a finished utterance with its
         * full tone instead of trailing off as if more were coming.
         */
        fun asSentence(text: String): String {
            val trimmed = text.trim()
            return if (trimmed.isNotEmpty() && Pinyin.isHan(trimmed.last())) "$trimmed。" else trimmed
        }
    }
}
