package com.example.nihaogo.data.content

import com.example.nihaogo.data.user.UserProgress

/** A word from one of the 8 categories, with the category's place on the path (1-based). */
data class OldWord(val word: Word, val stage: Int)

/** Links the special modes back to the category words the learner met on the path (stages 1–8). */
object OldWords {
    /** How many old words the AI is steered towards in one special-mode boss game. */
    const val BOSS_REVIEW_ROUNDS = 2

    /**
     * Words of every category whose boss is beaten (all of them with the debug unlock), minus the
     * words the special mode teaches itself.
     */
    fun learned(categories: List<Category>, progress: UserProgress, exclude: List<Word> = emptyList()): List<OldWord> {
        val own = exclude.map { it.hanzi }.toSet()
        return categories.withIndex()
            .filter { (_, c) -> progress.devUnlockAll || progress.bossWon(c.id) }
            .flatMap { (i, c) -> c.words.map { OldWord(it, i + 1) } }
            .filter { it.word.hanzi !in own }
            .distinctBy { it.word.hanzi }
    }

    /**
     * Category words that scene [index] of [mode] uses in its Chinese lines, so its intro can show
     * them for review. Single characters are skipped: they too often turn up inside other words
     * (发 in 发烧, 口 in 口罩).
     */
    fun inScene(mode: SpecialMode, index: Int, categories: List<Category>): List<OldWord> {
        val scene = mode.scenes[index]
        val text = sceneText(mode, scene)
        val own = scene.words.map { it.hanzi }.toSet()
        return categories.withIndex()
            .flatMap { (i, c) -> c.words.map { OldWord(it, i + 1) } }
            .filter { it.word.hanzi.length >= 2 && it.word.hanzi !in own && it.word.hanzi in text }
            .distinctBy { it.word.hanzi }
    }

    /** Every line of Chinese the learner reads or hears in [scene]. */
    private fun sceneText(mode: SpecialMode, scene: Scene): String = when (scene) {
        is Scene.Find -> scene.targets.map { it.hanzi }
        is Scene.Dialogue -> scene.turns.flatMap { turn ->
            listOf(turn.npc.hanzi, turn.reply.hanzi) + turn.options.map { it.hanzi } +
                turn.task?.options?.map { it.hanzi }.orEmpty()
        }
        is Scene.Puzzle -> scene.sentences.map { it.hanzi }
        is Scene.Training -> emptyList()
        is Scene.Interrogate -> mode.suspects.flatMap { s ->
            listOf(s.alibi.hanzi) + s.questions.flatMap { listOf(it.question.hanzi, it.answer.hanzi) }
        }
        is Scene.Roleplay -> scene.boss.script.flatMap { listOf(it.hanzi, it.sampleHanzi) }
    }.joinToString("\n")
}
