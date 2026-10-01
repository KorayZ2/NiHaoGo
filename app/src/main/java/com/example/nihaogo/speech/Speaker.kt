package com.example.nihaogo.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** App-wide Chinese text-to-speech. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    enum class State { Initializing, Ready, MissingChinese, Unavailable }

    private val _state = MutableStateFlow(State.Initializing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _state.value = State.Unavailable
            return
        }
        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        _state.value = if (result >= TextToSpeech.LANG_AVAILABLE) State.Ready else State.MissingChinese
    }

    fun speak(text: String, slow: Boolean = false) {
        if (_state.value != State.Ready) return
        tts.setSpeechRate(if (slow) 0.35f else 0.9f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nihaogo")
    }

    fun stop() {
        tts.stop()
    }
}
