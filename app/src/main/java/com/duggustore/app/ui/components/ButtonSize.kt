package com.duggustore.app.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duggustore.app.ui.theme.DugguType
import com.duggustore.app.ui.theme.Dimens

enum class ButtonSize { LARGE, MEDIUM, SMALL }

enum class ButtonVariant {
    PRIMARY, SECONDARY, GHOST, DANGER, INVERSE
}

data class ButtonSizeSpec(
    val height: Int,
    val paddingH: Int,
    val paddingV: Int,
    val textStyle: androidx.compose.ui.text.TextStyle,
    val iconSize: Dp
)

private fun buttonSizeSpec(size: ButtonSize): ButtonSizeSpec {
    val d = DugguTheme.dimens
    return when (size) {
        ButtonSize.LARGE  -> ButtonSizeSpec(52, 20, 14, DugguType.titleLg, d.iconSm)
        ButtonSize.MEDIUM -> ButtonSizeSpec(44, 16, 12, DugguType.titleSm, d.iconSm)
        ButtonSize.SMALL  -> ButtonSizeSpec(36, 12, 8,  DugguType.titleSm, d.iconXs)
    }
}
