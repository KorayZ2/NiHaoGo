package com.example.nihaogo.data.ai

import com.example.nihaogo.data.content.Boss
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.Line
import com.example.nihaogo.data.content.OldWords
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.speech.Pinyin
import com.example.nihaogo.speech.SpokenChinese
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
    /** Special modes only: another way to say what the learner meant. */
    val alternative: Line? = null,
)

interface BossEngine {
    val usesAi: Boolean
    suspend fun start(): BossLine
    suspend fun answer(text: String, isFinal: Boolean): BossJudgement
}

/**
 * Offline boss that follows the script in the content files and checks answers by keyword.
 * A [flexible] boss (special modes) also accepts a spoken answer close to the sample and suggests
 * another way to say it.
 */
class ScriptedBossEngine(boss: Boss, private val flexible: Boolean = false) : BossEngine {
    constructor(category: Category) : this(category.boss)

    private val script = boss.script
    private var index = 0

    override val usesAi = false

    override suspend fun start(): BossLine = lineAt(0)

    override suspend fun answer(text: String, isFinal: Boolean): BossJudgement {
        val turn = script[index]
        val correct = ChineseText.containsChinese(text) && (
            turn.keywords.any { ChineseText.containsSoundAlike(text, it) } ||
                flexible && SpokenChinese.similarity(turn.sampleHanzi, text) >= ScriptedInterrogation.MATCH_SCORE
            )
        val alternative = if (flexible) SpokenChinese.alternative(text, Line(turn.sampleHanzi, turn.samplePinyin, "")) else null
        val feedback = if (correct) PRAISE.random() else "ยังไม่ตรงคำถามนะ ลองตอบแบบนี้: ${turn.sampleHanzi}"
        index++
        val next = when {
            isFinal -> BossLine("再见！", "Zàijiàn!", "ลาก่อนนะ!", "", "")
            index < script.size -> lineAt(index)
            else -> null
        }
        return BossJudgement(correct, feedback, next, alternative)
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
 *
 * They also like to ask the same questions, so every game steers each round towards a shuffled
 * target word, lists the questions already asked (this game and the last ones), and swaps a repeated
 * question for an unused scripted one. The ✅/❌ follows the Thai feedback the learner reads, because
 * the model's "correct" flag sometimes contradicts its own words.
 */
class AiBossEngine(
    private val boss: Boss,
    /** Words the learner has met, so the boss keeps to them. */
    private val knownWords: List<Word>,
    private val gemini: GeminiClient,
    /** Special modes only: words from the categories already beaten, worked into a few rounds. */
    private val reviewWords: List<Word> = emptyList(),
    /**
     * Special modes: judge spoken, casual answers by meaning rather than textbook grammar, and
     * suggest another way to say each answer.
     */
    private val flexible: Boolean = false,
) : BossEngine {
    constructor(category: Category, gemini: GeminiClient) : this(category.boss, category.words, gemini)

    private val history = mutableListOf<ChatTurn>()
    private val script = boss.script
    private var answered = 0
    private var question: BossLine? = null
    /** Questions asked so far this game. */
    private val asked = mutableListOf<String>()
    /** Target words in a fresh order each game; round n is steered towards focus[n]. */
    private var focus: List<String> = emptyList()
    /** Questions from this boss's earlier games, which the model is told to avoid. */
    private var earlier: List<String> = emptyList()

    override val usesAi = true

    override suspend fun start(): BossLine {
        history.clear()
        answered = 0
        asked.clear()
        focus = boss.targetWords.shuffled().let { targets ->
            if (reviewWords.isEmpty()) targets
            else steer(targets, reviewWords.shuffled().take(OldWords.BOSS_REVIEW_ROUNDS).map { it.hanzi })
        }
        earlier = recentQuestions(boss.name)
        val line = repair(send(openingMessage()), round = 0).toLine()
        ask(line)
        return line
    }

    override suspend fun answer(text: String, isFinal: Boolean): BossJudgement {
        val asked = question
        val reply = send(answerMessage(text, isFinal)).let { it.copy(correct = verdictOf(it.feedbackTh) ?: it.correct) }
        answered++
        val fixed = if (isFinal) repairFarewell(reply) else repair(reply, round = answered)
        val line = fixed.toLine()
        if (!isFinal) ask(line)
        val alternative = if (flexible) alternativeFor(text, reply, asked) else null
        return BossJudgement(fixed.correct, fixed.feedbackTh, line, alternative)
    }

    /** The model's other way to say the answer, or else the hint for the question it answered. */
    private fun alternativeFor(text: String, reply: AiReply, asked: BossLine?): Line? {
        if (isBossLine(reply.altHanzi) && !SpokenChinese.sameMeaning(reply.altHanzi, text)) {
            return Line(reply.altHanzi, reply.altPinyin, reply.altThai)
        }
        val hint = asked?.takeIf { isBossLine(it.hintHanzi) } ?: return null
        return SpokenChinese.alternative(text, Line(hint.hintHanzi, hint.hintPinyin, ""))
    }

    private fun ask(line: BossLine) {
        question = line
        asked += line.hanzi
        rememberQuestion(boss.name, line.hanzi)
    }

    private fun focusFor(round: Int): String =
        focus.getOrNull(round % focus.size.coerceAtLeast(1))?.let { """ Build it around "$it" if that fits the scene.""" }.orEmpty()

    private fun openingMessage(): String {
        val avoid = if (earlier.isEmpty()) "" else
            " Earlier games already used these questions, so open with a different one: ${earlier.joinToString(" / ")}."
        return "Start the scene: greet the learner and ask question 1 of ${GameRules.BOSS_ROUNDS}.${focusFor(0)}$avoid " +
            "Give a hint that answers that question."
    }

    private fun answerMessage(text: String, isFinal: Boolean): String {
        val round = answered + 1
        val last = question?.hanzi.orEmpty()
        return if (isFinal) {
            """Your question was: "$last". The learner's answer (round $round of ${GameRules.BOSS_ROUNDS}, the LAST one): "$text". """ +
                "Judge the answer against your question, then say a short friendly goodbye with no question. " +
                "Leave hint_hanzi and hint_pinyin empty."
        } else {
            val avoid = (asked + earlier).distinct().joinToString(" / ")
            """Your question was: "$last". The learner's answer (round $round of ${GameRules.BOSS_ROUNDS}): "$text". """ +
                "Judge the answer against your question, then ask question ${round + 1} of ${GameRules.BOSS_ROUNDS}: " +
                "a NEW question about something else — never repeat or rephrase any of these: $avoid.${focusFor(round)} " +
                "Give a hint that answers that NEW question."
        }
    }

    private fun isRepeat(hanzi: String): Boolean = asked.any { sameQuestion(it, hanzi) }

    /** The first scripted turn from [round] on (wrapping around) that has not been asked yet. */
    private fun fallbackTurn(round: Int) =
        (script.drop(round) + script.take(round)).firstOrNull { !isRepeat(it.hanzi) }
            ?: script[round.coerceAtMost(script.lastIndex)]

    /** Makes sure a mid-game reply asks a new, real question and carries a hint that answers it. */
    private suspend fun repair(reply: AiReply, round: Int): AiReply {
        val fixed = when {
            !isBossLine(reply.hanzi) || !isQuestion(reply.hanzi) || isRepeat(reply.hanzi) -> {
                val turn = fallbackTurn(round)
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
            gemini.generateJson(systemPrompt(), history, temperature = TEMPERATURE)
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
        // Interpolated lines carry their own indent so trimIndent() still lines everything up.
        val judging = if (flexible) FLEXIBLE_JUDGING else STRICT_JUDGING
        val altRule = if (flexible) "\n            $ALT_RULE" else ""
        val altKeys = if (flexible) ", \"alt_hanzi\": string, \"alt_pinyin\": string, \"alt_thai\": string" else ""
        val review = if (reviewWords.isEmpty()) "" else
            "Words from the learner's earlier lessons (reuse some where they fit the scene): " +
                reviewWords.joinToString("、") { it.hanzi }
        return """
            You are role-playing a character in a Chinese-learning game for Thai beginners (children and teenagers).
            Character: ${boss.persona} Your name is ${boss.name} (${boss.namePinyin}).
            Scene (in Thai): ${boss.scenarioTh}
            Target words the learner is practising: ${boss.targetWords.joinToString("、")}
            Words the learner already knows: $known
            $review

            Rules:
            - The conversation has exactly ${GameRules.BOSS_ROUNDS} learner answers. Every user message is written
              by the app: it tells you your last question, the learner's answer and the round number.
              Follow it exactly and never count rounds yourself.
            - "hanzi": speak ONLY short, simple Simplified Chinese (beginner level, at most about 15 characters).
              Each line is one friendly sentence ending with ONE question the learner can answer using the
              target words. Never write English, instructions or placeholders in "hanzi".
            - Every question must be new: never repeat or rephrase a question you already asked. Vary the
              topics and keep the conversation moving like a real scene.
            - $judging
            - "feedback_th": one short Thai sentence. Start it with exactly "ถูกต้อง!" when the answer is correct,
              or exactly "ยังไม่ถูกนะ" when it is not and then say briefly in Thai what the learner could have said.
              Stay encouraging, but never praise a wrong answer as right.
            - "correct": true or false, matching your feedback_th.
            - "hint_hanzi"/"hint_pinyin": what the LEARNER could say to answer the question in your "hanzi" of
              this same reply — first person, reusing words from that question (pinyin with tone marks).
              Not an answer to an older question, and not a greeting or thank-you unless your question asks for one.
            - In the opening message set correct=true and feedback_th="".$altRule
            - Reply with JSON only, exactly these keys in this order:
              {"feedback_th": string, "correct": boolean, "hanzi": string, "pinyin": string, "thai": string,
               "hint_hanzi": string, "hint_pinyin": string$altKeys}
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
        @SerialName("alt_hanzi") val altHanzi: String = "",
        @SerialName("alt_pinyin") val altPinyin: String = "",
        @SerialName("alt_thai") val altThai: String = "",
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

        /** How category bosses judge: an understandable answer to the question. */
        private const val STRICT_JUDGING =
            "Judge the learner's answer against the question the app quotes. It is correct only when it is " +
                "an understandable Chinese reply that actually answers that question. Small grammar mistakes and " +
                "homophone characters from speech recognition are fine. Answers only in Thai or English, empty " +
                "answers, or replies that ignore the question (e.g. just 谢谢 to a yes/no question) are not correct."

        /** How special-mode characters judge: by meaning, the way people really talk. */
        private const val FLEXIBLE_JUDGING =
            "Judge the learner's answer against the question the app quotes BY MEANING, not by textbook grammar. " +
                "This is everyday spoken Chinese: short answers (要咖啡 / 对 / 好的 / 不用了), a dropped subject, " +
                "spoken particles (啊、呢、吧、嘛、了), fillers (嗯、那个), a different word order or other words with " +
                "the same meaning are all correct as long as they answer the question. Homophone characters from " +
                "speech recognition are fine. Only answers just in Thai or English, empty answers, or replies that " +
                "do not answer the question are not correct."

        /** Special modes: the extra "say it another way" fields. */
        private const val ALT_RULE =
            "- \"alt_hanzi\"/\"alt_pinyin\"/\"alt_thai\": ANOTHER natural way to say what the learner meant in " +
                "their last answer (pinyin with tone marks, Thai meaning). If they spoke in full or formally, give a " +
                "casual spoken version; if they were short or casual, give a fuller standard sentence. If the answer " +
                "was not correct, give a good spoken answer to your question. Never just copy the learner's words. " +
                "Leave them empty in the opening message."

        /**
         * One focus word per round: the shuffled [targets], with the [review] words on rounds 1, 3, …
         * so the opening question always comes from the scene itself.
         */
        fun steer(targets: List<String>, review: List<String>): List<String> {
            if (targets.isEmpty()) return review
            val old = ArrayDeque(review)
            var next = 0
            return List(GameRules.BOSS_ROUNDS) { round ->
                if (round % 2 == 1 && old.isNotEmpty()) old.removeFirst() else targets[next++ % targets.size]
            }
        }

        /** Livelier than the default so replays do not all open the same way. */
        private const val TEMPERATURE = 1.0

        /** Characters that say nothing about what a question is about. */
        private const val FILLER = "你我他她它们的了吗呢吧啊呀是很也都不和这那个"

        /** Words in the Thai feedback that mean the answer was right / not right. */
        private val RIGHT_WORDS = listOf("ถูก", "เก่ง", "เยี่ยม", "ดีมาก", "สุดยอด", "เจ๋ง", "ใช่แล้ว", "ตอบได้ดี")
        private val WRONG_WORDS = listOf(
            "ไม่ถูก", "ยังไม่", "ผิด", "เกือบ", "ไม่ตรง", "ไม่ใช่", "ไม่เป็นไร",
            "ลองตอบ", "ลองพูด", "ควรตอบ", "ควรพูด", "น่าจะตอบ", "น่าจะพูด",
        )

        /**
         * What the Thai [feedback] tells the learner: true for right, false for not right, null when it
         * says neither. The earliest telling word wins, so "ยังไม่ถูก" reads as wrong and
         * "เก่งมาก! ลองพูดช้าๆ" as right.
         */
        fun verdictOf(feedback: String): Boolean? {
            fun firstIndex(words: List<String>) = words.mapNotNull { w -> feedback.indexOf(w).takeIf { it >= 0 } }.minOrNull()
            val right = firstIndex(RIGHT_WORDS)
            val wrong = firstIndex(WRONG_WORDS)
            return when {
                wrong != null && (right == null || wrong <= right) -> false
                right != null -> true
                else -> null
            }
        }

        /**
         * True when two questions ask about the same thing: the meaningful characters of their last
         * clause (the question itself, without a greeting like 你好！) mostly overlap.
         */
        fun sameQuestion(a: String, b: String): Boolean {
            fun topic(q: String) = (q.split(CLAUSE_BREAK).lastOrNull { it.any(Pinyin::isHan) } ?: q)
                .filter { Pinyin.isHan(it) && it !in FILLER }.toSet()
            val ta = topic(a)
            val tb = topic(b)
            if (ta.isEmpty() || tb.isEmpty()) return ChineseText.normalize(a) == ChineseText.normalize(b)
            return (ta intersect tb).size.toDouble() / (ta union tb).size >= SAME_QUESTION_OVERLAP
        }

        private const val SAME_QUESTION_OVERLAP = 0.75
        private val CLAUSE_BREAK = Regex("[！!。.，,；;：:]")

        /** How many of a boss's latest questions carry over into the next game. */
        private const val RECENT_LIMIT = 10
        private val recent = mutableMapOf<String, ArrayDeque<String>>()

        @Synchronized
        private fun recentQuestions(bossName: String): List<String> = recent[bossName].orEmpty().toList()

        @Synchronized
        private fun rememberQuestion(bossName: String, hanzi: String) {
            val list = recent.getOrPut(bossName) { ArrayDeque() }
            list.remove(hanzi)
            list.addLast(hanzi)
            while (list.size > RECENT_LIMIT) list.removeFirst()
        }

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
