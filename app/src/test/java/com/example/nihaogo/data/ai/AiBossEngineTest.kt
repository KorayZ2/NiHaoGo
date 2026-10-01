package com.example.nihaogo.data.ai

import com.example.nihaogo.data.content.ContentRepository
import com.example.nihaogo.data.user.GameRules
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

class AiBossEngineTest {

    private val category = ContentRepository.parse(
        File("src/main/assets/${ContentRepository.CATEGORIES_PATH}").readText()
    ).categories.first()

    /** Gemini stand-in that answers with the queued JSON texts, in order. */
    private fun engine(vararg replies: String): Pair<AiBossEngine, List<String>> {
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
        return AiBossEngine(category, GeminiClient("test-key", "test-model", http)) to requests
    }

    private fun reply(hanzi: String, hint: String = "", correct: Boolean = true) =
        """{"correct":$correct,"feedback_th":"ดี","hanzi":"$hanzi","pinyin":"","thai":"","hint_hanzi":"$hint","hint_pinyin":""}"""

    @Test
    fun leakedMarkerMidGameFallsBackToTheScriptedQuestion() = runBlocking {
        val (boss, _) = engine(
            reply("你好！你叫什么名字？", "我叫小美。"),
            reply("__FINAL__"),
        )
        boss.start()
        val next = boss.answer("我叫小美", isFinal = false).next!!
        assertEquals(category.boss.script[1].hanzi, next.hanzi)
        assertEquals(category.boss.script[1].sampleHanzi, next.hintHanzi)
    }

    @Test
    fun hintThatDoesNotAnswerTheQuestionIsReplaced() = runBlocking {
        val (boss, requests) = engine(
            reply("你好！你叫什么名字？", "我叫小美。"),
            reply("你认识中国人吗？", "谢谢你。"),
            """{"hint_hanzi":"我认识中国人。","hint_pinyin":"Wǒ rènshi Zhōngguó rén."}""",
        )
        boss.start()
        val next = boss.answer("我叫小美", isFinal = false).next!!
        assertEquals("你认识中国人吗？", next.hanzi)
        assertEquals("我认识中国人。", next.hintHanzi)
        // The model is told which question the learner answered and which round it is.
        assertTrue(requests[1].contains("你叫什么名字"))
        assertTrue(requests[1].contains("round 1 of ${GameRules.BOSS_ROUNDS}"))
    }

    @Test
    fun farewellNeverShowsAMarkerOrHint() = runBlocking {
        val (boss, _) = engine(
            reply("你好！你叫什么名字？", "我叫小美。"),
            reply("__FINAL__", "谢谢"),
        )
        boss.start()
        val last = boss.answer("再见", isFinal = true).next!!
        assertEquals("再见！", last.hanzi)
        assertEquals("", last.hintHanzi)
    }

    @Test
    fun answersCheck() {
        assertTrue(AiBossEngine.answers("我认识中国人", "你认识中国人吗？"))
        assertTrue(AiBossEngine.answers("我十二岁。", "你几岁？"))
        assertFalse(AiBossEngine.answers("谢谢你。", "你认识中国人吗？"))
        assertFalse(AiBossEngine.answers("", "你认识中国人吗？"))
    }
}
