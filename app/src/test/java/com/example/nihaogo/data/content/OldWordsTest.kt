package com.example.nihaogo.data.content

import com.example.nihaogo.data.ai.AiBossEngine
import com.example.nihaogo.data.user.CategoryProgress
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.UserProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OldWordsTest {

    private val categories = ContentRepository.parse(File("src/main/assets/${ContentRepository.CATEGORIES_PATH}").readText()).categories
    private val special = ContentRepository.parseSpecial(File("src/main/assets/${ContentRepository.SPECIAL_PATH}").readText())
    private val doctor = special.careers.first { it.id == "doctor" }

    private fun beaten(vararg ids: String) =
        UserProgress(categories = ids.associateWith { CategoryProgress(mapOf(Step.Boss to 3)) })

    @Test
    fun learnedWordsComeOnlyFromBeatenCategories() {
        val words = OldWords.learned(categories, beaten("greeting", "food"))
        assertEquals(setOf(1, 4), words.map { it.stage }.toSet())
        assertTrue(words.any { it.word.hanzi == "咖啡" })
        assertTrue(words.none { it.word.hanzi == "手机" })
    }

    @Test
    fun debugUnlockCountsEveryCategory() {
        val words = OldWords.learned(categories, UserProgress(devUnlockAll = true))
        assertEquals((1..categories.size).toSet(), words.map { it.stage }.toSet())
    }

    @Test
    fun learnedWordsSkipTheModesOwnWords() {
        val own = listOf(categories.first().words.first())
        val words = OldWords.learned(categories, beaten("greeting"), exclude = own)
        assertTrue(words.none { it.word.hanzi == own.first().hanzi })
    }

    @Test
    fun sceneIntroFindsOldWordsUsedInTheDialogue() {
        val old = OldWords.inScene(doctor, 1, categories)
        val hanzi = old.map { it.word.hanzi }
        assertTrue(hanzi.toString(), "咖啡" in hanzi)
        assertEquals(4, old.first { it.word.hanzi == "咖啡" }.stage)
        // Single characters are too ambiguous to list, and the scene's own words are already taught.
        assertTrue(old.all { it.word.hanzi.length >= 2 })
        assertTrue(old.none { o -> doctor.scenes[1].words.any { it.hanzi == o.word.hanzi } })
    }

    @Test
    fun everyDialogueAndRoleplaySceneReviewsSomeOldWords() {
        special.modes.forEach { mode ->
            mode.scenes.forEachIndexed { i, scene ->
                if (scene is Scene.Dialogue || scene is Scene.Roleplay) {
                    assertTrue("${mode.id}/$i", OldWords.inScene(mode, i, categories).isNotEmpty())
                }
            }
        }
    }

    @Test
    fun reviewWordsTakeOddRoundsAndTheOpeningStaysOnTheScene() {
        val focus = AiBossEngine.steer(listOf("a", "b", "c"), listOf("X", "Y"))
        assertEquals(GameRules.BOSS_ROUNDS, focus.size)
        assertEquals(listOf("a", "X", "b", "Y", "c"), focus)
    }
}
