package com.example.nihaogo.ui.special

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Line
import com.example.nihaogo.data.content.ModeKind
import com.example.nihaogo.data.content.Person
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.Task
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.answerFeedback
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.Jade
import com.example.nihaogo.ui.theme.Night
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.Wrong
import kotlin.math.max
import kotlin.math.roundToInt
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

    /** Reads out a line of the person the learner is talking to, in their own voice. */
    fun speakNpc(text: String) = speak(text, voice = mode.voiceOf(speaker(_state.value.turn)))

    fun choose(option: Int) {
        val s = _state.value
        val turn = scene.turns[s.turn]
        if (s.solved || option in s.tried) return
        sfx.answer(option == turn.answer)
        if (option == turn.answer) {
            speakNpc(turn.reply.hanzi)
            _state.update { it.copy(solved = true, points = it.points + if (it.tried.isEmpty()) 1 else 0) }
        } else {
            _state.update { it.copy(tried = it.tried + option) }
        }
    }

    fun chooseTask(option: Int) {
        val s = _state.value
        val task = scene.turns[s.turn].task ?: return
        if (!s.solved || s.taskSolved || option in s.taskTried) return
        sfx.answer(option == task.answer)
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
    val started by vm.started.collectAsStateWithLifecycle()
    val detective = vm.mode.kind == ModeKind.Detective
    val art = sceneArt(modeId, index)
    val spots = talkSpots(modeId, index)

    SceneFrame(
        vm = vm,
        onBack = onBack,
        passedMessage = if (detective) "สอบปากคำเสร็จแล้ว!" else "จบกะแล้ว! ดูแลทุกคนได้ดีมาก",
        detail = { "ถูกตั้งแต่ครั้งแรก ${state.points}/${vm.totalPoints}" },
        // With a painting that knows where everyone stands, the talk plays out on it in bubbles.
        fullScreen = if (started && art != null && spots != null) {
            { PaintedTalk(vm, state, art, spots, onBack) }
        } else null,
    ) { padding ->
        val turn = vm.scene.turns[state.turn]
        val scroll = rememberScrollState()
        LaunchedEffect(state.turn) {
            scroll.scrollTo(0)
            vm.speakNpc(turn.npc.hanzi)
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
            SpeechBubble(turn.npc, onSpeak = { vm.speakNpc(turn.npc.hanzi) })

            Text(
                if (detective) "คุณจะถามว่าอะไร?" else "คุณจะพูดว่าอะไร?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            turn.options.forEachIndexed { i, option ->
                OptionCard(
                    line = option,
                    right = state.solved && i == turn.answer,
                    wrong = i in state.tried,
                    showThai = state.solved || i in state.tried,
                    onClick = { vm.choose(i) },
                )
            }

            if (state.solved) {
                vm.speaker(state.turn)?.let { Text("${it.emoji} ${it.name} ตอบว่า:", fontWeight = FontWeight.Bold) }
                SpeechBubble(turn.reply, onSpeak = { vm.speakNpc(turn.reply.hanzi) }, modifier = Modifier.appear())
                if (turn.clueTh.isNotBlank()) ClueCard(turn.clueTh, isNew = true)
            }

            val task = turn.task
            if (state.solved && task != null) {
                Text(
                    task.promptTh,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.appear(150),
                )
                TaskChoices(task, state, onChoose = vm::chooseTask, modifier = Modifier.appear(250))
            }

            if (vm.canGoNext(state)) {
                GameButton(onClick = vm::next, modifier = Modifier.fillMaxWidth().appear(200)) {
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

/** One question to choose; [painted] is the cream, gold-edged look that reads over a painting. */
@Composable
private fun OptionCard(
    line: Line,
    right: Boolean,
    wrong: Boolean,
    showThai: Boolean,
    onClick: () -> Unit,
    painted: Boolean = false,
) {
    val idle = if (painted) Cream else MaterialTheme.colorScheme.surfaceContainerHigh
    val color by animateColorAsState(
        when {
            right -> Correct.copy(alpha = 0.3f).let { if (painted) it.compositeOver(idle) else it }
            wrong -> Wrong.copy(alpha = 0.3f).let { if (painted) it.compositeOver(idle) else it }
            else -> idle
        },
        label = "option",
    )
    GameCard(
        onClick = onClick,
        sound = false,
        modifier = Modifier.fillMaxWidth().answerFeedback(right, wrong),
        colors = if (painted) {
            CardDefaults.cardColors(containerColor = color, contentColor = Ink)
        } else {
            CardDefaults.cardColors(containerColor = color)
        },
        border = if (painted) BorderStroke(1.5.dp, Gold.copy(alpha = 0.7f)) else null,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PinyinText(
                line.hanzi,
                pinyin = line.pinyin,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                pinyinColor = if (painted) Jade else MaterialTheme.colorScheme.tertiary,
            )
            if (showThai) Text(line.thai, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The picture choices of a turn's task, once its question is answered. */
@Composable
private fun TaskChoices(task: Task, state: DialogueState, onChoose: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        task.options.forEachIndexed { i, option ->
            val right = state.taskSolved && i == task.answer
            val wrong = i in state.taskTried
            val color by animateColorAsState(
                when {
                    right -> Correct.copy(alpha = 0.35f)
                    wrong -> Wrong.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                label = "task",
            )
            GameCard(
                onClick = { onChoose(i) },
                sound = false,
                modifier = Modifier.weight(1f).answerFeedback(right, wrong),
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

/**
 * The talk played on the scene's painting: what the speaker says in a bubble pointing at them,
 * the question once it is chosen in one pointing at the detective, and the choices, clue and
 * Next button in a panel along the bottom.
 */
@Composable
private fun PaintedTalk(
    vm: DialogueSceneViewModel,
    state: DialogueState,
    @DrawableRes art: Int,
    spots: TalkSpots,
    onBack: () -> Unit,
) {
    LightSystemBarIcons()
    val turn = vm.scene.turns[state.turn]
    val speaker = vm.speaker(state.turn)
    val panelScroll = rememberScrollState()
    LaunchedEffect(state.turn) {
        panelScroll.scrollTo(0)
        vm.speakNpc(turn.npc.hanzi)
    }
    // Bring the clue, task and Next button into view as soon as they appear.
    LaunchedEffect(state.solved, state.taskSolved) {
        if (state.solved) {
            delay(100) // wait one layout pass so maxValue includes the new content
            panelScroll.animateScrollTo(panelScroll.maxValue)
        }
    }
    val painter = painterResource(art)

    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        // Cropped to fill the screen around its centre, as ContentScale.Crop draws it.
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val drawn = painter.intrinsicSize.let { it * max(w / it.width, h / it.height) }
        val origin = Offset((w - drawn.width) / 2, (h - drawn.height) / 2)
        fun onScreen(spot: Offset) = origin + Offset(spot.x * drawn.width, spot.y * drawn.height)
        Image(
            painter = painter,
            contentDescription = vm.scene.titleTh,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        val head = spots.speakers[speaker?.name] ?: spots.speakers.values.first()
        if (state.solved) {
            val question = turn.options[turn.answer]
            key(state.turn) {
                PointingBubble(
                    tip = onScreen(spots.detective),
                    who = "🕵️ คุณ (นักสืบ)",
                    line = question,
                    widthFraction = 0.62f,
                    tailAt = 0.35f,
                    onSpeak = { vm.speak(question.hanzi) },
                    modifier = Modifier.appear(),
                    // Stop short of the speaker's face, whatever the screen's shape crops away.
                    maxRight = onScreen(head.topLeft).x,
                )
            }
        }
        // The speaker's line, then their reply once the question is asked.
        val said = if (state.solved) turn.reply else turn.npc
        key(state.turn, state.solved) {
            PointingBubble(
                tip = onScreen(head.topCenter),
                who = speaker?.let { "${it.emoji} ${it.name} ${it.namePinyin}" + if (it.roleTh.isNotBlank()) " · ${it.roleTh}" else "" }.orEmpty(),
                line = said,
                widthFraction = 0.8f,
                tailAt = 0.7f,
                onSpeak = { vm.speakNpc(said.hanzi) },
                modifier = Modifier.appear(if (state.solved) 350 else 0),
            )
        }

        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
            PaintedProgressBar(state.turn + 1, vm.scene.turns.size, onBack)
        }

        // Shade the floor, so the painting's own name plate never peeks out above a short panel.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.32f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Night.copy(alpha = 0.9f), 1f to Night.copy(alpha = 0.95f))),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.85f), Night.copy(alpha = 0.92f))))
                .navigationBarsPadding()
                .heightIn(max = maxHeight * 0.5f)
                .verticalScroll(panelScroll)
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.solved) {
                Text(
                    if (vm.mode.kind == ModeKind.Detective) "คุณจะถามว่าอะไร?" else "คุณจะพูดว่าอะไร?",
                    color = Cream,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                turn.options.forEachIndexed { i, option ->
                    OptionCard(
                        line = option,
                        right = false,
                        wrong = i in state.tried,
                        showThai = i in state.tried,
                        onClick = { vm.choose(i) },
                        painted = true,
                    )
                }
            } else {
                if (turn.clueTh.isNotBlank()) {
                    key(state.turn) { PaintedClueCard(turn.clueTh, Modifier.appear(500)) }
                }
                val task = turn.task
                if (task != null) {
                    Text(
                        task.promptTh,
                        color = Cream,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.appear(150),
                    )
                    TaskChoices(task, state, onChoose = vm::chooseTask, modifier = Modifier.appear(250))
                }
                if (vm.canGoNext(state)) {
                    GameButton(onClick = vm::next, modifier = Modifier.fillMaxWidth().appear(600)) {
                        Text(if (state.turn < vm.scene.turns.lastIndex) "ถัดไป ▶" else "ดูผลลัพธ์")
                    }
                }
            }
        }
    }
}

private val BubbleBorder = 2.dp
private val TailWidth = 22.dp
private val TailHeight = 16.dp

/**
 * A speech bubble whose tail ends just above [tip] (in pixels of the parent), the talker's head,
 * with the bubble on top of it. The tail sits [tailAt] of the way along the bubble, which is at
 * most [widthFraction] of the screen wide and kept on screen, and left of [maxRight] (in pixels).
 */
@Composable
private fun PointingBubble(
    tip: Offset,
    who: String,
    line: Line,
    widthFraction: Float,
    tailAt: Float,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    maxRight: Float = Float.POSITIVE_INFINITY,
) {
    // Below the back button and progress bar, even if that means covering the top of a head.
    val minTop = WindowInsets.statusBars.getTop(LocalDensity.current) + with(LocalDensity.current) { 60.dp.roundToPx() }
    Layout(
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Cream, contentColor = Ink),
                border = BorderStroke(BubbleBorder, Gold),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                    // The replay button sits by the name, leaving the line the bubble's full width.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            who,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Red,
                        )
                        IconButton(onClick = onSpeak, modifier = Modifier.padding(start = 4.dp).size(36.dp)) {
                            Text("🔊", fontSize = 18.sp)
                        }
                    }
                    PinyinText(line.hanzi, pinyin = line.pinyin, fontSize = 20.sp, fontWeight = FontWeight.Bold, pinyinColor = Jade)
                    Text(line.thai, style = MaterialTheme.typography.bodyMedium)
                }
            }
            BubbleTail(Modifier.size(TailWidth, TailHeight))
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val gap = 4.dp.roundToPx()
        val corner = 16.dp.roundToPx()
        val right = minOf(constraints.maxWidth - margin, (maxRight - margin).roundToInt())
        val maxWidth = minOf((constraints.maxWidth * widthFraction).roundToInt(), right - margin)
        val body = measurables[0].measure(Constraints(maxWidth = maxWidth.coerceAtLeast(0)))
        val tail = measurables[1].measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (tip.x - body.width * tailAt).roundToInt()
                .coerceAtMost(right - body.width)
                .coerceAtLeast(margin)
            val tailX = (tip.x - tail.width / 2f).roundToInt()
                .coerceAtMost(x + body.width - corner - tail.width)
                .coerceAtLeast(x + corner)
            val border = BubbleBorder.roundToPx()
            val bodyY = (tip.y.roundToInt() - gap - tail.height - body.height + border).coerceAtLeast(minTop)
            body.place(x, bodyY)
            // The tail's top covers the bubble's bottom edge, so the two read as one shape.
            tail.place(tailX, bodyY + body.height - border)
        }
    }
}

@Composable
private fun BubbleTail(modifier: Modifier) {
    Canvas(modifier) {
        val point = Offset(size.width / 2, size.height)
        drawPath(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(point.x, point.y)
                close()
            },
            Cream,
        )
        val stroke = BubbleBorder.toPx()
        drawLine(Gold, Offset(0f, 0f), point, stroke)
        drawLine(Gold, Offset(size.width, 0f), point, stroke)
    }
}
