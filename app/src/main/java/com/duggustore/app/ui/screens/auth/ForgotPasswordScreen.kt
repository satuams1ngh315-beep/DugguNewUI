package com.duggustore.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.ui.components.*
import com.duggustore.app.ui.theme.*

@Composable
fun ForgotPasswordScreen(
    onSendReset: (String) -> Unit,
    onBackToLogin: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    resetSent: Boolean = false,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    // Only complain once the field has actually been touched, matching Login/Register.
    var emailTouched by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val emailLooksWrong = emailTouched && email.isNotBlank() && !isValidEmail(email)
    val canSubmit = email.isNotBlank() && !emailLooksWrong

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onClearError()
        onSendReset(email.trim())
    }

    AuthScaffold(
        title = if (resetSent) stringResource(R.string.auth_forgot_sent_title) else stringResource(R.string.auth_forgot_title),
        subtitle = if (resetSent) stringResource(R.string.auth_forgot_sent_subtitle) else null,
        showLogoName = false
    ) {
        if (resetSent) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(TealSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MarkEmailRead,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.auth_forgot_sent_body),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )
        } else {
            if (error != null) {
                AuthErrorBanner(message = error, onDismiss = onClearError)
                Spacer(Modifier.height(18.dp))
            }

            AuthField(
                value = email,
                onValueChange = { email = it; emailTouched = true; onClearError() },
                label = stringResource(R.string.auth_email),
                placeholder = stringResource(R.string.auth_email_hint),
                leadingIcon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                helper = if (emailLooksWrong) stringResource(R.string.auth_err_email_invalid) else null,
                isError = emailLooksWrong,
                imeAction = ImeAction.Done,
                onImeAction = { submit() }
            )

            Spacer(Modifier.height(24.dp))

            AuthPrimaryButton(
                text = stringResource(R.string.auth_send_reset_link),
                onClick = { submit() },
                isLoading = isLoading,
                enabled = canSubmit
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.auth_back_to_sign_in),
            modifier = Modifier
                .fillMaxWidth()
                .clickableText { onClearError(); onBackToLogin() },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Teal,
            textAlign = TextAlign.Center
        )
    }
}
