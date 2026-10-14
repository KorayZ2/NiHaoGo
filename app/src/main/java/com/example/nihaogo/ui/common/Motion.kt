package com.example.nihaogo.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.nihaogo.NiHaoGoApp
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Jade
import com.example.nihaogo.ui.theme.JadeLight
import com.example.nihaogo.ui.theme.Red
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** The app-wide sound player, for composables that play a sound themselves. */
@Composable
fun rememberSfx(): Sfx {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as NiHaoGoApp).container.sfx }
}

/** Shrinks a little while pressed. Give the clickable the same [interactionSource]. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.94f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Shakes side to side each time [active] turns true — for a wrong answer. */
fun Modifier.shake(active: Boolean): Modifier = composed {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(450, easing = LinearEasing))
        }
    }
    graphicsLayer {
        val p = progress.value
        translationX = sin(p * 6 * PI).toFloat() * 10.dp.toPx() * (1 - p)
    }
}

/** Swells and settles each time [active] turns true — for a right answer. */
fun Modifier.pop(active: Boolean, amount: Float = 0.08f): Modifier = composed {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
    }
    graphicsLayer {
        val s = 1f + sin(progress.value * PI).toFloat() * amount
        scaleX = s
        scaleY = s
    }
}

/** [shake] when wrong, [pop] when right. */
fun Modifier.answerFeedback(correct: Boolean, wrong: Boolean): Modifier = pop(correct).shake(wrong)

/**
 * Fades and slides up into place when first shown, after [delayMillis].
 * With [enabled] false it is simply there (e.g. when coming back to a screen).
 */
fun Modifier.appear(delayMillis: Int = 0, enabled: Boolean = true): Modifier = composed {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1 - p) * 24.dp.toPx()
    }
}

private class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val fall: Float,
    val drift: Float,
    val spin: Float,
    val width: Float,
    val color: Color,
)

private val confettiColors = listOf(Red, Gold, Jade, JadeLight, Correct, Color(0xFFFF7AA2))

/** Paper confetti falling across the whole area once. Draws only; touches pass through. */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, pieces: Int = 70, durationMillis: Int = 2800) {
    val parts = remember {
        val rnd = Random(System.nanoTime())
        List(pieces) {
            ConfettiPiece(
                x = rnd.nextFloat(),
                delay = rnd.nextFloat() * 0.3f,
                fall = 0.8f + rnd.nextFloat() * 0.6f,
                drift = (rnd.nextFloat() - 0.5f) * 0.3f,
                spin = (rnd.nextFloat() - 0.5f) * 1440f,
                width = 6f + rnd.nextFloat() * 6f,
                color = confettiColors[rnd.nextInt(confettiColors.size)],
            )
        }
    }
    val time = remember { Animatable(0f) }
    val done by remember { derivedStateOf { time.value >= 1f } }
    LaunchedEffect(Unit) { time.animateTo(1f, tween(durationMillis, easing = LinearEasing)) }
    if (done) return

    Canvas(modifier) {
        val t = time.value
        parts.forEach { p ->
            val local = ((t - p.delay) / (1 - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val w = p.width.dp.toPx()
            val y = -0.05f * size.height + local * p.fall * size.height * 1.1f
            val x = (p.x + p.drift * local + sin(local * 4 * PI + p.x * 10).toFloat() * 0.02f) * size.width
            val alpha = if (local > 0.75f) (1 - local) / 0.25f else 1f
            rotate(p.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - w / 2, y - w / 4),
                    size = Size(w, w / 2),
                )
            }
        }
    }
}
