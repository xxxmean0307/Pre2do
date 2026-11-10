package com.pastelle.todo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日记录（规格 2 节"日程、任务、每日记录"三类数据之一）：
 * 完成任务事务（规格 8 节 DAO 要求"同时写统计"）时同步 upsert 的日度汇总，
 * 供日历表情脸与统计页周 / 月聚合直接读取。
 */
@Entity(tableName = "daily_records")
data class DailyRecordEntity(
    /** YYYY-MM-DD */
    @PrimaryKey val date: String,
    /** 今日计划权重总和 = ΣD（分钟） */
    val plannedWeightMin: Double,
    /** 已获贡献 = Σ（D × 完成度档位） */
    val earnedWeightMin: Double,
    val completedTasks: Int,
    val totalTasks: Int,
    val pomodoroCount: Int,
    val focusMinutes: Int,
    /** 在 ±20% 窗口内主动完成的任务数 */
    val onTimeCount: Int,
    /** 100% 档位任务数（全勤连击） */
    val fullGearCount: Int,
    val xpTotal: Int,
    val updatedAt: Long,
)
