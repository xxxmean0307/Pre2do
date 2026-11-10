package com.pastelle.todo.data.avatar

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pastelle.todo.core.avatar.GrowthMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map

private val Context.avatarDataStore by preferencesDataStore(name = "avatar")

/** 养成状态（累计经验派生等级） */
data class AvatarGrowth(
    val totalXp: Int = 0,
) {
    val level: Int get() = GrowthMath.levelOf(totalXp)
    val xpIntoLevel: Int get() = GrowthMath.xpIntoLevel(totalXp)
    val xpForNext: Int get() = GrowthMath.levelSpan(level)
    val xpToNext: Int get() = GrowthMath.xpToNext(totalXp)
}

/**
 * 拟我持久化（全本地 DataStore）：
 * - 形象配置 JSON（null = 尚未创建，触发首次创建流程）
 * - 累计经验（完成度确认时由计时引擎写入）
 */
class AvatarStore(private val context: Context) {

    private val keyXp = intPreferencesKey("avatar_total_xp")

    val growthFlow: Flow<AvatarGrowth> = context.avatarDataStore.data.map { prefs ->
        AvatarGrowth(totalXp = prefs[keyXp] ?: 0)
    }

    private val _levelUps = MutableSharedFlow<LevelUpEvent>(extraBufferCapacity = 4)
    val levelUps: SharedFlow<LevelUpEvent> = _levelUps

    suspend fun addXp(delta: Int) {
        if (delta <= 0) return
        var emitted: LevelUpEvent? = null
        context.avatarDataStore.edit { prefs ->
            val cur = prefs[keyXp] ?: 0
            val new = cur + delta
            val oldLevel = GrowthMath.levelOf(cur)
            val newLevel = GrowthMath.levelOf(new)
            prefs[keyXp] = new
            if (newLevel > oldLevel) {
                val oldTier = GrowthMath.gearTierForLevel(oldLevel)
                val newTier = GrowthMath.gearTierForLevel(newLevel)
                emitted = LevelUpEvent(level = newLevel, unlockedTier = if (newTier != oldTier) newTier else null)
            }
        }
        emitted?.let { _levelUps.emit(it) }
    }
}

/** 升级事件（等级跃迁；装备档位变化时附带新解锁档位） */
data class LevelUpEvent(
    val level: Int,
    val unlockedTier: com.pastelle.todo.core.avatar.GearTier?,
)
