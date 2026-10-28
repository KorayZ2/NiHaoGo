package com.example.nihaogo.ui.special

import com.example.nihaogo.ui.theme.Paper
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.example.nihaogo.ui.common.PaperCard
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.Night
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.answerFeedback
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Jade
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

    /** Whether [wordIndex] was the word asked for; null when the tap doesn't count right now. */
    fun tap(wordIndex: Int): Boolean? {
        val s = _state.value
        if (s.newClue != null || s.wrong != null || wordIndex in s.found) return null
        val target = targetWord(s)
        if (scene.words[wordIndex].hanzi != target.hanzi) {
            sfx.wrong()
            _state.update { it.copy(wrong = wordIndex, mistakes = it.mistakes + 1) }
            viewModelScope.launch {
                delay(WrongMillis)
                _state.update { it.copy(wrong = null) }
            }
            return false
        }
        sfx.correct()
        speak(target.hanzi)
        val clue = scene.targets[s.target].clueTh.ifBlank { null }
        _state.update { it.copy(found = it.found + wordIndex, newClue = clue, clues = it.clues + listOfNotNull(clue)) }
        if (clue == null) {
            viewModelScope.launch {
                delay(700)
                advance()
            }
        }
        return true
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

    private companion object {
        const val WrongMillis = 900L
    }
}

@Composable
fun FindSceneScreen(modeId: String, index: Int, onBack: () -> Unit) {
    val vm = appViewModel { FindSceneViewModel(it, modeId, index) }
    val state by vm.state.collectAsStateWithLifecycle()
    val started by vm.started.collectAsStateWithLifecycle()
    val art = sceneArt(modeId, index)
    val spots = findSpots(modeId, index)

    SceneFrame(
        vm = vm,
        onBack = onBack,
        passedMessage = "สำรวจเสร็จแล้ว! ได้เบาะแส ${state.clues.size} ชิ้น",
        detail = { "แตะผิด ${state.mistakes} ครั้ง" },
        // With a painting that knows where things are, the player searches the painting itself.
        fullScreen = if (started && art != null && spots != null) {
            { PaintedFind(vm, state, art, spots, onBack) }
        } else null,
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
                    // Each new target slides in so the player notices the word changed.
                    AnimatedContent(
                        targetState = vm.targetWord(state),
                        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith fadeOut() },
                        label = "target",
                    ) { word -> TargetCard(word, onSpeak = vm::speakTarget) }
                    state.newClue?.let { clue ->
                        ClueCard(clue, isNew = true)
                        GameButton(onClick = vm::advance, modifier = Modifier.fillMaxWidth()) { Text("จดเบาะแสแล้ว ไปต่อ ▶") }
                    }
                }
            }
            items(state.layout, key = { it }) { wordIndex ->
                val word = vm.scene.words[wordIndex]
                val found = wordIndex in state.found
                val wrong = state.wrong == wordIndex
                val color by animateColorAsState(
                    when {
                        wrong -> Wrong.copy(alpha = 0.5f)
                        found -> Correct.copy(alpha = 0.3f)
                        else -> Paper
                    },
                    label = "item",
                )
                GameCard(
                    onClick = { vm.tap(wordIndex) },
                    sound = false,
                    modifier = Modifier.fillMaxWidth().height(96.dp).answerFeedback(found, wrong),
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Cream.copy(alpha = 0.8f), contentColor = Ink),
        border = BorderStroke(2.dp, Gold.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🔍 หาสิ่งนี้ในฉาก", style = MaterialTheme.typography.labelLarge)
                PinyinText(word.hanzi, pinyin = word.pinyin, fontSize = 36.sp, fontWeight = FontWeight.Bold, pinyinColor = Jade)
            }
            SpeakButton(onClick = { onSpeak(false) })
            SpeakButton(onClick = { onSpeak(true) }, slow = true, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * The search played on the scene's painting: tap the object itself. A find leaves a green ✓
 * with its word; a wrong object flashes a red ✗ with what it is; empty ground does nothing.
 */
@Composable
private fun PaintedFind(
    vm: FindSceneViewModel,
    state: FindState,
    @DrawableRes art: Int,
    spots: Map<String, List<Rect>>,
    onBack: () -> Unit,
) {
    LightSystemBarIcons()
    LaunchedEffect(state.target) { vm.speakTarget() }
    val painter = painterResource(art)
    val density = LocalDensity.current
    // Which part of the painting each word was tapped on, for its mark.
    val foundAt = remember { mutableStateMapOf<Int, Rect>() }
    var wrongAt by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(state.found) { if (state.found.isEmpty()) foundAt.clear() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        // Full width, resting on the bottom: on a tall phone the space above holds the target
        // card; on a short one the card floats over the sky.
        val w = constraints.maxWidth.toFloat()
        val drawnH = painter.intrinsicSize.run { height * w / width }
        val top = constraints.maxHeight - drawnH
        Image(
            painter = painter,
            contentDescription = vm.scene.titleTh,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize(),
        )
        if (top > 0) {
            val fade = with(density) { 80.dp.toPx() }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Night, Color.Transparent), startY = top, endY = top + fade),
                ),
            )
        }
        fun Modifier.over(r: Rect): Modifier = with(density) {
            offset((r.left * w).toDp(), (top + r.top * drawnH).toDp())
                .size((r.width * w).toDp(), (r.height * drawnH).toDp())
        }

        spots.forEach { (hanzi, rects) ->
            val wordIndex = vm.scene.words.indexOfFirst { it.hanzi == hanzi }
            if (wordIndex < 0) return@forEach
            rects.forEach { r ->
                Box(
                    Modifier.over(r).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        when (vm.tap(wordIndex)) {
                            true -> foundAt[wordIndex] = r
                            false -> wrongAt = r
                            null -> Unit
                        }
                    },
                )
            }
        }
        foundAt.forEach { (wordIndex, r) ->
            Box(Modifier.over(r), contentAlignment = Alignment.Center) { SpotMark(vm.scene.words[wordIndex], right = true) }
        }
        val wrong = state.wrong
        val wrongRect = wrongAt
        if (wrong != null && wrongRect != null) {
            Box(Modifier.over(wrongRect), contentAlignment = Alignment.Center) { SpotMark(vm.scene.words[wrong], right = false) }
        }

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaintedProgressBar(state.found.size, vm.scene.targets.size, onBack)
            AnimatedContent(
                targetState = vm.targetWord(state),
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith fadeOut() },
                label = "target",
            ) { word -> TargetCard(word, onSpeak = vm::speakTarget) }
        }

        state.newClue?.let { clue ->
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PaintedClueCard(clue)
                GameButton(onClick = vm::advance, modifier = Modifier.fillMaxWidth().appear(200)) { Text("จดเบาะแสแล้ว ไปต่อ ▶") }
            }
        }
    }
}

/** A ring on the painting: green with the word for a find, red with what it was for a miss. */
@Composable
private fun SpotMark(word: Word, right: Boolean) {
    val color = if (right) Correct else Wrong
    Column(
        Modifier.wrapContentSize(unbounded = true).pop(true, amount = 0.2f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).border(4.dp, color, CircleShape).background(color.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (right) "✓" else "✗", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            if (right) word.hanzi else "${word.hanzi} ${word.thai}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 2.dp)
                .background(color, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
