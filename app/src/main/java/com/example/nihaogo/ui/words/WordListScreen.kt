package com.example.nihaogo.ui.words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.R
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.CategoryThumbnail
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.TtsWarning
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.rememberSfx
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class WordListViewModel(private val container: AppContainer) : ViewModel() {
    val categories: List<Category> = container.content.categories
    private val ids = categories.map { it.id }
    val speakerState = container.speaker.state
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())

    fun isOpen(category: Category, progress: UserProgress) = GameRules.isCategoryUnlocked(ids, category.id, progress)

    fun speak(word: Word) = container.speaker.speak(word.hanzi)
}

/** Every word of the opened categories, with a chip per category; [initialCategoryId] null shows them all. */
@Composable
fun WordListScreen(initialCategoryId: String?, onBack: () -> Unit) {
    val vm = appViewModel { WordListViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val speakerState by vm.speakerState.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(initialCategoryId) }

    InkScaffold(topBar = { GameTopBar("คำศัพท์", coins = null, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip("ทั้งหมด", selected == null) { selected = null } }
                itemsIndexed(vm.categories, key = { _, c -> c.id }) { index, category ->
                    val open = vm.isOpen(category, progress)
                    Chip((if (open) "" else "🔒 ") + "บทที่ ${index + 1}", selected == category.id) { selected = category.id }
                }
            }
            val shown = vm.categories.filter { selected == null || it.id == selected }
            val chosenLocked = selected != null && shown.none { vm.isOpen(it, progress) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { TtsWarning(speakerState) }
                if (chosenLocked) {
                    item { LockedNotice() }
                }
                shown.filter { vm.isOpen(it, progress) }.forEach { category ->
                    item(key = "h-${category.id}") {
                        Text(
                            "บทที่ ${vm.categories.indexOf(category) + 1} · ${category.titleTh} (${category.words.size} คำ)",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = Red,
                        )
                    }
                    val number = vm.categories.indexOf(category)
                    items(category.words, key = { "${category.id}-${it.hanzi}" }) { word ->
                        WordRow(word, categoryIndex = number, onSpeak = { vm.speak(word) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val sfx = rememberSfx()
    Surface(
        onClick = {
            sfx.tap()
            onClick()
        },
        shape = CircleShape,
        color = if (selected) Red else Paper,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, Hairline),
        shadowElevation = if (selected) 3.dp else 1.dp,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * A word on a paper card: its lesson's painting with the word's own emoji in the corner, then
 * hanzi, pinyin and Thai. Tapping it (or the red speaker) reads it aloud.
 */
@Composable
private fun WordRow(word: Word, categoryIndex: Int, onSpeak: () -> Unit) {
    Surface(
        onClick = onSpeak,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Paper,
        border = BorderStroke(1.dp, Hairline),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryThumbnail(categoryIndex, Modifier.size(width = 76.dp, height = 68.dp)) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Paper.copy(alpha = 0.92f))
                        .border(1.dp, Gold.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(word.emoji, fontSize = 16.sp) }
            }
            Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
                Text(word.hanzi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(word.pinyin, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text(word.thai, style = MaterialTheme.typography.bodyMedium)
            }
            SpeakButton(onClick = onSpeak, modifier = Modifier.padding(end = 4.dp).size(44.dp))
        }
    }
}

@Composable
private fun LockedNotice() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.mascot_books), contentDescription = null, modifier = Modifier.size(140.dp))
        Text(
            "บทนี้ยังไม่เปิด ชนะบอสของบทก่อนหน้าก่อน แล้วมาดูคำศัพท์กันนะ",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = InkSoft,
        )
    }
}
