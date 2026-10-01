package com.example.nihaogo.data.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** special.json: the detective case and the careers (PLAN.md §2.3). */
@Serializable
data class SpecialPack(
    val detective: SpecialMode,
    val careers: List<SpecialMode>,
) {
    val modes: List<SpecialMode> get() = listOf(detective) + careers
}

@Serializable
enum class ModeKind { Detective, Career }

@Serializable
data class SpecialMode(
    val id: String,
    val kind: ModeKind,
    val emoji: String,
    val titleTh: String,
    val titleZh: String,
    val titlePinyin: String,
    val introTh: String,
    /** Category whose boss must be beaten before this mode opens. */
    val unlockAfter: String,
    val scenes: List<Scene>,
    /** Career only: title earned after each finished scene. */
    val ranks: List<String> = emptyList(),
    /** Detective only. */
    val suspects: List<Suspect> = emptyList(),
    val culprit: String = "",
    val confession: Line? = null,
    val endingTh: String = "",
) {
    /** Every word the mode teaches, for AI prompts. */
    val allWords: List<Word> get() = scenes.flatMap { it.words }
}

/** A line of Chinese with its reading and meaning. */
@Serializable
data class Line(val hanzi: String, val pinyin: String, val thai: String)

@Serializable
data class Person(val name: String, val namePinyin: String, val emoji: String, val roleTh: String = "")

@Serializable
data class Suspect(
    val id: String,
    val name: String,
    val namePinyin: String,
    val emoji: String,
    val roleTh: String,
    /** What this character knows and hides; English, fed to Gemini. */
    val secret: String,
    /** Said when the learner wrongly accuses them; doubles as a new clue. */
    val alibi: Line,
    /** Suggested questions with the scripted answers used offline. */
    val questions: List<SuggestedQuestion>,
)

@Serializable
data class SuggestedQuestion(val question: Line, val answer: Line)

/** One playable scene. Every scene first teaches its [words]. */
@Serializable
sealed class Scene {
    abstract val titleTh: String
    abstract val titleZh: String
    abstract val titlePinyin: String
    abstract val emoji: String
    abstract val introTh: String
    abstract val words: List<Word>

    /** Tap the object whose Chinese name is called out. */
    @Serializable
    @SerialName("find")
    data class Find(
        override val titleTh: String,
        override val titleZh: String,
        override val titlePinyin: String,
        override val emoji: String,
        override val introTh: String,
        override val words: List<Word>,
        val targets: List<FindTarget>,
    ) : Scene()

    /** Choose the right Chinese line in a conversation; optionally do a task afterwards. */
    @Serializable
    @SerialName("dialogue")
    data class Dialogue(
        override val titleTh: String,
        override val titleZh: String,
        override val titlePinyin: String,
        override val emoji: String,
        override val introTh: String,
        override val words: List<Word>,
        /** Default speaker; a turn may bring its own (e.g. a new patient). */
        val npc: Person? = null,
        val turns: List<DialogueTurn>,
    ) : Scene()

    /** Arrange torn-note tiles into sentences to reveal a clue. */
    @Serializable
    @SerialName("puzzle")
    data class Puzzle(
        override val titleTh: String,
        override val titleZh: String,
        override val titlePinyin: String,
        override val emoji: String,
        override val introTh: String,
        override val words: List<Word>,
        val sentences: List<Sentence>,
        val clueTh: String,
    ) : Scene()

    /** Career training: listen, repeat, then a listening quiz. */
    @Serializable
    @SerialName("training")
    data class Training(
        override val titleTh: String,
        override val titleZh: String,
        override val titlePinyin: String,
        override val emoji: String,
        override val introTh: String,
        override val words: List<Word>,
    ) : Scene()

    /** Detective finale: question the suspects in Chinese, then accuse one. */
    @Serializable
    @SerialName("interrogate")
    data class Interrogate(
        override val titleTh: String,
        override val titleZh: String,
        override val titlePinyin: String,
        override val emoji: String,
        override val introTh: String,
        override val words: List<Word>,
        val requiredQuestions: Int = 5,
    ) : Scene()

    /** Career finale: a customer played by Gemini (scripted offline), like a category boss. */
    @Serializable
    @SerialName("roleplay")
    data class Roleplay(
        override val titleTh: String,
        override val titleZh: String,
        override val titlePinyin: String,
        override val emoji: String,
        override val introTh: String,
        override val words: List<Word>,
        val boss: Boss,
    ) : Scene()
}

/** Detective clues a scene reveals, in the order the learner finds them. */
val Scene.clues: List<String>
    get() = when (this) {
        is Scene.Find -> targets.map { it.clueTh }
        is Scene.Dialogue -> turns.map { it.clueTh }
        is Scene.Puzzle -> listOf(clueTh)
        else -> emptyList()
    }.filter { it.isNotBlank() }

@Serializable
data class FindTarget(val hanzi: String, val clueTh: String = "")

@Serializable
data class DialogueTurn(
    val npc: Line,
    val options: List<Line>,
    val answer: Int,
    val reply: Line,
    val speaker: Person? = null,
    val clueTh: String = "",
    val task: Task? = null,
)

@Serializable
data class Task(val promptTh: String, val options: List<TaskOption>, val answer: Int)

@Serializable
data class TaskOption(val emoji: String, val hanzi: String, val pinyin: String, val thai: String)
