package com.example.nihaogo.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.CorrectLight
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.RedDeep
import com.example.nihaogo.ui.theme.RedLight
import com.example.nihaogo.ui.theme.Wrong
import com.example.nihaogo.ui.theme.WrongLight

/** The qilin mascot, cross-fading when [mascot] changes so its mood can follow the game. */
@Composable
fun Mascot(@DrawableRes mascot: Int, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Crossfade(mascot, modifier, label = "mascot") {
        Image(painterResource(it), contentDescription = null, modifier = Modifier.size(size))
    }
}

/** The qilin with what it says in a paper speech bubble beside it. */
@Composable
fun MascotSays(
    @DrawableRes mascot: Int,
    line: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    size: Dp = 96.dp,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Mascot(mascot, size = size)
        Surface(
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = Paper,
            border = BorderStroke(1.dp, Hairline),
            shadowElevation = 2.dp,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                PinyinText(line, style = MaterialTheme.typography.titleMedium)
                detail?.let { PinyinText(it, style = MaterialTheme.typography.bodySmall, color = InkSoft) }
            }
        }
    }
}

/** A paper card edged in gold holding a question or task, with a small red [label] on top. */
@Composable
fun PromptCard(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    PaperCard(modifier.fillMaxWidth(), border = BorderStroke(1.5.dp, Gold), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Red)
            content()
        }
    }
}

/**
 * One answer of a multiple-choice question: a lettered circle, then the option. It turns green
 * with a tick when [right], red with a cross when [wrong].
 */
@Composable
fun AnswerOption(
    index: Int,
    right: Boolean,
    wrong: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fill by animateColorAsState(
        when {
            right -> CorrectLight
            wrong -> WrongLight
            else -> Paper
        },
        label = "optionFill",
    )
    val edge by animateColorAsState(
        when {
            right -> Correct
            wrong -> Wrong
            else -> Hairline
        },
        label = "optionEdge",
    )
    GameCard(
        onClick = onClick,
        sound = false,
        modifier = modifier.fillMaxWidth().answerFeedback(right, wrong),
        colors = CardDefaults.cardColors(containerColor = fill),
        border = BorderStroke(if (right || wrong) 2.dp else 1.dp, edge),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            right -> Correct
                            wrong -> Wrong
                            else -> Cream
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        right -> "✓"
                        wrong -> "✗"
                        else -> ('A' + index).toString()
                    },
                    color = if (right || wrong) Color.White else InkSoft,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Box(Modifier.padding(start = 12.dp).weight(1f)) { content() }
        }
    }
}

/** Small red circle with a "go" arrow, at the end of a card that opens something. */
@Composable
fun ArrowBubble(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier
            .size(size)
            .shadow(4.dp, CircleShape, spotColor = Red)
            .background(Brush.verticalGradient(listOf(RedLight, Red, RedDeep)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("›", color = Color.White, fontSize = (size.value * 0.66f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
    }
}
