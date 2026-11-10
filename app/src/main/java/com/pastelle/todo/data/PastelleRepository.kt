package com.pastelle.todo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.data.db.DailyRecordEntity
import com.pastelle.todo.data.db.PastelleDatabase
import com.pastelle.todo.data.db.ScheduleEntity
import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState
import com.pastelle.todo.data.json.ParsedSchedule
import com.pastelle.todo.data.json.ParsedTask
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

val Context.settingsDataStore by preferencesDataStore(name = "pastelle_settings")

/**
 * 数据仓库：导入写入、查询、完成任务事务、导出。
 * 倒计时/状态机相关方法在里程碑 4 接入。
 */
class PastelleRepository(
    private val database: PastelleDatabase,
    val context: Context,
) {
    private val dao get() = database.dao()

    val settings = context.settingsDataStore

    // ── 查询 ──────────────────────────────────

    fun observeSchedule(date: String): Flow<ScheduleEntity?> = dao.observeSchedule(date)

    fun observeTasks(date: String): Flow<List<TaskEntity>> = dao.observeTasks(date)

    fun observeDailyRecord(date: String): Flow<DailyRecordEntity?> = dao.observeDailyRecord(date)

    fun observeDailyRange(start: String, end: String): Flow<List<DailyRecordEntity>> =
        dao.observeDailyRange(start, end)

    fun observeCompletedRange(start: String, end: String): Flow<List<TaskEntity>> =
        dao.observeCompletedRange(start, end)

    suspend fun hasSchedule(date: String): Boolean = dao.scheduleOfDate(date) != null

    suspend fun dailyRecordOf(date: String): DailyRecordEntity? = dao.dailyRecordOf(date)

    // ── 导入（规格 6.2：确认后写入 Room）──────

    suspend fun importSchedule(parsed: ParsedSchedule, now: Long = System.currentTimeMillis()) {
        val entities = parsed.tasks.map { it.toEntity(parsed.date) }
        database.withTransaction {
            dao.importDay(
                schedule = ScheduleEntity(
                    date = parsed.date,
                    title = parsed.title,
                    importedAt = now,
                ),
                tasks = entities,
            )
            dao.registerDayPlan(parsed.date, entities, now)
        }
        com.pastelle.todo.widget.TodayWidget.refresh(context)   // 桌面小组件即时刷新
    }

    /** 完成任务事务（任务统计 + 每日记录同步写入） */
    suspend fun completeTask(
        taskId: String,
        completedAt: Long,
        elapsedMin: Int,
        variancePct: Int,
        completionPct: Int,
        xp: Int,
        pomodoroCount: Int,
    ) {
        dao.completeTask(
            taskId = taskId,
            completedAt = completedAt,
            elapsedMin = elapsedMin,
            variancePct = variancePct,
            completionPct = completionPct,
            xp = xp,
            pomodoroCount = pomodoroCount,
        )
        com.pastelle.todo.widget.TodayWidget.refresh(context)
    }

    suspend fun resetTask(taskId: String) {
        dao.resetTask(taskId)
        com.pastelle.todo.widget.TodayWidget.refresh(context)
    }

    // ── 计时引擎（里程碑 4）────────────────────

    suspend fun unfinishedTasks(): List<TaskEntity> = dao.unfinishedTasks()

    suspend fun taskById(taskId: String): TaskEntity? = dao.taskById(taskId)

    /** 保存任务整体（延迟等原子更新用；REPLACE 语义） */
    suspend fun saveTask(task: TaskEntity) {
        dao.insertTasks(listOf(task))
        com.pastelle.todo.widget.TodayWidget.refresh(context)
    }

    suspend fun tasksOfDate(date: String): List<TaskEntity> = dao.tasksOfDate(date)

    /**
     * 轮盘编辑器：按日保存计划块。
     * 同 id 块保留运行时状态（进行中/已完成不丢），消失的块删除；无每日记录时登记计划基线。
     */
    suspend fun saveWheelDay(
        date: String,
        title: String,
        tasks: List<TaskEntity>,
        now: Long = System.currentTimeMillis(),
    ) {
        database.withTransaction {
            val hadRecord = dao.dailyRecordOf(date) != null
            dao.insertScheduleIfAbsent(ScheduleEntity(date = date, title = title, importedAt = now))
            val oldById = dao.tasksOfDate(date).associateBy { it.id }
            dao.insertTasks(
                tasks.map { t ->
                    val o = oldById[t.id] ?: return@map t
                    val timeChanged = o.startMinute != t.startMinute || o.durationMin != t.durationMin
                    if (timeChanged && o.state == TaskState.COMPLETED) {
                        // ★ 改起止时间＝重新规划：旧完成态/统计全部失效，回到「未开始」。
                        //   否则把任务改成 07:09、或把截止时间改到 10:30 之后仍挂着「已完成」。
                        //   taskOf() 生成的 t 本身即 WAITING + 空统计，直接采用。
                        t.copy(state = TaskState.WAITING)
                    } else {
                        // 同 id 块保留运行时状态（进行中/已完成不丢），消失的块删除
                        t.copy(
                            // 顺手洗掉「标了 COMPLETED 却无完成时刻」的遗留脏数据
                            // ★ 轮盘只编辑时间/标题/颜色，导入带来的阶段·地点·备注必须保留，
                            //   否则任何一次拖动分隔线都会把这些字段清空。
                            stage = o.stage,
                            location = o.location,
                            note = o.note,
                            state = if (o.state == TaskState.COMPLETED && o.completedAt == null) {
                                TaskState.WAITING
                            } else o.state,
                            phaseEndEpochMs = o.phaseEndEpochMs,
                            pomodoroCount = o.pomodoroCount,
                            startedAt = o.startedAt,
                            completedAt = o.completedAt,
                            elapsedMinOnComplete = o.elapsedMinOnComplete,
                            timeVariancePct = o.timeVariancePct,
                            completionPct = o.completionPct,
                            xpEarned = o.xpEarned,
                        )
                    }
                }
            )
            val stale = oldById.keys - tasks.map { it.id }.toSet()
            if (stale.isNotEmpty()) dao.deleteTasksByIds(stale.toList())
            if (!hadRecord) dao.registerDayPlan(date, tasks, now)
        }
        com.pastelle.todo.widget.TodayWidget.refresh(context)
    }

    suspend fun updatePhase(taskId: String, state: TaskState, phaseEnd: Long) =
        dao.updatePhase(taskId, state, phaseEnd)

    suspend fun markStarted(taskId: String, atMs: Long) = dao.markStarted(taskId, atMs)

    suspend fun incrementPomodoro(taskId: String) = dao.incrementPomodoro(taskId)

    // ── 统计与导出（里程碑 7）──────────────────

    suspend fun latestScheduleDate(): String? = dao.latestScheduleDate()

    /** 按天导出导入同款 JSON（可直接重新导入的往返格式） */
    suspend fun exportDayJson(date: String): String? {
        val tasks = dao.tasksOfDate(date)
        if (tasks.isEmpty()) return null
        return com.pastelle.todo.data.json.ScheduleExporter.exportDay(
            date = date,
            title = dao.scheduleOfDate(date)?.title,
            tasks = tasks,
        )
    }

    // ── DataStore：全勤连击 / 待播庆祝（规格 5.3、9.6）──

    private val keyStreak = intPreferencesKey("streak")
    private val keyPendingCelebration = stringPreferencesKey("pending_celebration")

    /** 仅 100% 档位计入全勤连击；返回新连击数 */
    suspend fun bumpStreak(): Int {
        var next = 0
        settings.edit { prefs ->
            next = (prefs[keyStreak] ?: 0) + 1
            prefs[keyStreak] = next
        }
        return next
    }

    suspend fun resetStreak(): Int {
        settings.edit { prefs -> prefs[keyStreak] = 0 }
        return 0
    }

    suspend fun streak(): Int =
        settings.data.first()[keyStreak] ?: 0

    /** 「taskId|gear|xp|streak」；先落库再播放，杀进程后可补播（规格 9.6） */
    suspend fun setPendingCelebration(taskId: String, gearPct: Int, xp: Int, streak: Int) {
        settings.edit { prefs -> prefs[keyPendingCelebration] = "$taskId|$gearPct|$xp|$streak" }
    }

    suspend fun pendingCelebration(): String? =
        settings.data.first()[keyPendingCelebration]

    suspend fun clearPendingCelebration() {
        settings.edit { prefs -> prefs.remove(keyPendingCelebration) }
    }

    companion object {
        fun ParsedTask.toEntity(date: String): TaskEntity = TaskEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            title = title,
            startMinute = startMinute,
            durationMin = durationMin,
            stage = stage,
            location = location,
            note = note,
            color = color.key,
            orderIndex = orderIndex,
            state = TaskState.WAITING,
            phaseEndEpochMs = 0L,
            pomodoroCount = 0,
            startedAt = null,
            completedAt = null,
            elapsedMinOnComplete = null,
            timeVariancePct = null,
            completionPct = null,
            xpEarned = null,
        )

        fun TaskEntity.colorEnum(): TaskColor = TaskColor.fromKey(color) ?: TaskColor.Pink
    }
}
