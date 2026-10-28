package com.example.nihaogo.ui.special

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.R
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.ModeKind
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.user.SpecialRules
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.ArrowBubble
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.MascotSays
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Red
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SpecialListViewModel(container: AppContainer) : ViewModel() {
    val categories: List<Category> = container.content.categories
    val modes: List<SpecialMode> = container.content.special.modes
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())
}

/**
 * Every special mode, shown in the home screen's special tab; a locked one says what opens it,
 * and tapping it hands the reason to [onLocked].
 */
@Composable
fun SpecialListContent(padding: PaddingValues, onOpenMode: (String) -> Unit, onLocked: (String) -> Unit) {
    val vm = appViewModel { SpecialListViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MascotSays(R.drawable.mascot_globe, "เล่นเป็นนักสืบหรือลองอาชีพในฝัน ด้วยภาษาจีนที่เรียนมา", size = 88.dp)
        }
        items(vm.modes, key = { it.id }) { mode ->
            val open = SpecialRules.isModeOpen(mode, progress)
            val after = vm.categories.indexOfFirst { it.id == mode.unlockAfter }
            SpecialModeCard(
                modifier = Modifier.appear(),
                mode = mode,
                open = open,
                status = when {
                    open -> modeProgressText(mode, progress)
                    mode.comingSoon -> "🔒 เร็ว ๆ นี้ · ยังไม่เปิดให้เล่น"
                    else -> "🔒 ชนะบอสหมวด ${after + 1} (${vm.categories[after].titleTh}) เพื่อเปิด"
                },
                onClick = {
                    when {
                        open -> onOpenMode(mode.id)
                        mode.comingSoon -> onLocked("โหมดนี้กำลังจะมาเร็ว ๆ นี้ รอติดตามนะ 🔒")
                        else -> onLocked("ชนะบอสหมวด ${after + 1} ก่อน ถึงจะเปิดโหมดนี้ได้นะ 🔒")
                    }
                },
            )
        }
    }
}

@Composable
private fun SpecialModeCard(modifier: Modifier, mode: SpecialMode, open: Boolean, status: String, onClick: () -> Unit) {
    GameCard(onClick = onClick, modifier = modifier.fillMaxWidth().alpha(if (open) 1f else 0.7f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Cream)
                    .border(1.dp, Gold.copy(alpha = 0.6f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) { Text(mode.emoji, fontSize = 32.sp) }
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(
                    if (mode.kind == ModeKind.Detective) "นักสืบ" else "อาชีพในฝัน",
                    style = MaterialTheme.typography.labelMedium,
                    color = Red,
                    fontWeight = FontWeight.Bold,
                )
                Text(mode.titleTh, fontWeight = FontWeight.Bold)
                PinyinText(mode.titleZh, pinyin = mode.titlePinyin, style = MaterialTheme.typography.bodySmall)
                Text(status, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            when {
                open -> ArrowBubble()
                // Not out yet: the qilin naps on its books until the mode arrives.
                mode.comingSoon -> Image(painterResource(R.drawable.mascot_sleep), contentDescription = null, modifier = Modifier.size(56.dp))
                else -> Text("🔒", fontSize = 22.sp)
            }
        }
    }
}
