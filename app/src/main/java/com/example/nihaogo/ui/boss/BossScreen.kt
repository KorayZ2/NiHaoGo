package com.example.nihaogo.ui.boss

import android.util.Log
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.ai.BossLine
import com.example.nihaogo.data.content.Boss
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.speech.Heard
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.MicButton
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.StepResult
import com.example.nihaogo.ui.common.StepResultPanel
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.recordScene
import com.example.nihaogo.ui.common.recordStep
import com.example.nihaogo.ui.common.rememberMicPermission
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatItem {
    data class BossSays(val line: BossLine) : ChatItem
    data class PlayerSays(val text: String) : ChatItem
    data class Feedback(val correct: Boolean, val text: String) : ChatItem
}

data class BossState(
    val messages: List<ChatItem> = emptyList(),
    val current: BossLine? = null,
    val answered: Int = 0,
    val correct: Int = 0,
    val input: String = "",
    val busy: Boolean = true,
    val error: String? = null,
    val hintShown: Boolean = false,
    val notice: String? = null,
    val result: StepResult? = null,
)

/** Whose boss conversation this is, which decides where the result is saved. */
sealed interface BossTarget {
    data class CategoryBoss(val categoryId: String) : BossTarget
    /** The customer at the end of a career ([Scene.Roleplay]). */
    data class SpecialScene(val modeId: String, val sceneIndex: Int) : BossTarget
}

class BossViewModel(private val container: AppContainer, private val target: BossTarget) : ViewModel() {
    private val category: Category? = (target as? BossTarget.CategoryBoss)?.let { container.content.category(it.categoryId) }
    private val mode: SpecialMode? = (target as? BossTarget.SpecialScene)?.let { container.content.mode(it.modeId) }

    val boss: Boss = category?.boss
        ?: (mode!!.scenes[(target as BossTarget.SpecialScene).sceneIndex] as Scene.Roleplay).boss
    private val knownWords: List<Word> = category?.words ?: mode!!.allWords

    val title: String = if (category != null) "บอส: ${boss.name}" else "${mode!!.emoji} ${boss.name}"
    val passedMessage: String = when {
        category != null -> "ชนะบอสแล้ว! ได้ถ้วยรางวัล 🏆"
        mode!!.ranks.isNotEmpty() -> "เลื่อนตำแหน่งเป็น${mode.ranks.last()}แล้ว! 🎖️"
        else -> "ผ่านฉากนี้แล้ว!"
    }
    val failedDoneLabel: String = if (category != null) "กลับไปหน้าหมวด" else "กลับ"

    val micAvailable = container.speechInput.isAvailable
    val listening = container.speechInput.listening
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())

    private var engine = container.bossEngine(boss, knownWords)
    val usesAi: Boolean get() = engine.usesAi

    private val _state = MutableStateFlow(BossState())
    val state: StateFlow<BossState> = _state.asStateFlow()

    /** Re-runs the call that failed. */
    private var retry: (() -> Unit)? = null

    init {
        start()
    }

    private fun start() = launchCall(onRetry = ::start) {
        val line = engine.start()
        _state.update { it.copy(messages = listOf(ChatItem.BossSays(line)), current = line) }
        container.speaker.speak(line.hanzi)
    }

    fun onInput(text: String) = _state.update { it.copy(input = text) }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.busy || _state.value.current == null) return
        _state.update {
            it.copy(messages = it.messages + ChatItem.PlayerSays(text), input = "", hintShown = false, notice = null)
        }
        submit(text)
    }

    private fun submit(text: String): Unit = launchCall(onRetry = { submit(text) }) {
        val isFinal = _state.value.answered + 1 >= GameRules.BOSS_ROUNDS
        val judgement = engine.answer(text, isFinal)
        _state.update {
            val added = buildList {
                if (judgement.feedbackTh.isNotBlank()) add(ChatItem.Feedback(judgement.correct, judgement.feedbackTh))
                judgement.next?.let { next -> add(ChatItem.BossSays(next)) }
            }
            it.copy(
                messages = it.messages + added,
                current = judgement.next ?: it.current,
                answered = it.answered + 1,
                correct = it.correct + if (judgement.correct) 1 else 0,
            )
        }
        judgement.next?.let { container.speaker.speak(it.hanzi) }
        if (isFinal) finish()
    }

    private suspend fun finish() {
        delay(2_000) // let the farewell line play before the result screen
        val correct = _state.value.correct
        val stars = GameRules.bossStars(correct)
        val outcome = when (target) {
            is BossTarget.CategoryBoss -> container.progress.recordStep(target.categoryId, Step.Boss, stars)
            is BossTarget.SpecialScene -> container.progress.recordScene(mode!!, target.sceneIndex, stars)
        }
        _state.update { it.copy(result = StepResult(correct * 100 / GameRules.BOSS_ROUNDS, stars, outcome.coinsEarned)) }
    }

    fun record() {
        viewModelScope.launch {
            when (val heard = container.speechInput.listen()) {
                is Heard.Failed -> _state.update { it.copy(notice = heard.messageTh) }
                is Heard.Text -> _state.update { it.copy(input = heard.candidates.first(), notice = null) }
            }
        }
    }

    fun showHint() {
        if (_state.value.hintShown || _state.value.current?.hintHanzi.isNullOrBlank()) return
        viewModelScope.launch {
            var paid = false
            container.progress.update { p -> GameRules.spend(p, GameRules.HINT_COST)?.also { paid = true } ?: p }
            _state.update {
                if (paid) it.copy(hintShown = true, notice = null)
                else it.copy(notice = "เหรียญไม่พอ ต้องใช้ ${GameRules.HINT_COST} 🪙 (เก็บเหรียญได้จากการผ่านด่าน)")
            }
        }
    }

    fun speak(line: BossLine) = container.speaker.speak(line.hanzi)

    fun speakHint() {
        _state.value.current?.hintHanzi?.let { container.speaker.speak(it) }
    }

    fun retryFailed() {
        retry?.invoke()
    }

    fun restart() {
        engine = container.bossEngine(boss, knownWords)
        _state.value = BossState()
        start()
    }

    private fun launchCall(onRetry: () -> Unit, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                retry = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("BossViewModel", "Boss call failed", e)
                retry = onRetry
                _state.update { it.copy(error = "บอสติดต่อไม่ได้ (${e.message?.take(120)})") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

@Composable
fun BossScreen(target: BossTarget, onBack: () -> Unit) {
    val vm = appViewModel { BossViewModel(it, target) }
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val boss = vm.boss

    InkScaffold(
        topBar = { GameTopBar(vm.title, progress.coins, onBack) },
        bottomBar = { if (state.result == null) InputBar(vm, state) },
    ) { padding ->
        val result = state.result
        if (result != null) {
            StepResultPanel(
                result = result,
                passedMessage = vm.passedMessage,
                detail = "ตอบถูก ${state.correct}/${GameRules.BOSS_ROUNDS} (ต้องถูก ${GameRules.BOSS_PASS_CORRECT} ข้อขึ้นไป)",
                onRetry = vm::restart,
                onDone = onBack,
                modifier = Modifier.padding(padding),
                failedDoneLabel = vm.failedDoneLabel,
            )
            return@InkScaffold
        }

        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BossHeader(vm, state) }
            items(state.messages) { item ->
                when (item) {
                    is ChatItem.BossSays -> BossBubble(boss.emoji, item.line, onSpeak = { vm.speak(item.line) })
                    is ChatItem.PlayerSays -> PlayerBubble(item.text)
                    is ChatItem.Feedback -> FeedbackLine(item)
                }
            }
            if (state.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        PinyinText("  ${boss.name} กำลังคิด…")
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
        }
    }
}

@Composable
private fun BossHeader(vm: BossViewModel, state: BossState) {
    val boss = vm.boss
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(boss.emoji, fontSize = 48.sp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                PinyinText(boss.name, pinyin = boss.namePinyin, fontWeight = FontWeight.Bold)
                Text(boss.roleTh, style = MaterialTheme.typography.bodySmall)
                Text(boss.scenarioTh, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "รอบ ${minOf(state.answered + 1, GameRules.BOSS_ROUNDS)}/${GameRules.BOSS_ROUNDS} · ถูก ${state.correct} · " +
                        if (vm.usesAi) "🤖 Gemini" else "📜 บทสำรอง",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun BossBubble(emoji: String, line: BossLine, onSpeak: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 32.sp)
        Card(Modifier.padding(start = 8.dp).widthIn(max = 300.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    PinyinText(line.hanzi, pinyin = line.pinyin, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(line.thai, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onSpeak) { Text("🔊", fontSize = 20.sp) }
            }
        }
    }
}

@Composable
private fun PlayerBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            PinyinText(
                text,
                modifier = Modifier.padding(12.dp),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                pinyinColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun FeedbackLine(item: ChatItem.Feedback) {
    PinyinText(
        (if (item.correct) "✅ " else "❌ ") + item.text,
        color = if (item.correct) Correct else Wrong,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun InputBar(vm: BossViewModel, state: BossState) {
    val listening by vm.listening.collectAsStateWithLifecycle()
    val withMic = rememberMicPermission()
    val current = state.current

    Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hintShown && current != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("💡 ลองตอบว่า", style = MaterialTheme.typography.labelMedium)
                        PinyinText(current.hintHanzi, pinyin = current.hintPinyin, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    SpeakButton(onClick = vm::speakHint)
                }
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Wrong) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.micAvailable) {
                    MicButton(listening, onClick = { withMic(vm::record) }, enabled = !state.busy)
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("พูดหรือพิมพ์ภาษาจีน") },
                    singleLine = true,
                )
                FilledIconButton(onClick = vm::send, enabled = state.input.isNotBlank() && !state.busy) {
                    Text("➤", fontSize = 20.sp)
                }
            }
            if (!state.hintShown && current != null && current.hintHanzi.isNotBlank()) {
                Button(onClick = vm::showHint, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("💡 ขอคำใบ้ (${GameRules.HINT_COST} 🪙)")
                }
            }
        }
    }
}
