package com.example.nihaogo.speech

import com.example.nihaogo.data.content.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenChineseTest {

    @Test
    fun spokenOpenersAndEndParticlesDoNotChangeTheMeaning() {
        assertEquals("你下午在哪儿", SpokenChinese.core("请问你下午在哪儿呀？"))
        assertTrue(SpokenChinese.sameMeaning("嗯，你去哪儿呢？", "你去哪儿？"))
    }

    @Test
    fun aDroppedSubjectStillMatches() {
        assertTrue(SpokenChinese.similarity("你下午在哪儿？", "下午在哪儿啊") >= 90)
        assertTrue(SpokenChinese.similarity("我要一杯咖啡。", "要一杯咖啡") >= 90)
    }

    @Test
    fun subjectIsKeptWhenItIsPartOfTheMeaning() {
        assertNull(SpokenChinese.withoutSubject("你家有龙玉吗？"))
        assertNull(SpokenChinese.withoutSubject("我的书"))
        assertEquals("要咖啡。", SpokenChinese.withoutSubject("我要咖啡。"))
    }

    @Test
    fun alternativeIsTheModelWhenTheLearnerSaidItDifferently() {
        val model = Line("我要一杯咖啡。", "Wǒ yào yì bēi kāfēi.", "ขอกาแฟหนึ่งแก้ว")
        assertEquals(model, SpokenChinese.alternative("要咖啡", model))
    }

    @Test
    fun alternativeIsTheCasualFormWhenTheLearnerSaidTheModel() {
        val model = Line("我要一杯咖啡。", "Wǒ yào yì bēi kāfēi.", "ขอกาแฟหนึ่งแก้ว")
        val alt = SpokenChinese.alternative("我要一杯咖啡", model)
        assertEquals("要一杯咖啡。", alt.hanzi)
        assertTrue(alt.pinyin.isNotBlank())
        assertTrue(alt.thai.contains("ภาษาพูด"))
    }
}
