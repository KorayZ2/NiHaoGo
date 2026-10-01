package com.example.nihaogo.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.nihaogo.data.content.ListeningItem
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.HanziBlock
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.StepResult
import com.example.nihaogo.ui.common.StepResultPanel
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.recordStep
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListeningState(
    val index: Int = 0,
    /** Options tried on the current question (wrong ones stay red). */
    val tried: Set<Int> = emptySet(),
    val solved: Boolean = false,
    val showText: Boolean = false,
    val firstTryCorrect: Int = 0,
    val result: StepResult? = null,
)

class ListeningViewModel(private val container: AppContainer, private val categoryId: String) : ViewModel() {
    val items: List<ListeningItem> = container.content.category(categoryId).listening
    val speakerState = container.speaker.state

    private val _state = MutableStateFlow(ListeningState())
    val state: StateFlow<ListeningState> = _state.asStateFlow()

    fun play(slow: Boolean = false) = container.speaker.speak(items[_state.value.index].hanzi, slow)

    fun toggleText() = _state.update { it.copy(showText = !it.showText) }

    fun choose(option: Int) {
        val s = _state.value
        if (s.solved || option in s.tried) return
        val correct = option == items[s.index].answer
        _state.update {
            it.copy(
                tried = it.tried + option,
                solved = correct,
                showText = it.showText || correct,
                firstTryCorrect = it.firstTryCorrect + if (correct && it.tried.isEmpty()) 1 else 0,
            )
        }
    }

    fun next() {
        val s = _state.value
        if (s.index < items.lastIndex) {
            _state.update { it.copy(index = it.index + 1, tried = emptySet(), solved = false, showText = false) }
        } else {
            viewModelScope.launch {
                val score = s.firstTryCorrect * 100 / items.size
                val stars = GameRules.starsForScore(score)
                val outcome = container.progress.recordStep(categoryId, Step.Listening, stars)
                _state.update { it.copy(result = StepResult(score, stars, outcome.coinsEarned)) }
            }
        }
    }

    fun restart() {
        _state.value = ListeningState()
    }
}

@Composable
fun ListeningStepScreen(categoryId: String, onBack: () -> Unit) {
    val vm = appViewModel { ListeningViewModel(it, categoryId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()

    InkScaffold(topBar = { GameTopBar(Step.Listening.titleTh, coins = null, onBack = onBack) }) { padding ->
        val result = state.result
        if (result != null) {
            StepResultPanel(
                result = result,
                passedMessage = "หูไวมาก!",
                detail = "ตอบถูกตั้งแต่ครั้งแรก ${state.firstTryCorrect}/${vm.items.size} ข้อ",
                onRetry = vm::restart,
                onDone = onBack,
                modifier = Modifier.padding(padding),
            )
            return@InkScaffold
        }

        val item = vm.items[state.index]
        LaunchedEffect(state.index, speakerState) { vm.play() }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressHeader(state.index + 1, vm.items.size)
            TtsWarning(speakerState)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                SpeakButton(onClick = { vm.play() })
                SpeakButton(onClick = { vm.play(slow = true) }, slow = true)
            }
            if (state.showText) {
                HanziBlock(item.hanzi, item.pinyin, item.thai, Modifier.fillMaxWidth(), hanziSize = 32)
            } else {
                TextButton(onClick = vm::toggleText, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("ฟังไม่ทัน? แสดงตัวอักษร")
                }
            }
            Text(item.questionTh, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            item.options.forEachIndexed { i, option ->
                val color = when {
                    state.solved && i == item.answer -> Correct
                    i in state.tried && i != item.answer -> Wrong
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                Card(
                    onClick = { vm.choose(i) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = color),
                ) {
                    PinyinText(option, modifier = Modifier.padding(16.dp), fontSize = 20.sp)
                }
            }
            if (state.solved) {
                Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.index < vm.items.lastIndex) "ถัดไป" else "ดูผลลัพธ์")
                }
            }
        }
    }
}
