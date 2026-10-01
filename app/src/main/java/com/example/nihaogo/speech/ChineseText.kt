package com.example.nihaogo.speech

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Text helpers for comparing what the learner said (speech-recognizer output) with the expected
 * Chinese. Comparison is done both by character and by toneless pinyin, because recognizers often
 * return a homophone (他/她/它) or Arabic digits (3点 for 三点).
 */
object ChineseText {

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        caseType = HanyuPinyinCaseType.LOWERCASE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    private val digits = "零一二三四五六七八九"

    /** Removes punctuation/whitespace, lowercases Latin text and spells Arabic numbers in Chinese. */
    fun normalize(text: String): String =
        Regex("\\d+").replace(text) { toChineseNumber(it.value) }
            .filter { it.isLetterOrDigit() }
            .lowercase()

    /** All toneless pinyin readings of a character (several for polyphones like 了 or 的). */
    fun readings(c: Char): Set<String> =
        PinyinHelper.toHanyuPinyinStringArray(c, pinyinFormat)?.toSet() ?: setOf(c.lowercase())

    /**
     * 0–100: how close [heard] is to [target], taking the better of character or pinyin match.
     * Syllables that differ only by a commonly confused sound (s/sh, n/l, an/ang…) still score
     * [FUZZY_SYLLABLE] so e.g. 四 (si) heard as 是 (shi) passes.
     */
    fun similarityScore(target: String, heard: String): Int {
        val t = normalize(target)
        val h = normalize(heard)
        if (t.isEmpty() || h.isEmpty()) return 0
        val byChar = similarity(t.toList(), h.toList()) { a, b -> if (a == b) 1.0 else 0.0 }
        val byPinyin = similarity(t.map(::readings), h.map(::readings)) { a, b ->
            a.maxOf { ra -> b.maxOf { rb -> syllableSimilarity(ra, rb) } }
        }
        return (100 * max(byChar, byPinyin)).roundToInt()
    }

    /** 0–1 closeness of two toneless pinyin syllables. */
    internal fun syllableSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val fa = fuzzy(a)
        val fb = fuzzy(b)
        if (fa == fb) return FUZZY_SYLLABLE
        // Partial credit by letters, kept below the fuzzy score so it never counts as a pass alone.
        return PARTIAL_WEIGHT * similarity(fa.toList(), fb.toList()) { x, y -> if (x == y) 1.0 else 0.0 }
    }

    /** Collapses sound pairs learners (and recognizers) often mix up into one spelling. */
    private fun fuzzy(syllable: String): String {
        val initial = initials.firstOrNull(syllable::startsWith).orEmpty()
        val final = syllable.removePrefix(initial)
        val fuzzyInitial = when (initial) {
            "zh" -> "z"
            "ch" -> "c"
            "sh" -> "s"
            "l" -> "n"
            else -> initial
        }
        val fuzzyFinal = when {
            final.endsWith("ang") -> final.dropLast(1)
            final.endsWith("eng") -> final.dropLast(1)
            final.endsWith("ing") -> final.dropLast(1)
            else -> final
        }
        return fuzzyInitial + fuzzyFinal
    }

    private const val FUZZY_SYLLABLE = 0.85
    private const val PARTIAL_WEIGHT = 0.7

    /** Pinyin initials, two-letter ones first so "zh" wins over "z". */
    private val initials = listOf(
        "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
        "j", "q", "x", "r", "z", "c", "s", "y", "w",
    )

    /** True when [text] contains [keyword] as a sequence of sound-alike characters. */
    fun containsSoundAlike(text: String, keyword: String): Boolean {
        val t = normalize(text).map(::readings)
        val k = normalize(keyword).map(::readings)
        if (k.isEmpty() || t.size < k.size) return false
        return (0..t.size - k.size).any { start ->
            k.indices.all { i -> t[start + i].any(k[i]::contains) }
        }
    }

    fun containsChinese(text: String): Boolean =
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    internal fun toChineseNumber(value: String): String {
        val n = value.toIntOrNull()
        if (n == null || n >= 10_000) return value.map { digits[it - '0'] }.joinToString("")
        return spell(n, leadingTen = false)
    }

    private fun spell(n: Int, leadingTen: Boolean): String = when {
        n < 10 -> digits[n].toString()
        n < 100 -> {
            val tens = n / 10
            val ones = n % 10
            val head = if (tens == 1 && !leadingTen) "十" else "${digits[tens]}十"
            head + if (ones == 0) "" else digits[ones]
        }
        n < 1000 -> "${digits[n / 100]}百" + rest(n % 100, 10)
        else -> "${digits[n / 1000]}千" + rest(n % 1000, 100)
    }

    /** Remainder after 百/千, inserting 零 for gaps (e.g. 105 → 一百零五, 110 → 一百一十). */
    private fun rest(r: Int, nextUnit: Int): String = when {
        r == 0 -> ""
        r < nextUnit -> "零" + spell(r, leadingTen = true)
        else -> spell(r, leadingTen = true)
    }

    /** 1 − normalized edit distance; [same] gives 0–1 likeness so near-misses cost less than 1. */
    private fun <T> similarity(a: List<T>, b: List<T>, same: (T, T) -> Double): Double {
        val distance = levenshtein(a, b, same)
        return 1.0 - distance / max(a.size, b.size)
    }

    private fun <T> levenshtein(a: List<T>, b: List<T>, same: (T, T) -> Double): Double {
        var prev = DoubleArray(b.size + 1) { it.toDouble() }
        for (i in 1..a.size) {
            val cur = DoubleArray(b.size + 1)
            cur[0] = i.toDouble()
            for (j in 1..b.size) {
                val cost = 1.0 - same(a[i - 1], b[j - 1])
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.size]
    }
}
