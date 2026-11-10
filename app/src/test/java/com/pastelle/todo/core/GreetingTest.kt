package com.pastelle.todo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingTest {

    @Test
    fun 全部完成走完成池() {
        assertTrue(Greeting.donePool.contains(Greeting.pick(hour = 9, completedCount = 3, totalCount = 3, seed = 1)))
    }

    @Test
    fun 部分完成走进度池并代入数量() {
        val g = Greeting.pick(hour = 9, completedCount = 2, totalCount = 5, seed = 3)
        assertTrue(Greeting.progressPool.any { it.replace("{n}", "2") == g })
    }

    @Test
    fun 未完成按时段分流() {
        assertTrue(Greeting.morningPool.contains(Greeting.pick(hour = 6, completedCount = 0, totalCount = 3, seed = 1)))
        assertTrue(Greeting.noonPool.contains(Greeting.pick(hour = 12, completedCount = 0, totalCount = 3, seed = 1)))
        assertTrue(Greeting.afternoonPool.contains(Greeting.pick(hour = 15, completedCount = 0, totalCount = 3, seed = 1)))
        assertTrue(Greeting.eveningPool.contains(Greeting.pick(hour = 20, completedCount = 0, totalCount = 3, seed = 1)))
        assertTrue(Greeting.nightPool.contains(Greeting.pick(hour = 23, completedCount = 0, totalCount = 3, seed = 1)))
    }

    @Test
    fun 同种子稳定且跨种子多样() {
        val a = Greeting.pick(hour = 9, completedCount = 0, totalCount = 3, seed = 42)
        assertEquals(a, Greeting.pick(hour = 9, completedCount = 0, totalCount = 3, seed = 42))
        val seen = (0 until 12).map { Greeting.pick(hour = 9, completedCount = 0, totalCount = 3, seed = it) }.toSet()
        assertTrue(seen.size > 1)
    }

    @Test
    fun 零任务也走时段问候() {
        val g = Greeting.pick(hour = 8, completedCount = 0, totalCount = 0, seed = 7)
        assertTrue(Greeting.morningPool.contains(g))
    }
}
