package com.example.nihaogo.ui.practice

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.R
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.Mission
import com.example.nihaogo.ui.common.ConfettiBurst
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.GameOutlinedButton
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.answerFeedback
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.common.shake
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.CorrectLight
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.Wood
import com.example.nihaogo.ui.theme.Wrong
import com.example.nihaogo.ui.theme.WrongLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val HEARTS = 3
private const val ROUNDS = 15
private const val POINTS = 10
private const val OPTIONS = 2

data class ChallengeRound(val word: Word, val options: List<Word>) {
    val answerIndex: Int get() = options.indexOf(word)
}

data class ChallengeState(
    val loading: Boolean = true,
    val rounds: List<ChallengeRound> = emptyList(),
    val round: Int = 0,
    val hearts: Int = HEARTS,
    val score: Int = 0,
    val chosen: Int? = null,
    val over: Boolean = false,
    val best: Int = 0,
    /** This game beat the saved best score. */
    val newBest: Boolean = false,
) {
    val current: ChallengeRound? get() = rounds.getOrNull(round)
}

class ChallengeViewModel(private val container: AppContainer, context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("challenge", Context.MODE_PRIVATE)
    val speakerState = container.speaker.state
    private var pool: List<Word> = emptyList()

    private val _state = MutableStateFlow(ChallengeState(best = prefs.getInt(KEY_BEST, 0)))
    val state: StateFlow<ChallengeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Only words with a picture can be matched to one.
            pool = container.openedWords().filter { it.emoji.isNotBlank() }
            start()
        }
    }

    fun start() {
        val picks = if (pool.isEmpty()) emptyList() else generateSequence { pool.shuffled() }.flatten().take(ROUNDS).toList()
        val rounds = picks.map { ChallengeRound(it, (distractorsFor(it, pool, OPTIONS - 1) + it).shuffled()) }
        _state.value = ChallengeState(loading = false, rounds = rounds, best = prefs.getInt(KEY_BEST, 0))
    }

    fun speak() {
        _state.value.current?.let { container.speaker.speak(it.word.hanzi) }
    }

    fun choose(option: Int) {
        val s = _state.value
        val round = s.current ?: return
        if (s.chosen != null || s.over) return
        val right = option == round.answerIndex
        container.sfx.answer(right)
        if (right) container.missions.add(Mission.Words)
        _state.update {
            it.copy(
                chosen = option,
                score = it.score + if (right) POINTS else 0,
                hearts = it.hearts - if (right) 0 else 1,
            )
        }
        viewModelScope.launch {
            // Long enough to see the right answer before the next pair slides in.
            delay(if (right) 900 else 1500)
            advance()
        }
    }

    private fun advance() {
        val s = _state.value
        if (s.hearts <= 0 || s.round >= s.rounds.lastIndex) {
            val newBest = s.score > s.best
            if (newBest) prefs.edit { putInt(KEY_BEST, s.score) }
            _state.update { it.copy(over = true, newBest = newBest, best = maxOf(it.best, it.score)) }
        } else {
            _state.update { it.copy(round = it.round + 1, chosen = null) }
        }
    }

    private companion object {
        const val KEY_BEST = "best_score"
    }
}

@Composable
fun ChallengeGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm = appViewModel { ChallengeViewModel(it, context) }
    val state by vm.state.collectAsStateWithLifecycle()
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()

    InkScaffold(topBar = { GameTopBar("เกมท้าทาย", coins = null, onBack = onBack) }) { padding ->
        val round = state.current
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            round == null -> NoWords(Modifier.padding(padding))
            state.over -> GameOver(state, onAgain = vm::start, onBack = onBack, modifier = Modifier.padding(padding))
            else -> {
                LaunchedEffect(state.round, speakerState) { vm.speak() }
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CounterChip("❤️ ${state.hearts}", Modifier.shake(state.chosen != null && state.chosen != round.answerIndex))
                        CounterChip("⭐ ${state.score}", Modifier.pop(state.chosen == round.answerIndex))
                    }
                    TtsWarning(speakerState)
                    ScrollBanner("จับคู่คำศัพท์กับรูปภาพให้ถูกต้อง")
                    Text(
                        "รอบ ${state.round + 1}/${state.rounds.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSoft,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WoodFrame(Modifier.weight(1f), onClick = vm::speak) {
                            PinyinText(
                                round.word.hanzi,
                                pinyin = round.word.pinyin,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                textAlign = TextAlign.Center,
                            )
                        }
                        WoodFrame(Modifier.weight(1f)) {
                            Text(round.word.emoji, fontSize = 72.sp, textAlign = TextAlign.Center)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        round.options.forEachIndexed { i, option ->
                            ThaiAnswer(
                                text = option.thai,
                                right = state.chosen != null && i == round.answerIndex,
                                wrong = state.chosen == i && i != round.answerIndex,
                                onClick = { vm.choose(i) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A paper scroll with wooden rods at both ends, holding the game's instruction. */
@Composable
private fun ScrollBanner(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ScrollRod()
        Surface(
            modifier = Modifier.weight(1f),
            color = Paper,
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
            shadowElevation = 3.dp,
        ) {
            Text(
                text,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        ScrollRod()
    }
}

@Composable
private fun ScrollRod() {
    Box(
        Modifier
            .width(10.dp)
            .height(58.dp)
            .shadow(2.dp, RoundedCornerShape(50))
            .background(Wood, RoundedCornerShape(50)),
    )
}

/** A card in a wooden picture frame, like the game boards in the mockups. */
@Composable
private fun WoodFrame(modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val frame = RoundedCornerShape(10.dp)
    val inner: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Cream)
                .border(1.dp, Hairline, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
    if (onClick != null) {
        GameCard(
            onClick = onClick,
            modifier = modifier.aspectRatio(0.82f),
            colors = CardDefaults.cardColors(containerColor = Wood),
            border = BorderStroke(2.dp, Wood.darker()),
            elevation = CardDefaults.cardElevation(4.dp),
        ) { inner() }
    } else {
        Box(
            modifier
                .aspectRatio(0.82f)
                .shadow(4.dp, frame)
                .background(Wood, frame)
                .border(2.dp, Wood.darker(), frame),
        ) { inner() }
    }
}

/** A darker wood tone for the frame's edge. */
private fun Color.darker(): Color = Color(red * 0.7f, green * 0.6f, blue * 0.5f, 1f)

@Composable
private fun ThaiAnswer(text: String, right: Boolean, wrong: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val fill by animateColorAsState(
        when {
            right -> CorrectLight
            wrong -> WrongLight
            else -> Paper
        },
        label = "answerFill",
    )
    Box(modifier) {
        GameCard(
            onClick = onClick,
            sound = false,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp).answerFeedback(right, wrong),
            colors = CardDefaults.cardColors(containerColor = fill),
            border = BorderStroke(
                if (right || wrong) 2.dp else 1.dp,
                when {
                    right -> Correct
                    wrong -> Wrong
                    else -> Hairline
                },
            ),
        ) {
            Text(
                text,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    right -> Correct
                    wrong -> Wrong
                    else -> Ink
                },
                textAlign = TextAlign.Center,
            )
        }
        if (right) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
                    .pop(true, amount = 0.3f)
                    .shadow(3.dp, CircleShape)
                    .background(Correct, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun GameOver(state: ChallengeState, onAgain: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val cleared = state.hearts > 0
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Image(
                painterResource(if (cleared || state.newBest) R.drawable.mascot_cheer else R.drawable.mascot_try),
                contentDescription = null,
                modifier = Modifier.size(160.dp).pop(true, amount = 0.2f),
            )
            Text(
                when {
                    state.newBest -> "สถิติใหม่! เก่งสุด ๆ"
                    cleared -> "ผ่านครบทุกรอบ!"
                    else -> "หัวใจหมดแล้ว ลองใหม่นะ"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text("คะแนน ${state.score}", style = MaterialTheme.typography.titleLarge, color = Red, modifier = Modifier.appear(150))
            Text("คะแนนสูงสุด ${state.best}", style = MaterialTheme.typography.titleMedium, color = InkSoft, modifier = Modifier.appear(250))
            Spacer(Modifier.height(8.dp))
            Column(Modifier.appear(400), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("เล่นอีกครั้ง") }
                GameOutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("กลับ") }
            }
        }
        if (state.newBest || cleared) ConfettiBurst(Modifier.matchParentSize())
    }
}
