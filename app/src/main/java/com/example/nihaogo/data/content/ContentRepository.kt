package com.example.nihaogo.data.content

import android.content.Context
import com.example.nihaogo.speech.Pinyin
import kotlinx.serialization.json.Json

class ContentRepository(context: Context) {

    private val loaded: Pair<ContentPack, SpecialPack> by lazy {
        fun read(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
        val pack = parse(read(CATEGORIES_PATH))
        val special = parseSpecial(read(SPECIAL_PATH))
        Pinyin.register(pack, special)
        pack to special
    }

    val pack: ContentPack get() = loaded.first
    val special: SpecialPack get() = loaded.second

    val categories: List<Category> get() = pack.categories

    fun category(id: String): Category = categories.first { it.id == id }

    fun mode(id: String): SpecialMode = special.modes.first { it.id == id }

    companion object {
        const val CATEGORIES_PATH = "content/categories.json"
        const val SPECIAL_PATH = "content/special.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ContentPack = json.decodeFromString(ContentPack.serializer(), text)

        fun parseSpecial(text: String): SpecialPack = json.decodeFromString(SpecialPack.serializer(), text)
    }
}
