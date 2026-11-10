package com.pastelle.todo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 统一 DAO（规格 8 节至少要求：按日期查询任务流、upsert 导入、完成任务事务（同时写统计）、查询周 / 月聚合）。
 */
@Dao
abstract class PastelleDao {

    // ── 查询：日程 / 任务 ──────────────────────

    @Query("SELECT * FROM schedules WHERE date = :date")
    abstract fun observeSchedule(date: String): Flow<ScheduleEntity?>

    @Query("SELECT * FROM schedules WHERE date = :date")
    abstract suspend fun scheduleOfDate(date: String): ScheduleEntity?

    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY orderIndex ASC")
    abstract fun observeTasks(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY orderIndex ASC")
    abstract suspend fun tasksOfDate(date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    abstract suspend fun taskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE state != 'COMPLETED' ORDER BY date ASC, orderIndex ASC")
    abstract fun observeUnfinishedTasks(): Flow<List<TaskEntity>>

    // ── 查询：每日记录与周 / 月聚合 ────────────

    @Query("SELECT * FROM daily_records WHERE date = :date")
    abstract fun observeDailyRecord(date: String): Flow<DailyRecordEntity?>

    @Query("SELECT * FROM daily_records WHERE date = :date")
    abstract suspend fun dailyRecordOf(date: String): DailyRecordEntity?

    /** 周聚合 / 月聚合（date 为 YYYY-MM-DD，字符串区间比较即日期区间比较） */
    @Query("SELECT * FROM daily_records WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    abstract fun observeDailyRange(start: String, end: String): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_records WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    abstract suspend fun dailyRange(start: String, end: String): List<DailyRecordEntity>

    @Query("SELECT * FROM tasks WHERE completedAt IS NOT NULL AND date BETWEEN :start AND :end ORDER BY completedAt ASC")
    abstract fun observeCompletedRange(start: String, end: String): Flow<List<TaskEntity>>

    // ── 导出（里程碑 7）──────────────────────

    @Query("SELECT * FROM schedules ORDER BY date ASC")
    abstract suspend fun allSchedules(): List<ScheduleEntity>

    @Query("SELECT * FROM tasks ORDER BY date ASC, orderIndex ASC")
    abstract suspend fun allTasks(): List<TaskEntity>

    @Query("SELECT date FROM schedules ORDER BY date DESC LIMIT 1")
    abstract suspend fun latestScheduleDate(): String?

    // ── 导入（覆盖式 upsert）──────────────────

    @Query("DELETE FROM schedules WHERE date = :date")
    abstract suspend fun deleteScheduleOfDay(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSchedule(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM daily_records WHERE date = :date")
    abstract suspend fun deleteDailyRecord(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDailyRecord(record: DailyRecordEntity)

    /** 覆盖式导入：删旧（CASCADE 连带任务）→ 写入新日程 */
    @Transaction
    open suspend fun importDay(schedule: ScheduleEntity, tasks: List<TaskEntity>) {
        deleteScheduleOfDay(schedule.date)
        insertSchedule(schedule)
        insertTasks(tasks)
    }

    // ── 运行时状态 ────────────────────────────

    @Query("SELECT * FROM tasks WHERE state != 'COMPLETED' ORDER BY date ASC, orderIndex ASC")
    abstract suspend fun unfinishedTasks(): List<TaskEntity>

    @Query("UPDATE tasks SET state = :state, phaseEndEpochMs = :phaseEnd WHERE id = :taskId")
    abstract suspend fun updatePhase(taskId: String, state: TaskState, phaseEnd: Long)

    /** 实际专注开始时刻（仅首次设置：提前开始=此刻，自动/晚到=计划时刻 T0） */
    @Query("UPDATE tasks SET startedAt = :atMs WHERE id = :taskId AND startedAt IS NULL")
    abstract suspend fun markStarted(taskId: String, atMs: Long)

    @Query("UPDATE tasks SET pomodoroCount = pomodoroCount + 1 WHERE id = :taskId")
    abstract suspend fun incrementPomodoro(taskId: String)

    @Query(
        "UPDATE tasks SET state = :state, completedAt = :completedAt, elapsedMinOnComplete = :elapsedMin, " +
            "timeVariancePct = :variancePct, completionPct = :completionPct, xpEarned = :xp, " +
            "pomodoroCount = :pomodoroCount, phaseEndEpochMs = 0 WHERE id = :taskId"
    )
    abstract suspend fun markCompleted(
        taskId: String,
        state: TaskState,
        completedAt: Long,
        elapsedMin: Int,
        variancePct: Int,
        completionPct: Int,
        xp: Int,
        pomodoroCount: Int,
    )

    @Query(
        "UPDATE tasks SET state = 'WAITING', phaseEndEpochMs = 0, pomodoroCount = 0, " +
            "startedAt = NULL, completedAt = NULL, elapsedMinOnComplete = NULL, " +
            "timeVariancePct = NULL, completionPct = NULL, xpEarned = NULL WHERE id = :taskId"
    )
    abstract suspend fun resetTask(taskId: String)

    /**
     * 完成任务事务（规格 8 节）：更新任务统计的同时写入每日记录。
     * onTime = |variance| ≤ 20 且未到 1.2D 强制边界（variance < +20）；
     * fullGear = 100% 档位（计入全勤连击）。
     */
    @Transaction
    open suspend fun completeTask(
        taskId: String,
        completedAt: Long,
        elapsedMin: Int,
        variancePct: Int,
        completionPct: Int,
        xp: Int,
        pomodoroCount: Int,
    ) {
        val task = taskById(taskId) ?: return
        markCompleted(
            taskId = taskId,
            state = TaskState.COMPLETED,
            completedAt = completedAt,
            elapsedMin = elapsedMin,
            variancePct = variancePct,
            completionPct = completionPct,
            xp = xp,
            pomodoroCount = pomodoroCount,
        )
        val old = dailyRecordOf(task.date) ?: DailyRecordEntity(
            date = task.date,
            plannedWeightMin = 0.0,
            earnedWeightMin = 0.0,
            completedTasks = 0,
            totalTasks = 0,
            pomodoroCount = 0,
            focusMinutes = 0,
            onTimeCount = 0,
            fullGearCount = 0,
            xpTotal = 0,
            updatedAt = 0L,
        )
        val total = tasksOfDate(task.date).size
        upsertDailyRecord(
            old.copy(
                earnedWeightMin = old.earnedWeightMin + task.durationMin * (completionPct / 100.0),
                completedTasks = old.completedTasks + 1,
                totalTasks = total,
                // ★ 累加而非覆盖：完成第二个任务时不能抹掉第一个任务的番茄数
                pomodoroCount = old.pomodoroCount + pomodoroCount,
                focusMinutes = old.focusMinutes + elapsedMin,
                onTimeCount = old.onTimeCount + if (variancePct in -20..19) 1 else 0,
                fullGearCount = old.fullGearCount + if (completionPct == 100) 1 else 0,
                xpTotal = old.xpTotal + xp,
                updatedAt = completedAt,
            )
        )
    }

    /** 轮盘编辑器：日程头仅在缺失时插入（IGNORE 不触发任务 CASCADE） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertScheduleIfAbsent(schedule: ScheduleEntity)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    abstract suspend fun deleteTasksByIds(ids: List<String>)

    /** 导入时登记当日计划权重与任务数（0 完成的基础记录） */
    @Transaction
    open suspend fun registerDayPlan(date: String, tasks: List<TaskEntity>, now: Long) {
        deleteDailyRecord(date)
        upsertDailyRecord(
            DailyRecordEntity(
                date = date,
                plannedWeightMin = tasks.sumOf { it.durationMin }.toDouble(),
                earnedWeightMin = 0.0,
                completedTasks = 0,
                totalTasks = tasks.size,
                pomodoroCount = 0,
                focusMinutes = 0,
                onTimeCount = 0,
                fullGearCount = 0,
                xpTotal = 0,
                updatedAt = now,
            )
        )
    }
}
