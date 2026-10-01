package com.example.nihaogo.data.content

import com.example.nihaogo.data.ai.ScriptedBossEngine
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.speech.ChineseText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Catches mistakes in categories.json before they reach the app. */
class ContentValidationTest {

    private val pack = ContentRepository.parse(
        File("src/main/assets/${ContentRepository.CATEGORIES_PATH}").readText()
    )

    @Test
    fun hasEightUniqueCategories() {
        assertEquals(8, pack.categories.size)
        assertEquals(8, pack.categories.map { it.id }.toSet().size)
    }

    @Test
    fun everyCategoryHasEnoughContent() {
        pack.categories.forEach { c ->
            assertTrue("${c.id} words", c.words.size in 12..15)
            assertTrue("${c.id} sentences", c.sentences.size in 6..8)
            assertTrue("${c.id} listening", c.listening.size >= 5)
            assertEquals("${c.id} boss turns", GameRules.BOSS_ROUNDS, c.boss.script.size)
            c.words.forEach { w ->
                assertTrue("${c.id}/${w.hanzi}", listOf(w.hanzi, w.pinyin, w.thai, w.emoji).all { it.isNotBlank() })
            }
        }
    }

    @Test
    fun sentenceTilesSpellTheSentence() {
        pack.categories.flatMap { it.sentences }.forEach { s ->
            assertEquals(s.hanzi, ChineseText.normalize(s.hanzi), ChineseText.normalize(s.tiles.joinToString("")))
            assertTrue("${s.hanzi}: distractor is also a tile", s.distractors.none { it in s.tiles })
        }
    }

    @Test
    fun listeningAnswersAreValid() {
        pack.categories.flatMap { it.listening }.forEach { item ->
            assertEquals(item.hanzi, 4, item.options.size)
            assertTrue(item.hanzi, item.answer in item.options.indices)
            assertEquals(item.hanzi, item.options.size, item.options.toSet().size)
        }
    }

    @Test
    fun scriptedBossAcceptsItsOwnSampleAnswers() = runBlocking {
        pack.categories.forEach { category ->
            val engine = ScriptedBossEngine(category)
            engine.start()
            category.boss.script.forEachIndexed { i, turn ->
                val judgement = engine.answer(turn.sampleHanzi, isFinal = i == category.boss.script.lastIndex)
                assertTrue("${category.id} turn $i sample '${turn.sampleHanzi}' rejected", judgement.correct)
            }
        }
    }

    @Test
    fun scriptedBossRejectsNonChinese() = runBlocking {
        val engine = ScriptedBossEngine(pack.categories.first())
        engine.start()
        assertTrue(!engine.answer("สวัสดี", isFinal = false).correct)
    }
}
