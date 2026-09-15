package com.duggustore.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.ui.theme.*

/**
 * Shared frame for the auth screens: logo, centred title, then the form
 * sitting directly on the page background — no card behind the fields,
 * which just doubled up on the outlined fields' own borders.
 *
 * A flat white page made every one of these screens feel bare above the
 * fields, so a soft gradient wash sits behind the logo, with two pale discs
 * bleeding off the top corners — the same soft-circle language the home
 * screen's offer cards already use — for a bit of depth without competing
 * with the form.
 */
@Composable
fun AuthScaffold(
    title: String?,
    subtitle: String?,
    showLogoName: Boolean = true,
    footer: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Brush.verticalGradient(listOf(TealSurface, Background)))
        )
        // One colour family, three depths — reads as a single considered
        // backdrop rather than the two unrelated brand accents this used to
        // mix (an orange disc and a teal one competing for attention).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-90).dp)
                .size(240.dp)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-70).dp, y = (-40).dp)
                .size(170.dp)
                .clip(CircleShape)
                .background(TealLight.copy(alpha = 0.14f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = 60.dp)
                .size(70.dp)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.16f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(showName = showLogoName)
            if (title != null || subtitle != null) {
                Spacer(Modifier.height(16.dp))
            }
            if (title != null) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
            if (title != null && subtitle != null) {
                Spacer(Modifier.height(6.dp))
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(22.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )

            Spacer(Modifier.height(20.dp))
            footer()
        }
    }
}

/**
 * The app's mark — the Duggu Store logo, matching the launcher icon — with
 * the name set as real text underneath rather than baked into the image, so
 * it stays crisp at any size and isn't stuck in whatever language the image
 * itself was drawn in. Sign in and sign up were the two screens with no
 * branding on them at all once the old teal header band was removed.
 */
@Composable
fun AppLogo(size: Int = 140, showName: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = "Duggu Store",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(size.dp)
                .aspectRatio(450f / 484f)
        )
        if (showName) {
            Spacer(Modifier.height((size / 20).dp))
            Text(
                text = stringResource(R.string.app_name),
                color = TextPrimary,
                fontSize = (size / 5).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Outlined field with a floating label, matching the compact pre-redesign
 * look the filled version replaced — the filled fields with a label stacked
 * above them took noticeably more vertical space per field for no real gain.
 * The leading icon takes its own tint rather than a fixed one: a whole form
 * of teal icons on a teal-bordered field reads as one flat colour rather
 * than as distinct fields.
 *
 * A password field carries its own show/hide toggle, and [helper] puts the
 * reason a value is wrong directly under the field it belongs to rather than
 * only in a banner at the top of the form.
 */
@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = BlinkitGreen,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    helper: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {}
) {
    var revealed by remember { mutableStateOf(false) }
    val hideCharacters = isPassword && !revealed

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, fontSize = 13.sp) },
            placeholder = if (placeholder.isBlank()) null else {
                { Text(placeholder, color = TextLight, fontSize = 14.sp) }
            },
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = leadingIconTint, modifier = Modifier.size(20.dp)) }
            },
            trailingIcon = if (!isPassword) null else {
                {
                    Icon(
                        imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (revealed) "Hide password" else "Show password",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { revealed = !revealed }
                    )
                }
            },
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() },
                onGo = { onImeAction() }
            ),
            visualTransformation =
                if (hideCharacters) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            // A soft filled tint at rest, with the border only appearing once
            // focused, reads less like a bare form and more like the rest of
            // the app's tinted surfaces (the search bar, the muted chips).
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceWhite,
                unfocusedContainerColor = SurfaceMuted,
                focusedBorderColor = BlinkitGreen,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = Coral,
                focusedLabelColor = BlinkitGreen,
                unfocusedLabelColor = TextSecondary,
                cursorColor = BlinkitGreen
            )
        )
        if (helper != null) {
            Text(
                text = helper,
                modifier = Modifier.padding(start = 14.dp, top = 5.dp),
                fontSize = 12.sp,
                color = if (isError) CoralDark else TextSecondary
            )
        }
    }
}

/** A requirement the person can watch turn green as they type, rather than a rule they only meet by trial and error. */
@Composable
fun AuthRequirementRow(text: String, met: Boolean) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) SuccessGreen else TextLight,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (met) SuccessGreen else TextSecondary
        )
    }
}

/** Full-width teal action button used at the bottom of each auth form. */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BlinkitGreen,
            disabledContainerColor = BorderGray
        ),
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** Coral banner for a failed attempt, dismissable so it does not sit forever. */
@Composable
fun AuthErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CoralSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = CoralDark,
                fontSize = 13.sp
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = CoralDark,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() }
            )
        }
    }
}

/** Teal banner for a message worth reading that is not a failure. */
@Composable
fun AuthInfoBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = TealSurface
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = TealDark,
            fontSize = 13.sp
        )
    }
}

/** Footer line pairing a question with the link to the other auth screen. */
@Composable
fun AuthSwitchRow(question: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(question, fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.width(4.dp))
        Text(
            text = action,
            modifier = Modifier.clickable { onClick() },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Teal
        )
    }
}

/**
 * Lets a plain Text act as a link. TextButton would work but brings its own
 * min-height and padding, which throws off the spacing in these forms.
 */
fun Modifier.clickableText(onClick: () -> Unit): Modifier = this.clickable { onClick() }
