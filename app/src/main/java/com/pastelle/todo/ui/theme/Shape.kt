package com.pastelle.todo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 形状令牌（规格 3.2） */
object PastelleShapes {
    /** 卡片：圆角 24dp */
    val Card = RoundedCornerShape(24.dp)

    /** 对话框 / 底部卡片 */
    val Dialog = RoundedCornerShape(24.dp)

    /** 全胶囊按钮 */
    val Pill = RoundedCornerShape(999.dp)

    /** 输入框 / 芯片 */
    val Chip = RoundedCornerShape(999.dp)

    /** 进度条胶囊 */
    val Bar = RoundedCornerShape(999.dp)

    /** 表情脸圆角方块 */
    val Face = RoundedCornerShape(14.dp)

    /** 小圆角 */
    val Small = RoundedCornerShape(12.dp)

    /** 底部导航条上圆角 */
    val BottomBar = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}
