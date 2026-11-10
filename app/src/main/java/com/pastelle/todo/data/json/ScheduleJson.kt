package com.pastelle.todo.data.json

import com.pastelle.todo.core.TaskColor
import kotlinx.serialization.Serializable

/**
 * 导入格式（JSON，UTF-8，version=1）——可序列化 DTO。
 * 解析时先宽松解码为 Raw*（字段可空），再逐条校验，以便"逐条列出第几条任务、什么问题"。
 */
@Serializable
data class ScheduleJson(
    val version: Int,
    val title: String? = null,
    val date: String,
    val tasks: List<TaskJson>,
)

@Serializable
data class TaskJson(
    val title: String,
    val start: String,
    val durationMin: Int,
    val stage: String? = null,
    val location: String? = null,
    val note: String? = null,
    val color: String? = null,
)

/** 宽松解码用的原始结构（全部可空，便于逐条报错） */
@Serializable
internal data class RawSchedule(
    val version: Int? = null,
    val title: String? = null,
    val date: String? = null,
    val tasks: List<RawTask>? = null,
)

@Serializable
internal data class RawTask(
    val title: String? = null,
    val start: String? = null,
    val durationMin: Int? = null,
    val stage: String? = null,
    val location: String? = null,
    val note: String? = null,
    val color: String? = null,
)

/** 校验通过后的任务（startMinute 为当天分钟数，orderIndex 为排序后的展示序） */
data class ParsedTask(
    val sourceIndex: Int,
    val title: String,
    val startMinute: Int,
    val durationMin: Int,
    val stage: String?,
    val location: String?,
    val note: String?,
    val color: TaskColor,
    val orderIndex: Int,
) {
    val endMinute: Int get() = startMinute + durationMin
}

/** 校验通过后的日程 */
data class ParsedSchedule(
    val date: String,
    val title: String,
    val tasks: List<ParsedTask>,
)

/**
 * 导入校验结果：
 * errors 非空 → 不写入任何数据；warnings（软冲突等）→ 允许导入、预览页琥珀色提示。
 */
data class ImportCheckResult(
    val schedule: ParsedSchedule?,
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean get() = schedule != null && errors.isEmpty()
}
