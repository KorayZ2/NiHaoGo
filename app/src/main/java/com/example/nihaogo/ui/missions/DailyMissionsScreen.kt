package com.example.nihaogo.ui.missions

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.R
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.data.user.DailyMissions
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.Mission
import com.example.nihaogo.data.user.MissionDay
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.ConfettiBurst
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.MascotSays
import com.example.nihaogo.ui.common.PaperCard
import com.example.nihaogo.ui.common.ProgressBar
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.common.rememberSfx
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Locked
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DailyMissionsViewModel(private val container: AppContainer) : ViewModel() {
    val missions: StateFlow<MissionDay> = container.missions.today
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())

    fun refreshDay() {
        container.missions.current()
    }

    /** The lesson to play for the lesson mission: the first opened one whose boss isn't beaten. */
    fun nextLesson(progress: UserProgress): String {
        val categories = container.content.categories
        val ids = categories.map { it.id }
        return categories.firstOrNull { GameRules.isCategoryUnlocked(ids, it.id, progress) && !progress.bossWon(it.id) }?.id
            ?: ids.last()
    }

    suspend fun claim(mission: Mission): Int = container.missions.claim(mission)

    suspend fun openChest(): Int = container.missions.openChest()
}

/** Today's four missions; each pays coins when claimed, and claiming all four opens the chest. */
@Composable
fun DailyMissionsScreen(
    onBack: () -> Unit,
    onOpenLesson: (categoryId: String) -> Unit,
    onOpenListenSpeak: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    val vm = appViewModel { DailyMissionsViewModel(it) }
    val day by vm.missions.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sfx = rememberSfx()
    var confetti by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.refreshDay() }

    val goDo: (Mission) -> Unit = { mission ->
        when (mission) {
            Mission.Lesson -> onOpenLesson(vm.nextLesson(progress))
            Mission.Words, Mission.ListenSpeak -> onOpenListenSpeak()
            Mission.Exercise -> onOpenExercise()
        }
    }
    val claim: (Mission) -> Unit = { mission ->
        scope.launch {
            val coins = vm.claim(mission)
            if (coins > 0) {
                sfx.play(Sfx.Sound.Coin)
                snackbar.showSnackbar("ได้รับ $coins 🪙")
            }
        }
    }

    InkScaffold(
        topBar = { GameTopBar(title = "ภารกิจรายวัน", coins = progress.coins, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MascotSays(
                        R.drawable.mascot_treasure,
                        "ทำภารกิจให้ครบทุกข้อ แล้วมาเปิดหีบสมบัติกัน!",
                        detail = "ภารกิจจะเริ่มใหม่ทุกวัน",
                        size = 84.dp,
                    )
                }
                itemsIndexed(Mission.entries) { i, mission ->
                    MissionRow(
                        mission = mission,
                        day = day,
                        icon = iconFor(mission, day),
                        onGo = { goDo(mission) },
                        onClaim = { claim(mission) },
                        modifier = Modifier.appear(delayMillis = 60 * i),
                    )
                }
                item {
                    ChestCard(
                        day = day,
                        onOpen = {
                            scope.launch {
                                val coins = vm.openChest()
                                if (coins > 0) {
                                    sfx.play(Sfx.Sound.Win)
                                    confetti = true
                                    snackbar.showSnackbar("เปิดหีบสมบัติ! ได้รับ $coins 🪙")
                                }
                            }
                        },
                        modifier = Modifier.appear(delayMillis = 300),
                    )
                }
            }
            if (confetti) ConfettiBurst(Modifier.matchParentSize())
        }
    }
}

@DrawableRes
private fun iconFor(mission: Mission, day: MissionDay): Int = when (mission) {
    Mission.Lesson -> R.drawable.ic_mission_lesson
    Mission.Words -> R.drawable.ic_mission_words
    Mission.ListenSpeak -> R.drawable.ic_mission_listen
    // The last mission waits behind a padlock until the exercise is done, as in the mockup.
    Mission.Exercise -> if (day.done(mission)) R.drawable.ic_menu_exercise else R.drawable.ic_mission_lock
}

@Composable
private fun MissionRow(
    mission: Mission,
    day: MissionDay,
    @DrawableRes icon: Int,
    onGo: () -> Unit,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = day.done(mission)
    val claimed = mission in day.claimed
    GameCard(
        onClick = if (day.claimable(mission)) onClaim else if (!done) onGo else ({}),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (claimed) lerp(Paper, Correct, 0.06f) else Paper),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(48.dp))
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(mission.titleTh, fontWeight = FontWeight.Bold)
                Text("+${mission.reward} 🪙", color = Gold, fontWeight = FontWeight.Bold)
                if (!done && mission.target > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        ProgressBar(day.count(mission).toFloat() / mission.target, Modifier.weight(1f), height = 6.dp)
                        Text(
                            " ${day.count(mission)}/${mission.target}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft,
                        )
                    }
                }
            }
            when {
                claimed -> CheckCircle(Correct)
                done -> Button(
                    onClick = onClaim,
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.heightIn(min = 36.dp).pop(true),
                ) { Text("รับ", fontWeight = FontWeight.Bold) }
                else -> CheckCircle(null)
            }
        }
    }
}

/** Green tick when [color] is given, otherwise an empty grey circle still to be filled. */
@Composable
private fun CheckCircle(color: Color?) {
    Box(
        Modifier
            .size(30.dp)
            .background(color ?: Locked.copy(alpha = 0.25f), CircleShape)
            .border(1.dp, color ?: Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (color != null) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun ChestCard(day: MissionDay, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val total = Mission.entries.size
    val claimed = day.claimed.size
    PaperCard(
        modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, Gold),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        day.chestOpened -> "เปิดหีบแล้ว พรุ่งนี้มาใหม่นะ"
                        else -> "รับรางวัลเมื่อทำครบ"
                    },
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(claimed.toFloat() / total, Modifier.weight(1f), height = 12.dp)
                    Text(" $claimed/$total", style = MaterialTheme.typography.labelLarge, color = InkSoft)
                }
                if (day.chestReady) {
                    GameButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Text("เปิดหีบ +${DailyMissions.CHEST_REWARD} 🪙")
                    }
                } else if (!day.chestOpened) {
                    Text("ในหีบมี ${DailyMissions.CHEST_REWARD} 🪙", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
            Image(
                painterResource(R.drawable.chest),
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(96.dp).pop(day.chestReady, amount = 0.12f),
            )
        }
    }
}
