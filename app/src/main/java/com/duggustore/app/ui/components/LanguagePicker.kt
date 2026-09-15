package com.duggustore.app.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.data.local.AppPrefs
import com.duggustore.app.platform.AppLanguage
import com.duggustore.app.ui.theme.*

/**
 * The "Eng" label in the home header used to be static text. It is a real
 * picker now: choosing a language stores it and recreates the activity, which
 * re-runs attachBaseContext and brings the whole app back in that language.
 */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = remember { AppLanguage.current(context) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .dugguClickable { open = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(current.short, fontSize = 14.sp, color = TextPrimary)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.language_title),
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                text = stringResource(R.string.language_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            AppLanguage.values().forEach { language ->
                val selected = language == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Teal else TextPrimary
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        open = false
                        if (!selected) {
                            AppPrefs.setLanguage(context, language.tag)
                            // Resources are resolved when the activity attaches,
                            // so the switch only takes effect on a fresh one.
                            (context as? Activity)?.recreate()
                        }
                    }
                )
            }
        }
    }
}
