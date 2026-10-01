package com.example.nihaogo.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.speech.Heard
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.HanziBlock
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.MicButton
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.StarsRow
import com.example.nihaogo.ui.common.StepResult
import com.example.nihaogo.ui.common.StepResultPanel
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.recordStep
import com.example.nihaogo.ui.common.rememberMicPermission
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

data class MatchTile(val wordIndex: Int, val isHanzi: Boolean) {
    val id: Int get() = wordIndex * 2 + if (isHanzi) 1 else 0
}

data class VocabState(
    val phase: Phase = Phase.Learn,
    val index: Int = 0,
    /** Best pronunciation score per word index. */
    val scores: Map<Int, Int> = emptyMap(),
    /** Speaking tries per word index (failed recognitions count too). */
    val tries: Map<Int, Int> = emptyMap(),
    /** Words the learner skipped after [GameRules.SKIP_AFTER_TRIES] tries. */
    val skipped: Set<Int> = emptySet(),
    /** Mic permission was refused, so speaking cannot gate progress. */
    val micDenied: Boolean = false,
    val heard: String? = null,
    val message: String? = null,
    val rounds: List<List<Int>> = emptyList(),
    val round: Int = 0,
    val tiles: List<MatchTile> = emptyList(),
    val selected: MatchTile? = null,
    val matched: Set<Int> = emptySet(),
    val wrong: Set<MatchTile> = emptySet(),
    val mistakes: Int = 0,
    val result: StepResult? = null,
) {
    enum class Phase { Learn, Match, Done }
}

class VocabViewModel(private val container: AppContainer, private val categoryId: String) : ViewModel() {
    val words: List<Word> = container.content.category(categoryId).words
    val micAvailable = container.speechInput.isAvailable
    val speakerState = container.speaker.state
    val listening = container.speechInput.listening

    private val _state = MutableStateFlow(VocabState())
    val state: StateFlow<VocabState> = _state.asStateFlow()

    fun speak(slow: Boolean = false) = container.speaker.speak(words[_state.value.index].hanzi, slow)

    fun record() {
        val index = _state.value.index
        viewModelScope.launch {
            val heard = container.speechInput.listen()
            _state.update { it.copy(tries = it.tries + (index to (it.tries[index] ?: 0) + 1)) }
            when (heard) {
                is Heard.Failed -> _state.update { it.copy(message = heard.messageTh, heard = null) }
                is Heard.Text -> {
                    val target = words[index].hanzi
                    val best = heard.candidates.maxBy { ChineseText.similarityScore(target, it) }
                    val score = ChineseText.similarityScore(target, best)
                    _state.update {
                        it.copy(
                            scores = it.scores + (index to maxOf(score, it.scores[index] ?: 0)),
                            heard = best,
                            message = feedbackFor(score),
                        )
                    }
                }
            }
        }
    }

    fun micDenied() = _state.update { it.copy(micDenied = true) }

    fun passed(s: VocabState, index: Int = s.index): Boolean =
        (s.scores[index] ?: 0) >= GameRules.SPEECH_PASS_SCORE

    /** Next is locked until the word is spoken well enough, unless speaking is impossible here. */
    fun canGoNext(s: VocabState): Boolean =
        !micAvailable || s.micDenied || passed(s) || s.index in s.skipped

    fun canSkip(s: VocabState): Boolean =
        !canGoNext(s) && (s.tries[s.index] ?: 0) >= GameRules.SKIP_AFTER_TRIES

    fun skip() {
        _state.update { it.copy(skipped = it.skipped + it.index) }
        next()
    }

    fun next() {
        val s = _state.value
        if (!canGoNext(s)) return
        if (s.index < words.lastIndex) {
            _state.update { it.copy(index = it.index + 1, heard = null, message = null) }
        } else {
            startMatch()
        }
    }

    fun previous() {
        if (_state.value.index == 0) return
        _state.update { it.copy(index = it.index - 1, heard = null, message = null) }
    }

    private fun startMatch() {
        val order = words.indices.shuffled()
        val roundCount = ceil(order.size / 5.0).toInt()
        val rounds = order.chunked(ceil(order.size.toDouble() / roundCount).toInt())
        _state.update { it.copy(phase = VocabState.Phase.Match, rounds = rounds, round = 0, mistakes = 0) }
        loadRound(0)
    }

    private fun loadRound(round: Int) {
        val wordIndexes = _state.value.rounds[round]
        val tiles = wordIndexes.flatMap { listOf(MatchTile(it, false), MatchTile(it, true)) }.shuffled()
        _state.update { it.copy(round = round, tiles = tiles, selected = null, matched = emptySet(), wrong = emptySet()) }
    }

    fun tap(tile: MatchTile) {
        val s = _state.value
        if (tile.wordIndex in s.matched || s.wrong.isNotEmpty()) return
        val selected = s.selected
        when {
            selected == null || selected.isHanzi == tile.isHanzi -> _state.update { it.copy(selected = tile) }
            selected.wordIndex == tile.wordIndex -> {
                container.speaker.speak(words[tile.wordIndex].hanzi)
                val matched = s.matched + tile.wordIndex
                _state.update { it.copy(matched = matched, selected = null) }
                if (matched.size == s.rounds[s.round].size) {
                    viewModelScope.launch {
                        delay(500)
                        if (s.round < s.rounds.lastIndex) loadRound(s.round + 1) else finish()
                    }
                }
            }
            else -> {
                _state.update { it.copy(mistakes = it.mistakes + 1, selected = null, wrong = setOf(selected, tile)) }
                viewModelScope.launch {
                    delay(600)
                    _state.update { it.copy(wrong = emptySet()) }
                }
            }
        }
    }

    private suspend fun finish() {
        val s = _state.value
        val speech = speechAverage(s)
        val match = matchScore(s)
        val score = if (micAvailable) (0.6 * speech + 0.4 * match).roundToInt() else match
        val stars = GameRules.starsForScore(score)
        val outcome = container.progress.recordStep(categoryId, Step.Vocab, stars)
        _state.update {
            it.copy(phase = VocabState.Phase.Done, result = StepResult(score, stars, outcome.coinsEarned))
        }
    }

    fun speechAverage(s: VocabState): Int = words.indices.map { s.scores[it] ?: 0 }.average().roundToInt()

    fun matchScore(s: VocabState): Int = (100 - s.mistakes * 8).coerceAtLeast(0)

    fun restart() {
        _state.value = VocabState()
    }

    private fun feedbackFor(score: Int) = when {
        score >= 90 -> "เป๊ะมาก! 🌟"
        score >= GameRules.SPEECH_PASS_SCORE -> "ผ่าน! ใกล้เคียงมาก 👍"
        score >= 50 -> "ใกล้แล้ว ลองอีกนิดนะ"
        else -> "ยังไม่ค่อยเหมือน ฟังแล้วลองพูดใหม่นะ"
    }
}

@Composable
fun VocabStepScreen(categoryId: String, onBack: () -> Unit) {
    val vm = appViewModel { VocabViewModel(it, categoryId) }
    val state by vm.state.collectAsStateWithLifecycle()

    InkScaffold(topBar = { GameTopBar(Step.Vocab.titleTh, coins = null, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                VocabState.Phase.Learn -> LearnPhase(vm, state)
                VocabState.Phase.Match -> MatchPhase(vm, state)
                VocabState.Phase.Done -> StepResultPanel(
                    result = state.result!!,
                    passedMessage = "จำคำศัพท์ได้แล้ว!",
                    detail = if (vm.micAvailable) {
                        "ออกเสียง ${vm.speechAverage(state)} · จับคู่ ${vm.matchScore(state)} (ผิด ${state.mistakes} ครั้ง)"
                    } else {
                        "จับคู่ ${vm.matchScore(state)} (ผิด ${state.mistakes} ครั้ง)"
                    },
                    onRetry = vm::restart,
                    onDone = onBack,
                )
            }
        }
    }
}

@Composable
private fun LearnPhase(vm: VocabViewModel, state: VocabState) {
    Column(Modifier.fillMaxSize()) {
        WordPractice(vm, state, Modifier.weight(1f))
        LearnNavigation(vm, state)
    }
}

@Composable
private fun WordPractice(vm: VocabViewModel, state: VocabState, modifier: Modifier) {
    val word = vm.words[state.index]
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val withMic = rememberMicPermission(onDenied = vm::micDenied)

    LaunchedEffect(state.index, speakerState) { vm.speak() }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProgressHeader(state.index + 1, vm.words.size)
        TtsWarning(speakerState)
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(word.emoji, fontSize = 80.sp)
                HanziBlock(word.hanzi, word.pinyin, word.thai, hanziSize = 48)
                if (word.english.isNotEmpty()) {
                    Text(
                        word.english,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SpeakButton(onClick = { vm.speak() })
            SpeakButton(onClick = { vm.speak(slow = true) }, slow = true)
            if (vm.micAvailable) MicButton(listening, onClick = { withMic(vm::record) })
        }
        if (vm.micAvailable) {
            Text("กด 🎤 แล้วพูดตาม", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("เครื่องนี้ฟังเสียงไม่ได้ ฟังแล้วกดถัดไปได้เลย", style = MaterialTheme.typography.bodyMedium)
        }
        state.scores[state.index]?.let { best ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("คะแนนออกเสียง $best  ", fontWeight = FontWeight.Bold)
                StarsRow(GameRules.starsForScore(best))
                if (vm.passed(state)) Text("  ผ่าน ✓", fontWeight = FontWeight.Bold, color = Correct)
            }
        }
        state.heard?.let { PinyinText("ได้ยินว่า: $it", style = MaterialTheme.typography.bodyMedium) }
        state.message?.let { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
    }
}

/** Pinned under the scrolling word so ◀/▶ are always reachable. */
@Composable
private fun LearnNavigation(vm: VocabViewModel, state: VocabState) {
    val canGoNext = vm.canGoNext(state)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!canGoNext) {
            Text(
                "พูดให้ได้ ${GameRules.SPEECH_PASS_SCORE} คะแนนขึ้นไป ถึงจะไปคำถัดไปได้",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (vm.canSkip(state)) {
            TextButton(onClick = vm::skip) { Text("ข้ามคำนี้ไปก่อน") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = vm::previous,
                enabled = state.index > 0,
                modifier = Modifier.weight(1f),
            ) { Text("◀ ย้อนกลับ") }
            Button(onClick = vm::next, enabled = canGoNext, modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        !canGoNext -> "ถัดไป 🔒"
                        state.index < vm.words.lastIndex -> "ถัดไป ▶"
                        else -> "เกมจับคู่ ▶"
                    }
                )
            }
        }
    }
}

@Composable
private fun MatchPhase(vm: VocabViewModel, state: VocabState) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("จับคู่ภาพกับคำจีน", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("รอบ ${state.round + 1}/${state.rounds.size} · ผิด ${state.mistakes} ครั้ง")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.tiles, key = { it.id }) { tile ->
                MatchTileCard(
                    word = vm.words[tile.wordIndex],
                    tile = tile,
                    selected = state.selected == tile,
                    matched = tile.wordIndex in state.matched,
                    wrong = tile in state.wrong,
                    onClick = { vm.tap(tile) },
                )
            }
        }
    }
}

@Composable
private fun MatchTileCard(
    word: Word,
    tile: MatchTile,
    selected: Boolean,
    matched: Boolean,
    wrong: Boolean,
    onClick: () -> Unit,
) {
    val color = when {
        wrong -> Wrong
        matched -> Correct
        selected -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(
        onClick = onClick,
        enabled = !matched,
        modifier = Modifier.fillMaxWidth().height(120.dp).alpha(if (matched) 0.4f else 1f),
        colors = CardDefaults.cardColors(containerColor = color, disabledContainerColor = color),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (tile.isHanzi) {
                PinyinText(word.hanzi, pinyin = word.pinyin, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            } else {
                Text(word.emoji, fontSize = 28.sp)
                Text(word.thai, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                if (word.english.isNotEmpty()) {
                    Text(
                        word.english,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
