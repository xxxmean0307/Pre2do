package com.pastelle.todo.core

import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState

/**
 * 页面层的当前阶段推导（里程碑 3 用墙钟推导；里程碑 4 起由计时引擎状态接管）。
 * 规则：T0−5min 前=即将开始；T0−5min~T0=准备中；
 * T0 后按 35 分钟专注 / 10 分钟休息循环推导；超过计划时长=已到计划时长。
 *
 * ★ M32：短于 70 分钟的任务不启用番茄钟（与 TimingEngine 一致），
 *   计划时段内一律为「专注中」并显示整段剩余，不再显示永远点不亮的番茄圆点
 *   和并不存在的「休息中」。
 */
enum class HeroPhase { Soon, Prep, Focus, Break, Due }

data class HeroCountdown(
    val phase: HeroPhase,
    val remainingMs: Long,
    /** 当前是第几个番茄钟（从 1 起） */
    val pomodoroIndex: Int,
)

object TaskStatus {
    fun heroCountdownFor(task: TaskEntity, nowMs: Long): HeroCountdown {
        if (task.state == TaskState.COMPLETED && task.completedAt != null) {
            return HeroCountdown(HeroPhase.Due, 0L, task.pomodoroCount)
        }
        val t0 = TimeUtils.epochMs(task.date, task.startMinute)
        val prepStart = t0 - PastelleConstants.PREP_SECONDS * 1000L
        val end = t0 + task.durationMin * 60_000L
        val cycleMs = PastelleConstants.CYCLE_SECONDS * 1000L
        val focusMs = PastelleConstants.FOCUS_SECONDS * 1000L

        return when {
            nowMs < prepStart -> HeroCountdown(HeroPhase.Soon, prepStart - nowMs, 1)
            nowMs < t0 -> HeroCountdown(HeroPhase.Prep, t0 - nowMs, 1)
            nowMs < end -> {
                if (!task.hasPomodoro()) {
                    // 短任务：整段连续专注，倒计时＝到计划结束的剩余
                    HeroCountdown(HeroPhase.Focus, end - nowMs, 1)
                } else {
                    val elapsed = nowMs - t0
                    val inCycle = elapsed % cycleMs
                    val index = (elapsed / cycleMs).toInt() + 1
                    if (inCycle < focusMs) {
                        HeroCountdown(HeroPhase.Focus, focusMs - inCycle, index)
                    } else {
                        HeroCountdown(HeroPhase.Break, cycleMs - inCycle, index)
                    }
                }
            }
            else -> HeroCountdown(HeroPhase.Due, 0L, task.pomodoroCount)
        }
    }

    /** 任务的计划番茄钟数（向上取整）；短任务返回 0 表示不显示圆点 */
    fun plannedPomodoros(durationMin: Int): Int =
        if (durationMin < PastelleConstants.POMODORO_MIN_DURATION_MIN) 0
        else ((durationMin * 60 + PastelleConstants.FOCUS_SECONDS - 1) / PastelleConstants.FOCUS_SECONDS)
            .coerceAtLeast(1)

    /** 今日进度：贡献 = weight × 完成度档位；进度 = 已获贡献 / 计划权重 */
    fun todayProgress(tasks: List<TaskEntity>): Pair<Double, Double> {
        val planned = tasks.sumOf { it.durationMin }.toDouble()
        val earned = tasks.sumOf { task ->
            task.durationMin * ((task.completionPct ?: 0) / 100.0)
        }
        return earned to planned
    }
}
