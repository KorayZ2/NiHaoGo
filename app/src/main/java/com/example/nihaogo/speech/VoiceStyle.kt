package com.example.nihaogo.speech

import kotlinx.serialization.Serializable

@Serializable
enum class VoiceKind { Any, Male, Female }

/**
 * How a character sounds: a male or female voice where the phone has one, then [pitch] and
 * [rate] on top (both relative to the app's normal voice, so 1 changes nothing).
 */
@Serializable
data class VoiceStyle(val kind: VoiceKind = VoiceKind.Any, val pitch: Float = 1f, val rate: Float = 1f) {
    companion object {
        val Default = VoiceStyle()
    }
}
