package com.example.nihaogo.data.ai

import com.example.nihaogo.data.content.ContentRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiInterrogationTest {

    private val mode = ContentRepository.parseSpecial(File("src/main/assets/${ContentRepository.SPECIAL_PATH}").readText()).detective
    private val xiaolong = mode.suspects.first { it.id == "xiaolong" }

    /** Gemini stand-in that answers with the queued JSON texts, in order. */
    private fun engine(vararg replies: String): Pair<AiInterrogation, List<String>> {
        val queue = ArrayDeque(replies.toList())
        val requests = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body?.writeTo(buffer)
            requests += buffer.readUtf8()
            val text = JsonPrimitive(queue.removeFirst()).toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"candidates":[{"content":{"parts":[{"text":$text}]}}]}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return AiInterrogation(mode, GeminiClient("test-key", "test-model", http)) to requests
    }

    @Test
    fun answersAsTheAskedSuspectWithTheCaseFacts() = runBlocking {
        val (ai, requests) = engine(
            """{"understood":true,"feedback_th":"","hanzi":"我在房间。","pinyin":"Wǒ zài fángjiān.","thai":"ผมอยู่ในห้อง"}"""
        )
        val reply = ai.ask(xiaolong, "你下午在哪儿？")
        assertTrue(reply.understood)
        assertEquals("我在房间。", reply.line.hanzi)
        // The model hears who is being asked and knows every suspect's secret.
        assertTrue(requests[0].contains("小龙"))
        assertTrue(requests[0].contains("school bag"))
    }

    @Test
    fun unusableReplyFallsBackToTheScript() = runBlocking {
        val (ai, _) = engine("""{"understood":true,"hanzi":"","pinyin":"","thai":""}""", "not json at all")
        val question = xiaolong.questions.first().question.hanzi
        assertEquals(xiaolong.questions.first().answer, ai.ask(xiaolong, question).line)
        assertEquals(xiaolong.questions.first().answer, ai.ask(xiaolong, question).line)
    }

    @Test
    fun notUnderstoodIsPassedThrough() = runBlocking {
        val (ai, _) = engine(
            """{"understood":false,"feedback_th":"ลองถามเป็นภาษาจีนนะ","hanzi":"我听不懂。","pinyin":"Wǒ tīng bu dǒng.","thai":"ฟังไม่เข้าใจ"}"""
        )
        val reply = ai.ask(xiaolong, "hello")
        assertFalse(reply.understood)
        assertEquals("ลองถามเป็นภาษาจีนนะ", reply.feedbackTh)
    }
}
