package com.example.nihaogo.data.user

import com.example.nihaogo.data.content.SpecialMode

/** Economy and unlock rules for the detective case and the careers (PLAN.md §2.3–2.4). */
object SpecialRules {
    /** Coins to unlock every scene after the first. */
    const val SCENE_COST = 20
    /** Paid once, the first time the last scene of a mode is finished. */
    const val FINISH_BONUS = 30

    enum class SceneState { Locked, NeedsPurchase, Open }

    fun sceneKey(modeId: String, index: Int) = "$modeId/$index"

    /** A mode opens once the boss of its [SpecialMode.unlockAfter] category is beaten. */
    fun isModeOpen(mode: SpecialMode, progress: UserProgress): Boolean =
        progress.devUnlockAll || progress.bossWon(mode.unlockAfter)

    fun sceneState(mode: SpecialMode, index: Int, progress: UserProgress): SceneState = when {
        !isModeOpen(mode, progress) -> SceneState.Locked
        progress.devUnlockAll || index == 0 -> SceneState.Open
        progress.sceneStars(mode.id, index - 1) == 0 -> SceneState.Locked
        sceneKey(mode.id, index) in progress.unlocked -> SceneState.Open
        else -> SceneState.NeedsPurchase
    }

    /** Returns null when the player cannot afford the scene. */
    fun purchase(progress: UserProgress, mode: SpecialMode, index: Int): UserProgress? =
        GameRules.spend(progress, SCENE_COST)?.let { it.copy(unlocked = it.unlocked + sceneKey(mode.id, index)) }

    /** Like [GameRules.applyStepResult]: coins only for stars above the previous best. */
    fun applySceneResult(progress: UserProgress, mode: SpecialMode, index: Int, stars: Int): GameRules.StepOutcome {
        val previous = progress.sceneStars(mode.id, index)
        if (stars <= previous) return GameRules.StepOutcome(progress, 0, improved = false)

        var coins = (stars - previous) * GameRules.COINS_PER_STAR
        if (index == mode.scenes.lastIndex && previous == 0) coins += FINISH_BONUS
        val updated = progress.copy(
            coins = progress.coins + coins,
            special = progress.special + (sceneKey(mode.id, index) to stars),
        )
        return GameRules.StepOutcome(updated, coins, improved = true)
    }

    fun scenesDone(mode: SpecialMode, progress: UserProgress): Int =
        mode.scenes.indices.count { progress.sceneStars(mode.id, it) > 0 }

    fun isFinished(mode: SpecialMode, progress: UserProgress): Boolean =
        scenesDone(mode, progress) == mode.scenes.size

    /** Career title for the scenes finished so far, or null before the first one. */
    fun rank(mode: SpecialMode, progress: UserProgress): String? =
        mode.ranks.getOrNull(scenesDone(mode, progress) - 1)

    /** Detective finale: 3 stars for a first-time accusation, one less per wrong guess. */
    fun accusationStars(wrongGuesses: Int): Int = (3 - wrongGuesses).coerceAtLeast(1)
}
