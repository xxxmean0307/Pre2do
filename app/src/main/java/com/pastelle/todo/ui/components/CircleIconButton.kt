package com.pastelle.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Ink

/**
 * 白色圆形图标按钮（参考图一头部铃铛 / 日历钮 / 返回钮样式）：
 * 白色圆底 + 柔和阴影 + 深色线性图标 + 按压缩放。
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    backgroundColor: Color = CardWhite,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .pastelleShadow()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 头像位：白色圆底里的一朵小花（参考图一的圆形头像位） */
@Composable
fun FlowerAvatar(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(CardWhite)
            .pastelleShadow(),
        contentAlignment = Alignment.Center,
    ) {
        FlowerBloom(
            size = size * 0.62f,
            petalColor = com.pastelle.todo.ui.theme.Pink,
            centerColor = com.pastelle.todo.ui.theme.PinkSoft,
        )
    }
}
