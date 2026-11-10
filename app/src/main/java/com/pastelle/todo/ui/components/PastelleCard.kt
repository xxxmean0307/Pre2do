package com.pastelle.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.PastelleElevation
import com.pastelle.todo.ui.theme.PastelleShapes

/** 统一粉紫弥散阴影修饰符（供卡片类组件复用） */
fun Modifier.pastelleShadow(): Modifier = this.shadow(
    elevation = PastelleElevation.Card,
    shape = PastelleShapes.Card,
    clip = false,
    ambientColor = PastelleElevation.CardShadowColor,
    spotColor = PastelleElevation.CardShadowSpotColor,
)

/**
 * 白色大圆角卡片：24dp 圆角、白色、粉紫色柔和弥散阴影（alpha 0.12、大模糊、Y 偏移 6dp）。
 */
@Composable
fun PastelleCard(
    modifier: Modifier = Modifier,
    containerColor: Color = CardWhite,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    elevation: Dp = PastelleElevation.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = PastelleShapes.Card,
                clip = false,
                ambientColor = PastelleElevation.CardShadowColor,
                spotColor = PastelleElevation.CardShadowSpotColor,
            )
            .clip(PastelleShapes.Card)
            .background(containerColor)
            .padding(contentPadding),
        content = content,
    )
}
