package com.pastelle.todo.core

/**
 * 多样问候语（M28.3）：按「已完成任务数」优先分流，未开始时按当前时段分流。
 * 同 seed 稳定（同日不跳字），seed+进度 变化时轮换，形成日日不同的问候。
 */
object Greeting {

    internal val donePool = listOf(
        "今天全部搞定，太棒了！",
        "任务清零，好好休息一下～",
        "全部完成！今天也是高效率的一天",
        "干得漂亮，没有剩下的事啦",
    )

    internal val progressPool = listOf(
        "已完成 {n} 个任务，继续加油！",
        "进度不错，再接再厉！",
        "已经完成 {n} 个啦，保持节奏～",
        "稳步前进，剩下的交给今天的你",
    )

    internal val morningPool = listOf(
        "早上好，今天也要加油！",
        "早安！新的一天从第一个任务开始",
        "清晨愉快，慢慢来比较快",
        "早上好，记得先伸个懒腰～",
    )

    internal val noonPool = listOf(
        "中午好，别忘了休息一下",
        "午间时光，补充点能量吧",
        "中午好，下午的计划安排好了吗？",
    )

    internal val afternoonPool = listOf(
        "下午好，保持专注！",
        "下午了，喝口水继续冲",
        "午后时光，一件一件来",
    )

    internal val eveningPool = listOf(
        "晚上好，回顾一下今天吧",
        "晚上好，今天辛苦了",
        "夜色正好，把最后的小事收个尾",
    )

    internal val nightPool = listOf(
        "夜深了，早点休息",
        "熬夜可不好哦，明天再战",
        "夜猫子你好，也别忘了休息～",
    )

    fun pick(hour: Int, completedCount: Int, totalCount: Int, seed: Int): String {
        val pool = when {
            totalCount > 0 && completedCount >= totalCount -> donePool
            completedCount > 0 -> progressPool
            else -> when (hour) {
                in 5..10 -> morningPool
                in 11..13 -> noonPool
                in 14..17 -> afternoonPool
                in 18..22 -> eveningPool
                else -> nightPool
            }
        }
        val idx = (((seed + completedCount) % pool.size) + pool.size) % pool.size
        return pool[idx].replace("{n}", completedCount.toString())
    }
}
