package com.example.nihaogo.data.user

import com.example.nihaogo.data.content.Step

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
