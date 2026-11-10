package com.pastelle.todo.data.json

import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.core.TimeUtils
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.ceil

/**
 * 导入 JSON 的解析与校验（规格 6.1 字段规则、校验与冲突处理）。
 *
 * - 解析失败 / 缺字段 / 时间格式错：逐条列出第几条任务、什么问题，不写入任何数据；
 * - 硬冲突（区间重叠）：列出冲突对，拦截导入；
 * - 软冲突（最晚结束 / 准备段轻微交叠）：允许导入，给出琥珀色提示；
 * - 不允许任务跨 24:00；tasks 乱序则自动排序；color 缺省按顺序循环分配。
 */
object ScheduleParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(text: String): ImportCheckResult {
        val raw = try {
            json.decodeFromString(RawSchedule.serializer(), text.trim())
        } catch (e: SerializationException) {
            return ImportCheckResult(
                schedule = null,
                errors = listOf("JSON 解析失败：${firstLine(e.message)}"),
                warnings = emptyList(),
            )
        } catch (e: IllegalArgumentException) {
            return ImportCheckResult(
                schedule = null,
                errors = listOf("JSON 解析失败：${firstLine(e.message)}"),
                warnings = emptyList(),
            )
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // ── 顶层字段 ──────────────────────────────
        when {
            raw.version == null -> errors += "缺少 version 字段"
            raw.version != 1 -> errors += "不支持的版本号 version=${raw.version}，请升级 App 后再导入"
        }

        val date = raw.date?.trim()
        if (date == null) {
            errors += "缺少 date 字段"
        } else if (TimeUtils.parseDateOrNull(date) == null) {
            errors += "date 格式错误（应为 YYYY-MM-DD）：$date"
        }

        val rawTasks = raw.tasks
        when {
            rawTasks == null -> errors += "缺少 tasks 字段"
            rawTasks.isEmpty() -> errors += "tasks 不能为空（至少 1 条任务）"
            rawTasks.size > 20 -> errors += "任务数量超过 20 条（当前 ${rawTasks.size} 条）"
        }

        // ── 逐条任务校验 ──────────────────────────
        data class ValidTask(
            val sourceIndex: Int,
            val title: String,
            val startMinute: Int,
            val durationMin: Int,
            val stage: String?,
            val location: String?,
            val note: String?,
            val explicitColor: TaskColor?,
        )

        val valid = mutableListOf<ValidTask>()
        rawTasks?.forEachIndexed { i, t ->
            val n = i + 1
            var ok = true

            val title = t.title?.trim()
            if (title.isNullOrBlank()) {
                errors += "第 $n 条任务：缺少标题 title"
                ok = false
            } else if (title.length > 30) {
                errors += "第 $n 条任务：标题超过 30 字（当前 ${title.length} 字）"
                ok = false
            }

            val startMinute = when {
                t.start.isNullOrBlank() -> {
                    errors += "第 $n 条任务：缺少开始时间 start"; ok = false; null
                }
                else -> {
                    val minute = TimeUtils.parseStartMinuteOrNull(t.start)
                    if (minute == null) {
                        errors += "第 $n 条任务：开始时间格式错误（应为 HH:mm，24 小时制）：${t.start}"
                        ok = false
                    }
                    minute
                }
            }

            val duration = when {
                t.durationMin == null -> {
                    errors += "第 $n 条任务：缺少时长 durationMin"; ok = false; null
                }
                t.durationMin < 15 || t.durationMin > 480 -> {
                    errors += "第 $n 条任务：时长需在 15–480 分钟之间（当前 ${t.durationMin}）"
                    ok = false; null
                }
                else -> t.durationMin
            }

            if (ok && startMinute != null && duration != null && startMinute + duration > 24 * 60) {
                errors += "第 $n 条任务：结束时间超过 24:00，不允许跨午夜"
                ok = false
            }

            val explicitColor = if (t.color == null) null else {
                val c = TaskColor.fromKey(t.color)
                if (c == null) {
                    errors += "第 $n 条任务：颜色 color 不合法（可选 pink/purple/peach/blue/green）：${t.color}"
                    ok = false
                }
                c
            }

            if (ok && title != null && startMinute != null && duration != null) {
                valid += ValidTask(
                    sourceIndex = i + 1,
                    title = title,
                    startMinute = startMinute,
                    durationMin = duration,
                    stage = t.stage?.trim()?.takeIf { it.isNotEmpty() },
                    location = t.location?.trim()?.takeIf { it.isNotEmpty() },
                    note = t.note?.trim()?.takeIf { it.isNotEmpty() },
                    explicitColor = explicitColor,
                )
            }
        }

        if (errors.isNotEmpty()) {
            return ImportCheckResult(schedule = null, errors = errors, warnings = warnings)
        }

        // ── 自动按 start 升序排序 ──────────────────
        val sorted = valid.sortedBy { it.startMinute }

        // ── 冲突检测（列出冲突对 / 软冲突提示）────
        for (a in sorted.indices) {
            for (b in a + 1 until sorted.size) {
                val first = sorted[a]
                val second = sorted[b]
                val firstEnd = first.startMinute + first.durationMin
                val secondEnd = second.startMinute + second.durationMin
                val overlap = second.startMinute < firstEnd
                if (overlap) {
                    errors += "硬冲突：第 ${first.sourceIndex} 条（${TimeUtils.formatHhMm(first.startMinute)}–${TimeUtils.formatHhMm(firstEnd)}）" +
                        "与第 ${second.sourceIndex} 条（${TimeUtils.formatHhMm(second.startMinute)}–${TimeUtils.formatHhMm(secondEnd)}）时间段重叠"
                }
            }
        }
        if (errors.isNotEmpty()) {
            return ImportCheckResult(schedule = null, errors = errors, warnings = warnings)
        }

        // 软冲突：仅看时间上相邻的任务对
        for (i in 0 until sorted.size - 1) {
            val first = sorted[i]
            val second = sorted[i + 1]
            val firstEnd = first.startMinute + first.durationMin
            val latestEnd = ceil(first.startMinute + first.durationMin * 1.2).toInt()
            val prepStart = second.startMinute - 5
            if (second.startMinute < latestEnd) {
                warnings += "软冲突：第 ${first.sourceIndex} 条最晚结束（${TimeUtils.formatHhMm(latestEnd)}）" +
                    "与第 ${second.sourceIndex} 条开始（${TimeUtils.formatHhMm(second.startMinute)}）可能交叠"
            }
            if (prepStart < firstEnd) {
                warnings += "软冲突：第 ${second.sourceIndex} 条的准备段（${TimeUtils.formatHhMm(prepStart)} 起）" +
                    "与第 ${first.sourceIndex} 条交叠"
            }
        }

        // ── 组装：color 缺省按顺序循环分配 ─────────
        val tasks = sorted.mapIndexed { orderIndex, t ->
            ParsedTask(
                sourceIndex = t.sourceIndex,
                title = t.title,
                startMinute = t.startMinute,
                durationMin = t.durationMin,
                stage = t.stage,
                location = t.location,
                note = t.note,
                color = t.explicitColor ?: TaskColor.palette[orderIndex % TaskColor.palette.size],
                orderIndex = orderIndex,
            )
        }

        val schedule = ParsedSchedule(
            date = date!!,
            title = raw.title?.trim()?.takeIf { it.isNotEmpty() } ?: date,
            tasks = tasks,
        )
        return ImportCheckResult(schedule = schedule, errors = emptyList(), warnings = warnings)
    }

    private fun firstLine(message: String?): String =
        message?.lineSequence()?.firstOrNull()?.take(120) ?: "格式错误"
}
