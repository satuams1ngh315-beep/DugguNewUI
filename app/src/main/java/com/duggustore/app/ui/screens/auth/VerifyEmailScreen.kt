package com.duggustore.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.ui.components.*
import com.duggustore.app.ui.theme.*
import kotlinx.coroutines.delay

private const val CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 30

@Composable
fun VerifyEmailScreen(
    email: String,
    onVerifyCode: (String) -> Unit,
    onResendEmail: () -> Unit,
    onBackToLogin: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    verificationResent: Boolean = false,
    onClearError: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    // Started the moment the person taps resend, not just after it succeeds —
    // otherwise nothing stopped them tapping it again immediately and hitting
    // Supabase's own rate limit, which only ever showed up as an error after
    // the fact.
    var cooldownSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000)
            cooldownSeconds -= 1
        }
    }

    AuthScaffold(
        title = stringResource(R.string.auth_verify_title),
        subtitle = stringResource(R.string.auth_verify_subtitle)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(88.dp)
                .clip(CircleShape)
                .background(TealSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MarkEmailRead,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.auth_sent_to),
            modifier = Modifier.fillMaxWidth(),
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Text(
            text = email,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Teal,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        CodeInput(
            code = code,
            enabled = !isLoading,
            onCodeChange = {
                if (it != code) {
                    code = it
                    onClearError()
                }
            }
        )

        Spacer(Modifier.height(22.dp))

        AuthPrimaryButton(
            text = stringResource(R.string.auth_verify_button),
            onClick = { onClearError(); onVerifyCode(code) },
            isLoading = isLoading,
            enabled = code.length == CODE_LENGTH
        )

        if (verificationResent) {
            Spacer(Modifier.height(16.dp))
            AuthInfoBanner(message = stringResource(R.string.auth_resend_success))
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            AuthErrorBanner(message = error, onDismiss = onClearError)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                onClearError()
                onResendEmail()
                cooldownSeconds = RESEND_COOLDOWN_SECONDS
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading && cooldownSeconds == 0,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (cooldownSeconds > 0)
                    stringResource(R.string.auth_resend_cooldown, cooldownSeconds)
                else stringResource(R.string.auth_resend_code),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.auth_back_to_sign_in),
            modifier = Modifier
                .fillMaxWidth()
                .clickableText { onClearError(); onBackToLogin() },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Six boxes fed by one hidden field. The field is what actually holds focus and
 * takes the keyboard; the boxes only draw what it contains.
 */
@Composable
private fun CodeInput(
    code: String,
    enabled: Boolean,
    onCodeChange: (String) -> Unit
) {
    BasicTextField(
        value = code,
        onValueChange = { input ->
            onCodeChange(input.filter { it.isDigit() }.take(CODE_LENGTH))
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        // The real text is never drawn; the boxes below stand in for it.
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        decorationBox = { innerTextField ->
          Box {
            // The real field stays in the composition so it can hold focus and
            // raise the keyboard. It draws nothing: its text and cursor are
            // both transparent.
            innerTextField()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(CODE_LENGTH) { index ->
                    val digit = code.getOrNull(index)
                    val active = index == code.length
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (digit != null) TealSurface else SurfaceMuted)
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) Teal else BorderGray,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit?.toString() ?: "",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
          }
        }
    )
}
