package com.example.nihaogo.data.user

import com.example.nihaogo.data.content.Step
import kotlinx.serialization.Serializable

@Serializable
data class UserProgress(
    val coins: Int = 0,
    val categories: Map<String, CategoryProgress> = emptyMap(),
    /** Special-mode scenes bought with coins, keyed by [SpecialRules.sceneKey]. */
    val unlocked: Set<String> = emptySet(),
    /** Best stars per special-mode scene, keyed by [SpecialRules.sceneKey]. */
    val special: Map<String, Int> = emptyMap(),
    val premium: Boolean = false,
    /** Debug builds only: opens every category and step for testing/demos. */
    val devUnlockAll: Boolean = false,
) {
    fun stars(categoryId: String, step: Step): Int = categories[categoryId]?.stars?.get(step) ?: 0

    fun bossWon(categoryId: String): Boolean = stars(categoryId, Step.Boss) > 0

    val trophies: Int get() = categories.values.count { (it.stars[Step.Boss] ?: 0) > 0 }

    /** Stars across every category step and special-mode scene. */
    val totalStars: Int get() = categories.values.sumOf { it.stars.values.sum() } + special.values.sum()

    val scenesCleared: Int get() = special.count { it.value > 0 }

    /** Nothing played or bought yet (the debug unlock switch doesn't count). */
    val isFresh: Boolean get() = copy(devUnlockAll = false) == UserProgress()

    fun sceneStars(modeId: String, index: Int): Int = special[SpecialRules.sceneKey(modeId, index)] ?: 0
}

@Serializable
data class CategoryProgress(
    /** Best star count (0–3) per step. */
    val stars: Map<Step, Int> = emptyMap(),
)
