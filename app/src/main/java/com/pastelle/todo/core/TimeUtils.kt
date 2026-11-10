package com.pastelle.todo.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 时间工具：日期/时刻解析与展示（本地时区、墙钟语义） */
object TimeUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeRegex = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    fun today(): String = formatDay(LocalDate.now())

    fun formatDay(date: LocalDate): String = date.format(dateFormatter)

    fun parseDateOrNull(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value.trim(), dateFormatter)
        } catch (e: Exception) {
            null
        }
    }

    /** "09:00" → 540；格式非法返回 null */
    fun parseStartMinuteOrNull(value: String?): Int? {
        val raw = value?.trim() ?: return null
        val match = timeRegex.matchEntire(raw) ?: return null
        val (h, m) = match.destructured
        return h.toInt() * 60 + m.toInt()
    }

    /** 540 → "09:00" */
    fun formatHhMm(minuteOfDay: Int): String {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** 日期 + 当天分钟数 → epoch 毫秒（系统墙钟） */
    fun epochMs(date: String, minuteOfDay: Int): Long {
        val day = parseDateOrNull(date) ?: LocalDate.now()
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() +
            minuteOfDay * 60_000L
    }

    fun epochMsOfDate(date: String): Long {
        val day = parseDateOrNull(date) ?: LocalDate.now()
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** 时长标签："90 分钟" */
    fun minutesLabel(minutes: Int): String = "$minutes 分钟"

    /** 毫秒倒计时 → "mm:ss"（超过 1 小时显示 "h:mm:ss"） */
    fun formatMmSs(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** 时间间隔标签："2 小时 30 分钟" / "45 分钟" */
    fun humanGap(totalMinutes: Int): String {
        val m = totalMinutes.coerceAtLeast(0)
        val h = m / 60
        val rest = m % 60
        return when {
            h > 0 && rest > 0 -> "$h 小时 $rest 分钟"
            h > 0 -> "$h 小时"
            else -> "$rest 分钟"
        }
    }

    /** 周标签："周三" */
    fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    /** 月日标签："9 月 23 日" */
    fun monthDayLabel(date: LocalDate): String = "${date.monthValue} 月 ${date.dayOfMonth} 日"
}
