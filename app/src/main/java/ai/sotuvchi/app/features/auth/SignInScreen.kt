package ai.sotuvchi.app.features.auth

import ai.sotuvchi.app.R
import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.auth.EmailCodeAuth
import ai.sotuvchi.app.core.format.Countdown
import ai.sotuvchi.app.core.l10n.AppLanguage
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.l10n.Localizer
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.InputField
import ai.sotuvchi.app.design.LabeledField
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Spinner
import ai.sotuvchi.app.design.Theme
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SignInStep { Email, Code }

/**
 * Email code sign-in. The same two steps sign in existing owners and create an
 * account for a new email.
 */
@Composable
fun SignInScreen(model: AppModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val language by Localizer.languageFlow.collectAsStateWithLifecycle()

    var step by rememberSaveable { mutableStateOf(SignInStep.Email) }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var codeIsWrong by remember { mutableStateOf(false) }
    var wrongCounter by remember { mutableIntStateOf(0) }
    var resendAvailableAt by remember { mutableLongStateOf(0L) }
    var codeValidMinutes by remember { mutableIntStateOf(10) }
    var showsWebSignIn by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.email) {
        if (email.isEmpty()) state.email?.let { email = it }
    }

    if (showsWebSignIn) {
        WebSignInScreen(model, onClose = { showsWebSignIn = false })
        return
    }

    fun sendCode() {
        if (isSending) return
        errorMessage = null
        codeIsWrong = false
        isSending = true
        scope.launch {
            try {
                val result = model.requestSignInCode(email)
                resendAvailableAt = System.currentTimeMillis() + result.resendAfterSeconds * 1000L
                codeValidMinutes = maxOf(1, result.expiresInSeconds / 60)
                code = ""
                step = SignInStep.Code
            } catch (failure: EmailCodeAuth.Failure) {
                errorMessage = failure.userMessage
                if (failure is EmailCodeAuth.Failure.TooSoon) {
                    // A code went out moments ago: let the owner type it.
                    resendAvailableAt = System.currentTimeMillis() + failure.retryAfterSeconds * 1000L
                    step = SignInStep.Code
                }
            } catch (error: Exception) {
                if (error.isCancellation) throw error
                errorMessage = error.userMessage
            } finally {
                isSending = false
            }
        }
    }

    fun verify(entered: String) {
        if (isVerifying) return
        errorMessage = null
        codeIsWrong = false
        isVerifying = true
        scope.launch {
            try {
                model.verifySignInCode(email, entered)
            } catch (failure: EmailCodeAuth.Failure) {
                errorMessage = failure.userMessage
                codeIsWrong = true
                wrongCounter++
                code = ""
                if (failure.needsNewCode) resendAvailableAt = 0L
            } catch (error: Exception) {
                if (error.isCancellation) throw error
                errorMessage = error.userMessage
                code = ""
            } finally {
                isVerifying = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.canvas)
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = Space.xxl, bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        if (step == SignInStep.Email) L("Sign in to Sotuvchi") else L("Enter the code"),
                        style = Theme.type.headline,
                        color = Theme.colors.ink,
                    )
                    Text(
                        if (step == SignInStep.Email) {
                            L("Enter your email and we will send you a 6-digit code. New here? The same code creates your account.")
                        } else {
                            L("We sent a 6-digit code to {email}.", mapOf("email" to EmailCodeAuth.normalize(email)))
                        },
                        style = Theme.type.bodyLarge,
                        color = Theme.colors.inkMuted,
                    )
                }
            }

            when (step) {
                SignInStep.Email -> Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    LabeledField(label = L("Work email")) {
                        InputField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "you@shop.uz",
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send,
                            onImeAction = { if (email.isNotBlank()) sendCode() },
                            modifier = Modifier.testTag("signIn.email"),
                        )
                    }
                    SButton(
                        L("Send code"),
                        onClick = { sendCode() },
                        loading = isSending,
                        enabled = email.isNotBlank(),
                        modifier = Modifier.testTag("signIn.sendCode"),
                    )
                }

                SignInStep.Code -> Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    OneTimeCodeField(
                        code = code,
                        onCodeChange = { code = it; if (it.isNotEmpty()) codeIsWrong = false },
                        isWrong = codeIsWrong,
                        wrongCounter = wrongCounter,
                        onComplete = { verify(it) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        if (isVerifying) {
                            Spinner(size = 14.dp)
                            Text(L("Signing you in…"), style = Theme.type.bodySmall, color = Theme.colors.inkMuted)
                        } else {
                            Text(
                                L("The code is valid for {minutes} minutes.", mapOf("minutes" to codeValidMinutes)),
                                style = Theme.type.bodySmall,
                                color = Theme.colors.inkMuted,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        SButton(
                            L("Open Mail"),
                            onClick = { openMailApp(context) },
                            kind = ButtonKind.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        ResendButton(
                            availableAt = resendAvailableAt,
                            isBusy = isSending,
                            enabled = !isVerifying,
                            onClick = { sendCode() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SButton(
                        L("Use a different email"),
                        onClick = {
                            step = SignInStep.Email
                            errorMessage = null
                            code = ""
                        },
                        kind = ButtonKind.Tertiary,
                    )
                }
            }

            errorMessage?.let { ErrorBanner(it) }

            Spacer(Modifier.height(Space.lg))

            // Footer
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { languageMenu = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Language, contentDescription = null, tint = Theme.colors.inkMuted, modifier = Modifier.size(18.dp))
                        Text(language.nativeName, style = Theme.type.bodySmall, color = Theme.colors.inkMuted)
                    }
                    LanguageMenu(expanded = languageMenu, onDismiss = { languageMenu = false }, onPick = {
                        model.setLanguage(it)
                        languageMenu = false
                    })
                }
                Text(
                    L("Sign in with a password"),
                    style = Theme.type.bodySmall,
                    color = Theme.colors.inkMuted,
                    modifier = Modifier
                        .clickable { showsWebSignIn = true }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun LanguageMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (AppLanguage) -> Unit) {
    val current by Localizer.languageFlow.collectAsStateWithLifecycle()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Theme.colors.surface,
        border = BorderStroke(1.dp, Theme.colors.hairline),
        shadowElevation = 0.dp,
    ) {
        for (language in AppLanguage.entries) {
            DropdownMenuItem(
                text = { Text(language.nativeName, style = Theme.type.body, color = Theme.colors.ink) },
                trailingIcon = {
                    if (language == current) Icon(Icons.Filled.Check, contentDescription = null, tint = Theme.colors.ink, modifier = Modifier.size(18.dp))
                },
                onClick = { onPick(language) },
            )
        }
    }
}

@Composable
private fun ResendButton(
    availableAt: Long,
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining by produceState(initialValue = remainingSeconds(availableAt), availableAt) {
        while (true) {
            value = remainingSeconds(availableAt)
            delay(1000)
        }
    }
    SButton(
        text = if (remaining > 0) L("New code in {time}", mapOf("time" to Countdown.clock(remaining))) else L("Send a new code"),
        onClick = onClick,
        kind = ButtonKind.Secondary,
        loading = isBusy,
        enabled = enabled && remaining <= 0,
        modifier = modifier.testTag("signIn.resend"),
    )
}

private fun remainingSeconds(availableAt: Long): Int {
    val ms = availableAt - System.currentTimeMillis()
    return if (ms <= 0) 0 else ((ms + 999) / 1000).toInt()
}

private fun openMailApp(context: android.content.Context) {
    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

/**
 * Six digit boxes over one text field. The field keeps paste and keyboard
 * code suggestions; digits past the sixth are dropped.
 */
@Composable
fun OneTimeCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    isWrong: Boolean,
    wrongCounter: Int,
    onComplete: (String) -> Unit,
) {
    val colors = Theme.colors
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(wrongCounter) {
        if (wrongCounter > 0) {
            repeat(3) {
                shake.animateTo(8f, tween(60))
                shake.animateTo(-8f, tween(60))
            }
            shake.animateTo(0f, tween(60))
        }
    }

    Box(modifier = Modifier.fillMaxWidth().offset { IntOffset(shake.value.dp.roundToPx(), 0) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.fillMaxWidth()) {
            val digits = code.toList()
            for (index in 0 until EmailCodeAuth.CODE_LENGTH) {
                val digit = digits.getOrNull(index)?.toString() ?: ""
                val isCurrent = focused && index == minOf(digits.size, EmailCodeAuth.CODE_LENGTH - 1)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .background(colors.surface, RoundedCornerShape(Radius.md))
                        .border(
                            BorderStroke(if (isCurrent) 1.5.dp else 1.dp, if (isWrong) colors.error else if (isCurrent) colors.ink else colors.hairline),
                            RoundedCornerShape(Radius.md),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        digit,
                        style = Theme.type.headline.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium),
                        color = colors.ink,
                    )
                }
            }
        }
        BasicTextField(
            value = code,
            onValueChange = { raw ->
                val digits = EmailCodeAuth.sanitize(raw)
                onCodeChange(digits)
                if (digits.length == EmailCodeAuth.CODE_LENGTH) onComplete(digits)
            },
            interactionSource = interaction,
            singleLine = true,
            textStyle = Theme.type.body.copy(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, capitalization = KeyboardCapitalization.None),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .focusRequester(focus)
                .testTag("signIn.code")
                .semantics { contentDescription = L("Sign-in code") },
        )
    }
}
