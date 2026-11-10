package com.pastelle.todo.core

/**
 * 计时数学（规格 5.3，纯函数便于单测）：
 * 完成窗口（0.8D 开闸 / 1.2D 强制）、时间偏差 variance% = elapsed/D − 1、
 * XP = 100 × 档位 × 时间系数（elapsed ≤ D 为 1.0，线性下降到 1.2D 时 0.8）。
 * 已进行时长 elapsed 以实际专注开始时刻（startedAt，可能因「提前开始」早于 T0）为锚。
 */
object TimingMath {
    const val MINUTE_MS = 60_000L

    /** 准备段开始时刻 = T0 − 5 分钟 */
    fun prepStartMs(t0Ms: Long): Long = t0Ms - PastelleConstants.PREP_SECONDS * 1000L

    /** 计划时长毫秒 D */
    fun durationMs(durationMin: Int): Long = durationMin * MINUTE_MS

    /** 最早可完成时刻 = 锚 + 0.8D */
    fun earlyGateMs(anchorMs: Long, durationMin: Int): Long =
        anchorMs + (durationMs(durationMin) * PastelleConstants.EARLY_FACTOR).toLong()

    /** 最晚结束时刻 = 锚 + 1.2D（强制弹出完成滑块） */
    fun lateGateMs(anchorMs: Long, durationMin: Int): Long =
        anchorMs + (durationMs(durationMin) * PastelleConstants.LATE_FACTOR).toLong()

    /** 完成按钮开闸：elapsed ≥ 0.8D */
    fun canComplete(elapsedMs: Long, durationMin: Int): Boolean =
        elapsedMs >= (durationMs(durationMin) * PastelleConstants.EARLY_FACTOR).toLong()

    /** 距离开闸还有多久（已开闸返回 0） */
    fun msToCompleteGate(elapsedMs: Long, durationMin: Int): Long =
        ((durationMs(durationMin) * PastelleConstants.EARLY_FACTOR).toLong() - elapsedMs).coerceAtLeast(0L)

    /** 已到 1.2D：强制弹出完成滑块 */
    fun isForced(elapsedMs: Long, durationMin: Int): Boolean =
        elapsedMs >= lateGateMs(0L, durationMin)

    /** 时间偏差 variance% = elapsed/D − 1，钳制 −20..+20 */
    fun variancePct(elapsedMs: Long, durationMin: Int): Int {
        val d = durationMs(durationMin)
        if (d <= 0) return 0
        val raw = Math.round((elapsedMs.toDouble() / d - 1.0) * 100.0).toInt()
        return raw.coerceIn(-20, 20)
    }

    /** 时间系数：elapsed ≤ D 为 1.0，之后线性下降，1.2D 时为 0.8 */
    fun timeFactor(elapsedMs: Long, durationMin: Int): Double {
        val d = durationMs(durationMin).toDouble()
        if (d <= 0) return 1.0
        return when {
            elapsedMs <= d -> 1.0
            else -> {
                val over = ((elapsedMs - d) / (d * 0.2)).coerceAtMost(1.0)
                1.0 - 0.2 * over
            }
        }
    }

    /** XP = 100 × 完成度档位 × 时间系数（规格 5.3） */
    fun xpFor(gearPct: Int, elapsedMs: Long, durationMin: Int): Int =
        Math.round(100.0 * (gearPct / 100.0) * timeFactor(elapsedMs, durationMin)).toInt()

    /** 已完成番茄钟数（自锚点起走完的 35 分钟专注段，含 BREAK 中完成时的当前段未计） */
    fun pomodorosCompleted(elapsedMs: Long): Int {
        val cycle = PastelleConstants.CYCLE_SECONDS * 1000L
        val focus = PastelleConstants.FOCUS_SECONDS * 1000L
        if (elapsedMs <= 0) return 0
        val cycles = elapsedMs / cycle
        val inCycle = elapsedMs % cycle
        return (cycles + (if (inCycle >= focus) 1 else 0)).toInt()
    }

    /** 当前是第几个番茄钟（从 1 起） */
    fun pomodoroIndex(elapsedMs: Long): Int = pomodorosCompleted(elapsedMs) + 1
}
