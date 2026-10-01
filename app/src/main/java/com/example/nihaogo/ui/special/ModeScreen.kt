package com.example.nihaogo.ui.special

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.nihaogo.data.content.ModeKind
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.clues
import com.example.nihaogo.data.user.SpecialRules
import com.example.nihaogo.data.user.SpecialRules.SceneState
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.StarsRow
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.theme.Correct
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModeViewModel(private val container: AppContainer, modeId: String) : ViewModel() {
    val mode: SpecialMode = container.content.mode(modeId)
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun purchase(index: Int) {
        viewModelScope.launch {
            var paid = false
            container.progress.update { p -> SpecialRules.purchase(p, mode, index)?.also { paid = true } ?: p }
            _messages.send(
                if (paid) "ปลดล็อกฉากที่ ${index + 1} แล้ว! 🔓"
                else "เหรียญไม่พอ ต้องใช้ ${SpecialRules.SCENE_COST} 🪙 (เก็บเหรียญได้จากการผ่านด่าน)"
            )
        }
    }
}

@Composable
fun ModeScreen(modeId: String, onBack: () -> Unit, onOpenScene: (Int) -> Unit) {
    val vm = appViewModel { ModeViewModel(it, modeId) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val mode = vm.mode

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    InkScaffold(
        topBar = { GameTopBar("${mode.emoji} ${mode.titleTh}", progress.coins, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ModeHeader(mode) }
            when (mode.kind) {
                ModeKind.Career -> item { RankLadder(mode, progress) }
                ModeKind.Detective -> {
                    item { SuspectsRow(mode) }
                    val found = mode.scenes.indices.filter { progress.sceneStars(mode.id, it) > 0 }.flatMap { mode.scenes[it].clues }
                    if (found.isNotEmpty()) {
                        item { Text("📓 สมุดเบาะแส", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        found.forEach { clue -> item { ClueCard(clue) } }
                    }
                    if (SpecialRules.isFinished(mode, progress)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Correct.copy(alpha = 0.2f))) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("📁 บทสรุปคดี", fontWeight = FontWeight.Bold)
                                    Text(mode.endingTh, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item { Text("ฉากทั้งหมด", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            itemsIndexed(mode.scenes) { index, scene ->
                SceneCard(
                    index = index,
                    scene = scene,
                    state = SpecialRules.sceneState(mode, index, progress),
                    stars = progress.sceneStars(mode.id, index),
                    onOpen = { onOpenScene(index) },
                    onBuy = { vm.purchase(index) },
                )
            }
        }
    }
}

@Composable
private fun ModeHeader(mode: SpecialMode) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mode.emoji, fontSize = 64.sp)
            PinyinText(mode.titleZh, pinyin = mode.titlePinyin, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(mode.titleTh, fontWeight = FontWeight.Bold)
            Text(mode.introTh, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RankLadder(mode: SpecialMode, progress: UserProgress) {
    val done = SpecialRules.scenesDone(mode, progress)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "🎖️ ตำแหน่งปัจจุบัน: ${SpecialRules.rank(mode, progress) ?: "ผู้สมัครใหม่"}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            mode.ranks.forEachIndexed { i, rank ->
                Text(
                    (if (i < done) "✅ " else "⬜ ") + "ผ่านฉากที่ ${i + 1} → $rank",
                    color = if (i < done) Correct else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SuspectsRow(mode: SpecialMode) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ผู้ต้องสงสัย", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mode.suspects.forEach { s ->
                Card(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.emoji, fontSize = 36.sp)
                        PinyinText(s.name, pinyin = s.namePinyin, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(s.roleTh, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

private fun kindLabel(scene: Scene): String = when (scene) {
    is Scene.Find -> "🔍 หาสิ่งของ"
    is Scene.Dialogue -> "💬 บทสนทนา"
    is Scene.Puzzle -> "🧩 ไขปริศนา"
    is Scene.Training -> "📚 อบรม"
    is Scene.Interrogate -> "🕵️ สอบปากคำ + ชี้ตัว"
    is Scene.Roleplay -> "🤖 คุยกับ AI"
}

@Composable
private fun SceneCard(index: Int, scene: Scene, state: SceneState, stars: Int, onOpen: () -> Unit, onBuy: () -> Unit) {
    val open = state == SceneState.Open
    Card(
        onClick = onOpen,
        enabled = open,
        modifier = Modifier.fillMaxWidth().alpha(if (state == SceneState.Locked) 0.5f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state == SceneState.Locked) "🔒" else scene.emoji, fontSize = 36.sp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${index + 1}. ${scene.titleTh}", fontWeight = FontWeight.Bold)
                PinyinText(scene.titleZh, pinyin = scene.titlePinyin, style = MaterialTheme.typography.bodySmall)
                Text(
                    when (state) {
                        SceneState.Locked -> "ผ่านฉากก่อนหน้าก่อน"
                        else -> "${kindLabel(scene)} · ศัพท์ใหม่ ${scene.words.size} คำ"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (state) {
                SceneState.NeedsPurchase -> Button(onClick = onBuy) { Text("ปลดล็อก ${SpecialRules.SCENE_COST} 🪙") }
                else -> StarsRow(stars)
            }
        }
    }
}

/** Short progress text for the home-screen card. */
fun modeProgressText(mode: SpecialMode, progress: UserProgress): String {
    val done = SpecialRules.scenesDone(mode, progress)
    return when {
        !SpecialRules.isModeOpen(mode, progress) -> ""
        mode.kind == ModeKind.Career -> "🎖️ ${SpecialRules.rank(mode, progress) ?: "ผู้สมัครใหม่"} · $done/${mode.scenes.size} ฉาก"
        SpecialRules.isFinished(mode, progress) -> "📁 ปิดคดีแล้ว!"
        else -> "🔍 $done/${mode.scenes.size} ฉาก"
    }
}
