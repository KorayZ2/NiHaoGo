package com.example.nihaogo.ui.home

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.example.nihaogo.AppContainer
import com.example.nihaogo.BuildConfig
import com.example.nihaogo.R
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.data.content.Category
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.Account
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.Mission
import com.example.nihaogo.data.user.MissionDay
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.ArrowBubble
import com.example.nihaogo.ui.common.CoinChip
import com.example.nihaogo.ui.common.GameCard
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.PinyinText
import com.example.nihaogo.ui.common.ProgressBar
import com.example.nihaogo.ui.common.RoundIconButton
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.CategoryThumbnail
import com.example.nihaogo.ui.common.pop
import com.example.nihaogo.ui.common.pressScale
import com.example.nihaogo.ui.common.rememberSfx
import com.example.nihaogo.ui.common.shake
import com.example.nihaogo.ui.special.SpecialListContent
import com.example.nihaogo.ui.profile.ProfileScreen
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Locked
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.RedDeep
import com.example.nihaogo.ui.theme.Wood
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val categories: List<Category> = container.content.categories
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())
    val account: StateFlow<Account?> = container.accounts.account
    val missions: StateFlow<MissionDay> = container.missions.today
    private val ids = categories.map { it.id }

    /** A category that opened since the map was last shown, waiting for its unlock animation. */
    private val _justUnlocked = MutableStateFlow<String?>(null)
    val justUnlocked: StateFlow<String?> = _justUnlocked.asStateFlow()

    /** The cards drop in only the first time the home screen is shown. */
    var introPlayed = false

    init {
        container.settings.markActiveToday()
    }

    /** Starts a new day's missions if the date changed while the app was in the background. */
    fun refreshDay() {
        container.missions.current()
    }

    /**
     * Run while the home screen is on screen, so a newly opened category is celebrated when the
     * player comes back rather than behind the boss result.
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

    /** Debug builds only; [onDone] should restart the app. TODO: remove before release. */
    fun resetAsNewUser(onDone: () -> Unit) {
        viewModelScope.launch {
            container.resetAsNewUser()
            onDone()
        }
    }
}

/** Starts the app over in a new process, from the cover, as if it had just been installed. */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    Runtime.getRuntime().exit(0)
}

/** The four tabs of the bottom bar. */
enum class HomeTab(val label: String, val icon: ImageVector) {
    Home("หน้าแรก", Icons.Filled.Home),
    Special("โหมดพิเศษ", Icons.Filled.Star),
    Map("แผนที่", Icons.Filled.LocationOn),
    Profile("โปรไฟล์", Icons.Filled.Person),
}

@Composable
fun HomeScreen(
    onOpenCategory: (String) -> Unit,
    onOpenPinyin: () -> Unit,
    onOpenWords: () -> Unit,
    onOpenListenSpeak: () -> Unit,
    onOpenExercise: () -> Unit,
    onOpenChallenge: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenMode: (String) -> Unit,
    onOpenLogin: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val missions by vm.missions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ids = remember(vm.categories) { vm.categories.map { it.id } }
    val justUnlocked by vm.justUnlocked.collectAsStateWithLifecycle()
    // Kept here so the map stays scrolled where it was while other tabs are shown.
    val mapState = rememberScrollState()
    // The lesson the qilin stands at on the map: the last one opened there, else the next to play.
    var qilinAt by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    val intro = !vm.introPlayed
    LaunchedEffect(vm) {
        vm.introPlayed = true
        vm.refreshDay()
        vm.watchUnlocks()
    }
    // Back from another tab returns to the home tab before leaving the app.
    BackHandler(enabled = tab != HomeTab.Home) { tab = HomeTab.Home }

    // The next category to play.
    val nextUp = vm.categories.firstOrNull {
        GameRules.isCategoryUnlocked(ids, it.id, progress) && !progress.bossWon(it.id)
    }
    val openCategory: (Category) -> Unit = { category ->
        if (GameRules.isCategoryUnlocked(ids, category.id, progress)) {
            onOpenCategory(category.id)
        } else {
            scope.launch { snackbar.showSnackbar("ชนะบอสของบทก่อนหน้าก่อน ถึงจะเปิดบทนี้ได้นะ 🔒") }
        }
    }
    val missionWaiting = Mission.entries.any { missions.claimable(it) } || missions.chestReady
    val bottomBar = @Composable { HomeBottomBar(tab, onSelect = { tab = it }) }

    Crossfade(tab, animationSpec = tween(220), label = "tab") { shown ->
        if (shown == HomeTab.Profile) {
            ProfileScreen(onBack = null, onOpenPinyin = onOpenPinyin, onOpenLogin = onOpenLogin, bottomBar = bottomBar)
            return@Crossfade
        }
        InkScaffold(
            topBar = {
                if (shown == HomeTab.Special) GameTopBar(title = "โหมดพิเศษ", coins = progress.coins, onBack = null)
            },
            bottomBar = bottomBar,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            when (shown) {
                HomeTab.Home -> HomeTabContent(
                    padding = padding,
                    intro = intro,
                    account = account,
                    progress = progress,
                    categories = vm.categories,
                    current = nextUp ?: vm.categories.last(),
                    allDone = nextUp == null,
                    missionWaiting = missionWaiting,
                    onContinue = { openCategory(nextUp ?: vm.categories.last()) },
                    onSelectTab = { tab = it },
                    menu = HomeMenu(
                        onWords = onOpenWords,
                        onListenSpeak = onOpenListenSpeak,
                        onExercise = onOpenExercise,
                        onChallenge = onOpenChallenge,
                        onMissions = onOpenMissions,
                        onPinyin = onOpenPinyin,
                    ),
                )
                HomeTab.Special -> SpecialListContent(
                    padding = padding,
                    onOpenMode = onOpenMode,
                    onLocked = { message -> scope.launch { snackbar.showSnackbar(message) } },
                )
                HomeTab.Map -> MapTabContent(
                    padding = padding,
                    scrollState = mapState,
                    categories = vm.categories,
                    progress = progress,
                    qilinAt = qilinAt ?: nextUp?.id ?: ids.last(),
                    justUnlocked = justUnlocked,
                    onCelebrated = vm::celebrated,
                    onOpen = { category ->
                        if (GameRules.isCategoryUnlocked(ids, category.id, progress)) qilinAt = category.id
                        openCategory(category)
                    },
                    onToggleDevUnlock = vm::toggleDevUnlock,
                    onResetAsNewUser = { vm.resetAsNewUser { restartApp(context) } },
                )
                HomeTab.Profile -> Unit
            }
        }
    }
}

/** Paper bar with rounded top corners; the chosen tab turns red. */
@Composable
private fun HomeBottomBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val sfx = rememberSfx()
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    NavigationBar(
        modifier = Modifier
            .shadow(12.dp, shape, spotColor = InkSoft.copy(alpha = 0.4f))
            .clip(shape)
            .border(1.dp, Hairline, shape),
        containerColor = Paper,
        tonalElevation = 0.dp,
    ) {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = {
                    if (tab != selected) sfx.tap()
                    onSelect(tab)
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Red,
                    selectedTextColor = Red,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = InkSoft,
                    unselectedTextColor = InkSoft,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------- Home tab

/** Where the home menu's tiles lead. */
private class HomeMenu(
    val onWords: () -> Unit,
    val onListenSpeak: () -> Unit,
    val onExercise: () -> Unit,
    val onChallenge: () -> Unit,
    val onMissions: () -> Unit,
    val onPinyin: () -> Unit,
)

@Composable
private fun HomeTabContent(
    padding: PaddingValues,
    intro: Boolean,
    account: Account?,
    progress: UserProgress,
    categories: List<Category>,
    current: Category,
    allDone: Boolean,
    missionWaiting: Boolean,
    onContinue: () -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    menu: HomeMenu,
) {
    val index = categories.indexOf(current)
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GreetingHeader(
            account = account,
            coins = progress.coins,
            trophies = progress.trophies,
            total = categories.size,
            missionWaiting = missionWaiting,
            onProfile = { onSelectTab(HomeTab.Profile) },
            onBell = menu.onMissions,
            modifier = Modifier.appear(enabled = intro),
        )
        JourneyHero(
            title = if (allDone) "เก่งมาก!\nครบทุกบทแล้ว" else "ออกเดินทาง\nเรียนภาษาจีน",
            subtitle = if (allDone) "กลับไปทบทวนได้ทุกเมื่อ" else "ต่อที่บทที่ ${index + 1}: ${current.titleTh}",
            onClick = onContinue,
            modifier = Modifier.appear(delayMillis = 80, enabled = intro),
        )
        Column(
            Modifier.appear(delayMillis = 160, enabled = intro),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuTile(R.drawable.mascot_scholar, "พินอิน", Modifier.weight(1f), onClick = menu.onPinyin)
                MenuTile(R.drawable.ic_menu_words, "คำศัพท์", Modifier.weight(1f), onClick = menu.onWords)
                MenuTile(R.drawable.ic_menu_listen, "ฟัง-พูด", Modifier.weight(1f), onClick = menu.onListenSpeak)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuTile(R.drawable.ic_menu_exercise, "แบบฝึกหัด", Modifier.weight(1f), onClick = menu.onExercise)
                MenuTile(R.drawable.ic_menu_game, "เกมท้าทาย", Modifier.weight(1f), onClick = menu.onChallenge)
                MenuTile(R.drawable.ic_menu_missions, "ภารกิจรายวัน", Modifier.weight(1f), badge = missionWaiting, onClick = menu.onMissions)
            }
        }
        Column(Modifier.appear(delayMillis = 240, enabled = intro), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelectTab(HomeTab.Map) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("เส้นทางการเรียนของฉัน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("›", fontSize = 26.sp, color = InkSoft)
            }
            LessonCard(
                category = current,
                index = index,
                stepsPassed = stepsPassed(progress, current),
                unlocked = true,
                onClick = onContinue,
            )
        }
    }
}

private fun stepsPassed(progress: UserProgress, category: Category): Int =
    Step.entries.count { progress.stars(category.id, it) > 0 }

@Composable
private fun GreetingHeader(
    account: Account?,
    coins: Int,
    trophies: Int,
    total: Int,
    missionWaiting: Boolean,
    onProfile: () -> Unit,
    onBell: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(60.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Cream)
                    .border(2.5.dp, Gold, CircleShape)
                    .clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) {
                val photo = account?.takeUnless { it.isGuest }?.photoUrl
                if (photo != null) {
                    AsyncImage(photo, contentDescription = "รูปโปรไฟล์", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Image(
                        painterResource(R.drawable.mascot_happy),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("สวัสดี 👋", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                Text(
                    account?.takeUnless { it.isGuest }?.name ?: "นักเดินทาง",
                    style = MaterialTheme.typography.headlineSmall,
                    color = RedDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CoinChip(coins)
            Box(Modifier.padding(start = 8.dp)) {
                RoundIconButton(onClick = onBell) {
                    Icon(Icons.Filled.Notifications, contentDescription = "ภารกิจรายวัน")
                }
                if (missionWaiting) RedDot(Modifier.align(Alignment.TopEnd))
            }
        }
        // Trophies stand in for the mockup's level bar: the app counts progress in beaten bosses.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Paper, border = BorderStroke(1.dp, Hairline), shadowElevation = 1.dp) {
                Text(
                    "🏆 $trophies/$total",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            ProgressBar(trophies.toFloat() / total, Modifier.weight(1f).padding(start = 10.dp), height = 12.dp)
        }
    }
}

@Composable
private fun RedDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .background(Red, CircleShape)
            .border(2.dp, Paper, CircleShape)
    )
}

/** The qilin setting off beside a wooden signboard; tapping either continues the journey. */
@Composable
private fun JourneyHero(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    val press = remember { MutableInteractionSource() }
    val sfx = rememberSfx()
    Box(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .pressScale(press, 0.97f)
            .clickable(interactionSource = press, indication = null) {
                sfx.tap()
                onClick()
            },
    ) {
        WoodSign(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.6f)
                .padding(top = 8.dp),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, lineHeight = 30.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
                ArrowBubble(Modifier.align(Alignment.End))
            }
        }
        Image(
            painterResource(R.drawable.mascot_explore),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomStart).size(190.dp).offset(x = (-6).dp),
        )
    }
}

/** A paper board in a wooden frame, hung from two pegs, like the mockup's signposts. */
@Composable
private fun WoodSign(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .shadow(6.dp, shape, spotColor = Ink.copy(alpha = 0.5f))
                .background(Wood, shape)
                .padding(5.dp)
                .background(Paper, RoundedCornerShape(6.dp))
                .border(1.dp, Wood.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
        ) { content() }
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(2) { Box(Modifier.size(12.dp).background(Wood, CircleShape).border(2.dp, Ink.copy(alpha = 0.35f), CircleShape)) }
        }
    }
}

@Composable
private fun MenuTile(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    GameCard(onClick = onClick, modifier = modifier.aspectRatio(1.08f)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
            }
            if (badge) RedDot(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** A lesson: its painted thumbnail, number and name, a star per step passed, and a go button. */
@Composable
private fun LessonCard(
    category: Category,
    index: Int,
    stepsPassed: Int,
    unlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GameCard(onClick = onClick, modifier = modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.75f)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryThumbnail(index, Modifier.size(width = 100.dp, height = 76.dp))
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text("บทที่ ${index + 1}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                PinyinText(
                    "${category.titleZh} (${category.titleTh})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                StepStars(stepsPassed, Modifier.padding(top = 2.dp))
            }
            if (unlocked) ArrowBubble() else Text("🔒", fontSize = 22.sp)
        }
    }
}

/** One gold star per step of the lesson passed. */
@Composable
private fun StepStars(passed: Int, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Row(modifier) {
        repeat(Step.entries.size) { i ->
            Text("★", fontSize = size.value.sp, color = if (i < passed) Gold else Locked.copy(alpha = 0.5f))
        }
    }
}

// ---------------------------------------------------------------- Map tab

/** The painted map's width over its height. */
private const val MapAspect = 941f / 1672f

/**
 * One stop on [R.drawable.map_world], in fractions of the picture's width and height: the blank
 * signboard painted there, the spot on its island where the qilin stands (its feet), and the
 * turns of the path from there to the next stop, so the qilin keeps to the bridges.
 */
private class MapStopSpot(val sign: Rect, val stand: Offset, val toNext: List<Offset>)

/** A point on the picture, from its pixel position. */
private fun px(x: Int, y: Int) = Offset(x / 941f, y / 1672f)

private fun spot(x0: Int, y0: Int, x1: Int, y1: Int, stand: Offset, toNext: List<Offset> = emptyList()) =
    MapStopSpot(Rect(x0 / 941f, y0 / 1672f, x1 / 941f, y1 / 1672f), stand, toNext)

private val MapStops = listOf(
    spot(322, 305, 492, 405, px(230, 430), listOf(px(328, 416), px(512, 520))),
    spot(665, 498, 838, 598, px(560, 595), listOf(px(620, 625), px(497, 720))),
    spot(292, 685, 466, 785, px(190, 775), listOf(px(360, 790), px(512, 893))),
    spot(724, 870, 896, 973, px(600, 955), listOf(px(690, 965), px(580, 1075), px(430, 1115))),
    spot(100, 1020, 278, 1122, px(370, 1150), listOf(px(420, 1170), px(522, 1225))),
    spot(705, 1162, 882, 1265, px(600, 1270), listOf(px(480, 1310), px(420, 1450))),
    spot(272, 1342, 437, 1442, px(180, 1440), listOf(px(448, 1460), px(582, 1543))),
    spot(712, 1515, 893, 1618, px(600, 1610)),
)

/** The way from stop [from] to stop [to]: along the bridges, through every stop between. */
private fun mapRoute(from: Int, to: Int): List<Offset> = buildList {
    if (to > from) {
        for (k in from until to) {
            addAll(MapStops[k].toNext)
            add(MapStops[k + 1].stand)
        }
    } else {
        for (k in from - 1 downTo to) {
            addAll(MapStops[k].toNext.asReversed())
            add(MapStops[k].stand)
        }
    }
}

/** Distance between two map points, in map widths. */
private fun mapDistance(a: Offset, b: Offset) = hypot(b.x - a.x, (b.y - a.y) / MapAspect)

/** The qilin's width, as a fraction of the map's. */
private const val QilinWidth = 0.23f

/** How fast the qilin runs, in map widths per second, and how far it goes per hop. */
private const val QilinSpeed = 1.4f
private const val QilinStride = 0.09f

@Composable
private fun MapTabContent(
    padding: PaddingValues,
    scrollState: ScrollState,
    categories: List<Category>,
    progress: UserProgress,
    qilinAt: String,
    justUnlocked: String?,
    onCelebrated: () -> Unit,
    onOpen: (Category) -> Unit,
    onToggleDevUnlock: () -> Unit,
    onResetAsNewUser: () -> Unit,
) {
    val ids = remember(categories) { categories.map { it.id } }
    val sfx = rememberSfx()
    val scope = rememberCoroutineScope()
    val stops = categories.take(MapStops.size)
    val homeIndex = ids.indexOf(qilinAt).coerceIn(0, stops.lastIndex)
    // The stop the qilin stands at, where it is drawn, and how far it has run (for the hops).
    var at by remember { mutableIntStateOf(homeIndex) }
    val qilin = remember { Animatable(MapStops[homeIndex].stand, Offset.VectorConverter) }
    var travelled by remember { mutableFloatStateOf(0f) }
    var walking by remember { mutableStateOf(false) }
    var facingLeft by remember { mutableStateOf(false) }
    LaunchedEffect(homeIndex) {
        if (!walking) {
            at = homeIndex
            qilin.snapTo(MapStops[homeIndex].stand)
        }
    }

    // The map runs under the status bar and the tab bar; the strips it leaves there take the
    // picture's own sky and cloud colours, so the whole screen is map.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewport = constraints.maxHeight
        val top = padding.calculateTopPadding()
        val topPx = with(density) { top.toPx() }
        val bottomPx = with(density) { padding.calculateBottomPadding().toPx() }
        // Full width, or taller when that would leave a gap above the tab bar (its sides are
        // then trimmed evenly).
        val mapHeight = maxOf(constraints.maxWidth / MapAspect, viewport - topPx - bottomPx)
        val mapHeightDp = with(density) { mapHeight.toDp() }
        // Bring the newly opened category into view before it unlocks.
        LaunchedEffect(justUnlocked) {
            val id = justUnlocked ?: return@LaunchedEffect
            val stop = MapStops.getOrNull(ids.indexOf(id)) ?: return@LaunchedEffect
            scrollState.animateScrollTo((topPx + stop.sign.center.y * mapHeight - viewport / 2f).toInt())
        }

        // Runs the qilin along the path to the tapped lesson, past every lesson on the way and
        // keeping it on screen, then opens the lesson. A locked one is just refused.
        val openStop: (Int) -> Unit = { index ->
            val category = stops[index]
            when {
                walking -> Unit
                !GameRules.isCategoryUnlocked(ids, category.id, progress) || index == at -> onOpen(category)
                else -> {
                    sfx.tap()
                    walking = true
                    travelled = 0f
                    scope.launch {
                        for (point in mapRoute(at, index)) {
                            val from = qilin.value
                            val start = travelled
                            if (abs(point.x - from.x) > 0.02f) facingLeft = point.x < from.x
                            val millis = (mapDistance(from, point) / QilinSpeed * 1000).toInt()
                            qilin.animateTo(point, tween(millis, easing = LinearEasing)) {
                                travelled = start + mapDistance(from, value)
                                // Follow the qilin when it nears the top or bottom of the screen.
                                val y = topPx + value.y * mapHeight - scrollState.value
                                val margin = (viewport - topPx - bottomPx) * 0.25f
                                if (y < topPx + margin) scrollState.dispatchRawDelta(y - topPx - margin)
                                if (y > viewport - bottomPx - margin) scrollState.dispatchRawDelta(y - (viewport - bottomPx - margin))
                            }
                        }
                        at = index
                        walking = false
                        onOpen(category)
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(Brush.verticalGradient(listOf(MapSky, MapClouds))),
        ) {
            Spacer(Modifier.height(top))
            Box(Modifier.fillMaxWidth().height(mapHeightDp).clipToBounds()) {
                BoxWithConstraints(Modifier.requiredSize(width = mapHeightDp * MapAspect, height = mapHeightDp)) {
                    val mapW = maxWidth
                    val mapH = maxHeight
                    Image(
                        painterResource(R.drawable.map_world),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                    stops.forEachIndexed { index, category ->
                        val sign = MapStops[index].sign
                        MapSign(
                            category = category,
                            index = index,
                            unlocked = GameRules.isCategoryUnlocked(ids, category.id, progress),
                            celebrate = category.id == justUnlocked,
                            onCelebrated = onCelebrated,
                            trophy = progress.bossWon(category.id),
                            onClick = { openStop(index) },
                            modifier = Modifier
                                .offset(x = mapW * sign.left, y = mapH * sign.top)
                                .size(width = mapW * sign.width, height = mapH * sign.height),
                        )
                    }
                    val size = mapW * QilinWidth
                    Image(
                        painterResource(R.drawable.mascot_explore),
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = mapW * qilin.value.x - size / 2, y = mapH * qilin.value.y - size)
                            .size(size)
                            .graphicsLayer {
                                // A hop every stride while it runs.
                                if (walking) translationY = -abs(sin(travelled / QilinStride * PI.toFloat())) * 8.dp.toPx()
                                scaleX = if (facingLeft) -1f else 1f
                            },
                    )
                }
                if (BuildConfig.DEBUG) {
                    var confirmReset by remember { mutableStateOf(false) }
                    // Over the clouds in the map's bottom-left corner, clear of the last sign.
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(
                            onClick = onToggleDevUnlock,
                            modifier = Modifier.background(Paper.copy(alpha = 0.85f), CircleShape),
                        ) {
                            Text(
                                if (progress.devUnlockAll) "🛠 ปิดปลดล็อก (debug)" else "🛠 ปลดล็อกทุกด่าน (debug)",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        // TODO: remove before release.
                        TextButton(
                            onClick = { confirmReset = true },
                            modifier = Modifier.background(Paper.copy(alpha = 0.85f), CircleShape),
                        ) {
                            Text("🔄 ผู้ใช้ใหม่ (debug)", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (confirmReset) {
                        AlertDialog(
                            onDismissRequest = { confirmReset = false },
                            title = { Text("รีเซ็ตเป็นผู้ใช้ใหม่?") },
                            text = {
                                Text(
                                    "ล้างความคืบหน้า เหรียญ ภารกิจ สตรีค และหน้าที่เคยดูทั้งหมดในเครื่องนี้ " +
                                        "ออกจากระบบ แล้วเปิดแอปใหม่เหมือนครั้งแรก (บัญชีบนคลาวด์ไม่ถูกลบ)",
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmReset = false
                                    onResetAsNewUser()
                                }) { Text("รีเซ็ต") }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmReset = false }) { Text("ยกเลิก") }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
}

/** The colours at the top and bottom edges of [R.drawable.map_world]. */
private val MapSky = Color(0xFF4C9EEE)
private val MapClouds = Color(0xFF9EA3B7)

/**
 * A lesson written on one of the map's painted signboards, in the picture's own style: its
 * number above its Chinese name. A locked one is shaded with a padlock; a beaten one gets a trophy.
 */
@Composable
private fun MapSign(
    category: Category,
    index: Int,
    unlocked: Boolean,
    /** Just opened: shown locked, the padlock rattles, then it pops open with a sound. */
    celebrate: Boolean,
    onCelebrated: () -> Unit,
    trophy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val sfx = rememberSfx()
    var rattling by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(celebrate) {
        if (!celebrate) return@LaunchedEffect
        revealed = false
        delay(700) // let the map scroll here first
        rattling = true
        delay(450)
        rattling = false
        revealed = true
        sfx.play(Sfx.Sound.Unlock)
        delay(800)
        onCelebrated()
    }
    val open = unlocked && (!celebrate || revealed)
    val press = remember { MutableInteractionSource() }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .shake(rattling)
            .pop(revealed, amount = 0.25f)
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
    ) {
        // Inside the painted frame; the Chinese name shrinks to fit both its width and what is
        // left of its height under the number (a bold character runs a little over 1em wide).
        val byWidth = (maxWidth - 20.dp) / (category.titleZh.length.coerceAtLeast(2) * 1.25f)
        val byHeight = (maxHeight - 26.dp) / 1.2f
        val zhSize = with(density) { minOf(byWidth, byHeight, 22.dp).toSp() }
        val tight = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("บทที่ ${index + 1}", style = tight, fontSize = 11.sp, lineHeight = 14.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(category.titleZh, style = tight, fontSize = zhSize, lineHeight = zhSize * 1.2f, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        if (!open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .background(Ink.copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("🔒", fontSize = 18.sp) }
        }
        if (trophy) Text("🏆", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp))
    }
}
