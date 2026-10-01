package com.example.nihaogo.data.ai

import com.example.nihaogo.data.content.Boss
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.speech.Pinyin
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One line spoken by the boss, plus a model answer the learner can buy as a hint. */
data class BossLine(
    val hanzi: String,
    val pinyin: String,
    val thai: String,
    val hintHanzi: String,
    val hintPinyin: String,
)

data class BossJudgement(
    val correct: Boolean,
    val feedbackTh: String,
    /** The boss's next line (a farewell after the final answer). */
    val next: BossLine?,
)

interface BossEngine {
    val usesAi: Boolean
    suspend fun start(): BossLine
    suspend fun answer(text: String, isFinal: Boolean): BossJudgement
}

/** Offline boss that follows the script in the content files and checks answers by keyword. */
class ScriptedBossEngine(boss: Boss) : BossEngine {
    constructor(category: Category) : this(category.boss)

    private val script = boss.script
    private var index = 0

    override val usesAi = false

    override suspend fun start(): BossLine = lineAt(0)

    override suspend fun answer(text: String, isFinal: Boolean): BossJudgement {
        val turn = script[index]
        val correct = ChineseText.containsChinese(text) &&
            turn.keywords.any { ChineseText.containsSoundAlike(text, it) }
        val feedback = if (correct) PRAISE.random() else "ยังไม่ตรงคำถามนะ ลองตอบแบบนี้: ${turn.sampleHanzi}"
        index++
        val next = when {
            isFinal -> BossLine("再见！", "Zàijiàn!", "ลาก่อนนะ!", "", "")
            index < script.size -> lineAt(index)
            else -> null
        }
        return BossJudgement(correct, feedback, next)
    }

    private fun lineAt(i: Int): BossLine = script[i].let {
        BossLine(it.hanzi, it.pinyin, it.thai, it.sampleHanzi, it.samplePinyin)
    }

    private companion object {
        val PRAISE = listOf("เยี่ยมมาก! 👍", "ถูกต้อง! 🎉", "เก่งมาก! ✨", "สุดยอด! 🌟")
    }
}

/**
 * Gemini plays the boss, judges every answer and improvises the conversation.
 *
 * Small models lose count of rounds and sometimes write a hint for the wrong question, so the app
 * keeps track of the round itself, tells the model which question the learner is answering, and
 * repairs any reply that would leave the learner stuck (falling back to the scripted line).
 */
class AiBossEngine(
    private val boss: Boss,
    /** Words the learner has met, so the boss keeps to them. */
    private val knownWords: List<Word>,
    private val gemini: GeminiClient,
) : BossEngine {
    constructor(category: Category, gemini: GeminiClient) : this(category.boss, category.words, gemini)

    private val history = mutableListOf<ChatTurn>()
    private val script = boss.script
    private var answered = 0
    private var question: BossLine? = null

    override val usesAi = true

    override suspend fun start(): BossLine {
        history.clear()
        answered = 0
        val line = repair(send(openingMessage()), round = 0).toLine()
        question = line
        return line
    }

    override suspend fun answer(text: String, isFinal: Boolean): BossJudgement {
        val reply = send(answerMessage(text, isFinal))
        answered++
        val fixed = if (isFinal) repairFarewell(reply) else repair(reply, round = answered)
        val line = fixed.toLine()
        if (!isFinal) question = line
        return BossJudgement(fixed.correct, fixed.feedbackTh, line)
    }

    private fun openingMessage() =
        "Start the scene: greet the learner and ask question 1 of ${GameRules.BOSS_ROUNDS}. " +
            "Give a hint that answers that question."

    private fun answerMessage(text: String, isFinal: Boolean): String {
        val round = answered + 1
        val asked = question?.hanzi.orEmpty()
        return if (isFinal) {
            """Your question was: "$asked". The learner's answer (round $round of ${GameRules.BOSS_ROUNDS}, the LAST one): "$text". """ +
                "Judge the answer against your question, then say a short friendly goodbye with no question. " +
                "Leave hint_hanzi and hint_pinyin empty."
        } else {
            """Your question was: "$asked". The learner's answer (round $round of ${GameRules.BOSS_ROUNDS}): "$text". """ +
                "Judge the answer against your question, then ask question ${round + 1} of ${GameRules.BOSS_ROUNDS} " +
                "and give a hint that answers that NEW question."
        }
    }

    /** Makes sure a mid-game reply asks a real question and carries a hint that answers it. */
    private suspend fun repair(reply: AiReply, round: Int): AiReply {
        val fixed = when {
            !isBossLine(reply.hanzi) || !isQuestion(reply.hanzi) -> {
                val turn = script[round.coerceAtMost(script.lastIndex)]
                reply.copy(
                    hanzi = turn.hanzi, pinyin = turn.pinyin, thai = turn.thai,
                    hintHanzi = turn.sampleHanzi, hintPinyin = turn.samplePinyin,
                )
            }
            !answers(reply.hintHanzi, reply.hanzi) -> {
                val hint = askHint(reply.hanzi)
                reply.copy(hintHanzi = hint?.hintHanzi.orEmpty(), hintPinyin = hint?.hintPinyin.orEmpty())
            }
            else -> reply
        }
        return fixed.also(::rememberFixed)
    }

    private fun repairFarewell(reply: AiReply): AiReply {
        val base = if (isBossLine(reply.hanzi)) reply else reply.copy(hanzi = "再见！", pinyin = "Zàijiàn!", thai = "ลาก่อนนะ!")
        return base.copy(hintHanzi = "", hintPinyin = "").also(::rememberFixed)
    }

    /** Keeps the model's view of the conversation in line with what the learner actually saw. */
    private fun rememberFixed(reply: AiReply) {
        if (history.lastOrNull()?.fromUser == false) {
            history[history.lastIndex] = ChatTurn(fromUser = false, text = json.encodeToString(AiReply.serializer(), reply))
        }
    }

    /** Second, focused request used only when the model's own hint does not fit its question. */
    private suspend fun askHint(questionHanzi: String): AiReply? = try {
        val raw = gemini.generateJson(HINT_PROMPT, listOf(ChatTurn(fromUser = true, text = questionHanzi)))
        parse(raw).takeIf { answers(it.hintHanzi, questionHanzi) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun send(message: String): AiReply {
        history += ChatTurn(fromUser = true, text = message)
        val raw = try {
            gemini.generateJson(systemPrompt(), history)
        } catch (e: Exception) {
            history.removeAt(history.lastIndex) // allow a clean retry
            throw e
        }
        history += ChatTurn(fromUser = false, text = raw)
        return try {
            parse(raw)
        } catch (e: Exception) {
            history.subList(history.size - 2, history.size).clear()
            throw e
        }
    }

    private fun systemPrompt(): String {
        val known = knownWords.joinToString("、") { it.hanzi }
        return """
            You are role-playing a character in a Chinese-learning game for Thai beginners (children and teenagers).
            Character: ${boss.persona} Your name is ${boss.name} (${boss.namePinyin}).
            Scene (in Thai): ${boss.scenarioTh}
            Target words the learner is practising: ${boss.targetWords.joinToString("、")}
            Words the learner already knows: $known

            Rules:
            - The conversation has exactly ${GameRules.BOSS_ROUNDS} learner answers. Every user message is written
              by the app: it tells you your last question, the learner's answer and the round number.
              Follow it exactly and never count rounds yourself.
            - "hanzi": speak ONLY short, simple Simplified Chinese (beginner level, at most about 15 characters).
              Each line is one friendly sentence ending with ONE question the learner can answer using the
              target words. Never write English, instructions or placeholders in "hanzi".
            - Judge the learner's answer against the question the app quotes. "correct" is true only when it is
              an understandable Chinese reply that actually answers that question. Small grammar mistakes and
              homophone characters from speech recognition are fine. Answers only in Thai or English, empty
              answers, or replies that ignore the question (e.g. just 谢谢 to a yes/no question) are not correct.
            - "feedback_th": one short, encouraging sentence in Thai. If the answer was not correct, say briefly
              in Thai what the learner could have said.
            - "hint_hanzi"/"hint_pinyin": what the LEARNER could say to answer the question in your "hanzi" of
              this same reply — first person, reusing words from that question (pinyin with tone marks).
              Not an answer to an older question, and not a greeting or thank-you unless your question asks for one.
            - In the opening message set correct=true and feedback_th="".
            - Reply with JSON only, exactly these keys:
              {"correct": boolean, "feedback_th": string, "hanzi": string, "pinyin": string, "thai": string,
               "hint_hanzi": string, "hint_pinyin": string}
        """.trimIndent()
    }

    @Serializable
    private data class AiReply(
        val correct: Boolean = false,
        @SerialName("feedback_th") val feedbackTh: String = "",
        val hanzi: String = "",
        val pinyin: String = "",
        val thai: String = "",
        @SerialName("hint_hanzi") val hintHanzi: String = "",
        @SerialName("hint_pinyin") val hintPinyin: String = "",
    ) {
        fun toLine() = BossLine(hanzi, pinyin, thai, hintHanzi, hintPinyin)
    }

    private fun parse(raw: String): AiReply {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            json.decodeFromString(AiReply.serializer(), cleaned)
        } catch (e: Exception) {
            throw GeminiException("Could not read Gemini reply: ${raw.take(200)}")
        }
    }

    internal companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        val HINT_PROMPT = """
            The user message is a question in Simplified Chinese asked to a Thai beginner learning Chinese.
            Write a short, simple answer the learner could say (first person, reusing words from the question).
            Reply with JSON only: {"hint_hanzi": string, "hint_pinyin": string (pinyin with tone marks)}
        """.trimIndent()

        /** Characters that say nothing about what a question is about. */
        private const val FILLER = "你我他她它们的了吗呢吧啊呀是很也都不和这那个"

        /** A line the learner can read: Chinese, and no leaked markers like __FINAL__. */
        fun isBossLine(hanzi: String): Boolean = ChineseText.containsChinese(hanzi) && "_" !in hanzi

        fun isQuestion(hanzi: String): Boolean =
            hanzi.contains('？') || hanzi.contains('?') ||
                listOf("吗", "呢", "什么", "几", "哪", "谁", "怎么", "多少").any(hanzi::contains)

        /** Rough check that [hint] answers [question]: it must reuse a meaningful character from it. */
        fun answers(hint: String, question: String): Boolean {
            if (!isBossLine(hint)) return false
            val topic = question.filter { Pinyin.isHan(it) && it !in FILLER }.toSet()
            return topic.isEmpty() || hint.any { it in topic }
        }
    }
}
