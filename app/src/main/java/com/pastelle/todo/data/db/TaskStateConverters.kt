package com.pastelle.todo.data.db

import androidx.room.TypeConverter

class TaskStateConverters {
    @TypeConverter
    fun fromState(state: TaskState): String = state.name

    @TypeConverter
    fun toState(value: String): TaskState =
        TaskState.entries.firstOrNull { it.name == value } ?: TaskState.WAITING
}
