package com.pastelle.todo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 计时数学单测（规格 5.3 硬约束） */
class TimingMathTest {

    private val d90 = 90 // 分钟

    @Test
    fun `完成窗口在八成时开闸`() {
        val dMs = TimingMath.durationMs(d90)
        assertFalse(TimingMath.canComplete((dMs * 0.79).toLong(), d90))
        assertTrue(TimingMath.canComplete((dMs * 0.8).toLong(), d90))
        assertTrue(TimingMath.canComplete(dMs, d90))
    }

    @Test
    fun `十二成时强制弹出`() {
        val dMs = TimingMath.durationMs(d90)
        assertFalse(TimingMath.isForced((dMs * 1.19).toLong(), d90))
        assertTrue(TimingMath.isForced((dMs * 1.2).toLong(), d90))
    }

    @Test
    fun `时间偏差 variance 覆盖 -20 到 +20 并钳制`() {
        val dMs = TimingMath.durationMs(d90)
        assertEquals(-20, TimingMath.variancePct((dMs * 0.8).toLong(), d90))
        assertEquals(0, TimingMath.variancePct(dMs, d90))
        assertEquals(20, TimingMath.variancePct((dMs * 1.2).toLong(), d90))
        assertEquals(20, TimingMath.variancePct((dMs * 1.5).toLong(), d90))
    }

    @Test
    fun `XP 时间系数线性下降`() {
        val dMs = TimingMath.durationMs(d90)
        assertEquals(100, TimingMath.xpFor(100, dMs, d90))
        assertEquals(80, TimingMath.xpFor(100, (dMs * 1.2).toLong(), d90))
        assertEquals(54, TimingMath.xpFor(60, (dMs * 1.1).toLong(), d90))
        assertEquals(30, TimingMath.xpFor(30, (dMs * 0.8).toLong(), d90))
    }

    @Test
    fun `番茄钟计数按 35 分钟循环`() {
        val focus = PastelleConstants.FOCUS_SECONDS * 1000L
        val cycle = PastelleConstants.CYCLE_SECONDS * 1000L
        assertEquals(0, TimingMath.pomodorosCompleted(focus - 1))
        assertEquals(1, TimingMath.pomodorosCompleted(focus))
        assertEquals(1, TimingMath.pomodorosCompleted(cycle - 1))
        assertEquals(2, TimingMath.pomodorosCompleted(cycle + focus))
        assertEquals(1, TimingMath.pomodoroIndex(0L))
    }
}
