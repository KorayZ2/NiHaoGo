package com.example.nihaogo.ui.practice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.RedLight
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Words of every category the player has opened, without repeats; the practice screens draw from these. */
suspend fun AppContainer.openedWords(): List<Word> {
    val current = progress.progress.first()
    val categories = content.categories
    val ids = categories.map { it.id }
    return categories
        .filter { GameRules.isCategoryUnlocked(ids, it.id, current) }
        .flatMap { it.words }
        .distinctBy { it.hanzi }
}

/** [count] other words to offer next to [answer], none sharing its hanzi or its Thai meaning. */
fun distractorsFor(answer: Word, pool: List<Word>, count: Int): List<Word> =
    pool.filter { it.hanzi != answer.hanzi && it.thai != answer.thai }
        .distinctBy { it.thai }
        .shuffled()
        .take(count)

/** Small paper pill for a counter in the top area, like hearts or score. */
@Composable
fun CounterChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Paper,
        border = BorderStroke(1.dp, Hairline),
        shadowElevation = 2.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Red bars that bob like a sound wave; livelier while [active]. */
@Composable
fun SoundWave(active: Boolean, modifier: Modifier = Modifier, bars: Int = 9) {
    val wave = rememberInfiniteTransition(label = "wave")
    val phase by wave.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (active) 700 else 1600, easing = LinearEasing)),
        label = "wavePhase",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(bars) { i ->
            val swing = sin((phase + i / bars.toFloat()) * 2 * PI).toFloat()
            // Taller in the middle, like a waveform.
            val shape = 1f - abs(i - (bars - 1) / 2f) / bars
            val h = (8 + shape * (if (active) 26 else 12) * (0.55f + 0.45f * swing)).dp
            Box(
                Modifier
                    .width(4.dp)
                    .height(h)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(RedLight, Red))),
            )
        }
    }
}

/** A gentle up-and-down float, for mascots and music notes. */
@Composable
fun floatOffset(periodMillis: Int = 1800): Float {
    val bob = rememberInfiniteTransition(label = "bob")
    val y by bob.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobY",
    )
    return y
}

