package com.example.nihaogo.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.example.nihaogo.speech.Pinyin

/**
 * Text with pinyin written above the Chinese.
 *
 * - Only Chinese (plus punctuation): one pinyin line above the whole text, using [pinyin]
 *   when given, otherwise [Pinyin.of].
 * - Thai/English mixed with Chinese: each Chinese run carries its own pinyin above it, inline.
 * - No Chinese: a plain [Text].
 */
@Composable
fun PinyinText(
    text: String,
    modifier: Modifier = Modifier,
    pinyin: String? = null,
    color: Color = Color.Unspecified,
    pinyinColor: Color = MaterialTheme.colorScheme.tertiary,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val merged = style.merge(
        TextStyle(color = textColor, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign ?: TextAlign.Unspecified)
    )
    val kind = remember(text) { kindOf(text) }
    if (kind == Kind.Plain) {
        Text(text, modifier, style = merged)
        return
    }

    val hanziSize = merged.fontSize.takeIf { it.isSpecified }?.value ?: 14f
    val pinyinStyle = TextStyle(
        color = pinyinColor,
        fontSize = (hanziSize * 0.5f).coerceIn(10f, 20f).sp,
        fontWeight = FontWeight.Normal,
        textAlign = merged.textAlign,
    )

    if (kind == Kind.Chinese) {
        val alignment = when (merged.textAlign) {
            TextAlign.Center -> Alignment.CenterHorizontally
            TextAlign.End, TextAlign.Right -> Alignment.End
            else -> Alignment.Start
        }
        val reading = pinyin?.takeIf { it.isNotBlank() } ?: remember(text) { Pinyin.of(text) }
        Column(modifier, horizontalAlignment = alignment) {
            Text(reading, style = pinyinStyle)
            Text(text, style = merged)
        }
        return
    }

    // Mixed text: every Chinese run becomes an inline block of pinyin over hanzi, so Thai keeps
    // wrapping naturally around it.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val runs = remember(text) { runsOf(text) }
    val inline = HashMap<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        runs.forEachIndexed { i, run ->
            if (!run.chinese) {
                append(run.text)
                return@forEachIndexed
            }
            val id = "zh$i"
            val reading = Pinyin.of(run.text)
            val hanziSizePx = measurer.measure(run.text, merged, softWrap = false).size
            val pinyinSizePx = measurer.measure(reading, pinyinStyle, softWrap = false).size
            val width = maxOf(hanziSizePx.width, pinyinSizePx.width)
            val height = hanziSizePx.height + pinyinSizePx.height
            appendInlineContent(id, run.text)
            inline[id] = InlineTextContent(
                with(density) { Placeholder(width.toSp(), height.toSp(), PlaceholderVerticalAlign.TextBottom) }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(reading, style = pinyinStyle, maxLines = 1, softWrap = false)
                    Text(run.text, style = merged, maxLines = 1, softWrap = false)
                }
            }
        }
    }
    Text(annotated, modifier, style = merged, inlineContent = inline)
}

private enum class Kind { Plain, Chinese, Mixed }

private data class Run(val text: String, val chinese: Boolean)

private fun kindOf(text: String): Kind = when {
    text.none(Pinyin::isHan) -> Kind.Plain
    text.all { Pinyin.isHan(it) || isNeutral(it) } -> Kind.Chinese
    else -> Kind.Mixed
}

/** Spaces and punctuation do not make a Chinese text "mixed"; digits, emoji and other scripts do. */
private fun isNeutral(c: Char): Boolean =
    c.isWhitespace() || Character.getType(c).toByte() in punctuationTypes

private val punctuationTypes = setOf(
    Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
    Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
    Character.OTHER_PUNCTUATION,
)

private fun runsOf(text: String): List<Run> {
    val runs = ArrayList<Run>()
    var start = 0
    for (i in 1..text.length) {
        if (i == text.length || Pinyin.isHan(text[i]) != Pinyin.isHan(text[start])) {
            runs += Run(text.substring(start, i), Pinyin.isHan(text[start]))
            start = i
        }
    }
    return runs
}
