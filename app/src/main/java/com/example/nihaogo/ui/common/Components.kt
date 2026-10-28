package com.example.nihaogo.ui.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.example.nihaogo.R
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.speech.Speaker
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.Locked
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Peach
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.RedDeep
import com.example.nihaogo.ui.theme.RedLight
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTopBar(
    title: String,
    coins: Int?,
    onBack: (() -> Unit)?,
    onProfile: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = { PinyinText(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                RoundIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ย้อนกลับ")
                }
            }
        },
        actions = {
            if (coins != null) CoinChip(coins, Modifier.padding(end = if (onProfile != null) 0.dp else 12.dp))
            if (onProfile != null) {
                RoundIconButton(onClick = onProfile, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "โปรไฟล์")
                }
            }
            actions()
        },
        // Transparent so the ink-wash background shows through (see InkScaffold).
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

/** Small round paper button with a warm edge, for the top bar. */
@Composable
fun RoundIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val press = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .pressScale(press, 0.9f)
            .shadow(3.dp, CircleShape, spotColor = Ink.copy(alpha = 0.3f))
            .background(Paper, CircleShape)
            .border(1.dp, Hairline, CircleShape),
        interactionSource = press,
        colors = IconButtonDefaults.iconButtonColors(contentColor = Ink),
        content = content,
    )
}

/** Counts up or down and bounces when the balance changes while on screen. */
@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier) {
    val shown = remember { Animatable(coins.toFloat()) }
    val bump = remember { Animatable(1f) }
    var gained by remember { mutableStateOf(true) }
    // The first value after a screen opens is the saved balance loading in, not a change to show.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(800)
        settled = true
    }
    LaunchedEffect(coins) {
        if (!settled) {
            shown.snapTo(coins.toFloat())
            return@LaunchedEffect
        }
        gained = coins > shown.value
        launch {
            bump.snapTo(0f)
            bump.animateTo(1f, tween(500))
        }
        shown.animateTo(coins.toFloat(), tween(600))
    }
    val flash = sin(bump.value * PI).toFloat()
    val base = Paper
    Surface(
        modifier = modifier.graphicsLayer {
            val s = 1f + flash * 0.22f
            scaleX = s
            scaleY = s
        },
        shape = RoundedCornerShape(50),
        color = lerp(base, if (gained) Gold else Wrong.copy(alpha = 0.6f), flash * 0.7f),
        border = BorderStroke(1.dp, Hairline),
        shadowElevation = 2.dp,
    ) {
        Text(
            "🪙 ${shown.value.roundToInt()}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun StarsRow(stars: Int, modifier: Modifier = Modifier, size: Int = 18) {
    Row(modifier) {
        repeat(3) { i ->
            Text(
                "★",
                fontSize = size.sp,
                color = if (i < stars) Gold else Locked.copy(alpha = 0.5f),
            )
        }
    }
}

/** Big Chinese text with pinyin above and Thai underneath. */
@Composable
fun HanziBlock(
    hanzi: String,
    pinyin: String,
    thai: String?,
    modifier: Modifier = Modifier,
    hanziSize: Int = 40,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PinyinText(hanzi, pinyin = pinyin, fontSize = hanziSize.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (thai != null) {
            Text(thai, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SpeakButton(onClick: () -> Unit, modifier: Modifier = Modifier, slow: Boolean = false) {
    val press = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .pressScale(press, 0.88f)
            .shadow(4.dp, CircleShape, spotColor = Red),
        interactionSource = press,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(if (slow) "🐢" else "🔊", fontSize = 24.sp)
    }
}

@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val press = remember { MutableInteractionSource() }
    val ring = MaterialTheme.colorScheme.primary
    Box(modifier.size(72.dp), contentAlignment = Alignment.Center) {
        // Rings ripple out while the mic is listening.
        if (listening) MicWaves(ring, Modifier.matchParentSize())
        FilledIconButton(
            onClick = onClick,
            enabled = enabled && !listening,
            modifier = Modifier
                .size(72.dp)
                .pressScale(press, 0.88f)
                .shadow(if (enabled) 8.dp else 0.dp, CircleShape, spotColor = Red),
            interactionSource = press,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = if (listening) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            ),
        ) {
            if (listening) {
                CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("🎤", fontSize = 30.sp)
            }
        }
    }
}

@Composable
private fun MicWaves(color: Color, modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val wave by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "micWave",
    )
    Canvas(modifier) {
        listOf(wave, (wave + 0.5f) % 1f).forEach { w ->
            drawCircle(color.copy(alpha = 0.45f * (1 - w)), radius = size.minDimension / 2 * (0.8f + w * 0.55f))
        }
    }
}

/**
 * Returns a function that runs an action once microphone permission is granted
 * (asking for it first if needed). [onDenied] runs when the user refuses the permission.
 */
@Composable
fun rememberMicPermission(onDenied: () -> Unit = {}): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val currentOnDenied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pending?.invoke()
        } else {
            Toast.makeText(context, "ต้องอนุญาตไมโครโฟนก่อน ถึงจะฝึกพูดได้นะ", Toast.LENGTH_LONG).show()
            currentOnDenied()
        }
        pending = null
    }
    val currentLauncher by rememberUpdatedState(launcher)
    return remember(context) {
        { action ->
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                action()
            } else {
                pending = action
                currentLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/** Shown when the device has no Chinese TTS voice. */
@Composable
fun TtsWarning(state: Speaker.State, modifier: Modifier = Modifier) {
    if (state == Speaker.State.Ready || state == Speaker.State.Initializing) return
    val context = LocalContext.current
    PaperCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "เครื่องนี้ยังไม่มีเสียงอ่านภาษาจีน จึงเล่นเสียงไม่ได้",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text("ดาวน์โหลดเสียงภาษาจีน") }
        }
    }
}

/**
 * End-of-step summary. Stars pop in one by one, then the win (or lose) sound, then the coins count up.
 * Confetti falls for three stars, or for any win when [bigWin] (a beaten boss).
 */
@Composable
fun StepResultPanel(
    result: StepResult,
    passedMessage: String,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    /** Label for the main button when the step was not passed. */
    failedDoneLabel: String = "กลับไปหน้าหมวด",
    bigWin: Boolean = false,
) {
    val passed = result.stars > 0
    val sfx = rememberSfx()
    var shownStars by remember(result) { mutableIntStateOf(0) }
    var confetti by remember(result) { mutableStateOf(false) }
    val score = remember(result) { Animatable(0f) }
    val coins = remember(result) { Animatable(0f) }
    val emoji = remember(result) { Animatable(0f) }

    LaunchedEffect(result) {
        launch { emoji.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
        launch { score.animateTo(result.score.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
        delay(350)
        if (!passed) sfx.play(Sfx.Sound.Lose)
        repeat(result.stars) { i ->
            delay(if (i == 0) 150 else 330)
            shownStars = i + 1
            sfx.play(Sfx.Sound.Star, rate = 1f + 0.12f * i)
        }
        if (passed) {
            delay(250)
            sfx.play(Sfx.Sound.Win)
            confetti = result.stars == 3 || bigWin
        }
        if (result.coinsEarned > 0) {
            delay(450)
            sfx.play(Sfx.Sound.Coin)
            coins.animateTo(result.coinsEarned.toFloat(), tween(600))
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Image(
                painterResource(if (passed) R.drawable.mascot_cheer else R.drawable.mascot_try),
                contentDescription = null,
                modifier = Modifier.size(150.dp).graphicsLayer {
                    scaleX = emoji.value
                    scaleY = emoji.value
                    rotationZ = (1 - emoji.value) * if (passed) -30f else 0f
                },
            )
            Text(
                if (passed) passedMessage else "เกือบแล้ว! ลองอีกครั้งนะ",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.appear(150),
            )
            AnimatedStars(shownStars, size = 44)
            Text("คะแนน ${score.value.roundToInt()}", style = MaterialTheme.typography.titleLarge)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.appear(300),
                )
            }
            if (result.coinsEarned > 0) {
                Text(
                    "+${coins.value.roundToInt()} 🪙",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (coins.value > 0f) 1f else 0f }
                        .pop(coins.value >= result.coinsEarned, amount = 0.2f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.appear(500), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(if (passed) "ไปต่อ" else failedDoneLabel)
                }
                GameOutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("เล่นอีกครั้ง") }
            }
        }
        if (confetti) ConfettiBurst(Modifier.matchParentSize())
    }
}

/** Gold stars that spring in over grey placeholders as [shown] rises. */
@Composable
private fun AnimatedStars(shown: Int, size: Int) {
    Row {
        repeat(3) { i ->
            val scale = remember { Animatable(0f) }
            LaunchedEffect(i < shown) {
                if (i < shown) scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) else scale.snapTo(0f)
            }
            Box(contentAlignment = Alignment.Center) {
                Text("★", fontSize = size.sp, color = Locked.copy(alpha = 0.5f))
                Text(
                    "★",
                    fontSize = size.sp,
                    color = Gold,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        rotationZ = (1 - scale.value) * 90f
                        alpha = scale.value.coerceIn(0f, 1f)
                    },
                )
            }
        }
    }
}

private val PillShape = RoundedCornerShape(50)
private val ButtonGradient = Brush.verticalGradient(listOf(RedLight, Red, RedDeep))

/** Red pill [Button] with a soft gradient and glow, that shrinks when pressed and clicks. */
@Composable
fun GameButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val sfx = rememberSfx()
    val press = remember { MutableInteractionSource() }
    Button(
        onClick = {
            sfx.tap()
            onClick()
        },
        modifier = modifier
            .heightIn(min = 52.dp)
            .pressScale(press)
            .then(
                if (enabled) {
                    Modifier
                        .shadow(6.dp, PillShape, spotColor = Red)
                        .background(ButtonGradient, PillShape)
                } else {
                    Modifier
                }
            ),
        enabled = enabled,
        shape = PillShape,
        // Disabled stays a solid grey pill so it still reads as a button over the painting.
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Locked,
            disabledContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
        interactionSource = press,
        content = content,
    )
}

/** Paper pill [OutlinedButton] edged in red, that shrinks when pressed and clicks. */
@Composable
fun GameOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val sfx = rememberSfx()
    val press = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = {
            sfx.tap()
            onClick()
        },
        modifier = modifier.heightIn(min = 52.dp).pressScale(press),
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Paper,
            disabledContainerColor = Paper.copy(alpha = 0.85f),
            disabledContentColor = Locked,
        ),
        border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.primary else Locked.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
        interactionSource = press,
        content = content,
    )
}

/**
 * Clickable [Card] that shrinks when pressed. Leave [sound] off for answer choices, where the
 * right/wrong sound is the feedback.
 */
@Composable
fun GameCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sound: Boolean = true,
    colors: CardColors = CardDefaults.cardColors(containerColor = Paper),
    border: BorderStroke? = BorderStroke(1.dp, Hairline),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val sfx = rememberSfx()
    val press = remember { MutableInteractionSource() }
    Card(
        onClick = {
            if (sound) sfx.tap()
            onClick()
        },
        modifier = modifier.pressScale(press, 0.96f),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = colors,
        border = border,
        elevation = elevation,
        interactionSource = press,
        content = content,
    )
}

/**
 * [Card] in the app's look: bright paper, a warm hairline edge and a soft shadow. Clickable only
 * when [onClick] is given; use [GameCard] for game choices that should shrink and click.
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors = CardDefaults.cardColors(containerColor = Paper),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border: BorderStroke? = BorderStroke(1.dp, Hairline),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Card(onClick, modifier, enabled, shape, colors, elevation, border, content = content)
    } else {
        Card(modifier, shape, colors, elevation, border, content)
    }
}

/** Rice-paper word card: dark ink hanzi with Chinese-red pinyin, edged in gold. */
@Composable
fun TileChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GameCard(
        onClick = onClick,
        modifier = modifier.appear(),
        colors = CardDefaults.cardColors(containerColor = Cream),
        border = BorderStroke(1.5.dp, Gold),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        PinyinText(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = Ink,
            pinyinColor = Red,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun FeedbackCard(title: String, tip: String, color: Color) {
    PaperCard(
        Modifier.fillMaxWidth().appear(),
        colors = CardDefaults.cardColors(containerColor = lerp(Paper, color, 0.15f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = color)
            if (tip.isNotBlank()) PinyinText("💡 $tip", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ProgressHeader(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ProgressBar(current.toFloat() / total, Modifier.weight(1f))
        Text(
            "$current/$total",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Rounded red bar on a peach track that glides to [fraction]. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(450), label = "progress")
    Box(
        modifier
            .height(height)
            .clip(PillShape)
            .background(Peach),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .clip(PillShape)
                .background(Brush.horizontalGradient(listOf(RedLight, Red))),
        )
    }
}

/** Which page of a swipeable intro is showing; the current one is drawn wider. */
@Composable
fun PageDots(current: Int, count: Int, inactive: Color = MaterialTheme.colorScheme.outlineVariant) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 18.dp else 8.dp, label = "dot$i")
            Box(
                Modifier
                    .size(width, 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else inactive),
            )
        }
    }
}
