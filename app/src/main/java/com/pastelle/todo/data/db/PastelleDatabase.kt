package com.pastelle.todo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ScheduleEntity::class, TaskEntity::class, DailyRecordEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(TaskStateConverters::class)
abstract class PastelleDatabase : RoomDatabase() {
    abstract fun dao(): PastelleDao
}
