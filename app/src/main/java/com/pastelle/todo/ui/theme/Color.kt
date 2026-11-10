package com.pastelle.todo.ui.theme

import androidx.compose.ui.graphics.Color

// ── 背景：竖向三段 pastel 渐变 ──────────────────────────────
val BgGradientTop = Color(0xFFFFD3E6)     // 樱花粉
val BgGradientMid = Color(0xFFF3E1FF)     // 淡紫
val BgGradientBottom = Color(0xFFE9E1F1)  // 淡紫收底（与流动背景同族）
val ScreenBase = Color(0xFFFFF8FB)        // 页面最底层底色

val CardWhite = Color(0xFFFFFFFF)

val Ink = Color(0xFF332933)               // 主文字（暖黑）
val InkSoft = Color(0xFF8B7E88)           // 次要文字
val Track = Color(0xFFF1E8F0)             // 进度条轨道/分隔

val Pink = Color(0xFFFF8FB8)
val PinkSoft = Color(0xFFFFD9E8)

val Purple = Color(0xFFB79CFF)
val PurpleSoft = Color(0xFFE7DDFF)

val Peach = Color(0xFFBC93D8)       // 淡紫（原桃黄，与流动背景同族）
val PeachSoft = Color(0xFFEDE2F8)

val Blue = Color(0xFF8AB8FF)
val BlueSoft = Color(0xFFE2EEFF)

val Green = Color(0xFF58CC02)             // 多邻国绿，仅用于完成/庆祝语义
val GreenSoft = Color(0xFFDDF6C8)

val Amber = Color(0xFFFFD166)
val Coral = Color(0xFFFF8F7E)
val GlowGreen = Color(0xFFB7F5C9)          // 100% 滑块光晕（规格 7.2 Glow）

val BlackPill = Color(0xFF2B2630)         // 黑色胶囊按钮

// ── 派生令牌 ──────────────────────────────────────────────
// 进度条填充渐变（"完成 = 变绿"）
val ProgressFillStart = Color(0xFF6FE09A)
val ProgressFillEnd = Color(0xFF4FC14F)

// ── 任务主题色映射（JSON color → 设计令牌）────────────────
fun com.pastelle.todo.core.TaskColor.mainColor(): Color = when (this) {
    com.pastelle.todo.core.TaskColor.Pink -> Pink
    com.pastelle.todo.core.TaskColor.Purple -> Purple
    com.pastelle.todo.core.TaskColor.Peach -> Peach
    com.pastelle.todo.core.TaskColor.Blue -> Blue
    com.pastelle.todo.core.TaskColor.Green -> Green
}

fun com.pastelle.todo.core.TaskColor.softColor(): Color = when (this) {
    com.pastelle.todo.core.TaskColor.Pink -> PinkSoft
    com.pastelle.todo.core.TaskColor.Purple -> PurpleSoft
    com.pastelle.todo.core.TaskColor.Peach -> PeachSoft
    com.pastelle.todo.core.TaskColor.Blue -> BlueSoft
    com.pastelle.todo.core.TaskColor.Green -> GreenSoft
}
