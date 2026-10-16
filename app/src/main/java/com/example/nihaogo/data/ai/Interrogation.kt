package com.example.nihaogo.data.ai

import com.example.nihaogo.data.content.Line
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Suspect
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.speech.SpokenChinese
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A suspect's answer to one of the learner's questions. */
data class SuspectReply(
    val line: Line,
    /** False when the question was not understandable Chinese; it then does not count. */
    val understood: Boolean,
    val feedbackTh: String,
    /** Another way the learner could have asked the same thing. */
    val alternative: Line? = null,
)

/** The detective finale: the learner asks, a suspect answers. */
interface InterrogationEngine {
    val usesAi: Boolean
    suspend fun ask(suspect: Suspect, question: String): SuspectReply
}

/** Offline: answers the closest suggested question from special.json. */
class ScriptedInterrogation : InterrogationEngine {
    override val usesAi = false

    override suspend fun ask(suspect: Suspect, question: String): SuspectReply = reply(suspect, question)

    companion object {
        /** How close (0–100) a question must be to a suggested one to be understood. */
        const val MATCH_SCORE = 60

        val NOT_UNDERSTOOD = Line("我听不懂，请再说一遍。", "Wǒ tīng bu dǒng, qǐng zài shuō yí biàn.", "ฟังไม่เข้าใจ ช่วยพูดอีกครั้งได้ไหม")

        fun reply(suspect: Suspect, question: String): SuspectReply {
            if (!ChineseText.containsChinese(question)) {
                return SuspectReply(NOT_UNDERSTOOD, false, "ถามเป็นภาษาจีนนะ แตะคำถามตัวอย่างด้านล่างก็ได้")
            }
            // Spoken openers, end particles and a dropped subject don't stop a question from matching.
            val best = closest(suspect, question)
            return if (SpokenChinese.similarity(best.question.hanzi, question) >= MATCH_SCORE) {
                SuspectReply(best.answer, true, "", SpokenChinese.alternative(question, best.question))
            } else {
                SuspectReply(NOT_UNDERSTOOD, false, "ยังไม่มีคำตอบสำหรับคำถามนี้ ลองใช้คำถามตัวอย่างดูนะ", best.question)
            }
        }

        /** The suggested question to [suspect] that is closest to [question]. */
        fun closest(suspect: Suspect, question: String) =
            suspect.questions.maxBy { SpokenChinese.similarity(it.question.hanzi, question) }
    }
}

/** Gemini voices all suspects from their secret facts, keeping one shared memory of the case. */
class AiInterrogation(
    private val mode: SpecialMode,
    private val gemini: GeminiClient,
    /** Words from the categories already beaten, which the suspects may use too. */
    private val reviewWords: List<Word> = emptyList(),
) : InterrogationEngine {
    private val history = mutableListOf<ChatTurn>()

    override val usesAi = true

    override suspend fun ask(suspect: Suspect, question: String): SuspectReply {
        history += ChatTurn(fromUser = true, text = """Suspect: ${suspect.name} (${suspect.namePinyin}). The learner asks: "$question"""")
        val raw = try {
            gemini.generateJson(systemPrompt(), history)
        } catch (e: Exception) {
            history.removeAt(history.lastIndex) // allow a clean retry
            throw e
        }
        val reply = parse(raw)
        if (reply == null || !AiBossEngine.isBossLine(reply.hanzi)) {
            // Unusable model output: answer from the script so the learner is never stuck.
            history.removeAt(history.lastIndex)
            return ScriptedInterrogation.reply(suspect, question)
        }
        history += ChatTurn(fromUser = false, text = raw)
        return SuspectReply(Line(reply.hanzi, reply.pinyin, reply.thai), reply.understood, reply.feedbackTh, alternativeFor(suspect, question, reply))
    }

    /** The model's other way to ask, or else the closest suggested question. */
    private fun alternativeFor(suspect: Suspect, question: String, reply: AiReply): Line {
        if (AiBossEngine.isBossLine(reply.altHanzi) && !SpokenChinese.sameMeaning(reply.altHanzi, question)) {
            return Line(reply.altHanzi, reply.altPinyin, reply.altThai)
        }
        return SpokenChinese.alternative(question, ScriptedInterrogation.closest(suspect, question).question)
    }

    private fun systemPrompt(): String {
        val suspects = mode.suspects.joinToString("\n") { "- ${it.name} (${it.namePinyin}), ${it.roleTh}: ${it.secret}" }
        val known = (mode.allWords + reviewWords).distinctBy { it.hanzi }.joinToString("、") { it.hanzi }
        return """
            You voice the suspects in a detective game for Thai beginners learning Chinese (children and teenagers).
            Case: at Grandpa Wang's 80th birthday party the family's dragon jade (龙玉) disappeared from its glass
            cabinet at about 3:30 pm. The learner is a young detective questioning the suspects in Chinese.
            Suspects and their secret facts:
            $suspects
            Words the learner knows: $known

            Rules:
            - Each user message names the suspect being asked and quotes the learner's question. Answer ONLY as
              that suspect, in the first person.
            - "hanzi": short, simple Simplified Chinese (beginner level, at most about 20 characters). Stay
              consistent with the facts above and with everything already said. Never say who took the jade and
              never confess; a nervous suspect may lie, but leaves small contradictions the learner can notice.
            - "understood": true when the question is understandable Chinese, judged by MEANING, not textbook
              grammar. Everyday spoken questions count: a dropped subject (下午在哪儿？), spoken openers and
              particles (请问、那、嗯、啊、呢、吧), a different word order or other words with the same meaning, and
              homophone characters from speech recognition. If it is not Chinese or makes no sense, set false and
              let the suspect say politely that they did not understand.
            - "feedback_th": empty when understood; otherwise one short Thai sentence suggesting how to ask.
            - "alt_hanzi"/"alt_pinyin"/"alt_thai": ANOTHER natural way to ask what the learner asked (pinyin with
              tone marks, Thai meaning). If they asked in full or formally, give a casual spoken version; if they
              were short or casual, give a fuller standard question. If not understood, a good question they could
              ask this suspect. Never just copy the learner's words.
            - Reply with JSON only, exactly these keys:
              {"understood": boolean, "feedback_th": string, "hanzi": string, "pinyin": string, "thai": string,
               "alt_hanzi": string, "alt_pinyin": string, "alt_thai": string}
        """.trimIndent()
    }

    @Serializable
    private data class AiReply(
        val understood: Boolean = false,
        @SerialName("feedback_th") val feedbackTh: String = "",
        val hanzi: String = "",
        val pinyin: String = "",
        val thai: String = "",
        @SerialName("alt_hanzi") val altHanzi: String = "",
        @SerialName("alt_pinyin") val altPinyin: String = "",
        @SerialName("alt_thai") val altThai: String = "",
    )

    private fun parse(raw: String): AiReply? {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { json.decodeFromString(AiReply.serializer(), cleaned) }.getOrNull()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
