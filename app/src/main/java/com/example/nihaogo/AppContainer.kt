package com.example.nihaogo

import android.content.Context
import com.example.nihaogo.data.ai.AiBossEngine
import com.example.nihaogo.data.ai.AiInterrogation
import com.example.nihaogo.data.ai.BossEngine
import com.example.nihaogo.data.ai.GeminiClient
import com.example.nihaogo.data.ai.InterrogationEngine
import com.example.nihaogo.data.ai.ScriptedBossEngine
import com.example.nihaogo.data.ai.ScriptedInterrogation
import com.example.nihaogo.data.content.Boss
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.ContentRepository
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.AccountManager
import com.example.nihaogo.data.user.AuthRepository
import com.example.nihaogo.data.user.LocalProgressRepository
import com.example.nihaogo.data.user.ProgressRepository
import com.example.nihaogo.data.user.ProgressSync
import com.example.nihaogo.speech.Speaker
import com.example.nihaogo.speech.SpeechInput

/** Manual dependency container; one instance lives in [NiHaoGoApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val content = ContentRepository(appContext)
    val progress: ProgressRepository = LocalProgressRepository(appContext)
    private val auth = AuthRepository()
    val progressSync = ProgressSync(progress, auth)
    val accounts = AccountManager(auth, progress, progressSync)
    val speaker by lazy { Speaker(appContext) }
    val speechInput by lazy { SpeechInput(appContext) }
    val gemini = GeminiClient(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL)

    fun bossEngine(category: Category): BossEngine = bossEngine(category.boss, category.words)

    fun bossEngine(boss: Boss, knownWords: List<Word>): BossEngine =
        if (gemini.isConfigured) AiBossEngine(boss, knownWords, gemini) else ScriptedBossEngine(boss)

    fun interrogation(mode: SpecialMode): InterrogationEngine =
        if (gemini.isConfigured) AiInterrogation(mode, gemini) else ScriptedInterrogation()
}
