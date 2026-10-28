package com.example.nihaogo.ui.login

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nihaogo.AppContainer
import com.example.nihaogo.data.user.Account
import com.example.nihaogo.data.user.AccountManager.SignInResult
import com.example.nihaogo.data.user.GoogleCredentials
import com.example.nihaogo.data.user.UserProgress
import com.example.nihaogo.ui.common.PaperCard
import com.example.nihaogo.ui.theme.Gold
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Google and email sign-in shared by the login page and the profile. Snackbar lines arrive on
 * [messages]; [signedIn] fires once the player is signed in (after any [conflict] is resolved).
 */
class SignInViewModel(container: AppContainer) : ViewModel() {
    private val accounts = container.accounts
    val settings = container.settings
    val account: StateFlow<Account?> = accounts.account

    var busy by mutableStateOf(false)
        private set
    var conflict by mutableStateOf<SignInResult.NeedsChoice?>(null)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _signedIn = Channel<Unit>(Channel.CONFLATED)
    val signedIn = _signedIn.receiveAsFlow()

    /** [activity] hosts Credential Manager's account picker. */
    fun signInWithGoogle(activity: Context) = runBusy {
        when (val credential = GoogleCredentials.request(activity)) {
            GoogleCredentials.Result.Cancelled -> Unit
            GoogleCredentials.Result.NoAccount ->
                _messages.send("เครื่องนี้ยังไม่มีบัญชี Google เพิ่มบัญชีในการตั้งค่าของเครื่องก่อนนะ")
            is GoogleCredentials.Result.Token -> finish(accounts.signInWithGoogle(credential.idToken))
        }
    }

    fun signInWithEmail(email: String, password: String, remember: Boolean) {
        if (!checkFields(email, password)) return
        runBusy {
            finish(accounts.signInWithEmail(email.trim(), password))
            settings.rememberedEmail = email.trim().takeIf { remember }
        }
    }

    fun createEmailAccount(email: String, password: String, remember: Boolean) {
        if (!checkFields(email, password)) return
        runBusy {
            finish(accounts.createEmailAccount(email.trim(), password))
            settings.rememberedEmail = email.trim().takeIf { remember }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            viewModelScope.launch { _messages.send("กรอกอีเมลก่อน แล้วค่อยกด \"ลืมรหัสผ่าน?\" นะ") }
            return
        }
        runBusy {
            accounts.sendPasswordReset(email.trim())
            _messages.send("ส่งลิงก์ตั้งรหัสผ่านใหม่ไปที่ ${email.trim()} แล้ว")
        }
    }

    fun resolveConflict(chosen: UserProgress) = runBusy {
        accounts.resolveConflict(chosen)
        conflict = null
        succeeded()
    }

    private suspend fun finish(result: SignInResult) {
        when (result) {
            SignInResult.Done -> succeeded()
            is SignInResult.NeedsChoice -> conflict = result
        }
    }

    private suspend fun succeeded() {
        _messages.send("เข้าสู่ระบบแล้ว ความคืบหน้าจะเก็บไว้ในบัญชีของคุณ")
        _signedIn.send(Unit)
    }

    private fun checkFields(email: String, password: String): Boolean {
        val problem = when {
            email.isBlank() || password.isEmpty() -> "กรอกอีเมลและรหัสผ่านให้ครบก่อนนะ"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "รูปแบบอีเมลไม่ถูกต้อง"
            else -> null
        }
        if (problem != null) viewModelScope.launch { _messages.send(problem) }
        return problem == null
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SignIn", "Sign-in failed", e)
                _messages.send(messageFor(e))
            } finally {
                busy = false
            }
        }
    }

    private fun messageFor(e: Exception): String = when (e) {
        is FirebaseNetworkException -> "เชื่อมต่ออินเทอร์เน็ตไม่ได้ ลองใหม่อีกครั้งนะ"
        is FirebaseTooManyRequestsException -> "ลองหลายครั้งเกินไป รอสักครู่แล้วค่อยลองใหม่นะ"
        // Before the general invalid-credentials case: a weak password is one of them.
        is FirebaseAuthWeakPasswordException -> "รหัสผ่านสั้นเกินไป ใช้อย่างน้อย 6 ตัวนะ"
        is FirebaseAuthUserCollisionException -> "อีเมลนี้มีบัญชีอยู่แล้ว ลองกดเข้าสู่ระบบแทนนะ"
        is FirebaseAuthInvalidUserException -> "ไม่พบบัญชีนี้ ลองสมัครสมาชิกด้วยอีเมลนี้ดูนะ"
        is FirebaseAuthInvalidCredentialsException ->
            if (e.errorCode == "ERROR_INVALID_EMAIL") "รูปแบบอีเมลไม่ถูกต้อง"
            else "อีเมลหรือรหัสผ่านไม่ถูกต้อง"
        is FirebaseAuthException ->
            if (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") "ยังไม่ได้เปิดให้เข้าสู่ระบบด้วยวิธีนี้"
            else "เข้าสู่ระบบไม่สำเร็จ ลองใหม่อีกครั้งนะ"
        else -> "เข้าสู่ระบบไม่สำเร็จ ลองใหม่อีกครั้งนะ"
    }
}

/** Must be answered: dismissing it would leave syncing paused. */
@Composable
fun ConflictDialog(conflict: SignInResult.NeedsChoice, busy: Boolean, onChoose: (UserProgress) -> Unit) {
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
                    title = "☁️ ความคืบหน้าในบัญชี",
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
    PaperCard(
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
