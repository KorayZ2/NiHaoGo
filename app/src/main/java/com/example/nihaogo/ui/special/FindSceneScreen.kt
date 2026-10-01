package com.example.nihaogo.ui.special

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FindState(
    val target: Int = 0,
    /** Word indexes in the order they are laid out on screen. */
    val layout: List<Int> = emptyList(),
    val found: Set<Int> = emptySet(),
    val wrong: Int? = null,
    val mistakes: Int = 0,
    /** A clue was just found; the learner reads it before moving on. */
    val newClue: String? = null,
    val clues: List<String> = emptyList(),
)

class FindSceneViewModel(container: AppContainer, modeId: String, index: Int) :
    SceneViewModel<Scene.Find>(container, modeId, index) {

    private val _state = MutableStateFlow(FindState())
    val state: StateFlow<FindState> = _state.asStateFlow()

    fun targetWord(s: FindState = _state.value): Word = scene.words.first { it.hanzi == scene.targets[s.target].hanzi }

    override fun reset() {
        _state.value = FindState()
    }

    override fun onStart() {
        _state.update { it.copy(layout = scene.words.indices.shuffled()) }
    }

    fun speakTarget(slow: Boolean = false) = speak(targetWord().hanzi, slow)

    fun tap(wordIndex: Int) {
        val s = _state.value
        if (s.newClue != null || s.wrong != null || wordIndex in s.found) return
        val target = targetWord(s)
        if (scene.words[wordIndex].hanzi != target.hanzi) {
            _state.update { it.copy(wrong = wordIndex, mistakes = it.mistakes + 1) }
            viewModelScope.launch {
                delay(600)
                _state.update { it.copy(wrong = null) }
            }
            return
        }
        speak(target.hanzi)
        val clue = scene.targets[s.target].clueTh.ifBlank { null }
        _state.update { it.copy(found = it.found + wordIndex, newClue = clue, clues = it.clues + listOfNotNull(clue)) }
        if (clue == null) {
            viewModelScope.launch {
                delay(700)
                advance()
            }
        }
    }

    fun advance() {
        val s = _state.value
        if (s.target < scene.targets.lastIndex) {
            _state.update { it.copy(target = it.target + 1, newClue = null) }
        } else {
            _state.update { it.copy(newClue = null) }
            finish(score(s))
        }
    }

    fun score(s: FindState = _state.value): Int = (100 - s.mistakes * 10).coerceAtLeast(0)
}

@Composable
fun FindSceneScreen(modeId: String, index: Int, onBack: () -> Unit) {
    val vm = appViewModel { FindSceneViewModel(it, modeId, index) }
    val state by vm.state.collectAsStateWithLifecycle()

    SceneFrame(
        vm = vm,
        onBack = onBack,
        passedMessage = "สำรวจเสร็จแล้ว! ได้เบาะแส ${state.clues.size} ชิ้น",
        detail = { "แตะผิด ${state.mistakes} ครั้ง" },
    ) { padding ->
        LaunchedEffect(state.target) { vm.speakTarget() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressHeader(state.found.size, vm.scene.targets.size)
                    TargetCard(vm.targetWord(state), onSpeak = vm::speakTarget)
                    state.newClue?.let { clue ->
                        ClueCard(clue, isNew = true)
                        Button(onClick = vm::advance, modifier = Modifier.fillMaxWidth()) { Text("จดเบาะแสแล้ว ไปต่อ ▶") }
                    }
                }
            }
            items(state.layout, key = { it }) { wordIndex ->
                val word = vm.scene.words[wordIndex]
                val found = wordIndex in state.found
                val color = when {
                    state.wrong == wordIndex -> Wrong.copy(alpha = 0.5f)
                    found -> Correct.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                Card(
                    onClick = { vm.tap(wordIndex) },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    colors = CardDefaults.cardColors(containerColor = color),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(word.emoji, fontSize = 40.sp)
                        if (found) Text("✓ ${word.hanzi}", fontWeight = FontWeight.Bold, color = Correct)
                    }
                }
            }
            if (state.clues.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📓 สมุดเบาะแส", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        state.clues.forEach { ClueCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetCard(word: Word, onSpeak: (slow: Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🔍 หาสิ่งนี้ในฉาก", style = MaterialTheme.typography.labelLarge)
                PinyinText(word.hanzi, pinyin = word.pinyin, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            SpeakButton(onClick = { onSpeak(false) })
            SpeakButton(onClick = { onSpeak(true) }, slow = true, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
