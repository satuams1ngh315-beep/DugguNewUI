package com.duggustore.app.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.ui.components.*
import com.duggustore.app.R
import com.duggustore.app.ui.theme.*

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onSkip: () -> Unit = {},
    isLoading: Boolean = false,
    error: String? = null,
    onLogin: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Only complain about a field once it has been typed in and left alone —
    // marking an untouched field red the moment the screen opens is noise.
    var emailTouched by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val emailLooksWrong = emailTouched && email.isNotBlank() && !isValidEmail(email)
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !emailLooksWrong

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onLogin(email.trim(), password)
    }

    AuthScaffold(
        title = stringResource(R.string.auth_welcome_title),
        subtitle = null,
        showLogoName = false,
        footer = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AuthSwitchRow(
                    question = stringResource(R.string.auth_new_here),
                    action = stringResource(R.string.auth_create_account),
                    onClick = onNavigateToRegister
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.auth_skip),
                    modifier = Modifier.clickable { onSkip() },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }
        }
    ) {
        Spacer(Modifier.height(12.dp))

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
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
        )

        Spacer(Modifier.height(16.dp))

        AuthField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = stringResource(R.string.auth_password),
            placeholder = stringResource(R.string.auth_password_hint),
            leadingIcon = Icons.Default.Lock,
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { submit() }
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.auth_forgot),
            modifier = Modifier
                .align(Alignment.End)
                .clickable { onForgotPassword() },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Teal
        )

        Spacer(Modifier.height(24.dp))

        AuthPrimaryButton(
            text = stringResource(R.string.auth_sign_in),
            onClick = { submit() },
            isLoading = isLoading,
            // Nothing to send until both fields carry something.
            enabled = canSubmit
        )
    }
}
