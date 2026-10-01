package com.example.nihaogo.data.user

import com.example.nihaogo.data.content.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {

    @Test
    fun starThresholds() {
        assertEquals(0, GameRules.starsForScore(49))
        assertEquals(1, GameRules.starsForScore(50))
        assertEquals(2, GameRules.starsForScore(70))
        assertEquals(3, GameRules.starsForScore(90))
    }

    @Test
    fun bossNeedsFourCorrect() {
        assertEquals(0, GameRules.bossStars(3))
        assertEquals(2, GameRules.bossStars(4))
        assertEquals(3, GameRules.bossStars(5))
    }

    @Test
    fun coinsOnlyForImprovement() {
        val first = GameRules.applyStepResult(UserProgress(), "greeting", Step.Vocab, 2)
        assertEquals(10, first.coinsEarned)
        assertEquals(10, first.progress.coins)

        val same = GameRules.applyStepResult(first.progress, "greeting", Step.Vocab, 2)
        assertEquals(0, same.coinsEarned)
        assertFalse(same.improved)

        val better = GameRules.applyStepResult(first.progress, "greeting", Step.Vocab, 3)
        assertEquals(5, better.coinsEarned)
        assertEquals(3, better.progress.stars("greeting", Step.Vocab))
    }

    @Test
    fun firstBossWinPaysBonusAndTrophy() {
        val won = GameRules.applyStepResult(UserProgress(), "greeting", Step.Boss, 2)
        assertEquals(2 * GameRules.COINS_PER_STAR + GameRules.BOSS_WIN_BONUS, won.coinsEarned)
        assertEquals(1, won.progress.trophies)

        val again = GameRules.applyStepResult(won.progress, "greeting", Step.Boss, 3)
        assertEquals(GameRules.COINS_PER_STAR, again.coinsEarned)
    }

    @Test
    fun spendFailsWhenBroke() {
        assertNull(GameRules.spend(UserProgress(coins = 4), 5))
        assertEquals(1, GameRules.spend(UserProgress(coins = 6), 5)?.coins)
    }

    @Test
    fun categoriesUnlockAfterPreviousBoss() {
        val ids = listOf("a", "b", "c")
        val fresh = UserProgress()
        assertTrue(GameRules.isCategoryUnlocked(ids, "a", fresh))
        assertFalse(GameRules.isCategoryUnlocked(ids, "b", fresh))

        val beatA = GameRules.applyStepResult(fresh, "a", Step.Boss, 2).progress
        assertTrue(GameRules.isCategoryUnlocked(ids, "b", beatA))
        assertFalse(GameRules.isCategoryUnlocked(ids, "c", beatA))
    }

    @Test
    fun devUnlockOpensEverything() {
        val dev = UserProgress(devUnlockAll = true)
        assertTrue(GameRules.isCategoryUnlocked(listOf("a", "b"), "b", dev))
        assertTrue(GameRules.isStepUnlocked("b", Step.Boss, dev))
    }

    @Test
    fun stepsUnlockInOrder() {
        val fresh = UserProgress()
        assertTrue(GameRules.isStepUnlocked("a", Step.Vocab, fresh))
        assertFalse(GameRules.isStepUnlocked("a", Step.Sentence, fresh))

        val vocabDone = GameRules.applyStepResult(fresh, "a", Step.Vocab, 1).progress
        assertTrue(GameRules.isStepUnlocked("a", Step.Sentence, vocabDone))
        assertFalse(GameRules.isStepUnlocked("a", Step.Listening, vocabDone))
    }
}
