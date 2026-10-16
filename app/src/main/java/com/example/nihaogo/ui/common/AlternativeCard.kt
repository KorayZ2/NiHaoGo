package com.example.nihaogo.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nihaogo.data.content.Line
import com.example.nihaogo.ui.theme.Gold

/** Special modes: another natural way to say what the learner just said, with its sound. */
@Composable
fun AlternativeCard(
    line: Line,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "💡 พูดอีกแบบได้ว่า",
) {
    Card(
        modifier = modifier.fillMaxWidth().appear(),
        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.2f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                PinyinText(line.hanzi, pinyin = line.pinyin, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (line.thai.isNotBlank()) Text(line.thai, style = MaterialTheme.typography.bodySmall)
            }
            SpeakButton(onClick = onSpeak)
        }
    }
}
