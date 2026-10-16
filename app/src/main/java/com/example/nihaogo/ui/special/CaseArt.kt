package com.example.nihaogo.ui.special

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.nihaogo.R
import com.example.nihaogo.data.content.Line
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Suspect
import com.example.nihaogo.ui.common.ConfettiBurst
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.Night

/**
 * Painted art for a mode: the scene of the crime and the cast for its story intro (with where
 * each suspect stands, as fractions of the picture), a title painting per scene, and the ending.
 * [findSpots]: for a find scene (by index), where each word's object is in its painting.
 */
internal class CaseArt(
    @param:DrawableRes val story: Int,
    @param:DrawableRes val cast: Int,
    val spots: Map<String, Rect>,
    @param:DrawableRes val scenes: List<Int>,
    @param:DrawableRes val ending: Int,
    val findSpots: Map<Int, Map<String, List<Rect>>> = emptyMap(),
)

private val caseArts = mapOf(
    "mansion" to CaseArt(
        story = R.drawable.mansion_story,
        cast = R.drawable.mansion_suspects,
        spots = mapOf(
            "laozhang" to Rect(0.07f, 0.21f, 0.36f, 0.60f),
            "liayi" to Rect(0.37f, 0.23f, 0.71f, 0.63f),
            "xiaolong" to Rect(0.71f, 0.24f, 0.98f, 0.67f),
        ),
        scenes = listOf(R.drawable.mansion_front, R.drawable.mansion_hall, R.drawable.mansion_kitchen, R.drawable.mansion_suspects),
        ending = R.drawable.mansion_ending,
        // Big, loose areas first: later ones are drawn on top and win where they overlap.
        findSpots = mapOf(
            0 to mapOf(
                "树" to listOf(Rect(0.00f, 0.00f, 0.22f, 0.25f)),
                "花" to listOf(
                    Rect(0.55f, 0.00f, 1.00f, 0.14f),
                    Rect(0.00f, 0.26f, 0.20f, 0.40f),
                    Rect(0.88f, 0.39f, 1.00f, 0.62f),
                    Rect(0.00f, 0.73f, 0.30f, 0.95f),
                ),
                "车" to listOf(Rect(0.00f, 0.41f, 0.31f, 0.52f)),
                "门" to listOf(Rect(0.37f, 0.29f, 0.60f, 0.49f)),
                "窗户" to listOf(Rect(0.76f, 0.17f, 1.00f, 0.39f)),
                "狗" to listOf(Rect(0.40f, 0.56f, 0.67f, 0.67f)),
                // The last footprints run under the shoes' toes; the shoes win there.
                "脚印" to listOf(Rect(0.46f, 0.73f, 0.79f, 0.83f)),
                "鞋" to listOf(Rect(0.74f, 0.70f, 1.00f, 0.79f)),
            ),
        ),
    ),
)

internal fun caseArt(modeId: String): CaseArt? = caseArts[modeId]

/** Whether [modeId] opens with a story intro the first time it is entered. */
fun hasCaseIntro(modeId: String): Boolean = modeId in caseArts

@DrawableRes
internal fun sceneArt(modeId: String, index: Int): Int? = caseArts[modeId]?.scenes?.getOrNull(index)

@DrawableRes
internal fun endingArt(modeId: String): Int? = caseArts[modeId]?.ending

/** Where each word's object is in a find scene's painting, if it is played on the painting. */
internal fun findSpots(modeId: String, index: Int): Map<String, List<Rect>>? = caseArts[modeId]?.findSpots?.get(index)

/** The paintings are dark, so the status and navigation bar icons turn light while they show. */
@Composable
internal fun LightSystemBarIcons() {
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

/**
 * A painting across the full width from the top, so nothing at its sides is cut off, fading
 * into the dark below it; on a tall phone that space holds the page's text and buttons.
 */
@Composable
internal fun PaintedBackdrop(@DrawableRes art: Int, description: String) {
    val painter = painterResource(art)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        val drawnH = painter.intrinsicSize.run { height * constraints.maxWidth / width }
        Image(
            painter = painter,
            contentDescription = description,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        val fade = with(density) { 120.dp.toPx() }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Night), startY = drawnH - fade, endY = drawnH),
            ),
        )
    }
}

@Composable
private fun PaintedBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .statusBarsPadding()
            .padding(8.dp)
            .background(Night.copy(alpha = 0.55f), RoundedCornerShape(50)),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ย้อนกลับ", tint = Cream)
    }
}

/** A scene's title painting, shown on the way in; the painting carries the scene's name. */
@Composable
internal fun SceneOpening(
    @DrawableRes art: Int,
    title: String,
    intro: String,
    index: Int,
    count: Int,
    onBack: () -> Unit,
    onEnter: () -> Unit,
) {
    LightSystemBarIcons()
    Box(Modifier.fillMaxSize()) {
        PaintedBackdrop(art, title)
        PaintedBackButton(onBack)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ฉากที่ ${index + 1}/$count", color = Gold, fontWeight = FontWeight.Bold)
            Text(
                intro,
                color = Cream,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.appear(150),
            )
            GameButton(onClick = onEnter, modifier = Modifier.fillMaxWidth().appear(300)) { Text("เข้าสู่ฉาก ▶") }
        }
    }
}

/** The case solved: the celebration painting, the culprit's confession and how it all ended. */
@Composable
internal fun CaseClosed(
    @DrawableRes art: Int,
    mode: SpecialMode,
    culprit: Suspect,
    onSpeak: (Line) -> Unit,
    onDone: () -> Unit,
) {
    LightSystemBarIcons()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val textMax = maxHeight * 0.42f
        PaintedBackdrop(art, "ปิดคดี")
        ConfettiBurst(Modifier.matchParentSize())
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.9f))))
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Keep the painting in view: the text scrolls within the lower part of the screen.
            Column(
                Modifier.heightIn(max = textMax).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // On a dark pill, so it reads over the busy painting.
                Box(Modifier.fillMaxWidth().appear(), contentAlignment = Alignment.Center) {
                    Text(
                        "🎉 ปิดคดีสำเร็จ! ${culprit.name} เป็นคนเอาหยกไป",
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Night.copy(alpha = 0.82f), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                mode.confession?.let { line ->
                    Card(
                        Modifier.fillMaxWidth().appear(200),
                        colors = CardDefaults.cardColors(containerColor = Cream, contentColor = Ink),
                        border = BorderStroke(2.dp, Gold),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(culprit.emoji, fontSize = 28.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                PinyinText(line.hanzi, pinyin = line.pinyin, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(line.thai, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { onSpeak(line) }) { Text("🔊", fontSize = 22.sp) }
                        }
                    }
                }
                Card(
                    Modifier.fillMaxWidth().appear(400),
                    colors = CardDefaults.cardColors(containerColor = Night.copy(alpha = 0.82f), contentColor = Cream),
                    border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.7f)),
                ) {
                    Text(mode.endingTh, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            GameButton(onClick = onDone, modifier = Modifier.fillMaxWidth().appear(600)) { Text("ปิดคดี ดูผลลัพธ์ 📁") }
        }
    }
}
