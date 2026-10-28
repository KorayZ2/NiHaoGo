package com.example.nihaogo.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.nihaogo.ui.common.AnswerOption
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PaperCard
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.StepResult
import com.example.nihaogo.ui.common.StepResultPanel
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val QUESTIONS = 10

/** How a question asks about its word. */
enum class QuestionKind { ThaiToHanzi, HanziToThai, ListenToHanzi }

data class ExerciseQuestion(val kind: QuestionKind, val answer: Word, val options: List<Word>) {
    val answerIndex: Int get() = options.indexOf(answer)
}

data class ExerciseState(
    val loading: Boolean = true,
    val questions: List<ExerciseQuestion> = emptyList(),
    val index: Int = 0,
    /** The option picked on the current question; one pick per question. */
    val chosen: Int? = null,
    val correct: Int = 0,
    val result: StepResult? = null,
) {
    val question: ExerciseQuestion? get() = questions.getOrNull(index)
    val answered: Boolean get() = chosen != null
    val rightNow: Boolean get() = chosen != null && chosen == question?.answerIndex
}

class ExerciseViewModel(private val container: AppContainer) : ViewModel() {
    val speakerState = container.speaker.state
    private var pool: List<Word> = emptyList()

    private val _state = MutableStateFlow(ExerciseState())
    val state: StateFlow<ExerciseState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            pool = container.openedWords()
            start()
        }
    }

    private fun start() {
        // Words repeat only when fewer than ten are open.
        val picks = if (pool.isEmpty()) emptyList() else generateSequence { pool.shuffled() }.flatten().take(QUESTIONS).toList()
        val questions = picks.map { word ->
            val options = (distractorsFor(word, pool, 3) + word).shuffled()
            ExerciseQuestion(QuestionKind.entries.random(), word, options)
        }
        _state.value = ExerciseState(loading = false, questions = questions)
    }

    fun speak(slow: Boolean = false) {
        _state.value.question?.let { container.speaker.speak(it.answer.hanzi, slow) }
    }

    fun choose(option: Int) {
        val s = _state.value
        val q = s.question ?: return
        if (s.answered) return
        val right = option == q.answerIndex
        container.sfx.answer(right)
        if (right) container.missions.add(Mission.Words)
        // Hearing the word after answering helps it stick.
        if (q.kind != QuestionKind.ListenToHanzi) container.speaker.speak(q.answer.hanzi)
        _state.update { it.copy(chosen = option, correct = it.correct + if (right) 1 else 0) }
    }

    fun next() {
        val s = _state.value
        if (!s.answered) return
        if (s.index < s.questions.lastIndex) {
            _state.update { it.copy(index = it.index + 1, chosen = null) }
        } else {
            val score = s.correct * 100 / s.questions.size
            container.missions.add(Mission.Exercise)
            _state.update { it.copy(result = StepResult(score, GameRules.starsForScore(score), coinsEarned = 0)) }
        }
    }

    fun restart() = start()
}

@Composable
fun ExerciseScreen(onBack: () -> Unit) {
    val vm = appViewModel { ExerciseViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()

    InkScaffold(topBar = { GameTopBar("แบบฝึกหัด", coins = null, onBack = onBack) }) { padding ->
        val result = state.result
        val question = state.question
        when {
            result != null -> StepResultPanel(
                result = result,
                passedMessage = "ทำแบบฝึกหัดครบแล้ว!",
                detail = "ตอบถูก ${state.correct}/${state.questions.size} ข้อ",
                onRetry = vm::restart,
                onDone = onBack,
                failedDoneLabel = "กลับหน้าแรก",
                modifier = Modifier.padding(padding),
            )
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            question == null -> NoWords(Modifier.padding(padding))
            else -> {
                LaunchedEffect(state.index, speakerState) {
                    if (question.kind == QuestionKind.ListenToHanzi) vm.speak()
                }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProgressHeader(state.index + 1, state.questions.size)
                        TtsWarning(speakerState)
                        QuestionPrompt(state.index, question, onSpeak = vm::speak)
                        question.options.forEachIndexed { i, option ->
                            AnswerOption(
                                index = i,
                                right = state.answered && i == question.answerIndex,
                                wrong = state.chosen == i && i != question.answerIndex,
                                onClick = { vm.choose(i) },
                            ) {
                                if (question.kind == QuestionKind.HanziToThai) {
                                    Text(option.thai, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                    PinyinText(option.hanzi, pinyin = option.pinyin, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        QilinReaction(state.answered, state.rightNow, question, Modifier.align(Alignment.End))
                        GameButton(onClick = vm::next, enabled = state.answered, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.index < state.questions.lastIndex) "ข้อถัดไป" else "ดูผลลัพธ์")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionPrompt(index: Int, question: ExerciseQuestion, onSpeak: (Boolean) -> Unit) {
    val word = question.answer
    when (question.kind) {
        QuestionKind.ThaiToHanzi -> Text(
            "ข้อ ${index + 1}. ข้อใดแปลว่า “${word.thai}” ?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        QuestionKind.HanziToThai -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ข้อ ${index + 1}. คำนี้แปลว่าอะไร ?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            PaperCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PinyinText(
                        word.hanzi,
                        pinyin = word.pinyin,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    SpeakButton(onClick = { onSpeak(false) })
                }
            }
        }
        QuestionKind.ListenToHanzi -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ข้อ ${index + 1}. ฟังแล้วเลือกคำที่ได้ยิน", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            ) {
                SpeakButton(onClick = { onSpeak(false) })
                SpeakButton(onClick = { onSpeak(true) }, slow = true)
            }
        }
    }
}

/** The qilin pops up in the corner after an answer, with a word of praise or the right answer. */
@Composable
private fun QilinReaction(answered: Boolean, right: Boolean, question: ExerciseQuestion, modifier: Modifier) {
    AnimatedVisibility(
        visible = answered,
        modifier = modifier,
        enter = scaleIn(initialScale = 0.4f) + fadeIn(),
        exit = scaleOut(targetScale = 0.6f) + fadeOut(),
    ) {
        val bob = floatOffset()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Paper,
                border = BorderStroke(1.dp, Hairline),
                shadowElevation = 3.dp,
            ) {
                val answer = question.answer
                PinyinText(
                    if (right) "เก่งมาก!" else "คำตอบคือ ${answer.hanzi} (${answer.thai})",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (right) Correct else Wrong,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Image(
                painterResource(if (right) R.drawable.mascot_cheer else R.drawable.mascot_think),
                contentDescription = null,
                modifier = Modifier.size(96.dp).graphicsLayer { translationY = -bob * 6.dp.toPx() },
            )
        }
    }
}

@Composable
internal fun NoWords(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.mascot_think), contentDescription = null, modifier = Modifier.size(140.dp))
        Text(
            "ยังไม่มีคำศัพท์ให้ฝึก เปิดบทเรียนแรกก่อนนะ",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(),
        )
    }
}
