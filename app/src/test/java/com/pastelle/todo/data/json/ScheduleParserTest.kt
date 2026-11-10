package com.pastelle.todo.data.json

import com.pastelle.todo.core.TaskColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 导入解析与校验的单元测试（对应规格 6.1 字段规则与冲突处理） */
class ScheduleParserTest {

    private val example = """
        {
          "version": 1,
          "title": "周三冲刺日",
          "date": "2026-09-23",
          "tasks": [
            { "title": "高数第三章刷题", "start": "09:00", "durationMin": 90, "stage": "上午", "location": "图书馆三楼", "note": "极限部分 20 题", "color": "pink" },
            { "title": "英语听力精练", "start": "10:40", "durationMin": 50, "stage": "上午", "color": "purple" },
            { "title": "午餐与散步", "start": "12:00", "durationMin": 60, "stage": "中午", "color": "peach" },
            { "title": "写课程论文初稿", "start": "14:00", "durationMin": 120, "stage": "下午", "note": "先列提纲再写正文", "color": "blue" },
            { "title": "健身：背部训练", "start": "17:30", "durationMin": 45, "stage": "傍晚", "color": "green" }
          ]
        }
    """.trimIndent()

    @Test
    fun `示例 JSON 解析通过`() {
        val result = ScheduleParser.parse(example)
        assertTrue(result.errors.toString(), result.isValid)
        val schedule = result.schedule!!
        assertEquals("2026-09-23", schedule.date)
        assertEquals("周三冲刺日", schedule.title)
        assertEquals(5, schedule.tasks.size)
        assertEquals(TaskColor.Pink, schedule.tasks[0].color)
        assertEquals(540, schedule.tasks[0].startMinute)
        // 软冲突应被提示（09:00+90×1.2 = 10:48 > 10:40）
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `非法 JSON 报解析失败`() {
        val result = ScheduleParser.parse("{ 这不是 json ")
        assertFalse(result.isValid)
        assertTrue(result.errors.first().startsWith("JSON 解析失败"))
        assertNull(result.schedule)
    }

    @Test
    fun `错误版本号提示升级`() {
        val result = ScheduleParser.parse(example.replace("\"version\": 1", "\"version\": 2"))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("升级") })
    }

    @Test
    fun `缺少 date 与 version 逐条报错`() {
        val result = ScheduleParser.parse("""{ "tasks": [ { "title": "A", "start": "09:00", "durationMin": 30 } ] }""")
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("version") })
        assertTrue(result.errors.any { it.contains("date") })
    }

    @Test
    fun `空数组与超过20条被拦截`() {
        val empty = ScheduleParser.parse("""{ "version": 1, "date": "2026-09-23", "tasks": [] }""")
        assertFalse(empty.isValid)
        assertTrue(empty.errors.any { it.contains("不能为空") })

        val many = (1..21).joinToString(",") { """{ "title": "T$it", "start": "0$it:00", "durationMin": 15 }""" }
        val over = ScheduleParser.parse("""{ "version": 1, "date": "2026-09-23", "tasks": [$many] }""")
        assertFalse(over.isValid)
        assertTrue(over.errors.any { it.contains("20 条") })
    }

    @Test
    fun `任务字段问题逐条列出`() {
        val json = """
            {
              "version": 1, "date": "2026-09-23",
              "tasks": [
                { "title": "缺开始", "durationMin": 30 },
                { "title": "时间格式错", "start": "9点", "durationMin": 30 },
                { "title": "时长越界", "start": "10:00", "durationMin": 5 },
                { "title": "颜色非法", "start": "11:00", "durationMin": 30, "color": "rainbow" },
                { "start": "12:00", "durationMin": 30 },
                { "title": "${"长".repeat(31)}", "start": "13:00", "durationMin": 30 }
              ]
            }
        """.trimIndent()
        val result = ScheduleParser.parse(json)
        assertFalse(result.isValid)
        val text = result.errors.joinToString("\n")
        assertTrue(text.contains("第 1 条任务：缺少开始时间"))
        assertTrue(text.contains("第 2 条任务：开始时间格式错误"))
        assertTrue(text.contains("第 3 条任务：时长需在 15–480 分钟之间"))
        assertTrue(text.contains("第 4 条任务：颜色 color 不合法"))
        assertTrue(text.contains("第 5 条任务：缺少标题"))
        assertTrue(text.contains("第 6 条任务：标题超过 30 字"))
    }

    @Test
    fun `硬冲突区间重叠被拦截并列出冲突对`() {
        val json = """
            {
              "version": 1, "date": "2026-09-23",
              "tasks": [
                { "title": "A", "start": "09:00", "durationMin": 90 },
                { "title": "B", "start": "10:00", "durationMin": 60 }
              ]
            }
        """.trimIndent()
        val result = ScheduleParser.parse(json)
        assertFalse(result.isValid)
        val text = result.errors.joinToString("\n")
        assertTrue(text.contains("硬冲突"))
        assertTrue(text.contains("第 1 条"))
        assertTrue(text.contains("第 2 条"))
    }

    @Test
    fun `跨午夜任务被拦截`() {
        val json = """
            { "version": 1, "date": "2026-09-23",
              "tasks": [ { "title": "夜猫子", "start": "23:30", "durationMin": 60 } ] }
        """.trimIndent()
        val result = ScheduleParser.parse(json)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("跨午夜") })
    }

    @Test
    fun `软冲突只提示不拦截`() {
        val json = """
            {
              "version": 1, "date": "2026-09-23",
              "tasks": [
                { "title": "A", "start": "09:00", "durationMin": 90 },
                { "title": "B", "start": "10:45", "durationMin": 60 }
              ]
            }
        """.trimIndent()
        val result = ScheduleParser.parse(json)
        assertTrue(result.errors.toString(), result.isValid)
        assertTrue(result.warnings.any { it.contains("软冲突") })
    }

    @Test
    fun `乱序自动排序且缺省颜色循环分配`() {
        val json = """
            {
              "version": 1, "date": "2026-09-23",
              "tasks": [
                { "title": "下午任务", "start": "14:00", "durationMin": 60 },
                { "title": "上午任务", "start": "09:00", "durationMin": 60 }
              ]
            }
        """.trimIndent()
        val result = ScheduleParser.parse(json)
        assertTrue(result.isValid)
        val tasks = result.schedule!!.tasks
        assertEquals("上午任务", tasks[0].title)
        assertEquals("下午任务", tasks[1].title)
        assertEquals(TaskColor.Pink, tasks[0].color)     // 循环从 pink 开始
        assertEquals(TaskColor.Purple, tasks[1].color)
    }

    @Test
    fun `title 缺省取 date`() {
        val json = """
            { "version": 1, "date": "2026-09-23",
              "tasks": [ { "title": "A", "start": "09:00", "durationMin": 60 } ] }
        """.trimIndent()
        val result = ScheduleParser.parse(json)
        assertNotNull(result.schedule)
        assertEquals("2026-09-23", result.schedule!!.title)
    }
}
