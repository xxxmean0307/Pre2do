package com.pastelle.todo.timing

import com.pastelle.todo.PastelleApp
import com.pastelle.todo.R
import com.pastelle.todo.core.PastelleConstants
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.core.TimingMath
import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 计时引擎（规格 5，最高优先级逻辑）：
 * - 状态机 WAITING → PREP → FOCUS ⇄ BREAK → COMPLETED，以墙钟时间戳为准（phaseEndEpochMs）；
 * - 每秒 tick 核对墙钟推进阶段；AlarmManager 在 T0−5min / T0 / 阶段结束唤醒双保险；
 * - 完成窗口 0.8D 开闸、1.2D 强制；完成度（滑块档位）与时间偏差两个独立维度；
 * - 完成事务先落库再发事件（杀进程不丢进度、不重复计）。
 */
class TimingEngine(private val app: PastelleApp) {

    private val repo get() = app.repository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _ui = MutableStateFlow<TimerUiState>(TimerUiState.Idle)
    val ui: StateFlow<TimerUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<TimingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TimingEvent> = _events.asSharedFlow()

    private val started = AtomicBoolean(false)

    /** 一次性提醒（0.8D / 1.2D）按任务去重 */
    private val earlyNotified = mutableSetOf<String>()
    private val lateNotified = mutableSetOf<String>()

    // ── 生命周期 ───────────────────────────────────────

    /** Application.onCreate：建通知渠道、恢复状态、启动每秒校对循环 */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        Notifications.createChannels(app)
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                refreshLocked(now)
                // 杀进程重启后重建闹钟（force-stop 会清除已排闹钟）
                AlarmScheduler.planTasks(app, repo.unfinishedTasks(), now)
                val active = _ui.value as? TimerUiState.Active
                if (active != null && active.phase in listOf(TaskState.PREP, TaskState.FOCUS, TaskState.BREAK)) {
                    AlarmScheduler.planPhaseWake(app, active.task.id, active.phaseEndEpochMs)
                    TimingService.start(app)
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                mutex.withLock { refreshLocked(System.currentTimeMillis()) }
            }
        }
    }

    /** 系统时间被修改 / onResume：用墙钟重新校准（规格 9.5） */
    fun recalibrate() {
        scope.launch { mutex.withLock { refreshLocked(System.currentTimeMillis()) } }
    }

    /** 开机恢复（规格 9.2）：重建未来闹钟；错过 T0 ≤30 分钟直接进 FOCUS，超时交界面标记 */
    fun onBootRestore() {
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val tasks = repo.unfinishedTasks()
                tasks.forEach { task ->
                    val t0 = TimeUtils.epochMs(task.date, task.startMinute)
                    if (task.state == TaskState.WAITING && now >= TimingMath.prepStartMs(t0)) {
                        when {
                            now < t0 -> repo.updatePhase(task.id, TaskState.PREP, t0)
                            now - t0 <= MISSED_GRACE_MS -> {
                                repo.updatePhase(task.id, TaskState.FOCUS, now + FOCUS_MS)
                                repo.markStarted(task.id, t0)
                            }
                            // 超过 30 分钟：不启动，由界面标记「已错过」
                        }
                    }
                }
                AlarmScheduler.planTasks(app, repo.unfinishedTasks(), now)
                refreshLocked(now)
            }
        }
    }

    /** 导入确认后：为每个任务排 T0−5min 与 T0 闹钟（规格 6.2.3） */
    fun planImportedTasks(tasks: List<TaskEntity>) {
        AlarmScheduler.planTasks(app, tasks)
        recalibrate()
    }

    // ── 闹钟 / 通知按钮入口 ─────────────────────────────

    fun onPrepAlarm(taskId: String) {
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val task = repo.taskById(taskId)
                if (task != null && task.state == TaskState.WAITING) {
                    repo.updatePhase(taskId, TaskState.PREP, TimeUtils.epochMs(task.date, task.startMinute))
                    Notifications.alert(
                        app,
                        app.getString(R.string.notif_prep_title),
                        app.getString(R.string.notif_prep_text, task.title),
                        withStartAction = true,
                        taskId = taskId,
                    )
                }
                refreshLocked(now)
                TimingService.start(app)
            }
        }
    }

    fun onFocusAlarm(taskId: String) {
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val task = repo.taskById(taskId)
                if (task != null && task.state == TaskState.WAITING) {
                    repo.updatePhase(taskId, TaskState.PREP, TimeUtils.epochMs(task.date, task.startMinute))
                }
                refreshLocked(now) // PREP→FOCUS 边界在此推进并发出「该专注了」提醒
                TimingService.start(app)
            }
        }
    }

    /** 阶段结束唤醒校对（与服务内 tick 双保险） */
    fun onWakeAlarm(taskId: String) {
        scope.launch { mutex.withLock { refreshLocked(System.currentTimeMillis()) } }
    }

    /** 通知按钮「开始」：准备中=提前开始；否则打开应用（由 contentIntent 承担） */
    fun onNotifStart(taskId: String) {
        scope.launch {
            mutex.withLock {
                val task = repo.taskById(taskId) ?: return@withLock
                if (task.state == TaskState.PREP) {
                    enterFocusLocked(task, System.currentTimeMillis(), early = true)
                    refreshLocked(System.currentTimeMillis())
                }
            }
        }
        TimingService.start(app)
    }

    /** 通知按钮「跳过」：跳过准备（等同提前开始）或跳过休息（规格 4.2 / 5.3） */
    fun onNotifSkip(taskId: String) {
        onNotifStart(taskId) // BREAK→FOCUS 与 PREP→FOCUS 同一入口：立即进入专注
        scope.launch {
            mutex.withLock {
                val task = repo.taskById(taskId) ?: return@withLock
                if (task.state == TaskState.BREAK) {
                    enterFocusLocked(task, System.currentTimeMillis(), early = false, fromBreak = true)
                    refreshLocked(System.currentTimeMillis())
                }
            }
        }
    }

    // ── 用户动作（执行页按钮）──────────────────────────

    /** 「提前开始」：跳过剩余准备时间 */
    suspend fun startEarly(taskId: String) {
        mutex.withLock {
            val task = repo.taskById(taskId) ?: return
            if (task.state == TaskState.PREP || task.state == TaskState.WAITING) {
                enterFocusLocked(task, System.currentTimeMillis(), early = true)
                refreshLocked(System.currentTimeMillis())
            }
        }
    }

    /** 「跳过休息」：立即进入下一个番茄钟 */
    suspend fun skipBreak(taskId: String) {
        mutex.withLock {
            val task = repo.taskById(taskId) ?: return
            if (task.state == TaskState.BREAK) {
                enterFocusLocked(task, System.currentTimeMillis(), early = false, fromBreak = true)
                refreshLocked(System.currentTimeMillis())
            }
        }
    }

    /**
     * 「延迟任务」（M11）：整任务后移 N 分钟——拟我戏剧性回拨时间条的语义来源。
     * T0 / 相位截止 / startedAt 同步后移，等价于把已进行进度往回拨。
     */
    suspend fun delayTask(taskId: String, minutes: Int) {
        mutex.withLock {
            val task = repo.taskById(taskId) ?: return
            if (task.state == TaskState.COMPLETED) return
            val shiftMs = minutes * 60_000L
            val updated = task.copy(
                startMinute = (task.startMinute + minutes).coerceAtMost(23 * 60 + 55),
                phaseEndEpochMs = if (task.phaseEndEpochMs > 0) task.phaseEndEpochMs + shiftMs else 0L,
                startedAt = task.startedAt?.plus(shiftMs),
            )
            repo.saveTask(updated)
            if (updated.phaseEndEpochMs > 0) {
                AlarmScheduler.planPhaseWake(app, updated.id, updated.phaseEndEpochMs)
            }
            refreshLocked(System.currentTimeMillis())
        }
    }

    /**
     * 确认完成（规格 5.3 / 9.6）：先落库 COMPLETED（进度只计一次），再发奖励事件。
     * gearPct = 30/60/100；时间偏差与完成度两个独立维度。
     */
    suspend fun complete(taskId: String, gearPct: Int): TimingEvent.Completed? {
        val result = mutex.withLock {
            val now = System.currentTimeMillis()
            val task = repo.taskById(taskId) ?: return@withLock null
            if (task.state == TaskState.COMPLETED) return@withLock null
            val anchor = task.startedAt ?: TimeUtils.epochMs(task.date, task.startMinute)
            val elapsedMs = (now - anchor).coerceAtLeast(0L)
            val variance = TimingMath.variancePct(elapsedMs, task.durationMin)
            val xp = TimingMath.xpFor(gearPct, elapsedMs, task.durationMin)
            repo.completeTask(
                taskId = taskId,
                completedAt = now,
                elapsedMin = (elapsedMs / TimingMath.MINUTE_MS).toInt(),
                variancePct = variance,
                completionPct = gearPct,
                xp = xp,
                pomodoroCount = task.pomodoroCount,
            )
            AlarmScheduler.cancelTask(app, taskId)
            earlyNotified.remove(taskId)
            lateNotified.remove(taskId)
            val streak = if (gearPct == 100) repo.bumpStreak() else repo.resetStreak()
            // 先落库再庆祝：记录待播庆祝，进程被杀可在重开后补播（规格 9.6）
            repo.setPendingCelebration(taskId, gearPct, xp, streak)
            val event = TimingEvent.Completed(
                taskId = taskId,
                title = task.title,
                gearPct = gearPct,
                xp = xp,
                streak = streak,
                variancePct = variance,
            )
            refreshLocked(now)
            event
        }
        if (result != null) {
            // 养成系统：完成经验入账（等级与装备档位由 GrowthMath 派生）
            app.avatarStore.addXp(result.xp)
            _events.emit(result)
        }
        return result
    }

    /** 奖励动画播放完毕（或补播完毕）后清除待播记录 */
    suspend fun ackCelebration() = repo.clearPendingCelebration()

    suspend fun pendingCelebration(): String? = repo.pendingCelebration()

    // ── 状态推进（全部在 mutex 内调用）──────────────────

    private suspend fun refreshLocked(nowMs: Long) {
        val tasks = repo.unfinishedTasks()
        var t: TaskEntity = tasks.firstOrNull { currentCandidate(it, nowMs) } ?: run {
            _ui.value = TimerUiState.Idle
            return
        }

        // 状态进入：WAITING → PREP / FOCUS（含错过 T0 ≤30min 直接进 FOCUS，规格 9.2）
        if (t.state == TaskState.WAITING) {
            val t0 = TimeUtils.epochMs(t.date, t.startMinute)
            when {
                nowMs < TimingMath.prepStartMs(t0) -> {
                    // 即将开始：仅追踪，不起服务
                    _ui.value = buildActive(t, nowMs, TaskState.WAITING, TimingMath.prepStartMs(t0))
                    return
                }
                nowMs < t0 -> {
                    repo.updatePhase(t.id, TaskState.PREP, t0)
                    t = t.copy(state = TaskState.PREP, phaseEndEpochMs = t0)
                    AlarmScheduler.planPhaseWake(app, t.id, t0)
                    Notifications.alert(
                        app,
                        app.getString(R.string.notif_prep_title),
                        app.getString(R.string.notif_prep_text, t.title),
                        withStartAction = true,
                        taskId = t.id,
                    )
                    TimingService.start(app)
                }
                nowMs - t0 <= MISSED_GRACE_MS -> {
                    enterFocusLocked(t, nowMs, early = false)
                    t = repo.taskById(t.id) ?: t
                }
                else -> {
                    // 超过 30 分钟：标记为已错过（界面从状态推导展示），转下一任务
                    return refreshNextLocked(tasks, t, nowMs)
                }
            }
        }

        // 阶段边界推进（墙钟 + 闹钟双保险；休眠补课时可连续跨越多个边界）
        var guard = 0
        while (t.state != TaskState.COMPLETED && t.phaseEndEpochMs > 0 && t.phaseEndEpochMs <= nowMs && guard++ < 64) {
            t = advanceLocked(t, nowMs)
        }

        // 完成窗口一次性提醒
        val anchor = t.startedAt ?: TimeUtils.epochMs(t.date, t.startMinute)
        val elapsed = (nowMs - anchor).coerceAtLeast(0L)
        if (t.startedAt != null) {
            if (TimingMath.canComplete(elapsed, t.durationMin) && earlyNotified.add(t.id)) {
                Notifications.alert(
                    app,
                    app.getString(R.string.notif_early_title),
                    app.getString(R.string.notif_early_text, t.title),
                    withStartAction = false,
                    taskId = t.id,
                )
            }
            if (TimingMath.isForced(elapsed, t.durationMin) && lateNotified.add(t.id)) {
                Notifications.alert(
                    app,
                    app.getString(R.string.notif_late_title),
                    app.getString(R.string.notif_late_text, t.title),
                    withStartAction = false,
                    taskId = t.id,
                )
                _events.tryEmit(TimingEvent.ForcedPending(t.id))
            }
        }

        _ui.value = buildActive(t, nowMs, t.state, t.phaseEndEpochMs)
    }

    /** 当前任务已「错过」时转为追踪下一个任务 */
    private suspend fun refreshNextLocked(tasks: List<TaskEntity>, skipped: TaskEntity, nowMs: Long) {
        val next = tasks.firstOrNull { it.id != skipped.id && currentCandidate(it, nowMs) }
        if (next == null) {
            _ui.value = TimerUiState.Idle
            return
        }
        refreshLocked(nowMs) // 简单递归：候选集已包含 next
    }

    /** 是否可作为被追踪任务：未完成；错过太久的 WAITING 跳过 */
    private fun currentCandidate(task: TaskEntity, nowMs: Long): Boolean {
        if (task.state == TaskState.COMPLETED) return false
        if (task.state != TaskState.WAITING) return true
        val t0 = TimeUtils.epochMs(task.date, task.startMinute)
        return nowMs - t0 <= MISSED_GRACE_MS
    }

    /** 跨越一个阶段边界 */
    private suspend fun advanceLocked(t: TaskEntity, nowMs: Long): TaskEntity {
        val oldEnd = t.phaseEndEpochMs
        return when (t.state) {
            TaskState.PREP -> {
                // 到 T0 自动进入 FOCUS（锚 = 计划时刻）
                repo.updatePhase(t.id, TaskState.FOCUS, oldEnd + FOCUS_MS)
                repo.markStarted(t.id, TimeUtils.epochMs(t.date, t.startMinute))
                AlarmScheduler.planPhaseWake(app, t.id, oldEnd + FOCUS_MS)
                Notifications.alert(
                    app,
                    app.getString(R.string.notif_focus_title),
                    app.getString(
                        // 短任务不启用番茄钟，通知里不该再写「35 分钟番茄钟开始」
                        if (t.durationMin >= PastelleConstants.POMODORO_MIN_DURATION_MIN) {
                            R.string.notif_focus_text
                        } else {
                            R.string.notif_focus_text_short
                        },
                        t.title,
                    ),
                    withStartAction = true,
                    taskId = t.id,
                )
                t.copy(state = TaskState.FOCUS, phaseEndEpochMs = oldEnd + FOCUS_MS, startedAt = t.startedAt ?: TimeUtils.epochMs(t.date, t.startMinute))
            }
            TaskState.FOCUS -> {
                // 35 分钟专注结束 → 休息 10 分钟（番茄圆点点亮）
                if (t.durationMin < com.pastelle.todo.core.PastelleConstants.POMODORO_MIN_DURATION_MIN) {
                    // 短任务（<70 分钟）不设番茄钟：一路专注、不进休息、不计番茄
                    repo.updatePhase(t.id, TaskState.FOCUS, oldEnd + FOCUS_MS)
                    AlarmScheduler.planPhaseWake(app, t.id, oldEnd + FOCUS_MS)
                    t.copy(phaseEndEpochMs = oldEnd + FOCUS_MS)
                } else {
                    repo.incrementPomodoro(t.id)
                    repo.updatePhase(t.id, TaskState.BREAK, oldEnd + BREAK_MS)
                    AlarmScheduler.planPhaseWake(app, t.id, oldEnd + BREAK_MS)
                    Notifications.alert(
                        app,
                        app.getString(R.string.notif_focus_end_title),
                        app.getString(R.string.notif_focus_end_text),
                        withStartAction = false,
                        taskId = t.id,
                    )
                    t.copy(state = TaskState.BREAK, phaseEndEpochMs = oldEnd + BREAK_MS, pomodoroCount = t.pomodoroCount + 1)
                }
            }
            TaskState.BREAK -> {
                // 休息结束 → 继续专注（循环不息直至完成）
                repo.updatePhase(t.id, TaskState.FOCUS, oldEnd + FOCUS_MS)
                AlarmScheduler.planPhaseWake(app, t.id, oldEnd + FOCUS_MS)
                Notifications.alert(
                    app,
                    app.getString(R.string.notif_break_end_title),
                    app.getString(R.string.notif_break_end_text),
                    withStartAction = false,
                    taskId = t.id,
                )
                t.copy(state = TaskState.FOCUS, phaseEndEpochMs = oldEnd + FOCUS_MS)
            }
            else -> t
        }
    }

    /** PREP / WAITING → FOCUS：early=提前开始（锚=此刻）；fromBreak=跳过休息（锚不变） */
    private suspend fun enterFocusLocked(t: TaskEntity, nowMs: Long, early: Boolean, fromBreak: Boolean = false) {
        repo.updatePhase(t.id, TaskState.FOCUS, nowMs + FOCUS_MS)
        if (!fromBreak) {
            // 实际专注开始时刻：提前开始=此刻；自动/晚到=计划时刻 T0（规格 5.3 elapsed 锚）
            val anchor = if (early) nowMs else TimeUtils.epochMs(t.date, t.startMinute)
            repo.markStarted(t.id, anchor)
        }
        AlarmScheduler.planPhaseWake(app, t.id, nowMs + FOCUS_MS)
        TimingService.start(app)
    }

    private fun buildActive(t: TaskEntity, nowMs: Long, phase: TaskState, phaseEnd: Long): TimerUiState.Active {
        val t0 = TimeUtils.epochMs(t.date, t.startMinute)
        val anchor = t.startedAt ?: t0
        val elapsed = if (t.startedAt != null) (nowMs - anchor).coerceAtLeast(0L) else 0L
        return TimerUiState.Active(
            task = t,
            phase = phase,
            phaseEndEpochMs = phaseEnd,
            remainingMs = (phaseEnd - nowMs).coerceAtLeast(0L),
            pomodoroIndex = t.pomodoroCount + 1,
            elapsedMs = elapsed,
            canComplete = t.startedAt != null && TimingMath.canComplete(elapsed, t.durationMin),
            msToGate = if (t.startedAt != null) TimingMath.msToCompleteGate(elapsed, t.durationMin) else 0L,
            forced = t.startedAt != null && TimingMath.isForced(elapsed, t.durationMin),
        )
    }

    private companion object {
        val FOCUS_MS = PastelleConstants.FOCUS_SECONDS * 1000L
        val BREAK_MS = PastelleConstants.BREAK_SECONDS * 1000L
        const val MISSED_GRACE_MS = 30 * 60_000L // 错过 T0 不超过 30 分钟仍直接进入 FOCUS
    }
}
