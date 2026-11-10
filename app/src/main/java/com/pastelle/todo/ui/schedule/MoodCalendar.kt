package com.pastelle.todo.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.ui.components.CircleIconButton
import com.pastelle.todo.ui.components.MoodFace
import com.pastelle.todo.ui.components.MoodLevel
import com.pastelle.todo.ui.components.pressScale
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Dimens
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.Pink
import com.pastelle.todo.ui.theme.Track
import java.time.LocalDate
import java.time.YearMonth

/**
 * Mood Calendar 月视图（规格 4.3 + 参考图一）：
 * 月份切换圆形箭头、周日历头；有任务的日期格整格是彩色表情脸（按当日完成度着色），
 * 无任务日为白色格 + 日期数字，今天描粗黑边，点选日期切换下方列表。
 */
@Composable
fun MoodCalendar(
    viewMonth: YearMonth,
    selectedDate: String,
    today: String,
    dayMoods: Map<String, MoodLevel>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (String) -> Unit,
) {
    val firstDay = viewMonth.atDay(1)
    val daysInMonth = viewMonth.lengthOfMonth()
    // 周一为首
    val offset = (firstDay.dayOfWeek.value + 6) % 7
    val cells: List<LocalDate?> =
        List(offset) { null } + (1..daysInMonth).map { viewMonth.atDay(it) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(PastelleShapes.Card)
            .background(CardWhite)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        // 月份切换（圆形箭头钮，参考图一；白卡内用 Track 底色保证可见）
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_prev_month),
                onClick = onPrevMonth,
                size = 36.dp,
                iconSize = 20.dp,
                backgroundColor = Track,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(
                    R.string.calendar_month_fmt,
                    viewMonth.year,
                    viewMonth.monthValue,
                ),
                style = PastelleType.CardTitle,
                color = Ink,
            )
            Spacer(Modifier.weight(1f))
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_month),
                onClick = onNextMonth,
                size = 36.dp,
                iconSize = 20.dp,
                backgroundColor = Track,
            )
        }
        Spacer(Modifier.height(10.dp))

        // 周日历头
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, style = PastelleType.Caption, color = InkSoft)
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // 日期格：有任务的日子整格是表情脸（末行补空格对齐列）
        cells.chunked(7).map { it + List(7 - it.size) { null } }.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val dateKey = TimeUtils.formatDay(day)
                            DayCell(
                                dayOfMonth = day.dayOfMonth,
                                mood = dayMoods[dateKey],
                                isToday = dateKey == today,
                                isSelected = dateKey == selectedDate,
                                onClick = { onSelectDate(dateKey) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DayCell(
    dayOfMonth: Int,
    mood: MoodLevel?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(Dimens.CalendarCell)
            .clip(PastelleShapes.Face)
            .background(if (mood == null) CardWhite else Color.Transparent)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (mood != null) {
            // 整格表情脸（参考图一的日历格）
            MoodFace(level = mood, faceSize = Dimens.CalendarCell)
            Text(
                "$dayOfMonth",
                style = PastelleType.TabLabel.copy(fontWeight = FontWeight.ExtraBold),
                color = if (mood == MoodLevel.Star) CardWhite else Ink,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 3.dp),
            )
        } else {
            Text(
                "$dayOfMonth",
                style = if (isToday) {
                    PastelleType.Caption.copy(fontWeight = FontWeight.ExtraBold)
                } else {
                    PastelleType.Caption
                },
                color = Ink,
            )
        }

        // 今天描粗边 / 选中高亮（规格 4.3）
        val borderMod = when {
            isToday -> Modifier.border(2.dp, Ink, PastelleShapes.Face)
            isSelected -> Modifier.border(2.dp, Pink, PastelleShapes.Face)
            else -> Modifier
        }
        Box(Modifier.fillMaxSize().then(borderMod))
    }
}
