package com.example.nihaogo.data.user

import com.example.nihaogo.data.content.ContentRepository
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.SpecialRules.SceneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpecialRulesTest {

    private val special = ContentRepository.parseSpecial(File("src/main/assets/${ContentRepository.SPECIAL_PATH}").readText())
    private val detective = special.detective
    private val doctor = special.careers.first { it.id == "doctor" }

    private fun beat(categoryId: String, progress: UserProgress = UserProgress()) =
        GameRules.applyStepResult(progress, categoryId, Step.Boss, 2).progress

    @Test
    fun modesOpenAfterTheirCategoryBoss() {
        assertFalse(SpecialRules.isModeOpen(doctor, UserProgress()))
        assertTrue(SpecialRules.isModeOpen(doctor, beat("numbers")))
        assertFalse(SpecialRules.isModeOpen(detective, beat("numbers")))
        assertTrue(SpecialRules.isModeOpen(detective, beat("food")))
        assertTrue(SpecialRules.isModeOpen(detective, UserProgress(devUnlockAll = true)))
    }

    @Test
    fun firstSceneIsFreeLaterScenesNeedPreviousAndCoins() {
        val open = beat("numbers")
        assertEquals(SceneState.Open, SpecialRules.sceneState(doctor, 0, open))
        assertEquals(SceneState.Locked, SpecialRules.sceneState(doctor, 1, open))

        val trained = SpecialRules.applySceneResult(open, doctor, 0, 2).progress
        assertEquals(SceneState.NeedsPurchase, SpecialRules.sceneState(doctor, 1, trained))

        val bought = SpecialRules.purchase(trained, doctor, 1)!!
        assertEquals(trained.coins - SpecialRules.SCENE_COST, bought.coins)
        assertEquals(SceneState.Open, SpecialRules.sceneState(doctor, 1, bought))
    }

    @Test
    fun purchaseFailsWithoutCoins() {
        assertNull(SpecialRules.purchase(UserProgress(coins = SpecialRules.SCENE_COST - 1), doctor, 1))
    }

    @Test
    fun coinsOnlyForImprovementAndFinishBonusOnce() {
        val last = doctor.scenes.lastIndex
        val first = SpecialRules.applySceneResult(UserProgress(), doctor, last, 2)
        assertEquals(2 * GameRules.COINS_PER_STAR + SpecialRules.FINISH_BONUS, first.coinsEarned)

        val same = SpecialRules.applySceneResult(first.progress, doctor, last, 2)
        assertEquals(0, same.coinsEarned)

        val better = SpecialRules.applySceneResult(first.progress, doctor, last, 3)
        assertEquals(GameRules.COINS_PER_STAR, better.coinsEarned)
    }

    @Test
    fun careerRankFollowsFinishedScenes() {
        var p = UserProgress()
        assertNull(SpecialRules.rank(doctor, p))
        doctor.scenes.indices.forEach { i ->
            p = SpecialRules.applySceneResult(p, doctor, i, 1).progress
            assertEquals(doctor.ranks[i], SpecialRules.rank(doctor, p))
        }
        assertTrue(SpecialRules.isFinished(doctor, p))
    }

    @Test
    fun accusationStarsDropPerWrongGuess() {
        assertEquals(3, SpecialRules.accusationStars(0))
        assertEquals(2, SpecialRules.accusationStars(1))
        assertEquals(1, SpecialRules.accusationStars(2))
        assertEquals(1, SpecialRules.accusationStars(5))
    }
}
