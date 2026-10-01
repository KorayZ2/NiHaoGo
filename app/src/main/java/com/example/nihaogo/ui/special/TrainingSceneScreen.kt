package com.example.nihaogo.ui.special

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.speech.Heard
import com.example.nihaogo.ui.common.HanziBlock
import com.example.nihaogo.ui.common.MicButton
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.StarsRow
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.rememberMicPermission
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TrainingState(
    val quiz: Boolean = false,
    val index: Int = 0,
    /** Best pronunciation score per word index. */
    val scores: Map<Int, Int> = emptyMap(),
    val message: String? = null,
    /** Quiz: word indexes in question order, and the 4 choices of the current one. */
    val order: List<Int> = emptyList(),
    val choices: List<Int> = emptyList(),
    val tried: Set<Int> = emptySet(),
    val solved: Boolean = false,
    val quizFirstTry: Int = 0,
)

class TrainingSceneViewModel(container: AppContainer, modeId: String, index: Int) :
    SceneViewModel<Scene.Training>(container, modeId, index) {

    val words = scene.words
    val micAvailable = container.speechInput.isAvailable
    val listening = container.speechInput.listening

    private val _state = MutableStateFlow(TrainingState())
    val state: StateFlow<TrainingState> = _state.asStateFlow()

    override fun reset() {
        _state.value = TrainingState()
    }

    fun record() {
        val i = _state.value.index
        viewModelScope.launch {
            when (val heard = container.speechInput.listen()) {
                is Heard.Failed -> _state.update { it.copy(message = heard.messageTh) }
                is Heard.Text -> {
                    val score = heard.candidates.maxOf { ChineseText.similarityScore(words[i].hanzi, it) }
                    _state.update {
                        it.copy(
                            scores = it.scores + (i to maxOf(score, it.scores[i] ?: 0)),
                            message = if (score >= GameRules.SPEECH_PASS_SCORE) "ดีมาก! 👍" else "ลองฟังแล้วพูดอีกครั้งนะ",
                        )
                    }
                }
            }
        }
    }

    fun nextWord() {
        val s = _state.value
        if (s.index < words.lastIndex) {
            _state.update { it.copy(index = it.index + 1, message = null) }
        } else {
            val order = words.indices.shuffled()
            _state.update { it.copy(quiz = true, order = order, index = 0, message = null) }
            loadQuestion(0)
        }
    }

    private fun loadQuestion(q: Int) {
        val answer = _state.value.order[q]
        val choices = (words.indices.filter { it != answer }.shuffled().take(3) + answer).shuffled()
        _state.update { it.copy(index = q, choices = choices, tried = emptySet(), solved = false) }
    }

    fun questionWord(s: TrainingState) = words[s.order[s.index]]

    fun choose(wordIndex: Int) {
        val s = _state.value
        if (s.solved || wordIndex in s.tried) return
        if (wordIndex == s.order[s.index]) {
            _state.update { it.copy(solved = true, quizFirstTry = it.quizFirstTry + if (it.tried.isEmpty()) 1 else 0) }
        } else {
            _state.update { it.copy(tried = it.tried + wordIndex) }
        }
    }

    fun nextQuestion() {
        val s = _state.value
        if (s.index < s.order.lastIndex) loadQuestion(s.index + 1) else finish(score(s))
    }

    fun speechAverage(s: TrainingState = _state.value): Int = words.indices.map { s.scores[it] ?: 0 }.average().roundToInt()

    fun quizScore(s: TrainingState = _state.value): Int = s.quizFirstTry * 100 / words.size

    fun score(s: TrainingState = _state.value): Int =
        if (micAvailable) (0.4 * speechAverage(s) + 0.6 * quizScore(s)).roundToInt() else quizScore(s)
}

@Composable
fun TrainingSceneScreen(modeId: String, index: Int, onBack: () -> Unit) {
    val vm = appViewModel { TrainingSceneViewModel(it, modeId, index) }
    val state by vm.state.collectAsStateWithLifecycle()

    SceneFrame(
        vm = vm,
        onBack = onBack,
        passedMessage = "ผ่านการอบรมแล้ว! ได้ตำแหน่ง${vm.mode.ranks.getOrElse(vm.index) { "ใหม่" }} 🎖️",
        detail = {
            if (vm.micAvailable) "ออกเสียง ${vm.speechAverage()} · แบบทดสอบ ${vm.quizScore()}" else "แบบทดสอบ ${vm.quizScore()}"
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.quiz) Quiz(vm, state) else Learn(vm, state)
        }
    }
}

@Composable
private fun Learn(vm: TrainingSceneViewModel, state: TrainingState) {
    val word = vm.words[state.index]
    val listening by vm.listening.collectAsStateWithLifecycle()
    val withMic = rememberMicPermission()
    LaunchedEffect(state.index) { vm.speak(word.hanzi) }

    ProgressHeader(state.index + 1, vm.words.size)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(word.emoji, fontSize = 72.sp)
            HanziBlock(word.hanzi, word.pinyin, word.thai, hanziSize = 44)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        SpeakButton(onClick = { vm.speak(word.hanzi) })
        SpeakButton(onClick = { vm.speak(word.hanzi, slow = true) }, slow = true)
        if (vm.micAvailable) MicButton(listening, onClick = { withMic(vm::record) })
    }
    Text(
        if (vm.micAvailable) "กด 🎤 แล้วพูดตาม (คะแนนออกเสียงนับรวมในผลลัพธ์)" else "ฟังแล้วกดถัดไปได้เลย",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    state.scores[state.index]?.let { best ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("คะแนนออกเสียง $best  ", fontWeight = FontWeight.Bold)
            StarsRow(GameRules.starsForScore(best))
        }
    }
    state.message?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    Button(onClick = vm::nextWord, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.index < vm.words.lastIndex) "ถัดไป ▶" else "ไปทำแบบทดสอบ ▶")
    }
}

@Composable
private fun Quiz(vm: TrainingSceneViewModel, state: TrainingState) {
    val word = vm.questionWord(state)
    LaunchedEffect(state.index, state.quiz) { vm.speak(word.hanzi) }

    Text("🎧 แบบทดสอบฟังเสียง", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    ProgressHeader(state.index + 1, state.order.size)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SpeakButton(onClick = { vm.speak(word.hanzi) })
        SpeakButton(onClick = { vm.speak(word.hanzi, slow = true) }, slow = true)
    }
    Text("ได้ยินคำว่าอะไร?", style = MaterialTheme.typography.titleMedium)
    state.choices.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { choice ->
                val option = vm.words[choice]
                val color = when {
                    state.solved && choice == state.order[state.index] -> Correct.copy(alpha = 0.35f)
                    choice in state.tried -> Wrong.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                Card(
                    onClick = { vm.choose(choice) },
                    modifier = Modifier.weight(1f).height(110.dp),
                    colors = CardDefaults.cardColors(containerColor = color),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(option.emoji, fontSize = 36.sp)
                        Text(option.thai, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (state.solved) {
        HanziBlock(word.hanzi, word.pinyin, thai = null, hanziSize = 32)
        Button(onClick = vm::nextQuestion, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.index < state.order.lastIndex) "ข้อต่อไป ▶" else "ดูผลลัพธ์")
        }
    }
}
