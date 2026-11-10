package com.pastelle.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pastelle.todo.ui.theme.BlackPill
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleElevation
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType

/** 黑色胶囊按钮右端内嵌圆形按钮的图标 */
enum class PillTrailingIcon {
    /** 黑底白箭头 */
    Arrow,

    /** 白 ✓ */
    Check,
}

/**
 * 黑色胶囊主按钮（规格 3.2）：
 * 全胶囊形、BlackPill + 白字，右端内嵌一个圆形按钮（黑底白箭头或白 ✓）。
 * 所有可点元素按压 scale 0.96、松手 spring 回 1.0。
 */
@Composable
fun BlackPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: PillTrailingIcon = PillTrailingIcon.Arrow,
    trailingContentDescription: String? = null,
    height: Dp = 60.dp,
    containerColor: Color = BlackPill,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = PastelleElevation.Pill,
                shape = PastelleShapes.Pill,
                clip = false,
                ambientColor = PastelleElevation.CardShadowColor,
                spotColor = PastelleElevation.CardShadowSpotColor,
            )
            .clip(PastelleShapes.Pill)
            .background(if (enabled) containerColor else InkSoft.copy(alpha = 0.45f))
            .pressScale(interactionSource, enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(start = 26.dp, end = 8.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PastelleType.ButtonLabel,
            color = CardWhite,
            maxLines = 1,
        )
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(height - 12.dp)
                .border(width = 1.5.dp, color = CardWhite.copy(alpha = 0.9f), shape = CircleShape)
                .clip(CircleShape)
                .background(BlackPill),
            contentAlignment = Alignment.Center,
        ) {
            val icon: ImageVector =
                if (trailingIcon == PillTrailingIcon.Arrow) Icons.AutoMirrored.Rounded.ArrowForward
                else Icons.Rounded.Check
            Icon(
                imageVector = icon,
                contentDescription = trailingContentDescription,
                tint = CardWhite,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
