package com.example.nihaogo.speech

import com.example.nihaogo.data.content.Line

/**
 * Offline helpers for the special modes, where everyday spoken Chinese counts as much as the
 * textbook sentence: a dropped subject, a spoken opener or an end particle does not change the meaning.
 */
object SpokenChinese {
    /** Openers that say nothing about what is asked (请问你在哪儿 = 你在哪儿). */
    private val OPENERS = listOf("请问", "那么", "嗯", "喂", "哎", "诶")

    /** Particles that close a spoken sentence (你去哪儿呀 = 你去哪儿). */
    private const val ENDINGS = "啊呀呢吧嘛啦哦"

    /** Subjects that speakers often leave out (我要咖啡 → 要咖啡). */
    private val SUBJECTS = listOf("我们", "你们", "我", "你")

    /** [text] without punctuation, spoken openers or end particles, for comparing meanings. */
    fun core(text: String): String {
        var t = ChineseText.normalize(text)
        while (true) {
            val opener = OPENERS.firstOrNull { t.startsWith(it) && t.length > it.length } ?: break
            t = t.removePrefix(opener)
        }
        return t.trimEnd { it in ENDINGS }.ifEmpty { ChineseText.normalize(text) }
    }

    /** 0–100 like [ChineseText.similarityScore], but spoken openers, particles and a dropped subject don't count against. */
    fun similarity(target: String, heard: String): Int {
        if (!ChineseText.containsChinese(heard)) return 0
        val t = core(target)
        val h = core(heard)
        return maxOf(
            ChineseText.similarityScore(target, heard),
            ChineseText.similarityScore(t, h),
            ChineseText.similarityScore(withoutSubject(t) ?: t, withoutSubject(h) ?: h),
        )
    }

    /** True when [a] and [b] say the same thing apart from spoken extras. */
    fun sameMeaning(a: String, b: String): Boolean = core(a) == core(b)

    /** [hanzi] with its leading 我/你 subject dropped, or null when that would change the meaning (你家, 我的…). */
    fun withoutSubject(hanzi: String): String? {
        val subject = SUBJECTS.firstOrNull { hanzi.startsWith(it) } ?: return null
        val rest = hanzi.removePrefix(subject)
        if (rest.firstOrNull()?.let { it in "的家们" } != false || rest.count(Pinyin::isHan) < 2) return null
        return rest
    }

    /**
     * Another way to say what the learner [said], using [model] (a sample answer or question):
     * the model itself when the learner put it differently, otherwise its casual form without a
     * subject. Falls back to [model] when there is no casual form.
     */
    fun alternative(said: String, model: Line): Line {
        if (!sameMeaning(said, model.hanzi)) return model
        val casual = withoutSubject(model.hanzi) ?: return model
        val pinyin = Pinyin.of(casual).replaceFirstChar { it.uppercase() }
        val thai = listOf(model.thai, "(ภาษาพูด ละประธานได้)").filter { it.isNotBlank() }.joinToString(" ")
        return Line(casual, pinyin, thai)
    }
}
