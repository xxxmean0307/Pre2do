package com.pastelle.todo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 全 App 统一页面背景（M28.18 起）：视频背景之上的「调和层」。
 *
 * 原先这里用程序化径向色斑模拟用户提供的流动渐变视频；现在视频已真正作为
 * 全局底层播放（见 [VideoBackground]），本层只负责把视频里较浓的品红/紫
 * 压成 App 同族的淡紫，保证深色 Ink 文字与白色卡片始终可读，同时让色彩
 * 仍能从下方缓缓透出——即「磨砂玻璃后的流动」。
 *
 * 视频不可用时，AppNav 最底层的静态淡紫渐变即为兜底，本层叠上去也是正常的
 * 静态背景，不会出现黑屏。参数保留是为了兼容现有 7 处调用点，不再参与绘制。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun PastelBackground(
    modifier: Modifier = Modifier,
    gradient: List<Color> = emptyList(),
    flowerSeed: Int = 7,
    flowerCount: Int = 5,
    floatingFlowers: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawVideoScrim() }
    ) {
        content()
    }
}

/**
 * 视频调和层：上/下略厚（护住状态栏文字与底部玻璃胶囊），中部略薄（让流动更明显）。
 * 纯 draw phase，零重组、零分配；改这里即可整体调「通透度 ↔ 可读性」。
 */
internal fun DrawScope.drawVideoScrim() {
    drawRect(
        Brush.verticalGradient(
            0.00f to Color(0xFFF9F5FD).copy(alpha = 0.60f),
            0.42f to Color(0xFFF4EEFA).copy(alpha = 0.50f),
            1.00f to Color(0xFFF7F2FC).copy(alpha = 0.64f),
        )
    )
}
