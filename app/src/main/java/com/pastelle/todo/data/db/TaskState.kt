package com.pastelle.todo.data.db

/** 任务运行时状态（规格 8 节枚举）。
 *  1.2D 强制完成不单列状态：落库为 COMPLETED（timeVariancePct = +20，补发通知）。 */
enum class TaskState { WAITING, PREP, FOCUS, BREAK, COMPLETED }
