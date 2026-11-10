package com.pastelle.todo.core

import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState

/**
 * 全 App 统一的「任务状态口径」（M32）。
 *
 * 此前今日页 / 日程页 / 英雄卡 / 桌面小组件各自推导，出现同一个任务
 * 在四处分别显示「进行中 / 未完成 / 已到计划时长 / 已完成」的割裂。
 * 这里只负责**判定**，文案由各端用 stringResource 映射，保证语义一致。
 *
 * 关键约定：`COMPLETED` 必须有 `completedAt` 才算真实完成；
 * 没有完成时刻的完成态是历史脏数据，一律按墙钟重新推导。
 */
enum class TaskDisplayStatus {
    /** 真实完成（有完成时刻） */
    COMPLETED,

    /** 距开始还早于准备段 */
    UPCOMING,

    /** 已进入 T0−5min 准备段 */
    PREP,

    /** 处于计划时段内 */
    ONGOING,

    /** 计划时段已过但仍未完成 */
    MISSED,
}

fun TaskEntity.displayStatus(nowMs: Long = System.currentTimeMillis()): TaskDisplayStatus {
    if (state == TaskState.COMPLETED && completedAt != null) return TaskDisplayStatus.COMPLETED
    val t0 = TimeUtils.epochMs(date, startMinute)
    val prepStart = t0 - PastelleConstants.PREP_SECONDS * 1000L
    val end = t0 + durationMin * 60_000L
    return when {
        nowMs < prepStart -> TaskDisplayStatus.UPCOMING
        nowMs < t0 -> TaskDisplayStatus.PREP
        nowMs < end -> TaskDisplayStatus.ONGOING
        else -> TaskDisplayStatus.MISSED
    }
}

/** 是否参与番茄钟：与计时引擎一致，短于 70 分钟的任务不进休息、不计番茄 */
fun TaskEntity.hasPomodoro(): Boolean =
    durationMin >= PastelleConstants.POMODORO_MIN_DURATION_MIN
