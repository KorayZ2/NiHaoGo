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
    private fun engine(vararg replies: String, flexible: Boolean = false): Pair<AiBossEngine, List<String>> {
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
        val gemini = GeminiClient("test-key", "test-model", http)
        return AiBossEngine(category.boss, category.words, gemini, flexible = flexible) to requests
    }

    private fun reply(hanzi: String, hint: String = "", correct: Boolean = true, feedback: String = "ดี") =
        """{"correct":$correct,"feedback_th":"$feedback","hanzi":"$hanzi","pinyin":"","thai":"","hint_hanzi":"$hint","hint_pinyin":""}"""

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
    fun feedbackTextDecidesTheTick() = runBlocking {
        val (boss, _) = engine(
            reply("你好！你叫什么名字？", "我叫小美。"),
            reply("你是中国人吗？", "我不是中国人。", correct = false, feedback = "ถูกต้อง! เก่งมาก"),
            reply("你是学生吗？", "我是学生。", correct = true, feedback = "ยังไม่ถูกนะ ลองตอบว่า 我不是中国人"),
        )
        boss.start()
        assertTrue(boss.answer("我叫小美", isFinal = false).correct)
        assertFalse(boss.answer("谢谢", isFinal = false).correct)
    }

    @Test
    fun repeatedQuestionFallsBackToAnUnaskedScriptedOne() = runBlocking {
        val (boss, requests) = engine(
            reply("你好！你叫什么名字？", "我叫小美。"),
            reply("你的名字是什么？", "我的名字是小美。"),
        )
        boss.start()
        val next = boss.answer("我叫小美", isFinal = false).next!!
        assertEquals(category.boss.script[1].hanzi, next.hanzi)
        // The model is shown what it already asked.
        assertTrue(requests[1].contains("never repeat"))
    }

    @Test
    fun flexibleBossJudgesByMeaningAndSuggestsAnotherWay() = runBlocking {
        val (boss, requests) = engine(
            reply("你好！你叫什么名字？", "我叫小美。"),
            """{"feedback_th":"ถูกต้อง!","correct":true,"hanzi":"你是学生吗？","pinyin":"","thai":"","hint_hanzi":"我是学生。","hint_pinyin":"","alt_hanzi":"我的名字叫小美。","alt_pinyin":"Wǒ de míngzi jiào Xiǎo Měi.","alt_thai":"ฉันชื่อเสี่ยวเหม่ย"}""",
            flexible = true,
        )
        boss.start()
        assertTrue(requests[0].contains("BY MEANING"))
        assertTrue(requests[0].contains("alt_hanzi"))
        val judgement = boss.answer("叫小美", isFinal = false)
        assertEquals("我的名字叫小美。", judgement.alternative?.hanzi)
    }

    @Test
    fun flexibleBossFallsBackToTheHintWhenTheModelGivesNoAlternative() = runBlocking {
        val (boss, _) = engine(
            reply("你好！你叫什么名字？", "我叫小美。"),
            reply("你是学生吗？", "我是学生。", feedback = "ถูกต้อง!"),
            flexible = true,
        )
        boss.start()
        assertEquals("我叫小美。", boss.answer("小美", isFinal = false).alternative?.hanzi)
    }

    @Test
    fun categoryBossKeepsItsRulesAndHasNoAlternative() = runBlocking {
        val (boss, requests) = engine(
            reply("你好！你叫什么名字？", "我叫小美。"),
            reply("你是学生吗？", "我是学生。", feedback = "ถูกต้อง!"),
        )
        boss.start()
        assertFalse(requests[0].contains("alt_hanzi"))
        assertEquals(null, boss.answer("我叫小美", isFinal = false).alternative)
    }

    @Test
    fun verdictFollowsTheWordsTheLearnerReads() {
        assertEquals(true, AiBossEngine.verdictOf("ถูกต้อง! เก่งมาก"))
        assertEquals(true, AiBossEngine.verdictOf("เก่งมาก! ลองพูดให้ช้าลงนิดนะ"))
        assertEquals(false, AiBossEngine.verdictOf("ยังไม่ถูกนะ ลองตอบว่า 我是学生"))
        assertEquals(false, AiBossEngine.verdictOf("เกือบถูกแล้ว!"))
        assertEquals(null, AiBossEngine.verdictOf(""))
    }

    @Test
    fun sameQuestionCheck() {
        assertTrue(AiBossEngine.sameQuestion("你叫什么名字？", "你的名字是什么？"))
        assertTrue(AiBossEngine.sameQuestion("你好！你叫什么名字？", "你的名字是什么？"))
        assertTrue(AiBossEngine.sameQuestion("你好吗？", "你好吗"))
        assertFalse(AiBossEngine.sameQuestion("你想吃什么？", "你想喝什么？"))
        assertFalse(AiBossEngine.sameQuestion("你是中国人吗？", "你是学生吗？"))
    }

    @Test
    fun answersCheck() {
        assertTrue(AiBossEngine.answers("我认识中国人", "你认识中国人吗？"))
        assertTrue(AiBossEngine.answers("我十二岁。", "你几岁？"))
        assertFalse(AiBossEngine.answers("谢谢你。", "你认识中国人吗？"))
        assertFalse(AiBossEngine.answers("", "你认识中国人吗？"))
    }
}
