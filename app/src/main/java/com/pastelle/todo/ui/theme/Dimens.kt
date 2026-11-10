package com.pastelle.todo.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 组件级尺寸令牌（统一设计令牌，业务代码禁止写死尺寸）。
 * 规格明确的硬尺寸集中在此。
 */
object Dimens {
    /** 进度条高度 16dp 胶囊 */
    val ProgressBarHeight = 16.dp

    /** 表情脸标准格 40dp、圆角 14dp（规格 3.5） */
    val MoodFaceTile = 40.dp
    val MoodFaceRadius = 14.dp

    /** 日历格里的迷你表情脸 */
    val MoodFaceMini = 22.dp

    /** 日历日期格 */
    val CalendarCell = 46.dp

    /** 番茄圆点 */
    val PomodoroDot = 10.dp

    /** 时间轴圆点 / 竖线 */
    val TimelineDot = 14.dp
    val TimelineLine = 2.dp
    val TimelineRailWidth = 28.dp

    /** 卡片内边距 */
    val CardPadding = 20.dp

    // ── 任务执行页 / 完成滑块（规格 4.2、7.2）──

    /** 执行页圆环倒计时 */
    val ExecutionRing = 260.dp
    val ExecutionRingWidth = 14.dp
    val ExecutionRingThinWidth = 8.dp

    /** 竖向长椭圆完成滑块（规格 7.2 硬尺寸） */
    val SliderHeight = 340.dp
    val SliderWidth = 76.dp
    /** 手指可滑动映射区 0→260dp 映射填充 0→100% */
    val SliderFillMap = 260.dp
    val SliderThumb = 64.dp
    val SliderThumbActive = 72.dp
    val SliderThumbWave = 10.dp
    val SliderGlowBlur = 16.dp
    val SliderTrackStroke = 1.5.dp
    val SliderConfirm = 64.dp
    val SliderFaceBubble = 48.dp
}
