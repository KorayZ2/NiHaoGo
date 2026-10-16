package com.example.nihaogo.ui.intro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nihaogo.AppContainer
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.data.content.PinyinBasics
import com.example.nihaogo.ui.common.ConfettiBurst
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.GameOutlinedButton
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.HanziBlock
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressHeader
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.answerFeedback
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.Jade
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToneQuizState(
    val questions: List<PinyinBasics.QuizItem> = PinyinBasics.newQuiz(),
    val index: Int = 0,
    /** Tones tried on the current question (wrong ones stay red). */
    val tried: Set<Int> = emptySet(),
    val solved: Boolean = false,
    val firstTryCorrect: Int = 0,
    val done: Boolean = false,
) {
    val perfect get() = done && firstTryCorrect == questions.size
}

class PinyinIntroViewModel(private val container: AppContainer) : ViewModel() {
    val speakerState = container.speaker.state

    private val _quiz = MutableStateFlow(ToneQuizState())
    val quiz: StateFlow<ToneQuizState> = _quiz.asStateFlow()

    fun speak(text: String, slow: Boolean = false) = container.speaker.speak(text, slow)

    fun playQuestion(slow: Boolean = false) {
        val q = _quiz.value
        speak(q.questions[q.index].hanzi, slow)
    }

    fun choose(tone: Int) {
        val s = _quiz.value
        if (s.solved || tone in s.tried) return
        val correct = tone == s.questions[s.index].tone
        container.sfx.answer(correct)
        _quiz.update {
            it.copy(
                tried = it.tried + tone,
                solved = correct,
                firstTryCorrect = it.firstTryCorrect + if (correct && it.tried.isEmpty()) 1 else 0,
            )
        }
    }

    fun nextQuestion() {
        if (_quiz.value.index < _quiz.value.questions.lastIndex) {
            _quiz.update { it.copy(index = it.index + 1, tried = emptySet(), solved = false) }
        } else {
            _quiz.update { it.copy(done = true) }
            container.sfx.play(Sfx.Sound.Win)
        }
    }

    fun restartQuiz() {
        _quiz.value = ToneQuizState()
    }

    fun markSeen() {
        container.settings.pinyinIntroSeen = true
    }
}

private const val PageCount = 6
private const val QuizPage = PageCount - 1

/**
 * Pinyin primer: what pinyin is, initials, finals, tones, tone changes, then a short listening
 * quiz. On [firstRun] it can be skipped and leads on to the map; otherwise it is a review page.
 */
@Composable
fun PinyinIntroScreen(firstRun: Boolean, onDone: () -> Unit) {
    val vm = appViewModel { PinyinIntroViewModel(it) }
    val quiz by vm.quiz.collectAsStateWithLifecycle()
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()
    val pager = rememberPagerState { PageCount }
    val scope = rememberCoroutineScope()
    val finish: () -> Unit = {
        vm.markSeen()
        onDone()
    }
    val speak: (String) -> Unit = { vm.speak(it) }

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    InkScaffold(
        topBar = {
            GameTopBar(
                title = "รู้จักพินอิน",
                coins = null,
                onBack = if (firstRun) null else onDone,
                actions = {
                    if (firstRun) TextButton(onClick = finish) { Text("ข้าม") }
                },
            )
        },
        bottomBar = {
            IntroBottomBar(
                page = pager.currentPage,
                finishLabel = if (firstRun) "เริ่มเล่นเลย!" else "เสร็จแล้ว",
                canFinish = quiz.done,
                onPrev = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                onFinish = finish,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                TtsWarning(speakerState, Modifier.padding(horizontal = 16.dp))
                HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                    when (page) {
                        0 -> WhatIsPinyinPage(speak)
                        1 -> SoundsPage(
                            emoji = "🗣️",
                            title = "声母 พยัญชนะต้น",
                            subtitle = "เสียงที่ขึ้นต้นพยางค์ แตะที่การ์ดเพื่อฟังคำตัวอย่าง",
                            groups = PinyinBasics.initials,
                            notes = PinyinBasics.initialNotesTh,
                            onSpeak = speak,
                        )
                        2 -> SoundsPage(
                            emoji = "🎵",
                            title = "韵母 สระ",
                            subtitle = "เสียงที่ตามหลังพยัญชนะต้น แตะที่การ์ดเพื่อฟังคำตัวอย่าง",
                            groups = PinyinBasics.finals,
                            notes = PinyinBasics.finalNotesTh,
                            onSpeak = speak,
                        )
                        3 -> TonesPage(onSpeak = vm::speak)
                        4 -> ToneChangesPage(speak)
                        QuizPage -> ToneQuizPage(
                            quiz = quiz,
                            firstRun = firstRun,
                            onPlay = vm::playQuestion,
                            onChoose = vm::choose,
                            onNext = vm::nextQuestion,
                            onRestart = vm::restartQuiz,
                        )
                    }
                }
            }
            if (quiz.perfect && pager.currentPage == QuizPage) ConfettiBurst(Modifier.matchParentSize())
        }
        // Read each quiz word out as it comes up, once the quiz page has settled into view.
        if (pager.settledPage == QuizPage && !quiz.done) {
            LaunchedEffect(quiz.questions, quiz.index, speakerState) { vm.playQuestion() }
        }
    }
}

@Composable
private fun IntroBottomBar(
    page: Int,
    finishLabel: String,
    canFinish: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (page > 0) TextButton(onClick = onPrev) { Text("ย้อนกลับ") }
        }
        PageDots(page, PageCount)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (page < QuizPage) {
                GameButton(onClick = onNext) { Text("ถัดไป") }
            } else if (canFinish) {
                GameButton(onClick = onFinish, modifier = Modifier.appear()) { Text(finishLabel) }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 18.dp else 8.dp, label = "dot$i")
            Box(
                Modifier
                    .size(width, 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun IntroPage(emoji: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(emoji, fontSize = 40.sp)
        PinyinText(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

// --- Page 1: what pinyin is ------------------------------------------------------------------

private val InitialColor = Red
private val FinalColor = Jade
private val ToneMarkColor = Color(0xFF3E7BD6)

@Composable
private fun WhatIsPinyinPage(onSpeak: (String) -> Unit) {
    IntroPage(
        emoji = "👋",
        title = "พินอิน 拼音 คืออะไร?",
        subtitle = "พินอินคือการเขียนเสียงอ่านของอักษรจีนด้วยตัวอักษรโรมัน ในเกมนี้พินอินจะอยู่เหนืออักษรจีนเสมอ",
    ) {
        ExampleCard("你好", "nǐ hǎo", "สวัสดี", onSpeak)
        Text("หนึ่งพยางค์มี 3 ส่วน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SyllablePart("พยัญชนะต้น", InitialColor, Modifier.weight(1f)) { PartLetter("m", InitialColor) }
            Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            SyllablePart("สระ", FinalColor, Modifier.weight(1f)) { PartLetter("a", FinalColor) }
            Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            // The bare ˇ mark renders too small to read, so show the tone's shape instead.
            SyllablePart("วรรณยุกต์", ToneMarkColor, Modifier.weight(1f)) {
                ToneContour(PinyinBasics.tones[2].pitch, ToneMarkColor, Modifier.size(width = 44.dp, height = 36.dp))
            }
        }
        Text("=", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        ExampleCard("马", "mǎ", "ม้า", onSpeak)
        Text(
            "มาดูทีละส่วนกัน ปัดไปทางซ้ายหรือกด \"ถัดไป\" ได้เลย",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyllablePart(label: String, color: Color, modifier: Modifier, sample: @Composable () -> Unit) {
    Card(
        modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = BorderStroke(1.5.dp, color),
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) { sample() }
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PartLetter(text: String, color: Color) {
    Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color)
}

/** A word on rice paper with a speak button. */
@Composable
private fun ExampleCard(hanzi: String, pinyin: String, thai: String, onSpeak: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Cream, contentColor = Ink),
        border = BorderStroke(1.5.dp, Gold),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HanziBlock(hanzi, pinyin, thai, hanziSize = 36)
            SpeakButton(onClick = { onSpeak(hanzi) })
        }
    }
}

// --- Pages 2–3: initials and finals ----------------------------------------------------------

@Composable
private fun SoundsPage(
    emoji: String,
    title: String,
    subtitle: String,
    groups: List<PinyinBasics.SoundGroup>,
    notes: List<String>,
    onSpeak: (String) -> Unit,
) {
    IntroPage(emoji, title, subtitle) {
        groups.forEach { group ->
            Text(group.titleTh, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            SoundGrid(group.sounds, onSpeak)
        }
        NotesCard("จำง่าย ๆ", notes)
    }
}

private const val SoundColumns = 4

@Composable
private fun SoundGrid(sounds: List<PinyinBasics.Sound>, onSpeak: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sounds.chunked(SoundColumns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { SoundChip(it, onSpeak, Modifier.weight(1f).fillMaxHeight()) }
                repeat(SoundColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SoundChip(sound: PinyinBasics.Sound, onSpeak: (String) -> Unit, modifier: Modifier) {
    GameCard(
        onClick = { onSpeak(sound.hanzi) },
        modifier = modifier,
        sound = false,
        colors = CardDefaults.cardColors(containerColor = Cream, contentColor = Ink),
        border = BorderStroke(1.dp, Gold),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(sound.pinyin, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Red)
            Text(sound.thai, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text("${sound.hanzi} ${sound.example}", style = MaterialTheme.typography.bodySmall)
            Text(
                sound.meaningTh,
                style = MaterialTheme.typography.labelSmall,
                color = Ink.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NotesCard(title: String, notes: List<String>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("💡 $title", fontWeight = FontWeight.Bold)
            notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

// --- Page 4: tones ---------------------------------------------------------------------------

private fun toneColor(tone: Int): Color = when (tone) {
    1 -> Red
    2 -> Jade
    3 -> Color(0xFF3E7BD6)
    4 -> Color(0xFF8E4FBF)
    else -> Color(0xFF8A8178)
}

@Composable
private fun TonesPage(onSpeak: (String, Boolean) -> Unit) {
    IntroPage(
        emoji = "🎢",
        title = "声调 วรรณยุกต์",
        subtitle = "ภาษาจีนกลางมี 4 เสียงกับเสียงเบา พยางค์เดียวกันแต่เสียงต่างกัน ความหมายก็เปลี่ยน แตะเพื่อฟัง",
    ) {
        PinyinBasics.tones.forEach { ToneCard(it, onSpeak = { hanzi -> onSpeak(hanzi, false) }) }
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Cream, contentColor = Ink),
            border = BorderStroke(1.5.dp, Gold),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ลองฟังรวมกันในประโยค", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                HanziBlock(
                    PinyinBasics.toneSentence,
                    PinyinBasics.toneSentencePinyin,
                    PinyinBasics.toneSentenceTh,
                    hanziSize = 32,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpeakButton(onClick = { onSpeak(PinyinBasics.toneSentence, false) })
                    SpeakButton(onClick = { onSpeak(PinyinBasics.toneSentence, true) }, slow = true)
                }
            }
        }
        NotesCard("วางเครื่องหมายตรงไหน", listOf(PinyinBasics.markPlacementTh))
    }
}

@Composable
private fun ToneCard(tone: PinyinBasics.Tone, onSpeak: (String) -> Unit) {
    val color = toneColor(tone.number)
    GameCard(
        onClick = { onSpeak(tone.hanzi) },
        modifier = Modifier.fillMaxWidth(),
        sound = false,
        // Opaque, so the busy background painting doesn't show through the text.
        colors = CardDefaults.cardColors(containerColor = lerp(Cream, color, 0.15f), contentColor = Ink),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneContour(tone.pitch, color, Modifier.size(width = 52.dp, height = 40.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tone.nameTh,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(tone.howTh, style = MaterialTheme.typography.bodyMedium)
            }
            HanziBlock(tone.hanzi, tone.pinyin, tone.meaningTh, hanziSize = 28)
        }
    }
}

/** The tone's pitch drawn over five faint lines, high at the top (a single level is a dot). */
@Composable
private fun ToneContour(pitch: List<Int>, color: Color, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val inset = stroke
        fun y(level: Int) = inset + (size.height - 2 * inset) * (5 - level) / 4f
        fun x(fraction: Float) = inset + (size.width - 2 * inset) * fraction
        for (level in 1..5) {
            drawLine(grid, Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx())
        }
        if (pitch.size == 1) {
            drawCircle(color, radius = stroke * 1.2f, center = Offset(x(0.5f), y(pitch[0])))
            return@Canvas
        }
        val path = Path()
        pitch.forEachIndexed { i, level ->
            val px = x(i.toFloat() / pitch.lastIndex)
            if (i == 0) path.moveTo(px, y(level)) else path.lineTo(px, y(level))
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// --- Page 5: tone changes --------------------------------------------------------------------

@Composable
private fun ToneChangesPage(onSpeak: (String) -> Unit) {
    IntroPage(
        emoji = "🔄",
        title = "变调 การเปลี่ยนเสียง",
        subtitle = "บางคำเขียนอย่างหนึ่งแต่อ่านอีกอย่างเมื่ออยู่ติดกับคำอื่น พินอินในเกมเขียนตามรูปเดิม แต่เสียงอ่านจะเปลี่ยนตามกฎนี้",
    ) {
        PinyinBasics.toneChanges.forEach { change ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(change.titleTh, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(change.ruleTh, style = MaterialTheme.typography.bodyMedium)
                    change.examples.forEach { ChangeRow(it, onSpeak) }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(example: PinyinBasics.ChangeExample, onSpeak: (String) -> Unit) {
    GameCard(
        onClick = { onSpeak(example.hanzi) },
        modifier = Modifier.fillMaxWidth(),
        sound = false,
        colors = CardDefaults.cardColors(containerColor = Cream, contentColor = Ink),
        border = BorderStroke(1.dp, Gold),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(example.hanzi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (example.spoken != null) {
                    Text(
                        example.written,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough,
                        color = Ink.copy(alpha = 0.5f),
                    )
                    Text("อ่าน ${example.spoken}", fontWeight = FontWeight.Bold, color = Red)
                } else {
                    Text(example.written, fontWeight = FontWeight.Bold)
                    Text("ไม่เปลี่ยน", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.6f))
                }
            }
            Text(example.meaningTh, style = MaterialTheme.typography.bodyMedium)
            Text("🔊", fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// --- Page 6: tone quiz -----------------------------------------------------------------------

@Composable
private fun ToneQuizPage(
    quiz: ToneQuizState,
    firstRun: Boolean,
    onPlay: (Boolean) -> Unit,
    onChoose: (Int) -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
) {
    IntroPage(
        emoji = "🎧",
        title = "ทดสอบหูกันหน่อย",
        subtitle = "ฟังคำแล้วเลือกว่าเป็นวรรณยุกต์เสียงไหน",
    ) {
        if (quiz.done) {
            QuizDone(quiz, firstRun, onRestart)
            return@IntroPage
        }
        val item = quiz.questions[quiz.index]
        ProgressHeader(quiz.index + 1, quiz.questions.size)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            SpeakButton(onClick = { onPlay(false) })
            SpeakButton(onClick = { onPlay(true) }, slow = true)
        }
        if (quiz.solved) {
            HanziBlock(item.hanzi, item.pinyin, item.meaningTh, Modifier.fillMaxWidth().appear(), hanziSize = 40)
        } else {
            Text(
                "?",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        listOf(1, 2, 3, 4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tone ->
                    ToneOption(
                        tone = tone,
                        right = quiz.solved && tone == item.tone,
                        wrong = tone in quiz.tried && tone != item.tone,
                        onClick = { onChoose(tone) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (quiz.solved) {
            GameButton(onClick = onNext, modifier = Modifier.fillMaxWidth().appear(200)) {
                Text(if (quiz.index < quiz.questions.lastIndex) "ข้อต่อไป" else "ดูผล")
            }
        }
    }
}

@Composable
private fun ToneOption(tone: Int, right: Boolean, wrong: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val info = PinyinBasics.tones.first { it.number == tone }
    val container by animateColorAsState(
        when {
            right -> Correct
            wrong -> Wrong
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "tone$tone",
    )
    GameCard(
        onClick = onClick,
        sound = false,
        modifier = modifier.answerFeedback(right, wrong),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToneContour(
                info.pitch,
                if (right || wrong) Color.White else toneColor(tone),
                Modifier.size(width = 48.dp, height = 36.dp),
            )
            Text(info.nameTh, fontWeight = FontWeight.Bold)
            Text(info.pinyin, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuizDone(quiz: ToneQuizState, firstRun: Boolean, onRestart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (quiz.perfect) "🎉" else "👍", fontSize = 64.sp, modifier = Modifier.appear())
        Text(
            "ตอบถูกตั้งแต่ครั้งแรก ${quiz.firstTryCorrect}/${quiz.questions.size} ข้อ",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (quiz.perfect) "หูดีมาก! พร้อมลุยแล้ว" else "ไม่เป็นไร ในเกมยังได้ฝึกฟังอีกเยอะ",
            textAlign = TextAlign.Center,
        )
        if (firstRun) {
            Text(
                "กด \"เริ่มเล่นเลย!\" ด้านล่างเพื่อไปที่แผนที่ ถ้าอยากทบทวนอีก เปิดได้ที่หน้าโปรไฟล์",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        GameOutlinedButton(onClick = onRestart) { Text("ทำแบบทดสอบอีกครั้ง") }
    }
}
