package com.example.nihaogo.ui.special

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nihaogo.AppContainer
import com.example.nihaogo.R
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Suspect
import com.example.nihaogo.speech.VoiceStyle
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.PageDots
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.Night
import kotlinx.coroutines.launch

/**
 * Painted art for a mode's story intro: the scene of the crime, and the cast with where each
 * suspect stands in it (fractions of the picture, so taps line up however it is cropped).
 */
private class CaseArt(
    @param:DrawableRes val story: Int,
    @param:DrawableRes val cast: Int,
    val spots: Map<String, Rect>,
)

private val caseArt = mapOf(
    "mansion" to CaseArt(
        story = R.drawable.mansion_story,
        cast = R.drawable.mansion_suspects,
        spots = mapOf(
            "laozhang" to Rect(0.07f, 0.21f, 0.36f, 0.60f),
            "liayi" to Rect(0.37f, 0.23f, 0.71f, 0.63f),
            "xiaolong" to Rect(0.71f, 0.24f, 0.98f, 0.67f),
        ),
    ),
)

/** Whether [modeId] opens with a story intro the first time it is entered. */
fun hasCaseIntro(modeId: String): Boolean = modeId in caseArt

class CaseIntroViewModel(private val container: AppContainer, modeId: String) : ViewModel() {
    val mode: SpecialMode = container.content.mode(modeId)
    val speakerState = container.speaker.state

    fun speak(text: String, voice: VoiceStyle) = container.speaker.speak(text, style = voice)

    fun markSeen() = container.settings.markCaseIntroSeen(mode.id)
}

private const val PageCount = 2
private const val CastPage = PageCount - 1

/**
 * The case told over two painted pages: what happened, then the suspects, who each introduce
 * themselves in their own voice when tapped. On [firstRun] it leads on into the mode.
 */
@Composable
fun CaseIntroScreen(modeId: String, firstRun: Boolean, onDone: () -> Unit) {
    val vm = appViewModel { CaseIntroViewModel(it, modeId) }
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()
    val art = caseArt.getValue(modeId)
    val mode = vm.mode
    val pager = rememberPagerState { PageCount }
    val scope = rememberCoroutineScope()
    var speaking by rememberSaveable { mutableStateOf<String?>(null) }
    var met by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val finish: () -> Unit = {
        vm.markSeen()
        onDone()
    }
    val meet: (Suspect) -> Unit = { s ->
        speaking = s.id
        if (s.id !in met) met = met + s.id
        s.intro?.let { vm.speak(it.hanzi, s.voice) }
    }

    LightSystemBarIcons()
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> StoryPage(mode, art)
                CastPage -> SuspectsPage(mode, art, speaking, met, onMeet = meet)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = if (firstRun) finish else onDone,
                    modifier = Modifier.background(Night.copy(alpha = 0.55f), RoundedCornerShape(50)),
                ) { Text(if (firstRun) "ข้าม" else "ปิด", color = Cream) }
            }
            TtsWarning(speakerState, Modifier.padding(horizontal = 16.dp))
            Box(Modifier.weight(1f))
            CaseBottomBar(
                page = pager.currentPage,
                finishLabel = if (firstRun) "เริ่มสืบคดี! 🔍" else "เสร็จแล้ว",
                onPrev = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                onFinish = finish,
            )
        }
    }
}

/** The paintings are dark, so the status and navigation bar icons turn light while they show. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val bars = WindowCompat.getInsetsController(window, view)
        val status = bars.isAppearanceLightStatusBars
        val navigation = bars.isAppearanceLightNavigationBars
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = status
            bars.isAppearanceLightNavigationBars = navigation
        }
    }
}

@Composable
private fun CaseBottomBar(page: Int, finishLabel: String, onPrev: () -> Unit, onNext: () -> Unit, onFinish: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.9f))))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (page > 0) TextButton(onClick = onPrev) { Text("ย้อนกลับ", color = Cream) }
        }
        PageDots(page, PageCount, inactive = Cream.copy(alpha = 0.4f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (page < CastPage) {
                GameButton(onClick = onNext) { Text("ถัดไป") }
            } else {
                GameButton(onClick = onFinish, modifier = Modifier.appear()) { Text(finishLabel) }
            }
        }
    }
}

// --- Page 1: what happened -------------------------------------------------------------------

@Composable
private fun StoryPage(mode: SpecialMode, art: CaseArt) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(art.story),
            contentDescription = mode.titleTh,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.85f))))
                .padding(start = 16.dp, end = 16.dp, top = 32.dp)
                .navigationBarsPadding()
                // Room for the bottom bar drawn over the pager.
                .padding(bottom = 72.dp),
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Night.copy(alpha = 0.82f), contentColor = Cream),
                border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.7f)),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("📜 เกิดอะไรขึ้น?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gold)
                    mode.storyTh.ifEmpty { listOf(mode.introTh) }.forEachIndexed { i, paragraph ->
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.appear(delayMillis = 250 + i * 350),
                        )
                    }
                }
            }
        }
    }
}

// --- Page 2: the suspects --------------------------------------------------------------------

@Composable
private fun SuspectsPage(
    mode: SpecialMode,
    art: CaseArt,
    speaking: String?,
    met: List<String>,
    onMeet: (Suspect) -> Unit,
) {
    val painter = painterResource(art.cast)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Full width from the top, so no one's name card is cut off at the sides; on a tall
        // phone the space left below holds the speech bubble.
        val boxW = constraints.maxWidth.toFloat()
        val image = painter.intrinsicSize
        val drawnW = boxW
        val drawnH = image.height * boxW / image.width
        Image(
            painter = painter,
            contentDescription = "ผู้ต้องสงสัย",
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        // Let the bottom edge of the painting fade into the background.
        with(density) {
            val fade = 120.dp.toPx()
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Night),
                        startY = drawnH - fade,
                        endY = drawnH,
                    ),
                ),
            )
        }

        mode.suspects.forEach { s ->
            val spot = art.spots[s.id] ?: return@forEach
            with(density) {
                SuspectSpot(
                    talking = speaking == s.id,
                    met = s.id in met,
                    onClick = { onMeet(s) },
                    modifier = Modifier
                        .offset((spot.left * drawnW).toDp(), (spot.top * drawnH).toDp())
                        .size((spot.width * drawnW).toDp(), (spot.height * drawnH).toDp()),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        ) {
            AnimatedContent(
                targetState = speaking,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "speaker",
            ) { id ->
                val suspect = mode.suspects.firstOrNull { it.id == id }
                if (suspect == null) MeetHint(met.size, mode.suspects.size) else IntroBubble(suspect, onReplay = { onMeet(suspect) })
            }
        }
    }
}

/** A suspect's tap area: a bobbing marker above their head, outlined while they talk. */
@Composable
private fun SuspectSpot(talking: Boolean, met: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "bobY",
    )
    Box(
        modifier
            .then(if (talking) Modifier.border(3.dp, Gold, RoundedCornerShape(24.dp)) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Text(
            when {
                talking -> "💬"
                met -> "✅"
                else -> "👆"
            },
            fontSize = 26.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-30).dp)
                .graphicsLayer { if (!met && !talking) translationY = -6.dp.toPx() * bob }
                .pop(talking, amount = 0.25f),
        )
    }
}

@Composable
private fun MeetHint(met: Int, total: Int) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Night.copy(alpha = 0.82f), contentColor = Cream),
        border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🕵️ ผู้ต้องสงสัย", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gold)
            Text(
                "ตอนที่หยกหายมี 3 คนอยู่ในคฤหาสน์ แตะที่ตัวละครเพื่อให้แต่ละคนแนะนำตัวเป็นภาษาจีน",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (met > 0) Text("ทำความรู้จักแล้ว $met/$total คน", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** What a suspect says about themselves, read out in their voice. */
@Composable
private fun IntroBubble(suspect: Suspect, onReplay: () -> Unit) {
    val line = suspect.intro ?: return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Cream, contentColor = Ink),
        border = BorderStroke(2.dp, Gold),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(suspect.emoji, fontSize = 32.sp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    PinyinText(suspect.name, pinyin = suspect.namePinyin, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(suspect.roleTh, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
                }
                SpeakButton(onClick = onReplay)
            }
            PinyinText(line.hanzi, pinyin = line.pinyin, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(line.thai, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
