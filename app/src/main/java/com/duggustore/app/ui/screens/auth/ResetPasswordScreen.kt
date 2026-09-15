package com.duggustore.app.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
fun ResetPasswordScreen(
    onSubmit: (newPassword: String, confirmPassword: String) -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    onClearError: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val passwordLongEnough = password.length >= 6
    val passwordsMatch = password.isNotBlank() && password == confirm
    val canSubmit = passwordLongEnough && passwordsMatch

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onClearError()
        onSubmit(password, confirm)
    }

    AuthScaffold(
        title = stringResource(R.string.auth_reset_title),
        subtitle = stringResource(R.string.auth_reset_subtitle)
    ) {
        if (error != null) {
            AuthErrorBanner(message = error, onDismiss = onClearError)
            Spacer(Modifier.height(18.dp))
        }

        AuthField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = stringResource(R.string.auth_new_password),
            placeholder = stringResource(R.string.auth_password_min_hint),
            leadingIcon = Icons.Default.Lock,
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
        )

        Spacer(Modifier.height(16.dp))

        AuthField(
            value = confirm,
            onValueChange = { confirm = it; onClearError() },
            label = stringResource(R.string.auth_confirm_new_password),
            placeholder = stringResource(R.string.auth_confirm_password_hint),
            leadingIcon = Icons.Default.Lock,
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { submit() }
        )

        Spacer(Modifier.height(6.dp))
        AuthRequirementRow(stringResource(R.string.auth_requirement_password_length), passwordLongEnough)
        AuthRequirementRow(stringResource(R.string.auth_requirement_password_match), passwordsMatch)

        Spacer(Modifier.height(18.dp))

        AuthPrimaryButton(
            text = stringResource(R.string.auth_save_password),
            onClick = { submit() },
            isLoading = isLoading,
            enabled = canSubmit
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.auth_cancel),
            modifier = Modifier
                .fillMaxWidth()
                .clickableText { onClearError(); onCancel() },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
