package com.example.nihaogo.ui.cover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.example.nihaogo.AppContainer.StartupStep
import com.example.nihaogo.NiHaoGoApp
import com.example.nihaogo.R
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

// Even when everything is ready at once, long enough to take in the cover art.
private const val MinCoverMillis = 1_500L
private const val BarFillMillis = 400

// Where the bar and the line under it sit in cover.webp, in its own pixels (941 × 1672).
private const val ArtWidth = 941f
private const val ArtHeight = 1672f
private const val BarLeft = 324f
private const val BarTop = 813f
private const val BarWidth = 296f
private const val BarHeight = 27f
private const val LabelTop = 850f
private const val LabelHeight = 22f

private val BarTrack = Color(0xFFD4C2B4)
private val BarFill = Brush.horizontalGradient(listOf(Color(0xFFC45453), Color(0xFFF37D6A)))
private val LabelColor = Color(0xFF483C35)

/**
 * Full-screen cover art shown on launch, with a loading bar that fills as the app's lessons,
 * saved progress and sounds are made ready. Moves on once they are (and the art has had
 * [MinCoverMillis]); a tap skips the wait only after loading has finished.
 */
@Composable
fun CoverScreen(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val container = (LocalContext.current.applicationContext as NiHaoGoApp).container
    val finished = remember { mutableStateListOf<StartupStep>() }
    var ready by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(
        targetValue = finished.size / StartupStep.entries.size.toFloat(),
        animationSpec = tween(BarFillMillis),
        label = "loading",
    )

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        coroutineScope {
            StartupStep.entries.map { step ->
                async {
                    container.prepare(step)
                    finished += step
                }
            }.awaitAll()
        }
        ready = true
        // Let the bar visibly reach the end before the cover fades.
        delay(maxOf(BarFillMillis.toLong(), MinCoverMillis - (System.currentTimeMillis() - start)))
        done()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (ready) done()
            },
    ) {
        Image(
            painter = painterResource(R.drawable.cover),
            contentDescription = "NiHaoGo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Map the art's pixels onto the screen the way ContentScale.Crop lays it out.
        val density = LocalDensity.current
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val scale = maxOf(screenW / ArtWidth, screenH / ArtHeight)
        val artLeft = (screenW - ArtWidth * scale) / 2
        val artTop = (screenH - ArtHeight * scale) / 2
        fun x(px: Float) = (artLeft + px * scale).toInt()
        fun y(px: Float) = (artTop + px * scale).toInt()

        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(x(BarLeft), y(BarTop)) }
                    .size((BarWidth * scale).toDp(), (BarHeight * scale).toDp())
                    .clip(RoundedCornerShape(percent = 50))
                    .background(BarTrack),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(shown)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(BarFill),
                )
            }

            val label = StartupStep.entries.firstOrNull { it !in finished }?.label ?: "พร้อมแล้ว!"
            Text(
                text = "$label ${(shown * 100).toInt()}%",
                color = LabelColor,
                fontSize = (LabelHeight * scale).toSp(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset { IntOffset(0, y(LabelTop)) }
                    .width(screenW.toDp()),
            )
        }
    }
}
