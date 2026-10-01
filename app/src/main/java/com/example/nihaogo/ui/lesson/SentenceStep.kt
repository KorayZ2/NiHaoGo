package com.example.nihaogo.ui.lesson

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Sentence
import com.example.nihaogo.data.content.Step
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
import com.example.nihaogo.ui.common.StepResult
import com.example.nihaogo.ui.common.StepResultPanel
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.recordStep
import com.example.nihaogo.ui.common.rememberMicPermission
import com.example.nihaogo.ui.common.FeedbackCard
import com.example.nihaogo.ui.common.TileChip
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordTile(val id: Int, val text: String)

data class SentenceState(
    val index: Int = 0,
    val bank: List<WordTile> = emptyList(),
    val answer: List<WordTile> = emptyList(),
    val check: Check? = null,
    val wrongAttempts: Int = 0,
    val firstTryCorrect: Int = 0,
    val speechMessage: String? = null,
    val result: StepResult? = null,
) {
    enum class Check { Correct, Wrong, Revealed }
}

class SentenceViewModel(private val container: AppContainer, private val categoryId: String) : ViewModel() {
    val sentences: List<Sentence> = container.content.category(categoryId).sentences
    val micAvailable = container.speechInput.isAvailable
    val listening = container.speechInput.listening

    private val _state = MutableStateFlow(SentenceState())
    val state: StateFlow<SentenceState> = _state.asStateFlow()

    val current: Sentence get() = sentences[_state.value.index]

    init {
        load(0)
    }

    private fun load(index: Int) {
        val s = sentences[index]
        val bank = (s.tiles + s.distractors).mapIndexed { i, text -> WordTile(i, text) }.shuffled()
        _state.update {
            it.copy(index = index, bank = bank, answer = emptyList(), check = null, wrongAttempts = 0, speechMessage = null)
        }
    }

    fun pick(tile: WordTile) {
        if (_state.value.check != null) return
        _state.update { it.copy(bank = it.bank - tile, answer = it.answer + tile) }
    }

    fun unpick(tile: WordTile) {
        if (_state.value.check != null) return
        _state.update { it.copy(answer = it.answer - tile, bank = it.bank + tile) }
    }

    fun check() {
        val s = _state.value
        val correct = s.answer.map { it.text } == current.tiles
        if (correct) {
            container.speaker.speak(current.hanzi)
            _state.update {
                it.copy(
                    check = SentenceState.Check.Correct,
                    firstTryCorrect = it.firstTryCorrect + if (it.wrongAttempts == 0) 1 else 0,
                )
            }
        } else {
            _state.update { it.copy(check = SentenceState.Check.Wrong, wrongAttempts = it.wrongAttempts + 1) }
        }
    }

    fun tryAgain() {
        _state.update { it.copy(check = null, bank = (it.bank + it.answer).shuffled(), answer = emptyList()) }
    }

    fun reveal() {
        container.speaker.speak(current.hanzi)
        _state.update { it.copy(check = SentenceState.Check.Revealed) }
    }

    fun speak() = container.speaker.speak(current.hanzi)

    /** Optional practice: say the whole sentence. Does not affect stars. */
    fun practiceSpeaking() {
        viewModelScope.launch {
            val message = when (val heard = container.speechInput.listen()) {
                is Heard.Failed -> heard.messageTh
                is Heard.Text -> {
                    val score = heard.candidates.maxOf { ChineseText.similarityScore(current.hanzi, it) }
                    "พูดได้ $score คะแนน " + "⭐".repeat(GameRules.starsForScore(score))
                }
            }
            _state.update { it.copy(speechMessage = message) }
        }
    }

    fun next() {
        val s = _state.value
        if (s.index < sentences.lastIndex) {
            load(s.index + 1)
        } else {
            viewModelScope.launch {
                val score = s.firstTryCorrect * 100 / sentences.size
                val stars = GameRules.starsForScore(score)
                val outcome = container.progress.recordStep(categoryId, Step.Sentence, stars)
                _state.update { it.copy(result = StepResult(score, stars, outcome.coinsEarned)) }
            }
        }
    }

    fun restart() {
        _state.value = SentenceState()
        load(0)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceStepScreen(categoryId: String, onBack: () -> Unit) {
    val vm = appViewModel { SentenceViewModel(it, categoryId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val withMic = rememberMicPermission()

    InkScaffold(topBar = { GameTopBar(Step.Sentence.titleTh, coins = null, onBack = onBack) }) { padding ->
        val result = state.result
        if (result != null) {
            StepResultPanel(
                result = result,
                passedMessage = "เรียงประโยคเก่งมาก!",
                detail = "ถูกตั้งแต่ครั้งแรก ${state.firstTryCorrect}/${vm.sentences.size} ประโยค",
                onRetry = vm::restart,
                onDone = onBack,
                modifier = Modifier.padding(padding),
            )
            return@InkScaffold
        }

        val sentence = vm.sentences[state.index]
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressHeader(state.index + 1, vm.sentences.size)
            Text("แปลเป็นภาษาจีน", style = MaterialTheme.typography.labelLarge)
            Text("“${sentence.thai}”", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            val answerBorder = when (state.check) {
                SentenceState.Check.Correct -> Correct
                SentenceState.Check.Wrong -> Wrong
                else -> MaterialTheme.colorScheme.outline
            }
            OutlinedCard(Modifier.fillMaxWidth().heightIn(min = 80.dp), border = BorderStroke(2.dp, answerBorder)) {
                FlowRow(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.answer.forEach { tile -> TileChip(tile.text, onClick = { vm.unpick(tile) }) }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.bank.forEach { tile ->
                    TileChip(tile.text, onClick = { vm.pick(tile) })
                }
            }

            when (state.check) {
                null -> Button(onClick = vm::check, enabled = state.answer.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("ตรวจคำตอบ")
                }
                SentenceState.Check.Wrong -> {
                    FeedbackCard("ยังไม่ถูกนะ 🤔", sentence.tipTh, Wrong)
                    Button(onClick = vm::tryAgain, modifier = Modifier.fillMaxWidth()) { Text("ลองใหม่") }
                    if (state.wrongAttempts >= 2) {
                        OutlinedButton(onClick = vm::reveal, modifier = Modifier.fillMaxWidth()) { Text("ดูเฉลย") }
                    }
                }
                SentenceState.Check.Correct, SentenceState.Check.Revealed -> {
                    val correct = state.check == SentenceState.Check.Correct
                    FeedbackCard(
                        if (correct) "ถูกต้อง! 🎉" else "เฉลย",
                        sentence.tipTh,
                        if (correct) Correct else MaterialTheme.colorScheme.tertiary,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HanziBlock(sentence.hanzi, sentence.pinyin, thai = null, hanziSize = 28, modifier = Modifier.weight(1f))
                        SpeakButton(onClick = vm::speak)
                        if (vm.micAvailable) MicButton(listening, onClick = { withMic(vm::practiceSpeaking) })
                    }
                    state.speechMessage?.let { Text(it, modifier = Modifier.align(Alignment.CenterHorizontally)) }
                    Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.index < vm.sentences.lastIndex) "ถัดไป" else "ดูผลลัพธ์")
                    }
                }
            }
        }
    }
}

