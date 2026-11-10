package com.pastelle.todo.core

/**
 * 核心计时常量（规格 5.1，硬约束，不得更改）。
 * 计时引擎（里程碑 4）与页面推导共用。
 */
object PastelleConstants {
    /** 5 分钟准备 */
    const val PREP_SECONDS = 300

    /** 35 分钟专注番茄钟 */
    const val FOCUS_SECONDS = 2100

    /** 番茄钟仅对 ≥70 分钟的任务设置；短任务一路专注、不设休息循环 */
    const val POMODORO_MIN_DURATION_MIN = 70

    /** 10 分钟休息 */
    const val BREAK_SECONDS = 600

    /** 最早可在计划时长的 80% 时完成（提前 ≤20%） */
    const val EARLY_FACTOR = 0.8

    /** 最晚可进行到计划时长的 120%（延后 ≤20%） */
    const val LATE_FACTOR = 1.2

    /** 滑块三档完成度 */
    val COMPLETION_GEARS: List<Double> = listOf(0.30, 0.60, 1.00)

    /** 一个循环 = 专注 + 休息 */
    val CYCLE_SECONDS: Int get() = FOCUS_SECONDS + BREAK_SECONDS
}
