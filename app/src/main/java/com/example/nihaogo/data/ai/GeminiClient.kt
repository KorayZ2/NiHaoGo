package com.example.nihaogo.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatTurn(val fromUser: Boolean, val text: String)

class GeminiException(message: String) : Exception(message)

/**
 * Minimal Gemini REST client (generateContent). The API key is compiled into the app from
 * local.properties — fine for the prototype, but it must move server-side before a public release.
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)
        .build(),
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Sends the conversation and returns the model's text, which is requested as JSON. */
    suspend fun generateJson(systemPrompt: String, turns: List<ChatTurn>): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", systemPrompt) } }
            }
            putJsonArray("contents") {
                turns.forEach { turn ->
                    addJsonObject {
                        put("role", if (turn.fromUser) "user" else "model")
                        putJsonArray("parts") { addJsonObject { put("text", turn.text) } }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            }
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                throw GeminiException("Gemini HTTP ${response.code}: ${text.take(300)}")
            }
            extractText(json.parseToJsonElement(text).jsonObject)
        }
    }

    /** Joins the answer's text parts, skipping any "thought" parts newer models may include. */
    private fun extractText(response: JsonObject): String {
        val parts = response["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            .orEmpty()
            .map { it.jsonObject }
            .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
        val text = parts.mapNotNull { it["text"]?.jsonPrimitive?.content }.joinToString("")
        if (text.isBlank()) throw GeminiException("Gemini returned no text: ${response.toString().take(300)}")
        return text
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
