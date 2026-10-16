package com.example.nihaogo.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.example.nihaogo.audio.Sfx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** App-wide Chinese text-to-speech, tuned for learners: unhurried and said in full. */
class Speaker(context: Context, private val sfx: Sfx) : TextToSpeech.OnInitListener {

    enum class State { Initializing, Ready, MissingChinese, Unavailable }

    private val _state = MutableStateFlow(State.Initializing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val tts = TextToSpeech(context.applicationContext, this)

    // A little under full scale: the voice peaks near 0 dB, so at 1.0 it clips once it is
    // resampled to the output rate or meets a game sound, and that is heard as crackle.
    private val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Volume) }

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
        if (result >= TextToSpeech.LANG_AVAILABLE) useInstalledVoice()
        _state.value = if (result >= TextToSpeech.LANG_AVAILABLE) State.Ready else State.MissingChinese
    }

    /** Installed mainland voices, and the one used when a line asks for no voice in particular. */
    private var installed: List<Voice> = emptyList()
    private var defaultVoice: Voice? = null
    private var currentVoice: Voice? = null

    /**
     * Picks the best mainland Chinese voice that is installed on the phone. A network voice is
     * streamed, and on a weak connection it arrives with dropouts and crackle.
     */
    private fun useInstalledVoice() {
        installed = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter {
                it.locale.language in ChineseLanguages && it.locale.country == "CN" &&
                    !it.isNetworkConnectionRequired &&
                    it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
        defaultVoice = installed.maxByOrNull { it.quality } ?: return
        useVoice(defaultVoice)
    }

    private fun useVoice(voice: Voice?) {
        if (voice == null || voice == currentVoice) return
        if (runCatching { tts.voice = voice }.isSuccess) currentVoice = voice
    }

    /**
     * An installed voice of [kind]. Only Google's voices are known by name; on other engines,
     * or with none of that kind installed, the default voice does it with pitch alone.
     */
    private fun voiceFor(kind: VoiceKind): Voice? {
        val names = KnownVoices[kind] ?: return defaultVoice
        return names.firstNotNullOfOrNull { name -> installed.firstOrNull { it.name.startsWith(name) } } ?: defaultVoice
    }

    /** [style] gives a character their own voice; by default it is the app's usual one. */
    fun speak(text: String, slow: Boolean = false, style: VoiceStyle = VoiceStyle.Default) {
        if (_state.value != State.Ready) return
        useVoice(voiceFor(style.kind))
        tts.setPitch(style.pitch)
        // Same rate for single words as for sentences: stretching a word further smears glides
        // like "ian" until 天 tiān sounds like "ting".
        tts.setSpeechRate(((if (slow) SlowRate else NormalRate) * style.rate).coerceAtLeast(SlowRate))
        // A moment of silence first, so an output that is just waking up doesn't swallow the
        // start of the first syllable, and so the voice doesn't land on top of a right/wrong
        // chime that is still ringing (the two together clip).
        val leadIn = maxOf(LeadInMillis, sfx.millisUntilQuiet())
        tts.playSilentUtterance(leadIn, TextToSpeech.QUEUE_FLUSH, null)
        tts.speak(asSentence(text), TextToSpeech.QUEUE_ADD, params, "nihaogo")
    }

    fun stop() {
        tts.stop()
    }

    private companion object {
        // Below about 0.7 the engine's time-stretching turns grainy, so slow stays above it.
        const val NormalRate = 0.85f
        const val SlowRate = 0.65f
        const val LeadInMillis = 150L
        const val Volume = 0.85f
        val ChineseLanguages = setOf("zh", "cmn")

        /** Google's mainland voices by kind, preferred one first. */
        val KnownVoices = mapOf(
            VoiceKind.Male to listOf("cmn-cn-x-cce", "cmn-cn-x-ccd"),
            VoiceKind.Female to listOf("cmn-cn-x-ccc", "cmn-cn-x-ssa"),
        )

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
