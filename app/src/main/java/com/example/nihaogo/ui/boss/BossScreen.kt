package com.example.nihaogo.ui.boss

import android.app.Activity
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.common.shake
import kotlinx.coroutines.Job
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.nihaogo.R
import com.example.nihaogo.ui.common.ProgressBar
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.ui.common.PaperCard
import com.example.nihaogo.AppContainer
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.data.ai.BossLine
import com.example.nihaogo.data.content.Boss
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.Line
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.content.Word
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.Mission
import com.example.nihaogo.data.user.SpecialRules
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.monetization.RewardedAds
import com.example.nihaogo.speech.Heard
import com.example.nihaogo.ui.common.AlternativeCard
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameOutlinedButton
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.MicButton
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.SpeakButton
import com.example.nihaogo.ui.common.StepResult
import com.example.nihaogo.ui.common.StepResultPanel
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.recordScene
import com.example.nihaogo.ui.common.recordStep
import com.example.nihaogo.ui.common.rememberMicPermission
import com.example.nihaogo.ui.theme.Correct
import com.example.nihaogo.ui.theme.Wrong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatItem {
    data class BossSays(val line: BossLine) : ChatItem
    data class PlayerSays(val text: String) : ChatItem
    data class Feedback(val correct: Boolean, val text: String) : ChatItem
    /** Special modes: another way to say the learner's answer. */
    data class Alternative(val line: Line) : ChatItem
}

/** How the qilin boss looks: waiting, pleased with a right answer, or roaring at a wrong one. */
enum class BossMood(@DrawableRes val drawable: Int) {
    Idle(R.drawable.boss_qilin_idle),
    Win(R.drawable.boss_qilin_win),
    Roar(R.drawable.boss_qilin_roar),
}

data class BossState(
    val messages: List<ChatItem> = emptyList(),
    val current: BossLine? = null,
    val answered: Int = 0,
    val correct: Int = 0,
    val input: String = "",
    val busy: Boolean = true,
    val error: String? = null,
    val hintShown: Boolean = false,
    val notice: String? = null,
    val result: StepResult? = null,
    /** Replaying after a loss: the player confirms the free retry or the fee before the boss starts. */
    val retryGate: Boolean = false,
    val mood: BossMood = BossMood.Idle,
)

/** Whose boss conversation this is, which decides where the result is saved. */
sealed interface BossTarget {
    /** Identifies this boss in [UserProgress.bossRetry]. */
    val key: String

    data class CategoryBoss(val categoryId: String) : BossTarget {
        override val key get() = categoryId
    }

    /** The customer at the end of a career ([Scene.Roleplay]). */
    data class SpecialScene(val modeId: String, val sceneIndex: Int) : BossTarget {
        override val key get() = SpecialRules.sceneKey(modeId, sceneIndex)
    }
}

class BossViewModel(private val container: AppContainer, private val target: BossTarget) : ViewModel() {
    private val category: Category? = (target as? BossTarget.CategoryBoss)?.let { container.content.category(it.categoryId) }
    private val mode: SpecialMode? = (target as? BossTarget.SpecialScene)?.let { container.content.mode(it.modeId) }

    val boss: Boss = category?.boss
        ?: (mode!!.scenes[(target as BossTarget.SpecialScene).sceneIndex] as Scene.Roleplay).boss
    private val knownWords: List<Word> = category?.words ?: mode!!.allWords

    val title: String = if (category != null) "บอส: ${boss.name}" else "${mode!!.emoji} ${boss.name}"
    /** Chapter bosses are played by the qilin; a career's customer keeps its emoji. */
    val qilin: Boolean = category != null
    val passedMessage: String = when {
        category != null -> "ชนะบอสแล้ว! ได้ถ้วยรางวัล 🏆"
        mode!!.ranks.isNotEmpty() -> "เลื่อนตำแหน่งเป็น${mode.ranks.last()}แล้ว! 🎖️"
        else -> "ผ่านฉากนี้แล้ว!"
    }
    val failedDoneLabel: String = if (category != null) "กลับไปหน้าหมวด" else "กลับ"

    val micAvailable = container.speechInput.isAvailable
    val listening = container.speechInput.listening
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())

    private var engine = container.bossEngine(boss, knownWords)
    val usesAi: Boolean get() = engine.usesAi

    private val _state = MutableStateFlow(BossState())
    val state: StateFlow<BossState> = _state.asStateFlow()

    /** Re-runs the call that failed. */
    private var retry: (() -> Unit)? = null
    private var calmDown: Job? = null

    init {
        open()
    }

    /** Starts right away unless this is a replay after a loss, which goes through the retry gate. */
    private fun open() {
        viewModelScope.launch {
            val entry = GameRules.bossEntry(container.progress.progress.first(), target.key, GameRules.today())
            if (entry == GameRules.BossEntry.Free) start() else _state.value = BossState(busy = false, retryGate = true)
        }
    }

    fun entry(progress: UserProgress): GameRules.BossEntry =
        GameRules.bossEntry(progress, target.key, GameRules.today())

    fun adsLeft(progress: UserProgress): Int = GameRules.adsLeft(progress, GameRules.today())

    fun payAndStart() {
        viewModelScope.launch {
            var paid = false
            container.progress.update { p -> GameRules.payForBoss(p, target.key, GameRules.today())?.also { paid = true } ?: p }
            if (paid) {
                _state.value = BossState()
                start()
            } else {
                container.sfx.wrong()
                _state.update { it.copy(notice = "เหรียญไม่พอ ต้องใช้ ${GameRules.BOSS_RETRY_COST} 🪙") }
            }
        }
    }

    fun watchAd(activity: Activity) {
        viewModelScope.launch {
            val outcome = container.rewardedAds.watchForCoins(activity)
            if (outcome == RewardedAds.Outcome.Rewarded) container.sfx.play(Sfx.Sound.Coin)
            _state.update { it.copy(notice = outcome.messageTh) }
        }
    }

    private fun start() = launchCall(onRetry = ::start) {
        // A career customer also works in words from the categories the learner has beaten.
        if (mode != null) engine = container.bossEngine(boss, knownWords, container.reviewWords(mode), flexible = true)
        val line = engine.start()
        _state.update { it.copy(messages = listOf(ChatItem.BossSays(line)), current = line) }
        container.speaker.speak(line.hanzi)
    }

    fun onInput(text: String) = _state.update { it.copy(input = text) }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.busy || _state.value.current == null) return
        _state.update {
            it.copy(messages = it.messages + ChatItem.PlayerSays(text), input = "", hintShown = false, notice = null)
        }
        submit(text)
    }

    private fun submit(text: String): Unit = launchCall(onRetry = { submit(text) }) {
        if (_state.value.answered == 0) container.progress.update { GameRules.markBossAttempt(it, target.key) }
        val isFinal = _state.value.answered + 1 >= GameRules.BOSS_ROUNDS
        val judgement = engine.answer(text, isFinal)
        container.sfx.answer(judgement.correct)
        react(judgement.correct)
        _state.update {
            val added = buildList {
                if (judgement.feedbackTh.isNotBlank()) add(ChatItem.Feedback(judgement.correct, judgement.feedbackTh))
                judgement.alternative?.let { add(ChatItem.Alternative(it)) }
                judgement.next?.let { next -> add(ChatItem.BossSays(next)) }
            }
            it.copy(
                messages = it.messages + added,
                current = judgement.next ?: it.current,
                answered = it.answered + 1,
                correct = it.correct + if (judgement.correct) 1 else 0,
            )
        }
        judgement.next?.let { container.speaker.speak(it.hanzi) }
        if (isFinal) finish()
    }

    /** The qilin cheers or roars at the answer for a moment, then waits again. */
    private fun react(correct: Boolean) {
        _state.update { it.copy(mood = if (correct) BossMood.Win else BossMood.Roar) }
        calmDown?.cancel()
        calmDown = viewModelScope.launch {
            delay(2_500)
            _state.update { it.copy(mood = BossMood.Idle) }
        }
    }

    private suspend fun finish() {
        delay(2_000) // let the farewell line play before the result screen
        val correct = _state.value.correct
        val stars = GameRules.bossStars(correct)
        val outcome = when (target) {
            is BossTarget.CategoryBoss -> container.progress.recordStep(target.categoryId, Step.Boss, stars)
            is BossTarget.SpecialScene -> container.progress.recordScene(mode!!, target.sceneIndex, stars)
        }
        if (stars > 0) container.progress.update { GameRules.markBossWon(it, target.key) }
        if (stars > 0 && target is BossTarget.CategoryBoss) container.missions.add(Mission.Lesson)
        _state.update { it.copy(result = StepResult(correct * 100 / GameRules.BOSS_ROUNDS, stars, outcome.coinsEarned)) }
    }

    fun record() {
        viewModelScope.launch {
            when (val heard = container.speechInput.listen()) {
                is Heard.Failed -> _state.update { it.copy(notice = heard.messageTh) }
                is Heard.Text -> _state.update { it.copy(input = heard.candidates.first(), notice = null) }
            }
        }
    }

    fun showHint() {
        if (_state.value.hintShown || _state.value.current?.hintHanzi.isNullOrBlank()) return
        viewModelScope.launch {
            var paid = false
            container.progress.update { p -> GameRules.spend(p, GameRules.HINT_COST)?.also { paid = true } ?: p }
            if (paid) container.sfx.play(Sfx.Sound.Coin) else container.sfx.wrong()
            _state.update {
                if (paid) it.copy(hintShown = true, notice = null)
                else it.copy(notice = "เหรียญไม่พอ ต้องใช้ ${GameRules.HINT_COST} 🪙 (เก็บเหรียญได้จากการผ่านด่าน)")
            }
        }
    }

    fun speak(line: BossLine) = container.speaker.speak(line.hanzi)

    fun speakText(hanzi: String) = container.speaker.speak(hanzi)

    fun speakHint() {
        _state.value.current?.hintHanzi?.let { container.speaker.speak(it) }
    }

    fun retryFailed() {
        retry?.invoke()
    }

    fun restart() {
        engine = container.bossEngine(boss, knownWords)
        _state.value = BossState()
        open()
    }

    private fun launchCall(onRetry: () -> Unit, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                retry = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("BossViewModel", "Boss call failed", e)
                retry = onRetry
                _state.update { it.copy(error = "บอสติดต่อไม่ได้ (${e.message?.take(120)})") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

@Composable
fun BossScreen(target: BossTarget, onBack: () -> Unit) {
    val vm = appViewModel { BossViewModel(it, target) }
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val boss = vm.boss

    InkScaffold(
        topBar = { GameTopBar(vm.title, progress.coins, onBack) },
        bottomBar = { if (state.result == null && !state.retryGate) InputBar(vm, state) },
    ) { padding ->
        if (state.retryGate) {
            RetryGatePanel(vm, state, progress, onBack, Modifier.padding(padding))
            return@InkScaffold
        }
        val result = state.result
        if (result != null) {
            StepResultPanel(
                result = result,
                passedMessage = vm.passedMessage,
                detail = "ตอบถูก ${state.correct}/${GameRules.BOSS_ROUNDS} (ต้องถูก ${GameRules.BOSS_PASS_CORRECT} ข้อขึ้นไป)",
                onRetry = vm::restart,
                onDone = onBack,
                modifier = Modifier.padding(padding),
                failedDoneLabel = vm.failedDoneLabel,
                bigWin = true,
            )
            return@InkScaffold
        }

        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.qilin) QilinStage(vm, state)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { if (vm.qilin) ScenarioNote(vm) else BossHeader(vm, state) }
                items(state.messages) { item ->
                    Box(Modifier.animateItem()) {
                        when (item) {
                            is ChatItem.BossSays -> BossBubble(
                                emoji = boss.emoji.takeUnless { vm.qilin },
                                line = item.line,
                                onSpeak = { vm.speak(item.line) },
                            )
                            is ChatItem.PlayerSays -> PlayerBubble(item.text)
                            is ChatItem.Feedback -> FeedbackLine(item)
                            is ChatItem.Alternative -> AlternativeCard(item.line, onSpeak = { vm.speakText(item.line.hanzi) })
                        }
                    }
                }
                if (state.busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            PinyinText("  ${boss.name} กำลังคิด…")
                        }
                    }
                }
                state.error?.let { error ->
                    item {
                        PaperCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = vm::retryFailed) { Text("ลองใหม่") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shown before a replay after a loss: today's free retry, or the coin fee (with an ad for coins when short). */
@Composable
private fun RetryGatePanel(
    vm: BossViewModel,
    state: BossState,
    progress: UserProgress,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val entry = vm.entry(progress)
    val cost = if (entry == GameRules.BossEntry.PaidRetry) GameRules.BOSS_RETRY_COST else 0
    val adsLeft = vm.adsLeft(progress)
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Image(painterResource(R.drawable.mascot_try), contentDescription = null, modifier = Modifier.size(140.dp))
                if (vm.qilin) {
                    Image(painterResource(BossMood.Roar.drawable), contentDescription = null, modifier = Modifier.size(120.dp))
                } else {
                    BossAvatar(vm.boss.emoji, 72.dp)
                }
            }
            Text("ท้าบอสอีกครั้ง", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                when (entry) {
                    GameRules.BossEntry.PaidRetry ->
                        "ใช้สิทธิ์เล่นซ้ำฟรีของวันนี้ไปแล้ว ครั้งนี้ใช้ ${GameRules.BOSS_RETRY_COST} 🪙"
                    else -> "เล่นซ้ำหลังแพ้ได้ฟรีวันละ ${GameRules.FREE_BOSS_RETRIES_PER_DAY} ครั้ง " +
                        "จากนั้นครั้งละ ${GameRules.BOSS_RETRY_COST} 🪙"
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            GameButton(
                onClick = vm::payAndStart,
                enabled = progress.coins >= cost,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (cost > 0) "จ่าย $cost 🪙 แล้วเริ่มเล่น" else "เล่นซ้ำฟรี")
            }
            if (progress.coins < cost && adsLeft > 0 && activity != null) {
                GameOutlinedButton(onClick = { vm.watchAd(activity) }, modifier = Modifier.fillMaxWidth()) {
                    Text("📺 ดูโฆษณารับ +${GameRules.AD_REWARD} 🪙 (วันนี้เหลือ $adsLeft ครั้ง)")
                }
            }
            state.notice?.let { Text(it, textAlign = TextAlign.Center) }
            TextButton(onClick = onBack) { Text(vm.failedDoneLabel) }
        }
    }
}

@Composable
private fun BossHeader(vm: BossViewModel, state: BossState) {
    val boss = vm.boss
    PaperCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(2.dp, Gold),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BossAvatar(boss.emoji, 64.dp)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("บอสประจำบท", style = MaterialTheme.typography.labelMedium, color = Red, fontWeight = FontWeight.Bold)
                    PinyinText(boss.name, pinyin = boss.namePinyin, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(boss.roleTh, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
            Text(boss.scenarioTh, style = MaterialTheme.typography.bodyMedium)
            val round = minOf(state.answered + 1, GameRules.BOSS_ROUNDS)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(state.answered.toFloat() / GameRules.BOSS_ROUNDS, Modifier.weight(1f))
                Text(
                    "  รอบ $round/${GameRules.BOSS_ROUNDS} · ถูก ${state.correct}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                if (vm.usesAi) "🤖 คุยกับ AI (Gemini)" else "📜 บทสำรอง",
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft,
            )
        }
    }
}

/**
 * The qilin boss standing over the chat, breathing and reacting to each answer,
 * with a small frame beside it for the boss's name and the score.
 * Shrinks while the keyboard is up so the chat keeps its room.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QilinStage(vm: BossViewModel, state: BossState) {
    val compact = WindowInsets.isImeVisible
    val height by animateDpAsState(if (compact) 100.dp else 180.dp, label = "stage")
    val breath = rememberInfiniteTransition(label = "breath")
    val bob by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(0.95f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.45f), Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .background(Color.Black.copy(alpha = 0.12f), CircleShape),
            )
            Crossfade(state.mood, label = "qilin") { mood ->
                Image(
                    painterResource(mood.drawable),
                    contentDescription = vm.boss.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .pop(mood == BossMood.Win, amount = 0.12f)
                        .shake(mood == BossMood.Roar)
                        .graphicsLayer {
                            translationY = -bob * 4.dp.toPx()
                            scaleY = 1f + bob * 0.015f
                        },
                )
            }
        }
        BossFrame(vm, state, compact, Modifier.padding(start = 8.dp).weight(1f))
    }
}

/** The small frame by the qilin: who it plays in this chapter and how the rounds are going. */
@Composable
private fun BossFrame(vm: BossViewModel, state: BossState, compact: Boolean, modifier: Modifier = Modifier) {
    val boss = vm.boss
    val round = minOf(state.answered + 1, GameRules.BOSS_ROUNDS)
    PaperCard(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.5.dp, Gold),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        ) {
            if (!compact) {
                Text("บอสประจำบท", style = MaterialTheme.typography.labelSmall, color = Red, fontWeight = FontWeight.Bold)
            }
            PinyinText(boss.name, pinyin = boss.namePinyin, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (!compact) {
                Text(
                    boss.roleTh,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProgressBar(state.answered.toFloat() / GameRules.BOSS_ROUNDS, Modifier.fillMaxWidth(), height = 8.dp)
            Text("รอบ $round/${GameRules.BOSS_ROUNDS} · ถูก ${state.correct}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The chapter's situation, opening the chat as a short note rather than a card. */
@Composable
private fun ScenarioNote(vm: BossViewModel) {
    Surface(
        color = Paper.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(vm.boss.scenarioTh, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (vm.usesAi) "🤖 คุยกับ AI (Gemini)" else "📜 บทสำรอง",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
            )
        }
    }
}

/** The boss's emoji in a round cream frame edged in gold. */
@Composable
private fun BossAvatar(emoji: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(Cream)
            .border(2.dp, Gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.55f).sp) }
}

@Composable
private fun BossBubble(emoji: String?, line: BossLine, onSpeak: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        if (emoji != null) BossAvatar(emoji, 40.dp)
        PaperCard(
            Modifier.padding(start = if (emoji != null) 8.dp else 0.dp).widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    PinyinText(line.hanzi, pinyin = line.pinyin, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(line.thai, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                SpeakButton(onClick = onSpeak, modifier = Modifier.padding(start = 8.dp).size(40.dp))
            }
        }
    }
}

@Composable
private fun PlayerBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            shadowElevation = 3.dp,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            PinyinText(
                text,
                modifier = Modifier.padding(12.dp),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                pinyinColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun FeedbackLine(item: ChatItem.Feedback) {
    PinyinText(
        (if (item.correct) "✅ " else "❌ ") + item.text,
        color = if (item.correct) Correct else Wrong,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().appear(),
    )
}

@Composable
private fun InputBar(vm: BossViewModel, state: BossState) {
    val listening by vm.listening.collectAsStateWithLifecycle()
    val withMic = rememberMicPermission()
    val current = state.current

    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Surface(
        color = Paper,
        shape = sheet,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Hairline),
        modifier = Modifier.navigationBarsPadding().imePadding(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hintShown && current != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear()) {
                    Column(Modifier.weight(1f)) {
                        Text("💡 ลองตอบว่า", style = MaterialTheme.typography.labelMedium)
                        PinyinText(current.hintHanzi, pinyin = current.hintPinyin, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    SpeakButton(onClick = vm::speakHint)
                }
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Wrong) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.micAvailable) {
                    MicButton(listening, onClick = { withMic(vm::record) }, enabled = !state.busy)
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("พูดหรือพิมพ์ภาษาจีน") },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Red,
                        unfocusedBorderColor = Hairline,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
                FilledIconButton(
                    onClick = vm::send,
                    enabled = state.input.isNotBlank() && !state.busy,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                ) {
                    Text("➤", fontSize = 20.sp)
                }
            }
            if (!state.hintShown && current != null && current.hintHanzi.isNotBlank()) {
                GameOutlinedButton(onClick = vm::showHint, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("💡 ขอคำใบ้ (${GameRules.HINT_COST} 🪙)")
                }
            }
        }
    }
}
