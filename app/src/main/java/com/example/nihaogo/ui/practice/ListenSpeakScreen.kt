package com.example.nihaogo.ui.practice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.R
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.Mission
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.speech.Heard
import com.example.nihaogo.ui.common.ConfettiBurst
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameOutlinedButton
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.MicButton
import com.example.nihaogo.ui.common.PaperCard
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.RoundIconButton
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.StarsRow
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.common.rememberMicPermission
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Red
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ROUND_WORDS = 10

data class ListenSpeakState(
    val loading: Boolean = true,
    val words: List<Word> = emptyList(),
    val index: Int = 0,
    /** Best pronunciation score per word index. */
    val scores: Map<Int, Int> = emptyMap(),
    /** Mic permission was refused, so this round is listen-only. */
    val micDenied: Boolean = false,
    val heard: String? = null,
    val message: String? = null,
    val finished: Boolean = false,
) {
    val word: Word? get() = words.getOrNull(index)
    val passedCount: Int get() = scores.count { it.value >= GameRules.SPEECH_PASS_SCORE }
    fun passed(i: Int = index): Boolean = (scores[i] ?: 0) >= GameRules.SPEECH_PASS_SCORE
}

class ListenSpeakViewModel(private val container: AppContainer) : ViewModel() {
    val micAvailable = container.speechInput.isAvailable
    val speakerState = container.speaker.state
    val listening = container.speechInput.listening
    private var pool: List<Word> = emptyList()

    private val _state = MutableStateFlow(ListenSpeakState())
    val state: StateFlow<ListenSpeakState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            pool = container.openedWords()
            newRound()
        }
    }

    fun newRound() {
        _state.value = ListenSpeakState(loading = false, words = pool.shuffled().take(ROUND_WORDS), micDenied = _state.value.micDenied)
    }

    fun speak(slow: Boolean = false) {
        _state.value.word?.let { container.speaker.speak(it.hanzi, slow) }
    }

    fun record() {
        val index = _state.value.index
        val target = _state.value.word?.hanzi ?: return
        viewModelScope.launch {
            when (val heard = container.speechInput.listen()) {
                is Heard.Failed -> _state.update { it.copy(message = heard.messageTh, heard = null) }
                is Heard.Text -> {
                    val best = heard.candidates.maxBy { ChineseText.similarityScore(target, it) }
                    val score = ChineseText.similarityScore(target, best)
                    val pass = score >= GameRules.SPEECH_PASS_SCORE
                    container.sfx.answer(pass)
                    // Each word counts once towards today's missions, the first time it is said well.
                    if (pass && !_state.value.passed(index)) {
                        container.missions.add(Mission.ListenSpeak)
                        container.missions.add(Mission.Words)
                    }
                    _state.update {
                        it.copy(
                            scores = it.scores + (index to maxOf(score, it.scores[index] ?: 0)),
                            heard = best,
                            message = when {
                                pass -> "เยี่ยมมาก! ออกเสียงได้ชัดเลย"
                                score >= 50 -> "ใกล้แล้ว! ฟังอีกรอบแล้วลองใหม่นะ"
                                else -> "ยังไม่ค่อยตรง ลองฟังแล้วพูดช้า ๆ นะ"
                            },
                        )
                    }
                }
            }
        }
    }

    fun micDenied() = _state.update { it.copy(micDenied = true) }

    fun move(step: Int) {
        val s = _state.value
        val target = s.index + step
        when {
            target < 0 -> return
            target > s.words.lastIndex -> _state.update { it.copy(finished = true) }
            else -> _state.update { it.copy(index = target, heard = null, message = null) }
        }
    }
}

@Composable
fun ListenSpeakScreen(onBack: () -> Unit) {
    val vm = appViewModel { ListenSpeakViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()

    InkScaffold(topBar = { GameTopBar("ฟัง-พูด", coins = null, onBack = onBack) }) { padding ->
        val word = state.word
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            word == null -> NoWords(Modifier.padding(padding))
            state.finished -> RoundFinished(state, onAgain = vm::newRound, onBack = onBack, modifier = Modifier.padding(padding))
            else -> {
                LaunchedEffect(state.index, speakerState) { vm.speak() }
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProgressHeader(state.index + 1, state.words.size)
                    TtsWarning(speakerState)
                    ListeningQilin(cheering = state.passed(), onPrevious = { vm.move(-1) }, onNext = { vm.move(1) }, canGoBack = state.index > 0)
                    AnimatedContent(
                        targetState = state.index,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it * dir / 2 } + fadeOut())
                        },
                        label = "listenWord",
                    ) { index ->
                        WordCard(state.words[index], passed = state.passed(index), onSpeak = { vm.speak() }, onSlow = { vm.speak(slow = true) })
                    }
                    SpeakArea(vm, state)
                }
            }
        }
    }
}

/** The qilin in headphones bobbing to the music, with notes floating up and arrows either side. */
@Composable
private fun ListeningQilin(cheering: Boolean, canGoBack: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val bob = floatOffset(1400)
    val notes = rememberInfiniteTransition(label = "notes")
    val rise by notes.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "noteRise",
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(onClick = onPrevious, modifier = Modifier.alpha(if (canGoBack) 1f else 0.35f)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "คำก่อนหน้า")
        }
        Box(Modifier.weight(1f).height(200.dp), contentAlignment = Alignment.Center) {
            Image(
                painterResource(if (cheering) R.drawable.mascot_cheer else R.drawable.mascot_listen),
                contentDescription = null,
                modifier = Modifier.size(180.dp).graphicsLayer { translationY = -bob * 8.dp.toPx() },
            )
            // Two notes, half a cycle apart, drift up and fade out.
            listOf(rise to -1, (rise + 0.5f) % 1f to 1).forEach { (t, side) ->
                Text(
                    if (side < 0) "♪" else "♫",
                    color = Red,
                    fontSize = 28.sp,
                    modifier = Modifier.align(if (side < 0) Alignment.TopStart else Alignment.TopEnd).graphicsLayer {
                        translationY = (1 - t) * 90.dp.toPx()
                        translationX = side * 12.dp.toPx() * t
                        alpha = (1 - t) * 0.9f
                    }.padding(horizontal = 16.dp),
                )
            }
        }
        RoundIconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "คำถัดไป")
        }
    }
}

@Composable
private fun WordCard(word: Word, passed: Boolean, onSpeak: () -> Unit, onSlow: () -> Unit) {
    PaperCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                PinyinText(word.hanzi, pinyin = word.pinyin, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(word.thai, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                if (passed) Text("ผ่าน ✓", color = Correct, fontWeight = FontWeight.Bold, modifier = Modifier.pop(true))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeakButton(onClick = onSpeak)
                SpeakButton(onClick = onSlow, slow = true)
            }
        }
    }
}

@Composable
private fun SpeakArea(vm: ListenSpeakViewModel, state: ListenSpeakState) {
    val listening by vm.listening.collectAsStateWithLifecycle()
    val withMic = rememberMicPermission(onDenied = vm::micDenied)
    val canSpeak = vm.micAvailable && !state.micDenied

    Text(
        when {
            state.message != null -> state.message
            !vm.micAvailable -> "เครื่องนี้ฟังเสียงพูดไม่ได้ ฟังแล้วพูดตามในใจ แล้วกด › ได้เลย"
            state.micDenied -> "ยังไม่ได้อนุญาตไมโครโฟน ฟังแล้วพูดตามเบา ๆ แล้วกด › ได้เลย"
            else -> "แตะปุ่มแล้วพูดตาม"
        },
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        color = if (state.passed()) Correct else MaterialTheme.colorScheme.onBackground,
    )
    state.heard?.let { Text("ได้ยินว่า: $it", style = MaterialTheme.typography.bodySmall, color = InkSoft) }
    if (canSpeak) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoundWave(listening)
            MicButton(listening, onClick = { withMic(vm::record) })
            SoundWave(listening)
        }
    }
    state.scores[state.index]?.let { best ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear()) {
            Text("คะแนนออกเสียง $best  ", fontWeight = FontWeight.Bold)
            StarsRow(GameRules.starsForScore(best))
        }
    }
    if (state.passed() || !canSpeak) {
        GameButton(onClick = { vm.move(1) }, modifier = Modifier.fillMaxWidth().appear()) {
            Text(if (state.index < state.words.lastIndex) "คำถัดไป" else "ดูสรุป")
        }
    }
}

@Composable
private fun RoundFinished(state: ListenSpeakState, onAgain: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val great = state.passedCount >= state.words.size / 2 && state.passedCount > 0
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Image(
                painterResource(if (great) R.drawable.mascot_cheer else R.drawable.mascot_listen),
                contentDescription = null,
                modifier = Modifier.size(160.dp).pop(true, amount = 0.2f),
            )
            Text(
                if (great) "หูไว ปากไว เก่งมาก!" else "ฝึกครบรอบแล้ว!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "พูดผ่าน ${state.passedCount}/${state.words.size} คำ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.appear(200),
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.appear(400), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("ฝึกอีกรอบ") }
                GameOutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("กลับ") }
            }
        }
        if (great) ConfettiBurst(Modifier.matchParentSize())
    }
}
