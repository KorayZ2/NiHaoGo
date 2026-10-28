package com.example.nihaogo.ui.profile

import android.app.Activity
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.example.nihaogo.AppContainer
import com.example.nihaogo.R
import com.example.nihaogo.audio.Sfx
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.Account
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.monetization.RewardedAds
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.GameOutlinedButton
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.ProgressBar
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.rememberSfx
import com.example.nihaogo.ui.login.ConflictDialog
import com.example.nihaogo.ui.login.SignInViewModel
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Locked
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import com.example.nihaogo.ui.theme.RedDeep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(container: AppContainer) : ViewModel() {
    private val accounts = container.accounts
    private val rewardedAds = container.rewardedAds
    private val sfx = container.sfx
    val settings = container.settings
    val account: StateFlow<Account?> = accounts.account
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())
    val categoryCount = container.content.categories.size
    val sceneCount = container.content.special.modes.filterNot { it.comingSoon }.sumOf { it.scenes.size }

    var busy by mutableStateOf(false)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun adsLeft(progress: UserProgress): Int = GameRules.adsLeft(progress, GameRules.today())

    fun watchAd(activity: Activity) = runBusy("เปิดโฆษณาไม่สำเร็จ ลองใหม่อีกครั้งนะ") {
        val outcome = rewardedAds.watchForCoins(activity)
        if (outcome == RewardedAds.Outcome.Rewarded) sfx.play(Sfx.Sound.Coin)
        _messages.send(outcome.messageTh)
    }

    fun setSoundOn(on: Boolean) {
        settings.setSoundOn(on)
        sfx.tap() // a preview, so the player hears what they turned on
    }

    fun setVibrateOn(on: Boolean) {
        settings.setVibrateOn(on)
        sfx.buzz()
    }

    fun signOut() = runBusy("ออกจากระบบไม่สำเร็จ ลองใหม่อีกครั้งนะ") {
        accounts.signOut()
        _messages.send("ออกจากระบบแล้ว ตอนนี้เล่นแบบผู้เยี่ยมชม")
    }

    private fun runBusy(failure: String, block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Profile", failure, e)
                _messages.send(failure)
            } finally {
                busy = false
            }
        }
    }
}

/** Steps (of any category) passed with at least one star. */
private val UserProgress.stepsPassed: Int
    get() = categories.values.sumOf { c -> c.stars.values.count { it > 0 } }

/** The menu's folding sections. */
private enum class Section { Goals, Rewards, Settings, Help }

@Composable
fun ProfileScreen(
    onBack: (() -> Unit)?,
    onOpenPinyin: () -> Unit,
    onOpenLogin: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val vm = appViewModel { ProfileViewModel(it) }
    val signIn = appViewModel { SignInViewModel(it) }
    val account by vm.account.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<Section?>(null) }
    val sfx = rememberSfx()

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(signIn) { signIn.messages.collect { snackbar.showSnackbar(it) } }

    val toggle: (Section) -> Unit = {
        sfx.tap()
        open = if (open == it) null else it
    }

    InkScaffold(
        topBar = { GameTopBar(title = "โปรไฟล์", coins = progress.coins, onBack = onBack) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val guest = account?.isGuest != false
            ProfileHeader(account, Modifier.appear())
            Row(Modifier.fillMaxWidth().appear(80), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("🔥", "${vm.settings.streak}", "วันติดกัน", Modifier.weight(1f))
                StatCard("📘", "${progress.stepsPassed}", "บทเรียน", Modifier.weight(1f))
                StatCard("⭐", "${progress.totalStars}", "คะแนนรวม", Modifier.weight(1f))
            }
            if (guest) {
                GuestCard(
                    busy = signIn.busy,
                    onOpenLogin = onOpenLogin,
                    onGoogle = { signIn.signInWithGoogle(context) },
                    modifier = Modifier.appear(140),
                )
            }

            val activity = LocalActivity.current
            val soundOn by vm.settings.soundOn.collectAsStateWithLifecycle()
            val vibrateOn by vm.settings.vibrateOn.collectAsStateWithLifecycle()
            Column(Modifier.appear(200), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuSection(Icons.Outlined.CheckCircle, "เป้าหมายการเรียน", open == Section.Goals, { toggle(Section.Goals) }) {
                    GoalRow("🏆 ชนะบอสครบทุกหมวด", progress.trophies, vm.categoryCount)
                    GoalRow("📘 ผ่านทุกขั้นของทุกหมวด", progress.stepsPassed, vm.categoryCount * Step.entries.size)
                    GoalRow("🕵️ ผ่านฉากโหมดพิเศษ", progress.scenesCleared, vm.sceneCount)
                    val maxStars = vm.categoryCount * Step.entries.size * 3 + vm.sceneCount * 3
                    GoalRow("⭐ เก็บดาวให้ครบ", progress.totalStars, maxStars)
                }
                MenuSection(Icons.Outlined.Star, "รางวัลของฉัน", open == Section.Rewards, { toggle(Section.Rewards) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RewardChip("🪙", "${progress.coins}", "เหรียญ", Modifier.weight(1f))
                        RewardChip("🏆", "${progress.trophies}/${vm.categoryCount}", "ถ้วยบอส", Modifier.weight(1f))
                        RewardChip("🕵️", "${progress.scenesCleared}/${vm.sceneCount}", "ฉากพิเศษ", Modifier.weight(1f))
                    }
                    if (!progress.premium && activity != null) {
                        MenuDivider()
                        RewardedAdRow(adsLeft = vm.adsLeft(progress), busy = vm.busy, onWatch = { vm.watchAd(activity) })
                    }
                }
                MenuSection(Icons.Outlined.Notifications, "ตั้งค่าการแจ้งเตือน", open == Section.Settings, { toggle(Section.Settings) }) {
                    SettingRow("🔔", "เสียงเอฟเฟกต์", "ตอบถูก ตอบผิด ได้ดาว และแตะปุ่ม", soundOn, vm::setSoundOn)
                    MenuDivider()
                    SettingRow("📳", "สั่นเมื่อตอบผิด", null, vibrateOn, vm::setVibrateOn)
                    Text(
                        "เสียงอ่านภาษาจีนยังเปิดอยู่เสมอ ปรับความดังได้ที่ปุ่มเสียงของเครื่อง",
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                    )
                }
                MenuSection(Icons.Outlined.Info, "ช่วยเหลือ", open == Section.Help, { toggle(Section.Help) }) {
                    Text(
                        "เรียนทีละบทบนแผนที่: คำศัพท์ → ประโยค → ฟังแล้วตอบ → บอส AI " +
                            "ได้ดาวและเหรียญจากทุกขั้น ชนะบอสแล้วบทถัดไปจะเปิด " +
                            "ตอนฝึกพูด ให้แตะปุ่มไมค์แล้วพูดภาษาจีนตามให้ชัด ๆ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft,
                    )
                    MenuDivider()
                    MenuRow("🔤", "ทบทวนพินอิน", "พยัญชนะต้น สระ วรรณยุกต์ และการเปลี่ยนเสียง", onClick = onOpenPinyin) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkSoft)
                    }
                }
            }
            if (!guest) {
                GameOutlinedButton(
                    onClick = { confirmSignOut = true },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("ออกจากระบบ") }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("ออกจากระบบ?") },
            text = {
                Text(
                    "เครื่องนี้จะกลับไปเป็นผู้เยี่ยมชมที่เริ่มจากศูนย์ " +
                        "ส่วนความคืบหน้ายังเก็บอยู่ในบัญชี เข้าสู่ระบบกลับมาเมื่อไหร่ก็เล่นต่อได้"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    vm.signOut()
                }) { Text("ออกจากระบบ") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("ยกเลิก") } },
        )
    }

    signIn.conflict?.let { conflict ->
        ConflictDialog(conflict = conflict, busy = signIn.busy, onChoose = signIn::resolveConflict)
    }
}

/** The landscape painting with the round avatar and the player's name over it. */
@Composable
private fun ProfileHeader(account: Account?, modifier: Modifier) {
    val guest = account == null || account.isGuest
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Paper,
        border = BorderStroke(1.dp, Hairline),
        shadowElevation = 2.dp,
    ) {
        Box {
            Image(
                painterResource(R.drawable.bg_landscape),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Box(
                Modifier.fillMaxWidth().height(150.dp).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Paper.copy(alpha = 0.6f), Paper))
                )
            )
            Column(
                Modifier.fillMaxWidth().padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(account)
                Text(
                    if (guest) "ผู้เยี่ยมชม" else account?.name ?: "ผู้เล่น",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = RedDeep,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        guest -> "ยังไม่ได้เข้าสู่ระบบ"
                        account?.isGoogle == true -> "✅ ${account?.email.orEmpty()} · Google"
                        else -> "✅ ${account?.email.orEmpty()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Avatar(account: Account?) {
    val modifier = Modifier
        .size(104.dp)
        .shadow(6.dp, CircleShape)
        .clip(CircleShape)
        .background(Cream)
        .border(3.dp, Gold, CircleShape)
    val photo = account?.photoUrl
    if (account != null && !account.isGuest && photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = "รูปโปรไฟล์",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.mascot_happy),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** One of the three stat cards under the header. */
@Composable
private fun StatCard(emoji: String, value: String, label: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Paper,
        border = BorderStroke(1.dp, Hairline),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkSoft, maxLines = 1)
        }
    }
}

@Composable
private fun GuestCard(busy: Boolean, onOpenLogin: () -> Unit, onGoogle: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Paper,
        border = BorderStroke(1.5.dp, Gold),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("☁️ เก็บความคืบหน้าไว้ในบัญชี", fontWeight = FontWeight.Bold)
            Text(
                "ตอนนี้ความคืบหน้าผูกกับเครื่องนี้ ถ้าลบแอปหรือเปลี่ยนเครื่องจะหายไป " +
                    "เข้าสู่ระบบแล้วเล่นต่อได้ทุกเครื่อง",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
            )
            GameButton(onClick = onOpenLogin, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("เข้าสู่ระบบ / สมัครสมาชิก")
            }
            GameOutlinedButton(onClick = onGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("ล็อกอินด้วย Google")
                }
            }
        }
    }
}

/**
 * A menu row with an outline icon and a chevron, as in the mockup; tapping it folds its
 * [content] open underneath.
 */
@Composable
private fun MenuSection(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Paper,
        border = BorderStroke(1.dp, if (expanded) Gold else Hairline),
        shadowElevation = 2.dp,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
                Text(label, modifier = Modifier.padding(horizontal = 14.dp).weight(1f), fontWeight = FontWeight.Bold)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = InkSoft,
                    modifier = Modifier.rotate(turn),
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    MenuDivider()
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
                }
            }
        }
    }
}

@Composable
private fun GoalRow(label: String, value: Int, total: Int) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                "$value/$total",
                style = MaterialTheme.typography.labelLarge,
                color = if (total > 0 && value >= total) Red else InkSoft,
            )
        }
        ProgressBar(if (total > 0) value.toFloat() / total else 0f, Modifier.fillMaxWidth().padding(top = 4.dp), height = 8.dp)
    }
}

@Composable
private fun RewardChip(emoji: String, value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(Cream).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(value, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSoft, maxLines = 1)
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(color = Hairline)
}

/** The row's emoji in a small cream circle. */
@Composable
private fun MenuIcon(emoji: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Cream),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 20.sp) }
}

@Composable
private fun MenuRow(
    emoji: String,
    label: String,
    detail: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIcon(emoji)
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun RewardedAdRow(adsLeft: Int, busy: Boolean, onWatch: () -> Unit) {
    val enabled = adsLeft > 0 && !busy
    MenuRow(
        "📺",
        "ดูโฆษณารับเหรียญ",
        if (adsLeft > 0) "ดูจนจบได้ +${GameRules.AD_REWARD} 🪙 · วันนี้เหลือ $adsLeft/${GameRules.ADS_PER_DAY} ครั้ง"
        else "ครบแล้ว พรุ่งนี้มาใหม่นะ",
    ) {
        Surface(
            onClick = onWatch,
            enabled = enabled,
            shape = CircleShape,
            color = if (enabled) Red else Locked,
            shadowElevation = if (enabled) 3.dp else 0.dp,
        ) {
            Text(
                "ดูเลย",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SettingRow(emoji: String, label: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    MenuRow(
        emoji,
        label,
        detail,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Red, uncheckedBorderColor = Hairline),
        )
    }
}
