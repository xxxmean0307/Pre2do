package com.pastelle.todo.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 任务（规格 8 节） */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["date"],
            childColumns = ["date"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("date")],
)
data class TaskEntity(
    /** UUID */
    @PrimaryKey val id: String,
    val date: String,
    val title: String,
    /** 09:00 = 540 */
    val startMinute: Int,
    val durationMin: Int,
    val stage: String?,
    val location: String?,
    val note: String?,
    /** pink/purple/peach/blue/green */
    val color: String,
    val orderIndex: Int,

    // ── 运行时状态 ──
    val state: TaskState,
    /** 当前阶段（准备/专注/休息）的目标结束时刻 */
    val phaseEndEpochMs: Long,
    /** 已完成番茄钟数 */
    val pomodoroCount: Int,
    val startedAt: Long?,
    val completedAt: Long?,
    /** 完成时实际进行分钟 */
    val elapsedMinOnComplete: Int?,
    /** -20..+20 */
    val timeVariancePct: Int?,
    /** 30/60/100 */
    val completionPct: Int?,
    val xpEarned: Int?,
)
