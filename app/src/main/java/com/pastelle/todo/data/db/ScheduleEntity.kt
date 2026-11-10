package com.pastelle.todo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 每天一份的日程（规格 8 节） */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    /** YYYY-MM-DD，本地日期 */
    @PrimaryKey val date: String,
    val title: String,
    val importedAt: Long,
)
