package com.example.nihaogo.ui.special

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Line
import com.example.nihaogo.data.content.ModeKind
import com.example.nihaogo.data.content.Person
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DialogueState(
    val turn: Int = 0,
    /** Wrong options already tried on this turn. */
    val tried: Set<Int> = emptySet(),
    val solved: Boolean = false,
    val taskTried: Set<Int> = emptySet(),
    val taskSolved: Boolean = false,
    /** Questions and tasks answered right on the first try. */
    val points: Int = 0,
)

class DialogueSceneViewModel(container: AppContainer, modeId: String, index: Int) :
    SceneViewModel<Scene.Dialogue>(container, modeId, index) {

    private val _state = MutableStateFlow(DialogueState())
    val state: StateFlow<DialogueState> = _state.asStateFlow()

    /** Every question plus every task is worth one point. */
    val totalPoints = scene.turns.size + scene.turns.count { it.task != null }

    override fun reset() {
        _state.value = DialogueState()
    }

    fun speaker(turn: Int): Person? = scene.turns[turn].speaker ?: scene.npc

    fun choose(option: Int) {
        val s = _state.value
        val turn = scene.turns[s.turn]
        if (s.solved || option in s.tried) return
        if (option == turn.answer) {
            speak(turn.reply.hanzi)
            _state.update { it.copy(solved = true, points = it.points + if (it.tried.isEmpty()) 1 else 0) }
        } else {
            _state.update { it.copy(tried = it.tried + option) }
        }
    }

    fun chooseTask(option: Int) {
        val s = _state.value
        val task = scene.turns[s.turn].task ?: return
        if (!s.solved || s.taskSolved || option in s.taskTried) return
        if (option == task.answer) {
            speak(task.options[option].hanzi)
            _state.update { it.copy(taskSolved = true, points = it.points + if (it.taskTried.isEmpty()) 1 else 0) }
        } else {
            _state.update { it.copy(taskTried = it.taskTried + option) }
        }
    }

    fun canGoNext(s: DialogueState): Boolean = s.solved && (scene.turns[s.turn].task == null || s.taskSolved)

    fun next() {
        val s = _state.value
        if (!canGoNext(s)) return
        if (s.turn < scene.turns.lastIndex) {
            _state.update { DialogueState(turn = it.turn + 1, points = it.points) }
        } else {
            finish(score(s))
        }
    }

    fun score(s: DialogueState = _state.value): Int = s.points * 100 / totalPoints
}

@Composable
fun DialogueSceneScreen(modeId: String, index: Int, onBack: () -> Unit) {
    val vm = appViewModel { DialogueSceneViewModel(it, modeId, index) }
    val state by vm.state.collectAsStateWithLifecycle()
    val detective = vm.mode.kind == ModeKind.Detective

    SceneFrame(
        vm = vm,
        onBack = onBack,
        passedMessage = if (detective) "สอบปากคำเสร็จแล้ว!" else "จบกะแล้ว! ดูแลทุกคนได้ดีมาก",
        detail = { "ถูกตั้งแต่ครั้งแรก ${state.points}/${vm.totalPoints}" },
    ) { padding ->
        val turn = vm.scene.turns[state.turn]
        val scroll = rememberScrollState()
        LaunchedEffect(state.turn) {
            scroll.scrollTo(0)
            vm.speak(turn.npc.hanzi)
        }
        // Bring the reply, task and Next button into view as soon as they appear.
        LaunchedEffect(state.solved, state.taskSolved) {
            if (state.solved) {
                delay(100) // wait one layout pass so maxValue includes the new content
                scroll.animateScrollTo(scroll.maxValue)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressHeader(state.turn + 1, vm.scene.turns.size)
            vm.speaker(state.turn)?.let { SpeakerHeader(it) }
            SpeechBubble(turn.npc, onSpeak = { vm.speak(turn.npc.hanzi) })

            Text(
                if (detective) "คุณจะถามว่าอะไร?" else "คุณจะพูดว่าอะไร?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            turn.options.forEachIndexed { i, option ->
                OptionCard(
                    line = option,
                    color = when {
                        state.solved && i == turn.answer -> Correct
                        i in state.tried -> Wrong
                        else -> null
                    },
                    showThai = state.solved || i in state.tried,
                    onClick = { vm.choose(i) },
                )
            }

            if (state.solved) {
                vm.speaker(state.turn)?.let { Text("${it.emoji} ${it.name} ตอบว่า:", fontWeight = FontWeight.Bold) }
                SpeechBubble(turn.reply, onSpeak = { vm.speak(turn.reply.hanzi) })
                if (turn.clueTh.isNotBlank()) ClueCard(turn.clueTh, isNew = true)
            }

            val task = turn.task
            if (state.solved && task != null) {
                Text(task.promptTh, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.options.forEachIndexed { i, option ->
                        val color = when {
                            state.taskSolved && i == task.answer -> Correct.copy(alpha = 0.35f)
                            i in state.taskTried -> Wrong.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        Card(
                            onClick = { vm.chooseTask(i) },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = color),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(option.emoji, fontSize = 36.sp)
                                PinyinText(option.hanzi, pinyin = option.pinyin, fontWeight = FontWeight.Bold)
                                if (state.taskSolved || i in state.taskTried) {
                                    Text(option.thai, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            if (vm.canGoNext(state)) {
                Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.turn < vm.scene.turns.lastIndex) "ถัดไป ▶" else "ดูผลลัพธ์")
                }
            }
        }
    }
}

@Composable
fun SpeakerHeader(person: Person) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(person.emoji, fontSize = 48.sp)
        Column(Modifier.padding(start = 12.dp)) {
            PinyinText(person.name, pinyin = person.namePinyin, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (person.roleTh.isNotBlank()) Text(person.roleTh, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Chinese line with pinyin above, Thai below and a replay button. */
@Composable
fun SpeechBubble(line: Line, onSpeak: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PinyinText(line.hanzi, pinyin = line.pinyin, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(line.thai, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onSpeak) { Text("🔊", fontSize = 22.sp) }
        }
    }
}

@Composable
private fun OptionCard(line: Line, color: Color?, showThai: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color?.copy(alpha = 0.3f) ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PinyinText(line.hanzi, pinyin = line.pinyin, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (showThai) Text(line.thai, style = MaterialTheme.typography.bodySmall)
        }
    }
}
