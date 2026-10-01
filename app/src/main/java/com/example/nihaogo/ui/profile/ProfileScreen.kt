package com.example.nihaogo.ui.profile

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.Account
import com.example.nihaogo.data.user.AccountManager.SignInResult
import com.example.nihaogo.data.user.GoogleCredentials
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.GameTopBar
import com.example.nihaogo.ui.common.InkScaffold
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Gold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(container: AppContainer) : ViewModel() {
    private val accounts = container.accounts
    val account: StateFlow<Account?> = accounts.account
    val progress: StateFlow<UserProgress> = container.progress.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())
    val categoryCount = container.content.categories.size
    val sceneCount = container.content.special.modes.sumOf { it.scenes.size }

    var busy by mutableStateOf(false)
        private set
    var conflict by mutableStateOf<SignInResult.NeedsChoice?>(null)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** [activity] hosts Credential Manager's account picker. */
    fun signIn(activity: Context) = runBusy("ล็อกอินไม่สำเร็จ ลองใหม่อีกครั้งนะ") {
        when (val credential = GoogleCredentials.request(activity)) {
            GoogleCredentials.Result.Cancelled -> Unit
            GoogleCredentials.Result.NoAccount ->
                _messages.send("เครื่องนี้ยังไม่มีบัญชี Google เพิ่มบัญชีในการตั้งค่าของเครื่องก่อนนะ")
            is GoogleCredentials.Result.Token -> when (val result = accounts.signInWithGoogle(credential.idToken)) {
                SignInResult.Done -> _messages.send("ล็อกอินแล้ว ความคืบหน้าจะเก็บไว้ในบัญชี Google")
                is SignInResult.NeedsChoice -> conflict = result
            }
        }
    }

    fun resolveConflict(chosen: UserProgress) = runBusy("บันทึกไม่สำเร็จ ลองใหม่อีกครั้งนะ") {
        accounts.resolveConflict(chosen)
        conflict = null
        _messages.send("ล็อกอินแล้ว ความคืบหน้าจะเก็บไว้ในบัญชี Google")
    }

    fun signOut() = runBusy("ออกจากระบบไม่สำเร็จ ลองใหม่อีกครั้งนะ") {
        accounts.signOut()
        _messages.send("ออกจากระบบแล้ว ตอนนี้เล่นแบบ Guest")
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

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm = appViewModel { ProfileViewModel(it) }
    val account by vm.account.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    InkScaffold(
        topBar = { GameTopBar(title = "โปรไฟล์", coins = progress.coins, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val guest = account?.isGuest != false
            AccountCard(account)
            if (guest) {
                GuestCard(busy = vm.busy, onSignIn = { vm.signIn(context) })
            }
            StatsCard(progress, vm.categoryCount, vm.sceneCount)
            if (!guest) {
                OutlinedButton(
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
                    "เครื่องนี้จะกลับไปเป็น Guest ที่เริ่มจากศูนย์ " +
                        "ส่วนความคืบหน้ายังเก็บอยู่ในบัญชี Google ล็อกอินกลับมาเมื่อไหร่ก็เล่นต่อได้"
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

    vm.conflict?.let { conflict ->
        ConflictDialog(
            conflict = conflict,
            busy = vm.busy,
            onChoose = vm::resolveConflict,
        )
    }
}

@Composable
private fun AccountCard(account: Account?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(account)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                if (account == null || account.isGuest) {
                    Text("ผู้เล่น Guest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("ยังไม่ได้ล็อกอิน", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        account.name ?: "ผู้เล่น",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    account.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text("✅ ล็อกอินด้วย Google", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Avatar(account: Account?) {
    val modifier = Modifier.size(72.dp).clip(CircleShape)
    val photo = account?.photoUrl
    if (account != null && !account.isGuest && photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = "รูปโปรไฟล์",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Cream), contentAlignment = Alignment.Center) {
            val initial = account?.takeUnless { it.isGuest }?.name?.firstOrNull()?.uppercase()
            Text(initial ?: "🐼", fontSize = if (initial != null) 32.sp else 40.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GuestCard(busy: Boolean, onSignIn: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("เก็บความคืบหน้าไว้ในบัญชี Google", fontWeight = FontWeight.Bold)
            Text(
                "ตอนนี้ความคืบหน้าผูกกับเครื่องนี้ ถ้าลบแอปหรือเปลี่ยนเครื่องจะหายไป " +
                    "ล็อกอินด้วย Google แล้วเล่นต่อได้ทุกเครื่อง",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("ล็อกอินด้วย Google")
                }
            }
        }
    }
}

@Composable
private fun StatsCard(progress: UserProgress, categoryCount: Int, sceneCount: Int) {
    val maxStars = categoryCount * Step.entries.size * 3 + sceneCount * 3
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("สถิติของฉัน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatRow("🏆", "ถ้วยบอส", "${progress.trophies}/$categoryCount")
            StatRow("⭐", "ดาวทั้งหมด", "${progress.totalStars}/$maxStars")
            StatRow("🕵️", "ฉากพิเศษที่ผ่านแล้ว", "${progress.scenesCleared}/$sceneCount")
            StatRow("🪙", "เหรียญ", "${progress.coins}")
        }
    }
}

@Composable
private fun StatRow(emoji: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 22.sp)
        Text(label, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/** Must be answered: dismissing it would leave syncing paused. */
@Composable
private fun ConflictDialog(conflict: SignInResult.NeedsChoice, busy: Boolean, onChoose: (UserProgress) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("บัญชีนี้มีความคืบหน้าอยู่แล้ว") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("เลือกว่าจะเก็บความคืบหน้าฝั่งไหนไว้ อีกฝั่งจะถูกแทนที่")
                ChoiceCard(
                    title = "📱 ความคืบหน้าในเครื่องนี้",
                    progress = conflict.device,
                    recommended = conflict.deviceIsAhead,
                    enabled = !busy,
                    onClick = { onChoose(conflict.device) },
                )
                ChoiceCard(
                    title = "☁️ ความคืบหน้าในบัญชี Google",
                    progress = conflict.account,
                    recommended = conflict.accountIsAhead,
                    enabled = !busy,
                    onClick = { onChoose(conflict.account) },
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ChoiceCard(title: String, progress: UserProgress, recommended: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        border = if (recommended) BorderStroke(2.dp, Gold) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (recommended) {
                Surface(shape = CircleShape, color = Gold) {
                    Text(
                        "แนะนำ · ความคืบหน้ามากกว่า",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                "⭐ ${progress.totalStars} ดาว · 🏆 ${progress.trophies} ถ้วย · 🪙 ${progress.coins}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
