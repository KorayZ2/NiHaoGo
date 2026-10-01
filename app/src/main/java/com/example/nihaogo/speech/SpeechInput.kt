package com.example.nihaogo.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface Heard {
    /** Best guesses from the recognizer, most likely first. */
    data class Text(val candidates: List<String>) : Heard
    data class Failed(val messageTh: String) : Heard
}

/**
 * Chinese speech recognition via the platform [SpeechRecognizer]. Must be used from the main
 * thread; the caller is responsible for the RECORD_AUDIO permission.
 */
class SpeechInput(private val context: Context) {

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listen(): Heard {
        if (!isAvailable) return Heard.Failed("เครื่องนี้ยังไม่รองรับการฟังเสียง ลองพิมพ์หรือเลือกคำตอบแทนนะ")

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        _listening.value = true
        return try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { recognizer.cancel() }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle) {
                        val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        if (cont.isActive) {
                            cont.resume(if (list.isEmpty()) Heard.Failed(NO_MATCH) else Heard.Text(list))
                        }
                    }

                    override fun onError(error: Int) {
                        if (cont.isActive) cont.resume(Heard.Failed(messageFor(error)))
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                recognizer.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    }
                )
            }
        } finally {
            recognizer.destroy()
            _listening.value = false
        }
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NO_MATCH
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "ต้องต่ออินเทอร์เน็ตเพื่อฟังเสียงนะ"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ยังไม่ได้อนุญาตให้ใช้ไมโครโฟน"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ไมโครโฟนกำลังถูกใช้งาน ลองอีกครั้งนะ"
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
            "เครื่องนี้ยังไม่มีภาษาจีนสำหรับฟังเสียง (ตั้งค่าได้ที่แอป Google → ภาษา)"
        else -> "ฟังเสียงไม่สำเร็จ (รหัส $error) ลองอีกครั้งนะ"
    }

    private companion object {
        const val NO_MATCH = "ไม่ได้ยินเสียง ลองพูดอีกครั้งนะ"
        // Constants added in API 31; inlined so they compile against minSdk 24.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
