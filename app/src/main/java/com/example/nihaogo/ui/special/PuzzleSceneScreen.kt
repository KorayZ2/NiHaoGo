package com.example.nihaogo.ui.special

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nihaogo.AppContainer
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.data.content.Line
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.ui.common.FeedbackCard
import com.example.nihaogo.ui.common.AlternativeCard
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameOutlinedButton
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.TileChip
import com.example.nihaogo.ui.common.answerFeedback
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.common.rememberSfx
import com.example.nihaogo.ui.lesson.WordTile
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PuzzleState(
    val index: Int = 0,
    val bank: List<WordTile> = emptyList(),
    val answer: List<WordTile> = emptyList(),
    /** null = still arranging, true = right, false = wrong. */
    val correct: Boolean? = null,
    val revealed: Boolean = false,
    /** Other ways to say the solved sentence, shown under it. */
    val otherWays: List<Line> = emptyList(),
    val wrongAttempts: Int = 0,
    val firstTryCorrect: Int = 0,
    /** Every sentence solved: show the whole secret message. */
    val messageShown: Boolean = false,
)

class PuzzleSceneViewModel(container: AppContainer, modeId: String, index: Int) :
    SceneViewModel<Scene.Puzzle>(container, modeId, index) {

    private val _state = MutableStateFlow(PuzzleState())
    val state: StateFlow<PuzzleState> = _state.asStateFlow()

    val current get() = scene.sentences[_state.value.index]

    override fun reset() {
        _state.value = PuzzleState()
    }

    override fun onStart() = load(0)

    private fun load(i: Int) {
        val s = scene.sentences[i]
        val bank = (s.tiles + s.distractors).mapIndexed { n, text -> WordTile(n, text) }.shuffled()
        _state.update {
            it.copy(index = i, bank = bank, answer = emptyList(), correct = null, revealed = false, wrongAttempts = 0, otherWays = emptyList())
        }
    }

    fun pick(tile: WordTile) {
        if (_state.value.correct != null) return
        _state.update { it.copy(bank = it.bank - tile, answer = it.answer + tile) }
    }

    fun unpick(tile: WordTile) {
        if (_state.value.correct != null) return
        _state.update { it.copy(answer = it.answer - tile, bank = it.bank + tile) }
    }

    /** Every way to say [current] except the one the learner built. */
    private fun otherWays(built: String): List<Line> {
        val s = current
        return (listOf(Line(s.hanzi, s.pinyin, s.thai)) + s.alternatives)
            .filter { ChineseText.normalize(it.hanzi) != built }
    }

    fun check() {
        // Any grammatical order listed in the content counts, not only the textbook one.
        val built = ChineseText.normalize(_state.value.answer.joinToString("") { it.text })
        val ok = built in current.acceptedTexts
        sfx.answer(ok)
        if (ok) speak(built)
        _state.update {
            it.copy(
                correct = ok,
                otherWays = if (ok) otherWays(built) else emptyList(),
                wrongAttempts = it.wrongAttempts + if (ok) 0 else 1,
                firstTryCorrect = it.firstTryCorrect + if (ok && it.wrongAttempts == 0) 1 else 0,
            )
        }
    }

    fun tryAgain() = _state.update { it.copy(correct = null, bank = (it.bank + it.answer).shuffled(), answer = emptyList()) }

    fun reveal() {
        speak(current.hanzi)
        _state.update { it.copy(revealed = true, correct = true, otherWays = otherWays(ChineseText.normalize(current.hanzi))) }
    }

    fun next() {
        val s = _state.value
        if (s.index < scene.sentences.lastIndex) load(s.index + 1) else _state.update { it.copy(messageShown = true) }
    }

    fun complete() = finish(score())

    fun score(s: PuzzleState = _state.value): Int = s.firstTryCorrect * 100 / scene.sentences.size
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PuzzleSceneScreen(modeId: String, index: Int, onBack: () -> Unit) {
    val vm = appViewModel { PuzzleSceneViewModel(it, modeId, index) }
    val state by vm.state.collectAsStateWithLifecycle()

    SceneFrame(
        vm = vm,
        onBack = onBack,
        passedMessage = "ไขข้อความลับได้แล้ว!",
        detail = { "ถูกตั้งแต่ครั้งแรก ${state.firstTryCorrect}/${vm.scene.sentences.size} ประโยค" },
    ) { padding ->
        val scroll = rememberScrollState()
        LaunchedEffect(state.index, state.messageShown) { scroll.scrollTo(0) }
        // Keep the feedback and its button in view after checking.
        LaunchedEffect(state.correct) {
            if (state.correct != null) {
                delay(100)
                scroll.animateScrollTo(scroll.maxValue)
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.messageShown) {
                SecretMessage(vm)
                return@Column
            }
            val sentence = vm.current
            ProgressHeader(state.index + 1, vm.scene.sentences.size)
            Text("📝 ชิ้นส่วนโน้ตที่ ${state.index + 1} แปลว่า", style = MaterialTheme.typography.labelLarge)
            Text("“${sentence.thai}”", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            val border by animateColorAsState(
                when (state.correct) {
                    true -> Correct
                    false -> Wrong
                    null -> MaterialTheme.colorScheme.outline
                },
                label = "answerBorder",
            )
            OutlinedCard(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .answerFeedback(correct = state.correct == true && !state.revealed, wrong = state.correct == false),
                border = BorderStroke(2.dp, border),
            ) {
                FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.answer.forEach { tile -> TileChip(tile.text, onClick = { vm.unpick(tile) }) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.bank.forEach { tile -> TileChip(tile.text, onClick = { vm.pick(tile) }) }
            }

            when (state.correct) {
                null -> GameButton(onClick = vm::check, enabled = state.answer.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("ตรวจคำตอบ")
                }
                false -> {
                    FeedbackCard("ยังไม่ถูกนะ 🤔", sentence.tipTh, Wrong)
                    GameButton(onClick = vm::tryAgain, modifier = Modifier.fillMaxWidth()) { Text("ลองใหม่") }
                    if (state.wrongAttempts >= 2) {
                        GameOutlinedButton(onClick = vm::reveal, modifier = Modifier.fillMaxWidth()) { Text("ดูเฉลย") }
                    }
                }
                true -> {
                    FeedbackCard(if (state.revealed) "เฉลย" else "ถูกต้อง! 🎉", sentence.tipTh, if (state.revealed) MaterialTheme.colorScheme.tertiary else Correct)
                    val built = state.answer.joinToString("") { it.text }
                    val shown = sentence.alternatives
                        .firstOrNull { !state.revealed && ChineseText.normalize(it.hanzi) == ChineseText.normalize(built) }
                        ?: Line(sentence.hanzi, sentence.pinyin, sentence.thai)
                    SpeechBubble(shown, onSpeak = { vm.speak(shown.hanzi) }, modifier = Modifier.appear(150))
                    state.otherWays.forEachIndexed { i, line ->
                        AlternativeCard(line, onSpeak = { vm.speak(line.hanzi) }, modifier = Modifier.appear(250 + i * 100))
                    }
                    GameButton(onClick = vm::next, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.index < vm.scene.sentences.lastIndex) "ชิ้นต่อไป ▶" else "อ่านข้อความลับ 🔓")
                    }
                }
            }
        }
    }
}

/** All solved pieces put back together, plus the clue they reveal. */
@Composable
private fun SecretMessage(vm: PuzzleSceneViewModel) {
    val sfx = rememberSfx()
    LaunchedEffect(Unit) { sfx.play(Sfx.Sound.Unlock) }
    Text(
        "🔓 ข้อความลับ",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.pop(true, amount = 0.15f),
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The torn pieces drop into place one after another.
            vm.scene.sentences.forEachIndexed { i, s ->
                Column(Modifier.appear(200 + i * 220)) {
                    PinyinText(s.hanzi, pinyin = s.pinyin, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(s.thai, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    GameOutlinedButton(onClick = { vm.speak(vm.scene.sentences.joinToString("") { it.hanzi }) }, modifier = Modifier.fillMaxWidth()) {
        Text("🔊 ฟังทั้งข้อความ")
    }
    ClueCard(vm.scene.clueTh, isNew = true)
    GameButton(onClick = vm::complete, modifier = Modifier.fillMaxWidth()) { Text("ดูผลลัพธ์") }
}
