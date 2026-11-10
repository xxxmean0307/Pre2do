package com.pastelle.todo.timing

import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState

/** 计时 UI 状态（规格 8：Service 每秒把剩余时间以 StateFlow<TimerUiState> 暴露） */
sealed interface TimerUiState {
    /** 当前没有进行中的任务 */
    data object Idle : TimerUiState

    /** 有一个被追踪的任务（WAITING=即将开始 / PREP / FOCUS / BREAK） */
    data class Active(
        val task: TaskEntity,
        val phase: TaskState,
        /** 当前阶段目标结束时刻（WAITING 时为准备段开始时刻） */
        val phaseEndEpochMs: Long,
        /** 当前阶段剩余毫秒（墙钟推算） */
        val remainingMs: Long,
        /** 第 N 个番茄钟（从 1 起） */
        val pomodoroIndex: Int,
        /** 自实际专注开始（startedAt）起的已进行毫秒；未开始为 0 */
        val elapsedMs: Long,
        /** 完成窗口开闸（elapsed ≥ 0.8D） */
        val canComplete: Boolean,
        /** 距开闸剩余毫秒（已开闸为 0） */
        val msToGate: Long,
        /** 已到 1.2D：强制弹出完成滑块 */
        val forced: Boolean,
    ) : TimerUiState
}

/** 计时事件 */
sealed interface TimingEvent {
    /** 完成已落库（先落库后播放，规格 9.6）→ 播放奖励动画 */
    data class Completed(
        val taskId: String,
        val title: String,
        /** 30 / 60 / 100 */
        val gearPct: Int,
        val xp: Int,
        /** 全勤连击（仅 100% 累计；非 100% 清零） */
        val streak: Int,
        /** 时间偏差 −20..+20 */
        val variancePct: Int,
    ) : TimingEvent

    /** 已到 1.2D：自动弹出完成滑块（不能再拖） */
    data class ForcedPending(val taskId: String) : TimingEvent
}
