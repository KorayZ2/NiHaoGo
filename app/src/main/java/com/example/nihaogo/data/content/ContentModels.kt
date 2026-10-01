package com.example.nihaogo.data.content

import kotlinx.serialization.Serializable

@Serializable
data class ContentPack(
    val version: Int,
    val categories: List<Category>,
)

@Serializable
data class Category(
    val id: String,
    val emoji: String,
    val titleTh: String,
    val titleZh: String,
    val titlePinyin: String,
    val descriptionTh: String,
    val words: List<Word>,
    val sentences: List<Sentence>,
    val listening: List<ListeningItem>,
    val boss: Boss,
)

@Serializable
data class Word(
    val hanzi: String,
    val pinyin: String,
    val thai: String,
    val emoji: String,
    val english: String = "",
)

@Serializable
data class Sentence(
    val hanzi: String,
    val pinyin: String,
    val thai: String,
    /** Correct order; joined together they equal [hanzi] without punctuation. */
    val tiles: List<String>,
    val distractors: List<String> = emptyList(),
    val tipTh: String = "",
)

@Serializable
data class ListeningItem(
    val hanzi: String,
    val pinyin: String,
    val thai: String,
    val questionTh: String,
    val options: List<String>,
    val answer: Int,
)

@Serializable
data class Boss(
    val name: String,
    val namePinyin: String,
    val emoji: String,
    val roleTh: String,
    val scenarioTh: String,
    /** English role description fed to Gemini. */
    val persona: String,
    val targetWords: List<String>,
    /** Offline fallback used when there is no Gemini key or the network fails. */
    val script: List<BossTurn>,
)

@Serializable
data class BossTurn(
    val hanzi: String,
    val pinyin: String,
    val thai: String,
    /** The answer passes if it contains any of these (compared by pinyin, so homophones count). */
    val keywords: List<String>,
    val sampleHanzi: String,
    val samplePinyin: String,
)

/** The four steps of every category, in play order. */
enum class Step(val titleTh: String, val emoji: String) {
    Vocab("คำศัพท์ + ออกเสียง", "🗣️"),
    Sentence("ประโยค + ไวยากรณ์", "🧩"),
    Listening("ฟังแล้วตอบ", "🎧"),
    Boss("บอส AI", "👑"),
}
