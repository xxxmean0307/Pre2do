package com.pastelle.todo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pastelle.todo.R

// 打包内置字体：Baloo 2（标题 / 数字）、Nunito（正文 / 标签）
val Baloo2 = FontFamily(
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold),
    Font(R.font.baloo2_bold, FontWeight.Bold),
)

val Nunito = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

/** 统一字号阶梯（规格 3.3 参考字号，不得在业务代码里写死字号） */
object PastelleType {
    /** 大问候 30sp */
    val Greeting = TextStyle(
        fontFamily = Baloo2,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    )

    /** 页面标题 22sp */
    val PageTitle = TextStyle(
        fontFamily = Baloo2,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    /** 卡片标题 17sp */
    val CardTitle = TextStyle(
        fontFamily = Baloo2,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    )

    /** 正文 15sp */
    val Body = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    /** 辅助 12sp */
    val Caption = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    /** 计时器数字 56sp（Baloo 2 等宽数字） */
    val Timer = TextStyle(
        fontFamily = Baloo2,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp,
        lineHeight = 62.sp,
        fontFeatureSettings = "tnum",
    )

    /** 按钮标签 */
    val ButtonLabel = TextStyle(
        fontFamily = Baloo2,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )

    /** 底部导航小字 11sp */
    val TabLabel = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

    /** 百分比数字（进度条右侧） */
    val Percent = TextStyle(
        fontFamily = Baloo2,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = "tnum",
    )
}

/** Material3 Typography 映射，保证系统组件也走同一套字体 */
val PastelleTypography = Typography(
    displayLarge = PastelleType.Greeting,
    headlineMedium = PastelleType.PageTitle,
    titleMedium = PastelleType.CardTitle,
    bodyMedium = PastelleType.Body,
    bodySmall = PastelleType.Caption,
    labelSmall = PastelleType.TabLabel,
)
