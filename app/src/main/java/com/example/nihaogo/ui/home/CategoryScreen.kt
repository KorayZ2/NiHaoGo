package com.example.nihaogo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.StarsRow
import com.example.nihaogo.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CategoryViewModel(container: AppContainer, categoryId: String) : ViewModel() {
    val category: Category = container.content.category(categoryId)
    val bossUsesAi: Boolean = container.gemini.isConfigured
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())
}

@Composable
fun CategoryScreen(categoryId: String, onBack: () -> Unit, onOpenStep: (Step) -> Unit) {
    val vm = appViewModel { CategoryViewModel(it, categoryId) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val category = vm.category

    InkScaffold(topBar = { GameTopBar(category.titleTh, progress.coins, onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(category.emoji, fontSize = 64.sp)
                PinyinText(
                    category.titleZh,
                    pinyin = category.titlePinyin,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(category.descriptionTh, style = MaterialTheme.typography.bodyLarge)
            }
            Step.entries.forEach { step ->
                val unlocked = GameRules.isStepUnlocked(category.id, step, progress)
                StepCard(
                    step = step,
                    subtitle = subtitleFor(step, category, vm.bossUsesAi),
                    stars = progress.stars(category.id, step),
                    unlocked = unlocked,
                    onClick = { if (unlocked) onOpenStep(step) },
                )
            }
        }
    }
}

private fun subtitleFor(step: Step, category: Category, usesAi: Boolean): String = when (step) {
    Step.Vocab -> "${category.words.size} คำ · ฟัง พูดตาม แล้วจับคู่"
    Step.Sentence -> "${category.sentences.size} ประโยค · เรียงคำให้ถูก"
    Step.Listening -> "${category.listening.size} ข้อ · ฟังแล้วเลือกคำตอบ"
    Step.Boss -> "${category.boss.emoji} ${category.boss.name} (${category.boss.roleTh}) · " +
        if (usesAi) "คุยกับ AI" else "บทสำรอง"
}

@Composable
private fun StepCard(step: Step, subtitle: String, stars: Int, unlocked: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = unlocked,
        modifier = Modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.5f),
        colors = CardDefaults.cardColors(
            containerColor = if (step == Step.Boss) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (unlocked) step.emoji else "🔒", fontSize = 36.sp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${step.ordinal + 1}. ${step.titleTh}", fontWeight = FontWeight.Bold)
                PinyinText(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            StarsRow(stars)
        }
    }
}
