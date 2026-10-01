package com.example.nihaogo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.BuildConfig
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.ModeKind
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.SpecialRules
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.special.modeProgressText
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Locked
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val categories: List<Category> = container.content.categories
    val modes: List<SpecialMode> = container.content.special.modes
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())

    fun toggleDevUnlock() {
        viewModelScope.launch { container.progress.update { it.copy(devUnlockAll = !it.devUnlockAll) } }
    }
}

@Composable
fun HomeScreen(onOpenCategory: (String) -> Unit, onOpenMode: (String) -> Unit, onOpenProfile: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ids = remember(vm.categories) { vm.categories.map { it.id } }

    InkScaffold(
        topBar = {
            GameTopBar(title = "NiHaoGo 你好", coins = progress.coins, onBack = null, onProfile = onOpenProfile)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { WelcomeCard(progress.trophies, vm.categories.size) }
            itemsIndexed(vm.categories, key = { _, c -> c.id }) { index, category ->
                val unlocked = GameRules.isCategoryUnlocked(ids, category.id, progress)
                CategoryNode(
                    category = category,
                    index = index,
                    unlocked = unlocked,
                    stars = Step.entries.sumOf { progress.stars(category.id, it) },
                    trophy = progress.bossWon(category.id),
                    onClick = {
                        if (unlocked) {
                            onOpenCategory(category.id)
                        } else {
                            scope.launch { snackbar.showSnackbar("ชนะบอสของหมวดก่อนหน้าก่อน ถึงจะเปิดหมวดนี้ได้นะ 🔒") }
                        }
                    },
                )
            }
            item {
                Text(
                    "โหมดพิเศษ",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(vm.modes, key = { it.id }) { mode ->
                val open = SpecialRules.isModeOpen(mode, progress)
                val after = vm.categories.indexOfFirst { it.id == mode.unlockAfter }
                SpecialModeCard(
                    mode = mode,
                    open = open,
                    status = if (open) modeProgressText(mode, progress)
                    else "🔒 ชนะบอสหมวด ${after + 1} (${vm.categories[after].titleTh}) เพื่อเปิด",
                    onClick = {
                        if (open) {
                            onOpenMode(mode.id)
                        } else {
                            scope.launch { snackbar.showSnackbar("ชนะบอสหมวด ${after + 1} ก่อน ถึงจะเปิดโหมดนี้ได้นะ 🔒") }
                        }
                    },
                )
            }
            if (BuildConfig.DEBUG) {
                item {
                    TextButton(onClick = vm::toggleDevUnlock, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (progress.devUnlockAll) "🛠 ปิดโหมดปลดล็อกทุกด่าน (debug)"
                            else "🛠 ปลดล็อกทุกด่านสำหรับทดสอบ (debug)"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(trophies: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🐼", fontSize = 48.sp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    "วันนี้ฝึกหมวดไหนดี?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    "ชนะบอสเพื่อเก็บถ้วยให้ครบทุกหมวด",
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text("🏆 $trophies/$total", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Nodes zig-zag across the screen like a board-game path. */
@Composable
private fun CategoryNode(
    category: Category,
    index: Int,
    unlocked: Boolean,
    stars: Int,
    trophy: Boolean,
    onClick: () -> Unit,
) {
    val alignment = when (index % 4) {
        0 -> Alignment.Center
        1 -> Alignment.CenterEnd
        2 -> Alignment.Center
        else -> Alignment.CenterStart
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .padding(8.dp)
                .alpha(if (unlocked) 1f else 0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .shadow(4.dp, CircleShape)
                    .background(if (unlocked) Cream else Locked, CircleShape)
                    .border(2.dp, if (unlocked) Gold else Locked, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unlocked) category.emoji else "🔒", fontSize = 40.sp)
                if (trophy) {
                    Text("🏆", fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Text(
                "${index + 1}. ${category.titleTh}",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            PinyinText("${category.titleZh} · ⭐ $stars/12", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SpecialModeCard(mode: SpecialMode, open: Boolean, status: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().alpha(if (open) 1f else 0.7f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mode.emoji, fontSize = 36.sp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    (if (mode.kind == ModeKind.Detective) "นักสืบ: " else "อาชีพในฝัน: ") + mode.titleTh,
                    fontWeight = FontWeight.Bold,
                )
                PinyinText(mode.titleZh, pinyin = mode.titlePinyin, style = MaterialTheme.typography.bodySmall)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (open) "▶" else "🔒", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
