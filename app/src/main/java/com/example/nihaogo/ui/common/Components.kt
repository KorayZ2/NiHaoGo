package com.example.nihaogo.ui.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.nihaogo.speech.Speaker
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.Locked
import com.example.nihaogo.ui.theme.Red

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTopBar(title: String, coins: Int?, onBack: (() -> Unit)?, onProfile: (() -> Unit)? = null) {
    CenterAlignedTopAppBar(
        title = { PinyinText(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ย้อนกลับ")
                }
            }
        },
        actions = {
            if (coins != null) CoinChip(coins, Modifier.padding(end = if (onProfile != null) 0.dp else 12.dp))
            if (onProfile != null) {
                IconButton(onClick = onProfile) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "โปรไฟล์")
                }
            }
        },
        // Transparent so the ink-wash background shows through (see InkScaffold).
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            "🪙 $coins",
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
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
        ),
    ) {
        Text(if (slow) "🐢" else "🔊", fontSize = 24.sp)
    }
}

@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled && !listening,
        modifier = modifier.size(72.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (listening) {
            CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text("🎤", fontSize = 30.sp)
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
    Card(
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

/** End-of-step summary with stars and coins. */
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
) {
    val passed = result.stars > 0
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (passed) "🎉" else "💪", fontSize = 72.sp)
            Text(
                if (passed) passedMessage else "เกือบแล้ว! ลองอีกครั้งนะ",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            StarsRow(result.stars, size = 44)
            Text("คะแนน ${result.score}", style = MaterialTheme.typography.titleLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            if (result.coinsEarned > 0) {
                Text("+${result.coinsEarned} 🪙", style = MaterialTheme.typography.titleLarge, color = Gold)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(if (passed) "ไปต่อ" else failedDoneLabel)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("เล่นอีกครั้ง") }
        }
    }
}

/** Rice-paper word card: dark ink hanzi with Chinese-red pinyin, edged in gold. */
@Composable
fun TileChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
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
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = color)
            if (tip.isNotBlank()) PinyinText("💡 $tip", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ProgressHeader(current: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { current.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            "$current / $total",
            modifier = Modifier.align(Alignment.End),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
