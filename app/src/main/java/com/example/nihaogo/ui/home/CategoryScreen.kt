package com.example.nihaogo.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
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
import com.example.nihaogo.ui.common.CategoryThumbnail
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.StarsRow
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CategoryViewModel(container: AppContainer, categoryId: String) : ViewModel() {
    val category: Category = container.content.category(categoryId)
    val number: Int = container.content.categories.indexOf(category) + 1
    val bossUsesAi: Boolean = container.gemini.isConfigured

    /** The step cards drop in only on the first visit, not when coming back from a step. */
    var introPlayed = false
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())
}

@Composable
fun CategoryScreen(categoryId: String, onBack: () -> Unit, onOpenStep: (Step) -> Unit, onOpenWords: () -> Unit) {
    val vm = appViewModel { CategoryViewModel(it, categoryId) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val category = vm.category
    val intro = !vm.introPlayed
    LaunchedEffect(vm) { vm.introPlayed = true }

    InkScaffold(topBar = { GameTopBar("บทที่ ${vm.number}", progress.coins, onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryHeader(category, vm.number - 1, Modifier.appear(enabled = intro))
            WordListLink(category.words.size, onOpenWords, Modifier.appear(delayMillis = 60, enabled = intro))
            Step.entries.forEach { step ->
                val unlocked = GameRules.isStepUnlocked(category.id, step, progress)
                StepCard(
                    modifier = Modifier.appear(delayMillis = 120 + step.ordinal * 70, enabled = intro),
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

/** A paper sign edged in gold with the lesson's painting, its number and its name. */
@Composable
private fun CategoryHeader(category: Category, index: Int, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Paper,
        border = BorderStroke(1.5.dp, Gold),
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryThumbnail(index, Modifier.size(width = 108.dp, height = 112.dp))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("บทที่ ${index + 1} · ${category.titleTh}", style = MaterialTheme.typography.titleMedium, color = Red)
                PinyinText(
                    category.titleZh,
                    pinyin = category.titlePinyin,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(category.descriptionTh, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            }
        }
    }
}

@Composable
private fun WordListLink(count: Int, onClick: () -> Unit, modifier: Modifier) {
    GameCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📖", fontSize = 22.sp)
            Text(
                "ดูคำศัพท์ทั้งหมด ($count คำ)",
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                fontWeight = FontWeight.Bold,
            )
            Text("›", fontSize = 26.sp, color = InkSoft)
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
private fun StepCard(modifier: Modifier, step: Step, subtitle: String, stars: Int, unlocked: Boolean, onClick: () -> Unit) {
    val boss = step == Step.Boss
    GameCard(
        onClick = onClick,
        enabled = unlocked,
        modifier = modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.6f),
        colors = CardDefaults.cardColors(
            containerColor = if (boss) MaterialTheme.colorScheme.secondaryContainer else Paper,
            disabledContainerColor = if (boss) MaterialTheme.colorScheme.secondaryContainer else Paper,
        ),
        border = BorderStroke(if (boss) 2.dp else 1.dp, if (boss) Gold else Hairline),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = 12.dp).weight(1f)) {
                Text(
                    if (boss) "ด่านสุดท้าย" else "ขั้นที่ ${step.ordinal + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Red,
                    fontWeight = FontWeight.Bold,
                )
                Text(step.titleTh, fontWeight = FontWeight.Bold)
                PinyinText(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            if (unlocked) StarsRow(stars) else Text("🔒", fontSize = 22.sp)
        }
    }
}
