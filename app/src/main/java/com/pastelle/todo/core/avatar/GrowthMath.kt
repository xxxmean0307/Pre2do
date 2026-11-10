package com.pastelle.todo.core.avatar

/**
 * 养成系统数学（纯函数，可单测）：
 * 累计经验 → 等级 / 等级内进度 / 装备档位解锁。全部向下取整、单调递增。
 */
object GrowthMath {

    /** 升到 level（从 1 起）所需的累计经验：100·(n−1)·n/2 + 100·(n−1) —— 前快后慢的温和曲线 */
    fun xpForLevel(level: Int): Int {
        val n = level.coerceAtLeast(1)
        return 100 * (n - 1) + 50 * (n - 1) * (n - 2).coerceAtLeast(0)
    }

    /** 当前等级（≥1） */
    fun levelOf(totalXp: Int): Int {
        var lv = 1
        while (lv < 99 && totalXp >= xpForLevel(lv + 1)) lv++
        return lv
    }

    /** 距下一级还差的经验 */
    fun xpToNext(totalXp: Int): Int {
        val lv = levelOf(totalXp)
        return (xpForLevel(lv + 1) - totalXp).coerceAtLeast(0)
    }

    /** 本级已获得经验 */
    fun xpIntoLevel(totalXp: Int): Int {
        val lv = levelOf(totalXp)
        return (totalXp - xpForLevel(lv)).coerceAtLeast(0)
    }

    /** 本级总跨度 */
    fun levelSpan(level: Int): Int = (xpForLevel(level + 1) - xpForLevel(level)).coerceAtLeast(1)

    /** 装备档位：1/4/8/12/16 级依次解锁（CASUAL→SCOUT→ADVENTURER→GUNNER→LASER_MASTER） */
    fun gearTierForLevel(level: Int): GearTier = when {
        level >= 16 -> GearTier.LASER_MASTER
        level >= 12 -> GearTier.GUNNER
        level >= 8 -> GearTier.ADVENTURER
        level >= 4 -> GearTier.SCOUT
        else -> GearTier.CASUAL
    }

    /** 每档解锁等级 */
    val tierUnlockLevels: Map<GearTier, Int> = mapOf(
        GearTier.CASUAL to 1,
        GearTier.SCOUT to 4,
        GearTier.ADVENTURER to 8,
        GearTier.GUNNER to 12,
        GearTier.LASER_MASTER to 16,
    )
}
