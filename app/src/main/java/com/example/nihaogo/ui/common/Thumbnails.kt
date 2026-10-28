package com.example.nihaogo.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.nihaogo.R
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold

/** The qilin acting out each category, in the map's order. */
private val CategoryThumbnails = listOf(
    R.drawable.chapter_greet,
    R.drawable.chapter_numbers,
    R.drawable.chapter_family,
    R.drawable.chapter_food,
    R.drawable.chapter_school,
    R.drawable.chapter_shopping,
    R.drawable.chapter_travel,
    R.drawable.chapter_phone,
)

/** The picture for the category at [index] in the map's order (wraps around past the list). */
@DrawableRes
fun categoryThumbnail(index: Int): Int = CategoryThumbnails[index.mod(CategoryThumbnails.size)]

/** [categoryThumbnail] in a rounded frame with a thin gold edge; size it with [modifier]. */
@Composable
fun CategoryThumbnail(index: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .clip(shape)
            .background(Cream)
            .border(1.dp, Gold.copy(alpha = 0.6f), shape),
    ) {
        // The pictures are cut-outs, so they sit whole on the cream fill instead of filling the frame.
        Image(
            painterResource(categoryThumbnail(index)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
        )
        content()
    }
}
