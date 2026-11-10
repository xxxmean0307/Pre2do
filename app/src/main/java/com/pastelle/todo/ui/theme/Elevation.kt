package com.pastelle.todo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 阴影令牌（规格 3.2）：
 * 卡片使用柔和弥散阴影 —— 影色为粉紫色 alpha 0.12、模糊半径大、Y 偏移 6dp，
 * 禁止使用 Material 默认硬灰阴影。
 */
object PastelleElevation {
    /** 卡片阴影（大模糊半径） */
    val Card = 12.dp

    /** 卡片阴影 Y 偏移 */
    val CardShadowY = 6.dp

    /** 卡片阴影色：粉 alpha 0.12 */
    val CardShadowColor: Color = Pink.copy(alpha = 0.12f)

    /** 卡片阴影色（spot）：紫 alpha 0.12 */
    val CardShadowSpotColor: Color = Purple.copy(alpha = 0.12f)

    /** 滑块 thumb / 轨道阴影 */
    val Thumb = 10.dp
    val ThumbShadowColor: Color = Purple.copy(alpha = 0.18f)

    /** 胶囊按钮阴影 */
    val Pill = 4.dp

    /** 底部导航条阴影 */
    val BottomBar = 8.dp
}
