package com.pastelle.todo.data.json

import com.pastelle.todo.data.db.TaskEntity
import kotlinx.serialization.json.Json

/**
 * 日程导出（里程碑 7，规格 6.3）：导出为与导入完全相同的 JSON 格式（可直接重新导入的往返格式）。
 */
object ScheduleExporter {

    private val json = Json { prettyPrint = true }

    fun exportDay(date: String, title: String?, tasks: List<TaskEntity>): String {
        val doc = ScheduleJson(
            version = 1,
            title = title,
            date = date,
            tasks = tasks.map { t ->
                TaskJson(
                    title = t.title,
                    start = "%02d:%02d".format(t.startMinute / 60, t.startMinute % 60),
                    durationMin = t.durationMin,
                    stage = t.stage,
                    location = t.location,
                    note = t.note,
                    color = t.color,
                )
            },
        )
        return json.encodeToString(ScheduleJson.serializer(), doc)
    }
}
