package com.example.nihaogo.ui.special

import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.MascotSays
import com.example.nihaogo.R
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import com.example.nihaogo.ui.theme.Paper
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.ui.common.PaperCard
import com.example.nihaogo.AppContainer
import com.example.nihaogo.NiHaoGoApp
import com.example.nihaogo.data.content.OldWord
import com.example.nihaogo.data.content.OldWords
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.speech.Speaker
import com.example.nihaogo.speech.VoiceStyle
import com.example.nihaogo.ui.boss.BossScreen
import com.example.nihaogo.ui.boss.BossTarget
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.StepResult
import com.example.nihaogo.ui.common.StepResultPanel
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.common.recordScene
import com.example.nihaogo.ui.theme.Gold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared behaviour of every special-mode scene: a new-words intro, then the scene's own play,
 * then a saved result.
 */
abstract class SceneViewModel<S : Scene>(
    protected val container: AppContainer,
    modeId: String,
    val index: Int,
) : ViewModel() {
    val mode: SpecialMode = container.content.mode(modeId)

    @Suppress("UNCHECKED_CAST")
    val scene: S = mode.scenes[index] as S

    /** Words from stages 1–8 that this scene uses, shown in its intro for review. */
    val oldWords: List<OldWord> = OldWords.inScene(mode, index, container.content.categories)

    val speakerState = container.speaker.state

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    private val _result = MutableStateFlow<StepResult?>(null)
    val result: StateFlow<StepResult?> = _result.asStateFlow()

    fun speak(text: String, slow: Boolean = false, voice: VoiceStyle = VoiceStyle.Default) =
        container.speaker.speak(text, slow, voice)

    fun start() {
        _started.value = true
        onStart()
    }

    /** Plays the scene again from the beginning, skipping the words intro. */
    fun restart() {
        _result.value = null
        reset()
        start()
    }

    /** Called when play begins (first time and on every restart). */
    protected open fun onStart() = Unit

    /** Clears the scene's own play state. */
    protected abstract fun reset()

    protected val sfx get() = container.sfx

    protected fun finish(score: Int, stars: Int = GameRules.starsForScore(score)) {
        viewModelScope.launch {
            val outcome = container.progress.recordScene(mode, index, stars)
            _result.value = StepResult(score, stars, outcome.coinsEarned)
        }
    }
}

/**
 * Top bar, words intro and result screen around a scene's play [content]. A scene with a title
 * painting shows it first; [fullScreen], when set, takes over the whole screen until the result.
 */
@Composable
fun SceneFrame(
    vm: SceneViewModel<*>,
    onBack: () -> Unit,
    passedMessage: String,
    detail: () -> String?,
    bottomBar: @Composable () -> Unit = {},
    fullScreen: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val started by vm.started.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val scene = vm.scene
    var entered by rememberSaveable { mutableStateOf(false) }
    val art = sceneArt(vm.mode.id, vm.index)
    if (art != null && !entered && !started) {
        SceneOpening(art, scene.titleTh, scene.introTh, vm.index, vm.mode.scenes.size, onBack = onBack, onEnter = { entered = true })
        return
    }
    if (fullScreen != null && result == null) {
        fullScreen()
        return
    }

    InkScaffold(
        // The scene's title painting stays on behind the play.
        painting = art,
        topBar = { GameTopBar("${scene.emoji} ${scene.titleTh}", coins = null, onBack = onBack) },
        bottomBar = { if (started && result == null) bottomBar() },
    ) { padding ->
        val done = result
        when {
            done != null -> StepResultPanel(
                result = done,
                passedMessage = passedMessage,
                detail = detail(),
                onRetry = vm::restart,
                onDone = onBack,
                modifier = Modifier.padding(padding),
                failedDoneLabel = "กลับ",
            )
            !started -> NewWordsIntro(
                scene = scene,
                index = vm.index,
                oldWords = vm.oldWords,
                speakerState = vm.speakerState,
                onSpeak = { vm.speak(it) },
                onStart = vm::start,
                modifier = Modifier.padding(padding),
            )
            else -> content(padding)
        }
    }
}

/** The AI-customer finale of a career: the words intro first, then the boss chat. */
@Composable
fun RoleplaySceneScreen(modeId: String, index: Int, onBack: () -> Unit) {
    var started by rememberSaveable { mutableStateOf(false) }
    if (started) {
        BossScreen(BossTarget.SpecialScene(modeId, index), onBack)
        return
    }
    val container = (LocalContext.current.applicationContext as NiHaoGoApp).container
    val mode = container.content.mode(modeId)
    val scene = mode.scenes[index]
    val oldWords = remember(modeId, index) { OldWords.inScene(mode, index, container.content.categories) }
    InkScaffold(topBar = { GameTopBar("${scene.emoji} ${scene.titleTh}", coins = null, onBack = onBack) }) { padding ->
        NewWordsIntro(
            scene = scene,
            index = index,
            oldWords = oldWords,
            speakerState = container.speaker.state,
            onSpeak = { container.speaker.speak(it) },
            onStart = { started = true },
            modifier = Modifier.padding(padding),
        )
    }
}

/** Scene story, the new words it teaches and the old ones it reuses; tap a word to hear it. */
@Composable
private fun NewWordsIntro(
    scene: Scene,
    index: Int,
    oldWords: List<OldWord>,
    speakerState: StateFlow<Speaker.State>,
    onSpeak: (String) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier,
) {
    val tts by speakerState.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TtsWarning(tts)
        PaperCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), border = BorderStroke(1.5.dp, Gold)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(scene.emoji, fontSize = 56.sp)
                PinyinText(scene.titleZh, pinyin = scene.titlePinyin, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("ฉากที่ ${index + 1} · ${scene.titleTh}", fontWeight = FontWeight.Bold)
                Text(scene.introTh, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        }
        MascotSays(R.drawable.mascot_read, "มาฟังคำศัพท์ใหม่ของฉากนี้ก่อนนะ แตะคำเพื่อฟังเสียง", size = 84.dp)
        Text("คำศัพท์ใหม่ในฉากนี้", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        scene.words.forEachIndexed { i, word ->
            NewWordRow(word, onClick = { onSpeak(word.hanzi) }, modifier = Modifier.appear(100 + i * 50))
        }
        if (oldWords.isNotEmpty()) {
            Text("🔁 ทบทวนคำจากด่านเก่าที่ใช้ในฉากนี้", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            oldWords.forEachIndexed { i, old ->
                NewWordRow(
                    old.word,
                    onClick = { onSpeak(old.word.hanzi) },
                    modifier = Modifier.appear(100 + (scene.words.size + i) * 50),
                    stage = old.stage,
                )
            }
        }
        GameButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("เริ่มเลย ▶") }
    }
}

@Composable
private fun NewWordRow(word: Word, onClick: () -> Unit, modifier: Modifier = Modifier, stage: Int? = null) {
    GameCard(onClick = onClick, sound = false, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Cream)
                    .border(1.dp, Gold.copy(alpha = 0.6f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) { Text(word.emoji, fontSize = 28.sp) }
            PinyinText(
                word.hanzi,
                pinyin = word.pinyin,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(word.thai, style = MaterialTheme.typography.bodyLarge)
                if (word.english.isNotEmpty()) {
                    Text(word.english, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (stage != null) {
                    Text("จากหมวด $stage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            SpeakButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp).size(40.dp))
        }
    }
}

/** A clue card for the detective's notebook. */
@Composable
fun ClueCard(text: String, modifier: Modifier = Modifier, isNew: Boolean = false) {
    PaperCard(
        modifier = modifier.fillMaxWidth().appear(enabled = isNew).pop(isNew, amount = 0.05f),
        colors = CardDefaults.cardColors(containerColor = lerp(Paper, Gold, if (isNew) 0.45f else 0.2f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isNew) "🔍" else "📌", fontSize = 22.sp)
            PinyinText(
                (if (isNew) "เบาะแสใหม่! " else "") + text,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNew) FontWeight.Bold else null,
            )
        }
    }
}
