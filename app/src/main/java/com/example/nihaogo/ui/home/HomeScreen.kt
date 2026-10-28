package com.example.nihaogo.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.BuildConfig
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.ModeKind
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.SpecialRules
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.common.pressScale
import com.example.nihaogo.ui.common.rememberSfx
import com.example.nihaogo.ui.common.shake
import com.example.nihaogo.ui.special.modeProgressText
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Locked
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val categories: List<Category> = container.content.categories
    val modes: List<SpecialMode> = container.content.special.modes
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())
    private val ids = categories.map { it.id }

    /** A category that opened since the map was last shown, waiting for its unlock animation. */
    private val _justUnlocked = MutableStateFlow<String?>(null)
    val justUnlocked: StateFlow<String?> = _justUnlocked.asStateFlow()

    /** The nodes drop in only the first time the map is shown. */
    var introPlayed = false

    /**
     * Run while the map is on screen, so a newly opened category is celebrated when the player
     * comes back to the map rather than behind the boss result.
     */
    suspend fun watchUnlocks() {
        container.progress.progress.collect { p ->
            val open = ids.count { GameRules.isCategoryUnlocked(ids, it, p) }
            val seen = container.settings.seenUnlockedCategories
            // Only celebrate normal progress (one more category). Bigger jumps come from signing in
            // or the debug unlock, and simply update what has been seen.
            if (seen >= 0 && open == seen + 1) _justUnlocked.value = ids[seen]
            container.settings.seenUnlockedCategories = open
        }
    }

    fun celebrated() {
        _justUnlocked.value = null
    }

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
    val justUnlocked by vm.justUnlocked.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val intro = !vm.introPlayed
    LaunchedEffect(vm) {
        vm.introPlayed = true
        vm.watchUnlocks()
    }
    // Bring the newly opened category into view before it unlocks.
    LaunchedEffect(justUnlocked) {
        val id = justUnlocked ?: return@LaunchedEffect
        listState.animateScrollToItem(1 + ids.indexOf(id), scrollOffset = -300)
    }
    // The next category to play gently pulses.
    val nextUp = vm.categories.firstOrNull {
        GameRules.isCategoryUnlocked(ids, it.id, progress) && !progress.bossWon(it.id)
    }?.id

    InkScaffold(
        topBar = {
            GameTopBar(title = "NiHaoGo 你好", coins = progress.coins, onBack = null, onProfile = onOpenProfile)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { WelcomeCard(progress.trophies, vm.categories.size, Modifier.appear(enabled = intro)) }
            itemsIndexed(vm.categories, key = { _, c -> c.id }) { index, category ->
                val unlocked = GameRules.isCategoryUnlocked(ids, category.id, progress)
                CategoryNode(
                    modifier = Modifier.appear(delayMillis = 80 + index * 70, enabled = intro),
                    category = category,
                    index = index,
                    unlocked = unlocked,
                    nextUp = category.id == nextUp,
                    celebrate = category.id == justUnlocked,
                    onCelebrated = vm::celebrated,
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
                    modifier = Modifier.appear(delayMillis = 700, enabled = intro),
                    mode = mode,
                    open = open,
                    status = when {
                        open -> modeProgressText(mode, progress)
                        mode.comingSoon -> "🔒 เร็ว ๆ นี้ · ยังไม่เปิดให้เล่น"
                        else -> "🔒 ชนะบอสหมวด ${after + 1} (${vm.categories[after].titleTh}) เพื่อเปิด"
                    },
                    onClick = {
                        if (open) {
                            onOpenMode(mode.id)
                        } else if (mode.comingSoon) {
                            scope.launch { snackbar.showSnackbar("โหมดนี้กำลังจะมาเร็ว ๆ นี้ รอติดตามนะ 🔒") }
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
private fun WelcomeCard(trophies: Int, total: Int, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
    modifier: Modifier,
    category: Category,
    index: Int,
    unlocked: Boolean,
    nextUp: Boolean,
    /** Just opened: shown locked, the padlock rattles, then it pops open with a sound. */
    celebrate: Boolean,
    onCelebrated: () -> Unit,
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
    val sfx = rememberSfx()
    var rattling by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(celebrate) {
        if (!celebrate) return@LaunchedEffect
        revealed = false
        delay(700) // let the list scroll here first
        rattling = true
        delay(450)
        rattling = false
        revealed = true
        sfx.play(Sfx.Sound.Unlock)
        delay(800)
        onCelebrated()
    }
    val open = unlocked && (!celebrate || revealed)

    val pulse = rememberInfiniteTransition(label = "nextUp")
    val beat by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beat",
    )
    val glow = if (nextUp && open) beat else 0f
    val press = remember { MutableInteractionSource() }

    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .pressScale(press)
                .clip(MaterialTheme.shapes.large)
                .clickable(interactionSource = press, indication = LocalIndication.current) {
                    sfx.tap()
                    onClick()
                }
                .padding(8.dp)
                .alpha(if (open) 1f else 0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val s = 1f + glow * 0.06f
                        scaleX = s
                        scaleY = s
                    }
                    .shake(rattling)
                    .pop(revealed, amount = 0.25f)
                    .shadow((4 + glow * 8).dp, CircleShape, ambientColor = Gold, spotColor = Gold)
                    .background(if (open) Cream else Locked, CircleShape)
                    .border((2 + glow * 2).dp, if (open) Gold else Locked, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (open) category.emoji else "🔒", fontSize = 40.sp)
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
private fun SpecialModeCard(modifier: Modifier, mode: SpecialMode, open: Boolean, status: String, onClick: () -> Unit) {
    GameCard(onClick = onClick, modifier = modifier.fillMaxWidth().alpha(if (open) 1f else 0.7f)) {
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
