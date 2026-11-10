package com.pastelle.todo.core

/** 任务主题色（JSON 字段 color 的合法取值） */
enum class TaskColor(val key: String) {
    Pink("pink"),
    Purple("purple"),
    Peach("peach"),
    Blue("blue"),
    Green("green");

    companion object {
        /** 缺省按顺序循环分配的调色板 */
        val palette: List<TaskColor> = entries.toList()

        fun fromKey(key: String?): TaskColor? =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
    }
}
