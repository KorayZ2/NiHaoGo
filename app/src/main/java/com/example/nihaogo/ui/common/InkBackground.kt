package com.example.nihaogo.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.nihaogo.R

/** [Scaffold] drawn over the app's ink-wash (山水) landscape painting instead of a flat color. */
@Composable
fun InkScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(modifier.fillMaxSize()) {
        InkBackground(dark)
        Scaffold(
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

/**
 * The landscape painting cropped to fill the screen, under a translucent veil so text on top
 * stays readable (a dark veil in dark theme).
 */
@Composable
private fun InkBackground(dark: Boolean) {
    Image(
        painter = painterResource(R.drawable.bg_landscape),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    val veil = if (dark) Color(0xFF15120F).copy(alpha = 0.72f) else Color(0xFFFFF8EE).copy(alpha = 0.35f)
    Box(Modifier.fillMaxSize().background(veil))
}
