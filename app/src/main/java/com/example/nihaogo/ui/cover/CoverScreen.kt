package com.example.nihaogo.ui.cover

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.nihaogo.R
import kotlinx.coroutines.delay

private const val CoverMillis = 3_000L

/** Full-screen cover art shown on launch; moves on after [CoverMillis] or on tap. */
@Composable
fun CoverScreen(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        delay(CoverMillis)
        done()
    }
    Image(
        painter = painterResource(R.drawable.cover),
        contentDescription = "NiHaoGo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
    )
}
