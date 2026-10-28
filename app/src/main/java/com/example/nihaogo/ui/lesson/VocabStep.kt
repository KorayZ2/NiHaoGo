package com.example.nihaogo.ui.lesson

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.example.nihaogo.ui.common.Mascot
import com.example.nihaogo.ui.common.pressScale
import com.example.nihaogo.ui.common.rememberSfx
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.RedLight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.example.nihaogo.R
import com.example.nihaogo.data.content.Sentence
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.Mission
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.speech.Heard
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameOutlinedButton
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.answerFeedback
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
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
import com.example.nihaogo.ui.theme.CorrectLight
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.RedDeep
import com.example.nihaogo.ui.theme.Wood
import com.example.nihaogo.ui.theme.WrongLight
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
    val category = container.content.category(categoryId)
    val number: Int = container.content.categories.indexOf(category) + 1
    val words: List<Word> = category.words

    /** For each word, a sentence from this category's sentence step that uses it, if there is one. */
    val examples: List<Sentence?> = words.map { word -> category.sentences.firstOrNull { word.hanzi in it.hanzi } }
    val micAvailable = container.speechInput.isAvailable
    val speakerState = container.speaker.state
    val listening = container.speechInput.listening

    private val _state = MutableStateFlow(VocabState())
    val state: StateFlow<VocabState> = _state.asStateFlow()

    fun speak(slow: Boolean = false) = container.speaker.speak(words[_state.value.index].hanzi, slow)

    fun speakExample() {
        examples[_state.value.index]?.let { container.speaker.speak(it.hanzi) }
    }

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
                    val pass = score >= GameRules.SPEECH_PASS_SCORE
                    container.sfx.answer(pass)
                    // Each word counts once towards today's missions, the first time it is said well.
                    if (pass && !passed(_state.value, index)) container.missions.add(Mission.Words)
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
        !micAvailable || s.micDenied || passed(s) || s.index in s.skipped || container.devSkip.value

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
            selected == null || selected.isHanzi == tile.isHanzi -> {
                container.sfx.tap()
                _state.update { it.copy(selected = tile) }
            }
            selected.wordIndex == tile.wordIndex -> {
                container.sfx.correct()
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
                container.sfx.wrong()
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
        if (stars > 0) container.missions.add(Mission.Lesson)
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

    InkScaffold(
        topBar = {
            if (state.phase == VocabState.Phase.Learn) {
                LessonHeader(number = vm.number, title = "${vm.category.titleZh} (${vm.category.titleTh})", onClose = onBack)
            } else {
                GameTopBar(Step.Vocab.titleTh, coins = null, onBack = onBack)
            }
        },
    ) { padding ->
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

/** "บทที่ N" over the lesson's name, centred, with a red ✕ in the corner to leave. */
@Composable
private fun LessonHeader(number: Int, title: String, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.align(Alignment.Center).padding(horizontal = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("บทที่ $number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CloseButton(onClose, Modifier.align(Alignment.CenterEnd))
    }
}

/** Small red round button with a white ✕. */
@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sfx = rememberSfx()
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(40.dp)
            .pressScale(press, 0.9f)
            .shadow(4.dp, CircleShape, spotColor = Red)
            .background(Brush.verticalGradient(listOf(RedLight, Red, RedDeep)), CircleShape)
            .clickable(interactionSource = press, indication = LocalIndication.current) {
                sfx.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val withMic = rememberMicPermission(onDenied = vm::micDenied)

    LaunchedEffect(state.index, speakerState) { vm.speak() }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProgressHeader(state.index + 1, vm.words.size, Modifier.padding(horizontal = 8.dp))
        TtsWarning(speakerState)
        // The word card slides in from the side the learner is moving towards.
        AnimatedContent(
            targetState = state.index,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it * dir / 2 } + fadeOut())
            },
            label = "wordCard",
        ) { index ->
            WordScroll(vm.words[index], passed = vm.passed(state, index), onSpeak = { vm.speak() })
        }
        // The qilin stands under the card; what it says and the mic sit beside it.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mascot(
                when {
                    vm.passed(state) -> R.drawable.mascot_cheer
                    state.message != null -> R.drawable.mascot_think
                    else -> R.drawable.mascot_read
                },
                size = 150.dp,
            )
            Column(
                Modifier.weight(1f).padding(start = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpeechBubble(
                    line = state.message ?: if (vm.micAvailable) "กด 🎤 แล้วพูดตามนะ" else "เครื่องนี้ฟังเสียงไม่ได้ ฟังแล้วกดถัดไปได้เลย",
                    detail = state.heard?.let { "ได้ยินว่า: $it" },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.micAvailable) MicButton(listening, onClick = { withMic(vm::record) })
                    SpeakButton(onClick = { vm.speak(slow = true) }, slow = true, modifier = Modifier.size(48.dp))
                }
                state.scores[state.index]?.let { best ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear()) {
                        Text("$best  ", fontWeight = FontWeight.Bold)
                        StarsRow(GameRules.starsForScore(best))
                        if (vm.passed(state)) Text("  ผ่าน ✓", fontWeight = FontWeight.Bold, color = Correct)
                    }
                }
            }
        }
        vm.examples[state.index]?.let { ExampleCard(it, onSpeak = vm::speakExample) }
    }
}

/** A paper speech bubble for the qilin's line. */
@Composable
private fun SpeechBubble(line: String, detail: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Paper,
        border = BorderStroke(1.dp, Hairline),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            PinyinText(line, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            detail?.let { PinyinText(it, style = MaterialTheme.typography.bodySmall, color = InkSoft) }
        }
    }
}

/**
 * The word on a rice-paper scroll: wooden rods top and bottom, a gold line inside the edge, the
 * hanzi large with pinyin and Thai under it, and a red speaker on the right edge.
 */
@Composable
private fun WordScroll(word: Word, passed: Boolean, onSpeak: () -> Unit) {
    Box(Modifier.fillMaxWidth().pop(passed, amount = 0.04f)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = MaterialTheme.shapes.small,
            color = Paper,
            border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.8f)),
            shadowElevation = 4.dp,
        ) {
            Box(Modifier.padding(8.dp).border(1.dp, Gold.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp, top = 18.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(word.hanzi, fontSize = 60.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 68.sp)
                    Text(word.pinyin, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal, color = InkSoft)
                    Text(word.thai, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    if (word.english.isNotEmpty()) {
                        Text(
                            word.english,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Text(word.emoji, fontSize = 30.sp, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                SpeakButton(onClick = onSpeak, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(48.dp))
            }
        }
        // The scroll's wooden rods across the top and bottom of the paper.
        ScrollRod(Modifier.align(Alignment.TopCenter))
        ScrollRod(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ScrollRod(modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .shadow(2.dp, CircleShape)
            .background(Brush.verticalGradient(listOf(Wood.copy(alpha = 0.8f), Wood, Wood.copy(alpha = 0.7f))), CircleShape),
    )
}

/** "ตัวอย่างประโยค" on paper, with the sentence in a cream box and a speaker beside it. */
@Composable
private fun ExampleCard(sentence: Sentence, onSpeak: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Paper,
        border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.6f)),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("ตัวอย่างประโยค", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Cream)
                    .border(1.dp, Hairline, MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sentence.hanzi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(sentence.pinyin, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    Text(sentence.thai, style = MaterialTheme.typography.bodyMedium)
                }
                SpeakButton(onClick = onSpeak, modifier = Modifier.padding(start = 8.dp).size(44.dp))
            }
        }
    }
}

/** Pinned under the scrolling word so back and next are always reachable. */
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
            GameOutlinedButton(
                onClick = vm::previous,
                enabled = state.index > 0,
                modifier = Modifier.weight(1f),
            ) { Text("‹  ย้อนกลับ") }
            GameButton(onClick = vm::next, enabled = canGoNext, modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        !canGoNext -> "ถัดไป 🔒"
                        state.index < vm.words.lastIndex -> "ถัดไป"
                        else -> "เกมจับคู่"
                    }
                )
            }
        }
    }
}

@Composable
private fun MatchPhase(vm: VocabViewModel, state: VocabState) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = Paper,
            border = BorderStroke(1.5.dp, Gold),
            shadowElevation = 3.dp,
        ) {
            Text(
                "จับคู่คำศัพท์กับรูปภาพให้ถูกต้อง",
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("🔄 รอบ ${state.round + 1}/${state.rounds.size}")
            InfoChip("❌ ผิด ${state.mistakes} ครั้ง")
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.tiles, key = { _, t -> "${state.round}-${t.id}" }) { i, tile ->
                MatchTileCard(
                    modifier = Modifier.appear(i * 40),
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
private fun InfoChip(text: String) {
    Surface(shape = CircleShape, color = Paper, border = BorderStroke(1.dp, Hairline), shadowElevation = 2.dp) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** A paper tile in a wooden frame; the frame turns gold when picked, green when matched, red when wrong. */
@Composable
private fun MatchTileCard(
    modifier: Modifier,
    word: Word,
    tile: MatchTile,
    selected: Boolean,
    matched: Boolean,
    wrong: Boolean,
    onClick: () -> Unit,
) {
    val fill by animateColorAsState(
        when {
            wrong -> WrongLight
            matched -> CorrectLight
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> Paper
        },
        label = "tileFill",
    )
    val frame by animateColorAsState(
        when {
            wrong -> Wrong
            matched -> Correct
            selected -> Gold
            else -> Wood
        },
        label = "tileFrame",
    )
    val alpha by animateFloatAsState(if (matched) 0.55f else 1f, tween(400, delayMillis = 250), label = "tileAlpha")
    GameCard(
        onClick = onClick,
        enabled = !matched,
        sound = false,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .answerFeedback(correct = matched, wrong = wrong)
            .pop(selected, amount = 0.05f)
            .alpha(alpha),
        colors = CardDefaults.cardColors(containerColor = fill, disabledContainerColor = fill),
        border = BorderStroke(if (selected || matched || wrong) 3.dp else 2.5.dp, frame),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (tile.isHanzi) {
                    PinyinText(word.hanzi, pinyin = word.pinyin, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                } else {
                    Text(word.emoji, fontSize = 30.sp)
                    Text(word.thai, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
            if (matched) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                    shape = CircleShape,
                    color = Correct,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
