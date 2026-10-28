package com.example.nihaogo.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nihaogo.R
import com.example.nihaogo.ui.common.GameButton
import com.example.nihaogo.ui.common.appViewModel
import com.example.nihaogo.ui.common.appear
import com.example.nihaogo.ui.common.pressScale
import com.example.nihaogo.ui.common.rememberSfx
import com.example.nihaogo.ui.theme.Cream
import com.example.nihaogo.ui.theme.Hairline
import com.example.nihaogo.ui.theme.Ink
import com.example.nihaogo.ui.theme.InkSoft
import com.example.nihaogo.ui.theme.Paper
import com.example.nihaogo.ui.theme.Red
import kotlinx.coroutines.launch

/** Email, Google (and soon Apple/Facebook) sign-in over the landscape painting, after the mockup. */
@Composable
fun LoginScreen(firstRun: Boolean, onDone: () -> Unit) {
    val vm = appViewModel { SignInViewModel(it) }
    val account by vm.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentOnDone by rememberUpdatedState(onDone)

    var email by rememberSaveable { mutableStateOf(vm.settings.rememberedEmail.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(vm.settings.rememberedEmail != null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) { vm.signedIn.collect { currentOnDone() } }
    // Already signed in on this device: nothing to ask the first time round.
    LaunchedEffect(Unit) { if (firstRun && account?.isGuest == false) currentOnDone() }

    val signIn = { vm.signInWithEmail(email, password, rememberMe) }
    val notYet: () -> Unit = { scope.launch { snackbar.showSnackbar("ยังไม่รองรับ เร็ว ๆ นี้") } }

    Box(Modifier.fillMaxSize().background(Cream)) {
        Image(
            painterResource(R.drawable.bg_landscape),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Image(
                painterResource(R.drawable.logo_nihaogo),
                contentDescription = "NiHaoGo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.8f).appear(),
            )
            Text(
                "ภาษาจีน...ใกล้กว่าที่คิด\nเปิดโลกใหม่ ไปกับ NiHaoGo",
                modifier = Modifier.padding(top = 8.dp).appear(80),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                Image(
                    painterResource(R.drawable.mascot_wave),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp)
                        .offset(y = 12.dp)
                        .size(160.dp)
                        .appear(160),
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().appear(220),
                shape = RoundedCornerShape(28.dp),
                color = Paper.copy(alpha = 0.97f),
                border = BorderStroke(1.dp, Hairline),
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoginField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "อีเมล",
                        leading = { Icon(Icons.Outlined.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    LoginField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "รหัสผ่าน",
                        leading = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Text(
                                    if (showPassword) "🙈" else "👁️",
                                    fontSize = 18.sp,
                                    modifier = Modifier.semantics {
                                        contentDescription = if (showPassword) "ซ่อนรหัสผ่าน" else "แสดงรหัสผ่าน"
                                    },
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { signIn() }),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .weight(1f)
                                .toggleable(value = rememberMe, role = Role.Checkbox, onValueChange = { rememberMe = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = Red, uncheckedColor = InkSoft),
                            )
                            Text("จดจำฉัน", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { vm.sendPasswordReset(email) }, enabled = !vm.busy) {
                            Text("ลืมรหัสผ่าน?", color = InkSoft)
                        }
                    }
                    GameButton(onClick = signIn, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                        if (vm.busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("เข้าสู่ระบบ")
                        }
                    }
                    TextButton(
                        onClick = { vm.createEmailAccount(email, password, rememberMe) },
                        enabled = !vm.busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("ยังไม่มีบัญชี? สมัครสมาชิกด้วยอีเมลนี้", color = Red) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = Hairline)
                        Text(
                            "หรือเข้าสู่ระบบด้วย",
                            modifier = Modifier.padding(horizontal = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                        )
                        HorizontalDivider(Modifier.weight(1f), color = Hairline)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    ) {
                        SocialButton("Apple", onClick = notYet) { AppleGlyph() }
                        SocialButton("Google", onClick = { vm.signInWithGoogle(context) }, enabled = !vm.busy) {
                            Text(
                                "G",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge.copy(brush = GoogleBrush),
                            )
                        }
                        SocialButton("Facebook", onClick = notYet) {
                            Box(
                                Modifier.size(30.dp).background(FacebookBlue, CircleShape),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Text("f", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(x = 2.dp, y = 4.dp))
                            }
                        }
                    }
                }
            }
            // A paper pill so the link stays readable over the busy foot of the painting.
            Surface(
                modifier = Modifier.padding(vertical = 8.dp),
                shape = CircleShape,
                color = Paper.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Hairline),
                shadowElevation = 2.dp,
            ) {
                TextButton(onClick = onDone, enabled = !vm.busy) {
                    Text("เล่นต่อแบบผู้เยี่ยมชม ›", color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    vm.conflict?.let { conflict ->
        ConflictDialog(conflict = conflict, busy = vm.busy, onChoose = vm::resolveConflict)
    }
}

private val FacebookBlue = Color(0xFF1877F2)
private val GoogleBrush = Brush.linearGradient(
    listOf(Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4)),
)

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: @Composable () -> Unit,
    keyboardOptions: KeyboardOptions,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = InkSoft) },
        leadingIcon = leading,
        trailingIcon = trailing,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Red,
            unfocusedBorderColor = Hairline,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedLeadingIconColor = Red,
            unfocusedLeadingIconColor = InkSoft,
            cursorColor = Red,
        ),
    )
}

/** Round white button for a sign-in provider. */
@Composable
private fun SocialButton(
    name: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val sfx = rememberSfx()
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = {
            sfx.tap()
            onClick()
        },
        enabled = enabled,
        modifier = Modifier
            .size(56.dp)
            .pressScale(press, 0.9f)
            .shadow(4.dp, CircleShape, spotColor = InkSoft)
            .semantics { contentDescription = "เข้าสู่ระบบด้วย $name" },
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Hairline),
        interactionSource = press,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** A plain black apple with a bite and a leaf; there is no Apple icon in the core icon set. */
@Composable
private fun AppleGlyph() {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val black = Color.Black
        drawCircle(black, radius = w * 0.27f, center = Offset(w * 0.37f, w * 0.6f))
        drawCircle(black, radius = w * 0.27f, center = Offset(w * 0.63f, w * 0.6f))
        drawCircle(black, radius = w * 0.2f, center = Offset(w * 0.5f, w * 0.72f))
        // The bite, in the button's white.
        drawCircle(Color.White, radius = w * 0.13f, center = Offset(w * 0.92f, w * 0.52f))
        rotate(-35f, pivot = Offset(w * 0.58f, w * 0.18f)) {
            drawOval(black, topLeft = Offset(w * 0.5f, w * 0.08f), size = Size(w * 0.16f, w * 0.24f))
        }
    }
}
