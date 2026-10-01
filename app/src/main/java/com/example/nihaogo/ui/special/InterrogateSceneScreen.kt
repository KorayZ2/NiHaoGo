package com.example.nihaogo.ui.special

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Line
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.Suspect
import com.example.nihaogo.data.content.clues
import com.example.nihaogo.data.user.SpecialRules
import com.example.nihaogo.speech.Heard
import com.example.nihaogo.ui.common.MicButton
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.rememberMicPermission
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CaseEntry {
    data class Asked(val suspectId: String, val text: String) : CaseEntry
    data class Answered(val suspectId: String, val line: Line) : CaseEntry
    data class Feedback(val text: String) : CaseEntry
    /** A wrongly accused suspect explains where they were. */
    data class Cleared(val suspectId: String, val alibi: Line) : CaseEntry
}

data class InterrogateState(
    val selected: String = "",
    val log: List<CaseEntry> = emptyList(),
    /** Questions the suspects understood. */
    val asked: Int = 0,
    val input: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val accusing: Boolean = false,
    val cleared: Set<String> = emptySet(),
    val wrongGuesses: Int = 0,
    val solved: Boolean = false,
)

class InterrogateSceneViewModel(container: AppContainer, modeId: String, index: Int) :
    SceneViewModel<Scene.Interrogate>(container, modeId, index) {

    val suspects: List<Suspect> = mode.suspects
    /** Clues from the earlier scenes of the case. */
    val clues: List<String> = mode.scenes.take(index).flatMap { it.clues }
    val micAvailable = container.speechInput.isAvailable
    val listening = container.speechInput.listening

    private var engine = container.interrogation(mode)
    val usesAi: Boolean get() = engine.usesAi

    private val _state = MutableStateFlow(InterrogateState(selected = suspects.first().id))
    val state: StateFlow<InterrogateState> = _state.asStateFlow()

    private var retry: (() -> Unit)? = null

    fun suspect(id: String): Suspect = suspects.first { it.id == id }

    override fun reset() {
        engine = container.interrogation(mode)
        _state.value = InterrogateState(selected = suspects.first().id)
    }

    fun select(id: String) = _state.update { it.copy(selected = id, notice = null) }

    fun onInput(text: String) = _state.update { it.copy(input = text) }

    fun canAccuse(s: InterrogateState): Boolean = s.asked >= scene.requiredQuestions && !s.solved

    fun send() {
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty() || s.busy || s.solved) return
        _state.update { it.copy(log = it.log + CaseEntry.Asked(s.selected, text), input = "", notice = null) }
        ask(s.selected, text)
    }

    private fun ask(suspectId: String, text: String) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val reply = engine.ask(suspect(suspectId), text)
                speak(reply.line.hanzi)
                _state.update {
                    it.copy(
                        log = it.log + CaseEntry.Answered(suspectId, reply.line) +
                            listOfNotNull(reply.feedbackTh.ifBlank { null }?.let { f -> CaseEntry.Feedback(f) }),
                        asked = it.asked + if (reply.understood) 1 else 0,
                    )
                }
                retry = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Interrogate", "Suspect call failed", e)
                retry = { ask(suspectId, text) }
                _state.update { it.copy(error = "ติดต่อผู้ต้องสงสัยไม่ได้ (${e.message?.take(120)})") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun retryFailed() {
        retry?.invoke()
    }

    fun record() {
        viewModelScope.launch {
            when (val heard = container.speechInput.listen()) {
                is Heard.Failed -> _state.update { it.copy(notice = heard.messageTh) }
                is Heard.Text -> _state.update { it.copy(input = heard.candidates.first(), notice = null) }
            }
        }
    }

    fun openAccuse() {
        if (canAccuse(_state.value)) _state.update { it.copy(accusing = true) }
    }

    fun dismissAccuse() = _state.update { it.copy(accusing = false) }

    fun accuse(id: String) {
        if (id == mode.culprit) {
            mode.confession?.let { speak(it.hanzi) }
            _state.update { it.copy(accusing = false, solved = true) }
            return
        }
        val alibi = suspect(id).alibi
        speak(alibi.hanzi)
        _state.update {
            it.copy(
                accusing = false,
                wrongGuesses = it.wrongGuesses + 1,
                cleared = it.cleared + id,
                log = it.log + CaseEntry.Cleared(id, alibi),
                notice = "ชี้ผิด! ${suspect(id).name} มีพยานยืนยัน ลองคิดใหม่อีกครั้ง",
            )
        }
    }

    fun complete() {
        val wrong = _state.value.wrongGuesses
        finish(score = (100 - wrong * 30).coerceAtLeast(40), stars = SpecialRules.accusationStars(wrong))
    }
}

@Composable
fun InterrogateSceneScreen(modeId: String, index: Int, onBack: () -> Unit) {
    val vm = appViewModel { InterrogateSceneViewModel(it, modeId, index) }
    val state by vm.state.collectAsStateWithLifecycle()

    SceneFrame(
        vm = vm,
        onBack = onBack,
        passedMessage = "ไขคดีสำเร็จ! 🕵️",
        detail = { "ชี้ตัวผิด ${state.wrongGuesses} ครั้ง" },
        bottomBar = { if (!state.solved) AskBar(vm, state) },
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(state.log.size, state.solved) {
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last > 0) listState.animateScrollToItem(last)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { CaseHeader(vm, state) }
            item { ClueNotebook(vm.clues) }
            items(state.log) { entry -> LogEntry(vm, entry) }
            if (state.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        PinyinText("  ${vm.suspect(state.selected).name} กำลังคิด…")
                    }
                }
            }
            state.error?.let { error ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = vm::retryFailed) { Text("ลองใหม่") }
                        }
                    }
                }
            }
            if (state.solved) item { Solved(vm) }
        }
    }

    if (state.accusing) {
        AlertDialog(
            onDismissRequest = vm::dismissAccuse,
            title = { Text("ใครเอาหยกมังกรไป?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.suspects.filter { it.id !in state.cleared }.forEach { s ->
                        Card(onClick = { vm.accuse(s.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s.emoji, fontSize = 32.sp)
                                Column(Modifier.padding(start = 12.dp)) {
                                    PinyinText(s.name, pinyin = s.namePinyin, fontWeight = FontWeight.Bold)
                                    Text(s.roleTh, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::dismissAccuse) { Text("ขอสืบต่ออีกหน่อย") } },
        )
    }
}

@Composable
private fun CaseHeader(vm: InterrogateSceneViewModel, state: InterrogateState) {
    val need = vm.scene.requiredQuestions
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("🕵️ สอบปากคำผู้ต้องสงสัย", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "ถามเป็นภาษาจีนให้ครบ $need คำถาม แล้วกด \"ชี้ตัวคนร้าย\" · ถามแล้ว ${minOf(state.asked, need)}/$need · " +
                    if (vm.usesAi) "🤖 Gemini" else "📜 บทสำรอง",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Who the next question goes to; lives in the bottom bar so it never scrolls away. */
@Composable
private fun SuspectPicker(vm: InterrogateSceneViewModel, state: InterrogateState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.suspects.forEach { s ->
            val selected = s.id == state.selected
            Card(
                onClick = { vm.select(s.id) },
                modifier = Modifier.weight(1f),
                border = if (selected) BorderStroke(3.dp, Gold) else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.emoji, fontSize = 24.sp)
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(s.name, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        if (s.id in state.cleared) Text("✅ พ้นผิด", style = MaterialTheme.typography.labelSmall, color = Correct)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClueNotebook(clues: List<String>) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { open = !open }) {
            Text(if (open) "📓 ซ่อนสมุดเบาะแส" else "📓 เปิดสมุดเบาะแส (${clues.size})")
        }
        if (open) clues.forEach { ClueCard(it) }
    }
}

@Composable
private fun LogEntry(vm: InterrogateSceneViewModel, entry: CaseEntry) {
    when (entry) {
        is CaseEntry.Asked -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 280.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("ถาม ${vm.suspect(entry.suspectId).name}:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                    PinyinText(
                        entry.text,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        pinyinColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                }
            }
        }
        is CaseEntry.Answered -> SuspectLine(vm.suspect(entry.suspectId), entry.line, onSpeak = { vm.speak(entry.line.hanzi) })
        is CaseEntry.Cleared -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("❌ ${vm.suspect(entry.suspectId).name} ไม่ใช่คนร้าย!", fontWeight = FontWeight.Bold, color = Wrong)
            SuspectLine(vm.suspect(entry.suspectId), entry.alibi, onSpeak = { vm.speak(entry.alibi.hanzi) })
        }
        is CaseEntry.Feedback -> Text("💡 ${entry.text}", color = Wrong, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SuspectLine(suspect: Suspect, line: Line, onSpeak: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(suspect.emoji, fontSize = 32.sp)
        SpeechBubble(line, onSpeak = onSpeak, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Solved(vm: InterrogateSceneViewModel) {
    val culprit = vm.suspect(vm.mode.culprit)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🎉 ถูกต้อง! ${culprit.name} เป็นคนเอาหยกไป", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Correct)
        vm.mode.confession?.let { SuspectLine(culprit, it, onSpeak = { vm.speak(it.hanzi) }) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
            Text(vm.mode.endingTh, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Button(onClick = vm::complete, modifier = Modifier.fillMaxWidth()) { Text("ปิดคดี ดูผลลัพธ์ 📁") }
    }
}

@Composable
private fun AskBar(vm: InterrogateSceneViewModel, state: InterrogateState) {
    val listening by vm.listening.collectAsStateWithLifecycle()
    val withMic = rememberMicPermission()
    val suspect = vm.suspect(state.selected)

    Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("เลือกคนที่จะถาม", style = MaterialTheme.typography.labelMedium)
            SuspectPicker(vm, state)
            Text("คำถามตัวอย่างสำหรับ ${suspect.name} (แตะเพื่อใช้)", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suspect.questions.forEach { q ->
                    AssistChip(onClick = { vm.onInput(q.question.hanzi) }, label = { PinyinText(q.question.hanzi, pinyin = q.question.pinyin) })
                }
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Wrong) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.micAvailable) MicButton(listening, onClick = { withMic(vm::record) }, enabled = !state.busy)
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("ถาม ${suspect.name} เป็นภาษาจีน") },
                    singleLine = true,
                )
                FilledIconButton(onClick = vm::send, enabled = state.input.isNotBlank() && !state.busy) {
                    Text("➤", fontSize = 20.sp)
                }
            }
            val canAccuse = vm.canAccuse(state)
            Button(
                onClick = vm::openAccuse,
                enabled = canAccuse && !state.busy,
                modifier = Modifier.fillMaxWidth().alpha(if (canAccuse) 1f else 0.6f),
            ) {
                Text(
                    if (canAccuse) "👉 ชี้ตัวคนร้าย"
                    else "👉 ชี้ตัวคนร้าย (ถามอีก ${vm.scene.requiredQuestions - state.asked} คำถาม)"
                )
            }
        }
    }
}
