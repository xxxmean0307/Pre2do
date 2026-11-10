package com.pastelle.todo.core.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 养成数学（等级曲线 / 装备档位解锁） */
class GrowthMathTest {

    @Test
    fun `xpForLevel follows the published curve`() {
        assertEquals(0, GrowthMath.xpForLevel(1))
        assertEquals(100, GrowthMath.xpForLevel(2))
        assertEquals(300, GrowthMath.xpForLevel(3))
        assertEquals(600, GrowthMath.xpForLevel(4))
        assertEquals(1000, GrowthMath.xpForLevel(5))
    }

    @Test
    fun `level thresholds are inclusive at boundaries`() {
        assertEquals(1, GrowthMath.levelOf(0))
        assertEquals(1, GrowthMath.levelOf(99))
        assertEquals(2, GrowthMath.levelOf(100))
        assertEquals(2, GrowthMath.levelOf(299))
        assertEquals(3, GrowthMath.levelOf(300))
        assertEquals(4, GrowthMath.levelOf(600))
        assertEquals(5, GrowthMath.levelOf(1000))
    }

    @Test
    fun `level progress math is consistent`() {
        // 150 XP：位于 2 级（100–300），本级已入 50 / 跨度 200 / 还差 150
        assertEquals(50, GrowthMath.xpIntoLevel(150))
        assertEquals(200, GrowthMath.levelSpan(2))
        assertEquals(150, GrowthMath.xpToNext(150))
    }

    @Test
    fun `gear tiers unlock at 1-4-8-12-16`() {
        assertEquals(GearTier.CASUAL, GrowthMath.gearTierForLevel(1))
        assertEquals(GearTier.CASUAL, GrowthMath.gearTierForLevel(3))
        assertEquals(GearTier.SCOUT, GrowthMath.gearTierForLevel(4))
        assertEquals(GearTier.SCOUT, GrowthMath.gearTierForLevel(7))
        assertEquals(GearTier.ADVENTURER, GrowthMath.gearTierForLevel(8))
        assertEquals(GearTier.ADVENTURER, GrowthMath.gearTierForLevel(11))
        assertEquals(GearTier.GUNNER, GrowthMath.gearTierForLevel(12))
        assertEquals(GearTier.LASER_MASTER, GrowthMath.gearTierForLevel(16))
        assertEquals(GearTier.LASER_MASTER, GrowthMath.gearTierForLevel(30))
    }

    @Test
    fun `level is monotonic in xp`() {
        var last = 0
        for (xp in 0..2000 step 13) {
            val lv = GrowthMath.levelOf(xp)
            assertTrue("level decreased at xp=$xp", lv >= last)
            last = lv
        }
    }
}
