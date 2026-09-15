package com.duggustore.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.ui.components.*
import com.duggustore.app.R
import com.duggustore.app.ui.theme.*

private const val STEP_NAME = 0
private const val STEP_EMAIL = 1
private const val STEP_PHONE = 2
private const val STEP_ROLE = 3
private const val STEP_PASSWORD = 4
private const val STEP_REFERRAL = 5
private const val STEP_COUNT = 6

/**
 * One question per screen, with a progress bar, a "Step 3 of 6" count and a
 * back arrow that always goes somewhere — the way Facebook's sign-up guides
 * you through, rather than one long form that dumps every field on the
 * person at once. Each step says why it's asking, and validates as you type
 * instead of failing at the end.
 */
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    successMessage: String? = null,
    onRegister: (String, String, String, String, String, String) -> Unit,
    onClearError: () -> Unit,
    onClearSuccess: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(STEP_NAME) }
    var movingForward by remember { mutableStateOf(true) }

    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var referralCode by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf("customer") }
    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current

    val roles = listOf(
        RoleChoice(
            "customer", stringResource(R.string.auth_role_shop),
            stringResource(R.string.auth_role_shop_blurb), Icons.Default.ShoppingBag, Teal
        ),
        RoleChoice(
            "seller", stringResource(R.string.auth_role_sell),
            stringResource(R.string.auth_role_sell_blurb), Icons.Default.Storefront, Orange
        ),
        RoleChoice(
            "delivery", stringResource(R.string.auth_role_deliver),
            stringResource(R.string.auth_role_deliver_blurb), Icons.Default.LocalShipping, Violet
        )
    )

    val shownError = localError ?: error

    // Resolved here rather than inside goNext(): that function is not a
    // composable scope and cannot call stringResource.
    val errNoName = stringResource(R.string.auth_err_name)
    val errNoEmail = stringResource(R.string.auth_err_email)
    val errBadEmail = stringResource(R.string.auth_err_email_invalid)
    val errNoPhone = stringResource(R.string.auth_err_phone)
    val errShortPassword = stringResource(R.string.auth_err_password_short)
    val errPasswordMatch = stringResource(R.string.auth_err_password_match)
    val errTerms = stringResource(R.string.auth_err_terms)

    val passwordLongEnough = password.length >= 6
    val passwordsMatch = password.isNotBlank() && password == confirmPassword

    // What the current step needs before Next means anything. Keeping this in
    // one place is what lets the button below both disable itself and explain
    // the problem, instead of silently doing nothing when tapped.
    val stepProblem: String? = when (step) {
        STEP_NAME -> if (fullName.isBlank()) errNoName else null
        STEP_EMAIL -> when {
            email.isBlank() -> errNoEmail
            !isValidEmail(email) -> errBadEmail
            else -> null
        }
        STEP_PHONE -> if (phone.filter { it.isDigit() }.length < 10) errNoPhone else null
        STEP_PASSWORD -> when {
            !passwordLongEnough -> errShortPassword
            !passwordsMatch -> errPasswordMatch
            else -> null
        }
        STEP_REFERRAL -> if (!termsAccepted) errTerms else null
        else -> null
    }

    fun goNext() {
        if (stepProblem != null) {
            localError = stepProblem
            return
        }
        localError = null
        onClearError()
        focusManager.clearFocus()

        if (step == STEP_REFERRAL) {
            onClearSuccess()
            onRegister(email.trim(), password, fullName.trim(), phone.trim(), selectedRole, referralCode.trim())
        } else {
            movingForward = true
            step++
        }
    }

    fun goBack() {
        localError = null
        onClearError()
        focusManager.clearFocus()
        if (step == STEP_NAME) {
            onNavigateToLogin()
        } else {
            movingForward = false
            step--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::goBack) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.auth_back), tint = TextPrimary)
            }
            StepProgressBar(current = step, total = STEP_COUNT, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auth_step_of, step + 1, STEP_COUNT),
                modifier = Modifier.padding(horizontal = 12.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = slideStepTransition(movingForward),
                label = "signup-step"
            ) { s ->
                Column {
                    when (s) {
                        STEP_NAME -> {
                            StepHeading(
                                stringResource(R.string.auth_step_name_title),
                                stringResource(R.string.auth_step_name_body)
                            )
                            AuthField(
                                value = fullName,
                                onValueChange = { fullName = it; localError = null; onClearError() },
                                label = stringResource(R.string.auth_full_name),
                                placeholder = stringResource(R.string.auth_full_name_hint),
                                leadingIcon = Icons.Default.Person,
                                imeAction = ImeAction.Next,
                                onImeAction = { goNext() }
                            )
                            StepIllustration(Icons.Default.Person, Teal, stringResource(R.string.auth_step_name_illustration))
                        }
                        STEP_EMAIL -> {
                            StepHeading(
                                stringResource(R.string.auth_step_email_title),
                                stringResource(R.string.auth_step_email_body)
                            )
                            AuthField(
                                value = email,
                                onValueChange = { email = it; localError = null; onClearError() },
                                label = stringResource(R.string.auth_email),
                                placeholder = stringResource(R.string.auth_email_hint),
                                leadingIcon = Icons.Default.Email,
                                keyboardType = KeyboardType.Email,
                                helper = if (email.isNotBlank() && stepProblem == errBadEmail) errBadEmail else null,
                                isError = email.isNotBlank() && stepProblem == errBadEmail,
                                imeAction = ImeAction.Next,
                                onImeAction = { goNext() }
                            )
                            StepIllustration(Icons.Default.Email, Teal, stringResource(R.string.auth_step_email_illustration))
                        }
                        STEP_PHONE -> {
                            StepHeading(
                                stringResource(R.string.auth_step_phone_title),
                                stringResource(R.string.auth_step_phone_body)
                            )
                            AuthField(
                                value = phone,
                                onValueChange = { input ->
                                    phone = input.filter { it.isDigit() }.take(10)
                                    localError = null
                                    onClearError()
                                },
                                label = stringResource(R.string.auth_phone),
                                placeholder = stringResource(R.string.auth_phone_hint),
                                leadingIcon = Icons.Default.Phone,
                                keyboardType = KeyboardType.Phone,
                                helper = if (phone.length < 10) stringResource(R.string.auth_phone_progress, phone.length) else null,
                                imeAction = ImeAction.Next,
                                onImeAction = { goNext() }
                            )
                            StepIllustration(Icons.Default.LocalShipping, Teal, stringResource(R.string.auth_step_phone_illustration))
                        }
                        STEP_ROLE -> {
                            StepHeading(
                                stringResource(R.string.auth_step_role_title),
                                stringResource(R.string.auth_step_role_body)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                roles.forEach { choice ->
                                    RoleCard(
                                        choice = choice,
                                        selected = choice.value == selectedRole,
                                        onClick = { selectedRole = choice.value }
                                    )
                                }
                            }
                        }
                        STEP_PASSWORD -> {
                            StepHeading(
                                stringResource(R.string.auth_step_password_title),
                                stringResource(R.string.auth_step_password_body)
                            )
                            AuthField(
                                value = password,
                                onValueChange = { password = it; localError = null; onClearError() },
                                label = stringResource(R.string.auth_password),
                                placeholder = stringResource(R.string.auth_password_min_hint),
                                leadingIcon = Icons.Default.Lock,
                                keyboardType = KeyboardType.Password,
                                isPassword = true,
                                imeAction = ImeAction.Next,
                                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                            Spacer(Modifier.height(14.dp))
                            AuthField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; localError = null },
                                label = stringResource(R.string.auth_confirm_password),
                                placeholder = stringResource(R.string.auth_confirm_password_hint),
                                leadingIcon = Icons.Default.Lock,
                                keyboardType = KeyboardType.Password,
                                isPassword = true,
                                imeAction = ImeAction.Done,
                                onImeAction = { goNext() }
                            )
                            Spacer(Modifier.height(6.dp))
                            AuthRequirementRow(stringResource(R.string.auth_requirement_password_length), passwordLongEnough)
                            AuthRequirementRow(stringResource(R.string.auth_requirement_password_match), passwordsMatch)
                            StepIllustration(Icons.Default.Lock, Teal, stringResource(R.string.auth_step_password_illustration))
                        }
                        else -> {
                            StepHeading(
                                stringResource(R.string.auth_step_referral_title),
                                stringResource(R.string.auth_step_referral_body)
                            )
                            AuthField(
                                value = referralCode,
                                onValueChange = { referralCode = it.uppercase(); localError = null; onClearError() },
                                label = stringResource(R.string.auth_referral_code_label),
                                placeholder = stringResource(R.string.auth_referral_code_hint),
                                leadingIcon = Icons.Default.CardGiftcard,
                                imeAction = ImeAction.Done,
                                onImeAction = { goNext() }
                            )
                            Spacer(Modifier.height(18.dp))
                            SummaryCard(
                                name = fullName,
                                email = email,
                                phone = phone,
                                role = roles.firstOrNull { it.value == selectedRole }?.label.orEmpty()
                            )
                            Spacer(Modifier.height(18.dp))
                            TermsAcceptanceRow(
                                checked = termsAccepted,
                                onCheckedChange = { termsAccepted = it; localError = null; onClearError() },
                                onLinkClick = { showTermsDialog = true }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (shownError != null) {
                AuthErrorBanner(message = shownError, onDismiss = { localError = null; onClearError() })
                Spacer(Modifier.height(12.dp))
            }
            if (successMessage != null) {
                AuthInfoBanner(message = successMessage)
                Spacer(Modifier.height(12.dp))
            }
            AuthPrimaryButton(
                text = if (step == STEP_REFERRAL) stringResource(R.string.auth_create_title)
                       else stringResource(R.string.auth_next),
                onClick = ::goNext,
                isLoading = isLoading && step == STEP_REFERRAL,
                enabled = stepProblem == null
            )
            if (step == STEP_NAME) {
                Spacer(Modifier.height(12.dp))
                AuthSwitchRow(
                    question = stringResource(R.string.auth_have_account),
                    action = stringResource(R.string.auth_sign_in),
                    onClick = onNavigateToLogin
                )
            }
        }
    }

    if (showTermsDialog) {
        TermsPrivacyDialog(onDismiss = { showTermsDialog = false })
    }
}

/** A tap target big enough to actually hit, rather than just the checkbox's own small square. */
@Composable
private fun TermsAcceptanceRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLinkClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Teal)
        )
        Spacer(Modifier.width(2.dp))
        Text(stringResource(R.string.auth_terms_prefix), fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.auth_terms_link),
            modifier = Modifier.clickable { onLinkClick() },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Teal
        )
    }
}

@Composable
private fun TermsPrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_terms_link)) },
        text = {
            Text(
                stringResource(R.string.auth_terms_dialog_body),
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 19.sp,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close), fontWeight = FontWeight.Bold, color = Teal)
            }
        }
    )
}

private data class RoleChoice(
    val value: String,
    val label: String,
    val blurb: String,
    val icon: ImageVector,
    val accent: androidx.compose.ui.graphics.Color
)

@Composable
private fun RoleCard(choice: RoleChoice, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) choice.accent.copy(alpha = 0.08f) else SurfaceWhite,
        border = BorderStroke(1.5.dp, if (selected) choice.accent else BorderGray)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(choice.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(choice.icon, null, tint = choice.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(choice.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(choice.blurb, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = choice.accent)
            )
        }
    }
}

/** Last-step recap, so nobody submits a typo they made four screens ago without seeing it. */
@Composable
private fun SummaryCard(name: String, email: String, phone: String, role: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceMuted
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.auth_summary_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            SummaryRow(stringResource(R.string.auth_summary_name), name)
            SummaryRow(stringResource(R.string.auth_summary_email), email)
            SummaryRow(stringResource(R.string.auth_summary_phone), phone)
            SummaryRow(stringResource(R.string.auth_summary_role), role)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.auth_summary_edit_hint),
                fontSize = 11.sp,
                color = TextLight
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(90.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}
