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

    @Test
    fun bossIsFreeUntilAnAttemptIsLost() {
        val fresh = UserProgress()
        assertEquals(GameRules.BossEntry.Free, GameRules.bossEntry(fresh, "a", DAY))
        assertEquals(fresh, GameRules.payForBoss(fresh, "a", DAY))

        val lost = GameRules.markBossAttempt(fresh, "a")
        assertEquals(GameRules.BossEntry.FreeRetry, GameRules.bossEntry(lost, "a", DAY))
        assertEquals(GameRules.BossEntry.Free, GameRules.bossEntry(lost, "b", DAY))

        val won = GameRules.markBossWon(lost, "a")
        assertEquals(GameRules.BossEntry.Free, GameRules.bossEntry(won, "a", DAY))
    }

    @Test
    fun oneFreeRetryPerDayAcrossBosses() {
        val lostBoth = UserProgress(coins = 15, bossRetry = setOf("a", "b"))
        val usedFree = GameRules.payForBoss(lostBoth, "a", DAY)!!
        assertEquals(15, usedFree.coins)
        assertEquals(GameRules.BossEntry.PaidRetry, GameRules.bossEntry(usedFree, "b", DAY))

        val paid = GameRules.payForBoss(usedFree, "b", DAY)!!
        assertEquals(15 - GameRules.BOSS_RETRY_COST, paid.coins)
        assertNull(GameRules.payForBoss(paid, "a", DAY))

        assertEquals(GameRules.BossEntry.FreeRetry, GameRules.bossEntry(paid, "a", "2026-10-14"))
    }

    @Test
    fun premiumRetriesForFree() {
        val premium = UserProgress(premium = true, bossRetry = setOf("a"), daily = DailyUsage(DAY, freeRetries = 1))
        assertEquals(GameRules.BossEntry.Free, GameRules.bossEntry(premium, "a", DAY))
        assertEquals(0, GameRules.adsLeft(premium, DAY))
    }

    @Test
    fun adsPayCoinsUpToTheDailyLimit() {
        var progress = UserProgress()
        repeat(GameRules.ADS_PER_DAY) { progress = GameRules.applyAdReward(progress, DAY)!! }
        assertEquals(GameRules.ADS_PER_DAY * GameRules.AD_REWARD, progress.coins)
        assertEquals(0, GameRules.adsLeft(progress, DAY))
        assertNull(GameRules.applyAdReward(progress, DAY))

        assertEquals(GameRules.ADS_PER_DAY, GameRules.adsLeft(progress, "2026-10-14"))
    }

    @Test
    fun todayIsLocalIsoDate() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(GameRules.today()))
    }

    private companion object {
        const val DAY = "2026-10-13"
    }
}
