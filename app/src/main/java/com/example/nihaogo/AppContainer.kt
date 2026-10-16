package com.example.nihaogo

import android.content.Context
import com.example.nihaogo.audio.AppSettings
import com.example.nihaogo.audio.Sfx
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
import com.example.nihaogo.data.content.OldWords
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.AccountManager
import com.example.nihaogo.data.user.AuthRepository
import com.example.nihaogo.data.user.LocalProgressRepository
import com.example.nihaogo.data.user.ProgressRepository
import com.example.nihaogo.data.user.ProgressSync
import com.example.nihaogo.monetization.RewardedAds
import com.example.nihaogo.speech.Speaker
import com.example.nihaogo.speech.SpeechInput
import kotlinx.coroutines.flow.first

/** Manual dependency container; one instance lives in [NiHaoGoApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val content = ContentRepository(appContext)
    val progress: ProgressRepository = LocalProgressRepository(appContext)
    private val auth = AuthRepository()
    val progressSync = ProgressSync(progress, auth)
    val accounts = AccountManager(auth, progress, progressSync)
    val speaker by lazy { Speaker(appContext, sfx) }
    val speechInput by lazy { SpeechInput(appContext) }
    val settings = AppSettings(appContext)
    // Created up front so the sounds have finished loading by the first tap.
    val sfx = Sfx(appContext, settings)
    val gemini = GeminiClient(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL)
    val rewardedAds = RewardedAds(appContext, BuildConfig.ADMOB_REWARDED_ID, progress)

    fun bossEngine(category: Category): BossEngine = bossEngine(category.boss, category.words)

    /** [flexible]: special modes, where spoken answers count and each answer gets another way to say it. */
    fun bossEngine(
        boss: Boss,
        knownWords: List<Word>,
        reviewWords: List<Word> = emptyList(),
        flexible: Boolean = false,
    ): BossEngine =
        if (gemini.isConfigured) AiBossEngine(boss, knownWords, gemini, reviewWords, flexible) else ScriptedBossEngine(boss, flexible)

    fun interrogation(mode: SpecialMode, reviewWords: List<Word> = emptyList()): InterrogationEngine =
        if (gemini.isConfigured) AiInterrogation(mode, gemini, reviewWords) else ScriptedInterrogation()

    /** Words from the categories the learner has beaten, for [mode]'s AI characters to reuse. */
    suspend fun reviewWords(mode: SpecialMode): List<Word> =
        OldWords.learned(content.categories, progress.progress.first(), exclude = mode.allWords).map { it.word }
}
