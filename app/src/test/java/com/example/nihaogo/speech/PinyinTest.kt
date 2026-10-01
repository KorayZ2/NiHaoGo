package com.example.nihaogo.speech

import com.example.nihaogo.data.content.ContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PinyinTest {

    private val pack = ContentRepository.parse(
        File("src/main/assets/${ContentRepository.CATEGORIES_PATH}").readText()
    ).also(Pinyin::register)

    private fun syllables(hanzi: String, pinyin: String) = Pinyin.align(hanzi, pinyin)?.map { it.text }

    @Test
    fun alignSplitsWrittenPinyinPerCharacter() {
        assertEquals(listOf("Wǒ", "shì", "xué", "sheng"), syllables("我是学生。", "Wǒ shì xuésheng."))
        assertEquals(listOf("yì", "diǎn", "r"), syllables("一点儿", "yìdiǎnr"))
        assertEquals(listOf("shí", "èr"), syllables("十二", "shí'èr"))
        assertNull(syllables("你好", "xièxie"))
    }

    @Test
    fun everyWrittenPinyinInTheContentLinesUp() {
        val pairs = pack.categories.flatMap { c ->
            listOf(c.titleZh to c.titlePinyin, c.boss.name to c.boss.namePinyin) +
                c.words.map { it.hanzi to it.pinyin } +
                c.sentences.map { it.hanzi to it.pinyin } +
                c.listening.map { it.hanzi to it.pinyin } +
                c.boss.script.flatMap { listOf(it.hanzi to it.pinyin, it.sampleHanzi to it.samplePinyin) }
        }
        val broken = pairs.filter { (h, p) -> Pinyin.align(h, p) == null }
        assertTrue("pinyin does not match hanzi: $broken", broken.isEmpty())
    }

    @Test
    fun ofUsesContentReadings() {
        assertEquals("xuésheng", Pinyin.of("学生"))
        assertEquals("Xiǎo Míng", Pinyin.of("小明"))
        // Sentence tile: reading comes from the sentence, neutral tone kept.
        assertEquals("shénme", Pinyin.of("什么"))
        assertEquals("wǒ shì xuésheng.", Pinyin.of("我是学生。"))
    }

    @Test
    fun ofFallsBackForUnknownText() {
        assertEquals("lóng", Pinyin.of("龙"))
        assertEquals("3 diǎn", Pinyin.of("3点"))
    }
}
