package com.example.nihaogo.speech

import com.example.nihaogo.data.content.Boss
import com.example.nihaogo.data.content.ContentPack
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.Sentence
import com.example.nihaogo.data.content.SpecialPack
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import java.text.Normalizer

/**
 * Pinyin (with tone marks) for any Chinese text shown in the app.
 *
 * The hand-written pinyin in the content pack is the most accurate source (neutral tones, tone
 * sandhi, polyphones like 了/的), so [register] splits it into per-character syllables and remembers
 * every word and sentence tile. Text that is not in the pack falls back to pinyin4j.
 */
object Pinyin {

    private val toneMarkFormat = HanyuPinyinOutputFormat().apply {
        toneType = HanyuPinyinToneType.WITH_TONE_MARK
        caseType = HanyuPinyinCaseType.LOWERCASE
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }

    private val punctuation = mapOf(
        '。' to ".", '，' to ",", '、' to ",", '！' to "!", '？' to "?", '：' to ":", '；' to ";",
        '（' to "(", '）' to ")", '“' to "\"", '”' to "\"", '…' to "…",
    )

    /** Hanzi → pinyin for whole words and sentence tiles, longest entry wins. */
    @Volatile private var lexicon: Map<String, String> = emptyMap()

    /** Most common syllable of each character inside the pack's sentences. */
    @Volatile private var charReadings: Map<Char, String> = emptyMap()

    @Volatile private var longest = 1

    fun register(pack: ContentPack, special: SpecialPack? = null) {
        val entries = LinkedHashMap<String, String>()
        val counts = HashMap<Char, MutableMap<String, Int>>()

        fun learn(hanzi: String, pinyin: String) {
            val syllables = align(hanzi, pinyin) ?: return
            hanzi.filter(::isHan).forEachIndexed { i, c ->
                counts.getOrPut(c) { HashMap() }.merge(syllables[i].text.lowercase(), 1, Int::plus)
            }
        }

        // Sentence tiles take their reading from the sentence they belong to.
        fun learnSentence(s: Sentence) {
            learn(s.hanzi, s.pinyin)
            val syllables = align(s.hanzi, s.pinyin) ?: return
            var at = 0
            s.tiles.forEach { tile ->
                val size = tile.count(::isHan)
                if (size > 0 && at + size <= syllables.size) {
                    val text = s.pinyin.substring(syllables[at].start, syllables[at + size - 1].end)
                    entries[tile] = if (at == 0) text.replaceFirstChar(Char::lowercaseChar) else text
                }
                at += size
            }
        }

        fun learnBoss(boss: Boss) = boss.script.forEach {
            learn(it.hanzi, it.pinyin)
            learn(it.sampleHanzi, it.samplePinyin)
        }

        pack.categories.forEach { c ->
            learn(c.titleZh, c.titlePinyin)
            c.words.forEach { learn(it.hanzi, it.pinyin) }
            c.listening.forEach { learn(it.hanzi, it.pinyin) }
            learnBoss(c.boss)
            c.sentences.forEach(::learnSentence)
        }
        special?.modes?.forEach { mode ->
            learn(mode.titleZh, mode.titlePinyin)
            mode.confession?.let { learn(it.hanzi, it.pinyin) }
            mode.suspects.forEach { s ->
                learn(s.alibi.hanzi, s.alibi.pinyin)
                s.questions.forEach {
                    learn(it.question.hanzi, it.question.pinyin)
                    learn(it.answer.hanzi, it.answer.pinyin)
                }
            }
            mode.scenes.forEach { scene ->
                learn(scene.titleZh, scene.titlePinyin)
                scene.words.forEach { learn(it.hanzi, it.pinyin) }
                when (scene) {
                    is Scene.Dialogue -> scene.turns.forEach { turn ->
                        (listOf(turn.npc, turn.reply) + turn.options).forEach { learn(it.hanzi, it.pinyin) }
                        turn.task?.options?.forEach { learn(it.hanzi, it.pinyin) }
                    }
                    is Scene.Puzzle -> scene.sentences.forEach(::learnSentence)
                    is Scene.Roleplay -> learnBoss(scene.boss)
                    else -> Unit
                }
            }
        }
        // Hand-written word pinyin overrides anything derived from sentences.
        pack.categories.forEach { c ->
            entries[c.titleZh] = c.titlePinyin
            entries[c.boss.name] = c.boss.namePinyin
            c.words.forEach { entries[it.hanzi] = it.pinyin }
        }
        special?.modes?.forEach { mode ->
            entries[mode.titleZh] = mode.titlePinyin
            mode.suspects.forEach { entries[it.name] = it.namePinyin }
            mode.scenes.forEach { scene ->
                entries[scene.titleZh] = scene.titlePinyin
                scene.words.forEach { entries[it.hanzi] = it.pinyin }
                if (scene is Scene.Roleplay) entries[scene.boss.name] = scene.boss.namePinyin
                if (scene is Scene.Dialogue) {
                    (listOfNotNull(scene.npc) + scene.turns.mapNotNull { it.speaker }).forEach {
                        entries[it.name] = it.namePinyin
                    }
                }
            }
        }

        lexicon = entries
        charReadings = counts.mapValues { (_, byReading) -> byReading.maxBy { it.value }.key }
        longest = entries.keys.maxOfOrNull { it.length } ?: 1
    }

    /** Pinyin for [text]: syllables separated by spaces, punctuation kept, non-Chinese passed through. */
    fun of(text: String): String {
        val out = StringBuilder()
        fun token(t: String) {
            if (out.isNotEmpty() && !out.last().isWhitespace()) out.append(' ')
            out.append(t)
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                isHan(c) -> {
                    val len = (minOf(longest, text.length - i) downTo 1).firstOrNull { lexicon.containsKey(text.substring(i, i + it)) }
                    if (len != null) {
                        token(lexicon.getValue(text.substring(i, i + len)))
                        i += len
                    } else {
                        token(syllable(c))
                        i++
                    }
                }
                c in punctuation -> { out.append(punctuation.getValue(c)); i++ }
                c.isWhitespace() -> { if (out.isNotEmpty() && !out.last().isWhitespace()) out.append(' '); i++ }
                else -> {
                    // Keep runs of Latin letters / digits (e.g. "3点") together.
                    val start = i
                    while (i < text.length && !isHan(text[i]) && !text[i].isWhitespace() && text[i] !in punctuation) i++
                    token(text.substring(start, i))
                }
            }
        }
        return out.toString().trim()
    }

    fun isHan(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

    private fun syllable(c: Char): String =
        charReadings[c] ?: PinyinHelper.toHanyuPinyinStringArray(c, toneMarkFormat)?.firstOrNull() ?: c.toString()

    internal data class Syllable(val text: String, val start: Int, val end: Int)

    /**
     * Splits hand-written [pinyin] into one syllable per Chinese character of [hanzi]
     * (e.g. 学生 + "xuésheng" → xué, sheng), or null when they do not line up.
     */
    internal fun align(hanzi: String, pinyin: String): List<Syllable>? {
        val letters = StringBuilder()
        val origin = ArrayList<Int>()
        pinyin.forEachIndexed { i, c ->
            baseLetter(c)?.let { letters.append(it); origin += i }
        }
        val chars = hanzi.filter(::isHan)
        val ends = IntArray(chars.length)

        fun match(ci: Int, pos: Int): Boolean {
            if (ci == chars.length) return pos == letters.length
            val c = chars[ci]
            val candidates = ChineseText.readings(c) + if (c == '儿') setOf("r") else emptySet()
            return candidates.sortedByDescending { it.length }.any { r ->
                letters.startsWith(r, pos) && run { ends[ci] = pos + r.length; match(ci + 1, pos + r.length) }
            }
        }
        if (chars.isEmpty() || !match(0, 0)) return null

        var from = 0
        return chars.indices.map { ci ->
            val start = origin[from]
            var end = origin[ends[ci] - 1] + 1
            while (end < pinyin.length && Character.getType(pinyin[end]) == Character.NON_SPACING_MARK.toInt()) end++
            from = ends[ci]
            Syllable(pinyin.substring(start, end), start, end)
        }
    }

    /** Toneless lowercase letter for a pinyin character (ü → v), or null for spaces/punctuation. */
    private fun baseLetter(c: Char): Char? {
        val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        val base = decomposed[0].lowercaseChar()
        if (base !in 'a'..'z') return null
        return if (base == 'u' && '̈' in decomposed) 'v' else base
    }
}
