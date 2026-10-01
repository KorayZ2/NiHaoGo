package com.example.nihaogo.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseTextTest {

    @Test
    fun exactMatchScoresFull() {
        assertEquals(100, ChineseText.similarityScore("你好", "你好"))
    }

    @Test
    fun punctuationIsIgnored() {
        assertEquals(100, ChineseText.similarityScore("你叫什么名字？", "你叫什么名字"))
    }

    @Test
    fun homophoneCountsAsCorrect() {
        // Recognizers often return 他 when the learner says 她.
        assertEquals(100, ChineseText.similarityScore("她", "他"))
        assertEquals(100, ChineseText.similarityScore("一", "衣"))
    }

    @Test
    fun commonlyConfusedSoundsStillPass() {
        // 四 (si) is often recognized as 是 (shi).
        val si = ChineseText.similarityScore("四", "是")
        assertTrue("was $si", si in 80..89)
        assertTrue(ChineseText.similarityScore("老师", "脑斯") >= 80) // l/n, sh/s
        assertTrue(ChineseText.similarityScore("朋友", "盆友") >= 80) // eng/en
        assertTrue(ChineseText.similarityScore("我是泰国人", "我四泰国人") >= 90)
    }

    @Test
    fun differentSoundsDoNotPass() {
        assertTrue(ChineseText.similarityScore("四", "八") < 50)
        assertTrue(ChineseText.similarityScore("四", "西") < 80) // si vs xi
    }

    @Test
    fun arabicDigitsAreSpelledOut() {
        assertEquals("十二岁", ChineseText.normalize("12岁"))
        assertEquals("现在三点", ChineseText.normalize("现在3点"))
        assertEquals("二十块", ChineseText.normalize("20块"))
        assertEquals("一百零五", ChineseText.normalize("105"))
        assertEquals("一百一十", ChineseText.normalize("110"))
        assertEquals("一千零五十", ChineseText.normalize("1050"))
        assertEquals(100, ChineseText.similarityScore("我十二岁。", "我12岁"))
    }

    @Test
    fun partialAnswerGetsPartialScore() {
        val score = ChineseText.similarityScore("我想吃面条", "我想吃")
        assertTrue("was $score", score in 50..70)
    }

    @Test
    fun unrelatedAnswerScoresLow() {
        assertTrue(ChineseText.similarityScore("谢谢", "再见") < 50)
        assertEquals(0, ChineseText.similarityScore("谢谢", ""))
    }

    @Test
    fun containsSoundAlikeFindsKeyword() {
        assertTrue(ChineseText.containsSoundAlike("我叫小美", "叫"))
        assertTrue(ChineseText.containsSoundAlike("我是泰国人", "泰国"))
        assertTrue(ChineseText.containsSoundAlike("明天10点见", "点"))
        assertFalse(ChineseText.containsSoundAlike("我叫小美", "学生"))
    }

    @Test
    fun detectsChinese() {
        assertTrue(ChineseText.containsChinese("hello 你好"))
        assertFalse(ChineseText.containsChinese("สวัสดี hello"))
    }
}
