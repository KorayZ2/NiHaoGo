package com.example.nihaogo.data.user

import com.example.nihaogo.data.content.Step
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure game-economy rules (see PLAN.md §2.4) so they can be unit tested. */
object GameRules {
    const val COINS_PER_STAR = 5
    const val BOSS_WIN_BONUS = 30
    const val HINT_COST = 5
    const val BOSS_ROUNDS = 5
    const val BOSS_PASS_CORRECT = 4
    /** Pronunciation score needed before moving to the next vocab word. */
    const val SPEECH_PASS_SCORE = 80
    /** Failed speaking tries after which a vocab word can be skipped. */
    const val SKIP_AFTER_TRIES = 3
    /** Boss replays after a loss: this many free each day (across all bosses), then [BOSS_RETRY_COST]. */
    const val FREE_BOSS_RETRIES_PER_DAY = 1
    const val BOSS_RETRY_COST = 10
    const val AD_REWARD = 20
    const val ADS_PER_DAY = 2

    /** The local date as yyyy-MM-dd, used to reset the daily limits. */
    fun today(now: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)

    fun starsForScore(score: Int): Int = when {
        score >= 90 -> 3
        score >= 70 -> 2
        score >= 50 -> 1
        else -> 0
    }

    fun bossStars(correct: Int): Int = when {
        correct >= BOSS_ROUNDS -> 3
        correct >= BOSS_PASS_CORRECT -> 2
        else -> 0
    }

    data class StepOutcome(val progress: UserProgress, val coinsEarned: Int, val improved: Boolean)

    /**
     * Records a finished step. Coins are only paid for stars above the previous best,
     * so replaying a step cannot farm coins.
     */
    fun applyStepResult(progress: UserProgress, categoryId: String, step: Step, stars: Int): StepOutcome {
        val previous = progress.stars(categoryId, step)
        if (stars <= previous) return StepOutcome(progress, 0, improved = false)

        var coins = (stars - previous) * COINS_PER_STAR
        if (step == Step.Boss && previous == 0) coins += BOSS_WIN_BONUS

        val category = progress.categories[categoryId] ?: CategoryProgress()
        val updated = progress.copy(
            coins = progress.coins + coins,
            categories = progress.categories + (categoryId to category.copy(stars = category.stars + (step to stars))),
        )
        return StepOutcome(updated, coins, improved = true)
    }

    /** Returns null when the player cannot afford it. */
    fun spend(progress: UserProgress, amount: Int): UserProgress? =
        if (progress.coins >= amount) progress.copy(coins = progress.coins - amount) else null

    enum class BossEntry { Free, FreeRetry, PaidRetry }

    /** What starting this boss costs: nothing unless the last attempt was lost or abandoned. */
    fun bossEntry(progress: UserProgress, bossKey: String, today: String): BossEntry = when {
        progress.premium || bossKey !in progress.bossRetry -> BossEntry.Free
        progress.usage(today).freeRetries < FREE_BOSS_RETRIES_PER_DAY -> BossEntry.FreeRetry
        else -> BossEntry.PaidRetry
    }

    /** Pays for starting the boss (a free retry or coins); null when the player cannot afford it. */
    fun payForBoss(progress: UserProgress, bossKey: String, today: String): UserProgress? =
        when (bossEntry(progress, bossKey, today)) {
            BossEntry.Free -> progress
            BossEntry.FreeRetry -> progress.usage(today).let { progress.copy(daily = it.copy(freeRetries = it.freeRetries + 1)) }
            BossEntry.PaidRetry -> spend(progress, BOSS_RETRY_COST)
        }

    /**
     * Called once the first answer is sent, so the next attempt counts as a retry until the boss is
     * beaten — leaving halfway cannot dodge the fee.
     */
    fun markBossAttempt(progress: UserProgress, bossKey: String): UserProgress =
        progress.copy(bossRetry = progress.bossRetry + bossKey)

    fun markBossWon(progress: UserProgress, bossKey: String): UserProgress =
        progress.copy(bossRetry = progress.bossRetry - bossKey)

    /** Rewarded ads left today; premium players see no ads. */
    fun adsLeft(progress: UserProgress, today: String): Int =
        if (progress.premium) 0 else (ADS_PER_DAY - progress.usage(today).adsWatched).coerceAtLeast(0)

    /** Pays for a watched rewarded ad; null once today's limit is used up. */
    fun applyAdReward(progress: UserProgress, today: String): UserProgress? {
        if (adsLeft(progress, today) == 0) return null
        val usage = progress.usage(today)
        return progress.copy(coins = progress.coins + AD_REWARD, daily = usage.copy(adsWatched = usage.adsWatched + 1))
    }

    /** A category opens once the boss of the previous category has been beaten. */
    fun isCategoryUnlocked(orderedIds: List<String>, categoryId: String, progress: UserProgress): Boolean {
        val index = orderedIds.indexOf(categoryId)
        return progress.devUnlockAll || index <= 0 || progress.bossWon(orderedIds[index - 1])
    }

    /** Steps inside a category open in order. */
    fun isStepUnlocked(categoryId: String, step: Step, progress: UserProgress): Boolean =
        progress.devUnlockAll || step.ordinal == 0 ||
            progress.stars(categoryId, Step.entries[step.ordinal - 1]) > 0
}
