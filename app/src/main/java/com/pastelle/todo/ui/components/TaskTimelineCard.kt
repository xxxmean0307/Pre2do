package com.pastelle.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.Track

/**
 * 任务卡白色面（展示态 / 星期卡片块 / 形变飞行层三方共用）：
 * 字体、内边距、圆角、阴影由构造唯一 —— 形变前后逐像素一致，杜绝「变换前后字体不一样」。
 * 高度不足时按同一张卡自然裁切（短块 = 卡面顶部裁切），不换排版。
 */
@Composable
fun TaskCardSurface(
    startLabel: String,
    title: String,
    durationLabel: String,
    themeColor: Color,
    modifier: Modifier = Modifier,
    stage: String? = null,
    location: String? = null,
    note: String? = null,
    trailing: String? = null,
    compact: Boolean = false,
) {
    if (compact) {
        // ★ 矮块紧凑单行（时间・标题）：块多到高度不足时内文不再被内边距挤没（M27「任务消失」修复）
        Row(
            modifier = modifier
                .pastelleShadow()
                .clip(PastelleShapes.Card)
                .background(CardWhite)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(startLabel, style = PastelleType.Caption, color = themeColor)
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = PastelleType.Caption,
                color = Ink,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }
    Column(
        modifier = modifier
            .pastelleShadow()
            .clip(PastelleShapes.Card)
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(startLabel, style = PastelleType.CardTitle, color = themeColor)
            if (stage != null) {
                Spacer(Modifier.width(10.dp))
                Text(stage, style = PastelleType.Caption, color = InkSoft)
            }
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = PastelleType.Caption, color = InkSoft)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = PastelleType.CardTitle, color = Ink)
        Spacer(Modifier.height(4.dp))
        Row {
            Text(durationLabel, style = PastelleType.Caption, color = InkSoft)
            if (location != null) {
                Text("・$location", style = PastelleType.Caption, color = InkSoft, maxLines = 1)
            }
        }
        if (note != null) {
            Spacer(Modifier.height(4.dp))
            Text(note, style = PastelleType.Caption, color = InkSoft, maxLines = 2)
        }
    }
}

/**
 * 时间线任务卡（今日页 / 导入预览 / 日程展示态共用）：
 * 左侧时间轴竖线 + 任务色圆点；白色卡面 = [TaskCardSurface]。
 * 入场按列表顺序 35ms 级联（cardEntrance）。
 */
@Composable
fun TaskTimelineCard(
    startLabel: String,
    title: String,
    durationLabel: String,
    themeColor: Color,
    index: Int = 0,
    stage: String? = null,
    location: String? = null,
    note: String? = null,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .cardEntrance(index),
    ) {
        // ── 左侧时间轴：圆点 + 竖线 ──
        Column(
            modifier = Modifier.width(28.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(themeColor)
            )
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(Track)
            )
        }
        // ── 右侧白色卡片（与星期卡片块 / 形变飞行层同源）──
        TaskCardSurface(
            startLabel = startLabel,
            title = title,
            durationLabel = durationLabel,
            themeColor = themeColor,
            modifier = Modifier.weight(1f).padding(bottom = 14.dp),
            stage = stage,
            location = location,
            note = note,
            trailing = trailing,
        )
    }
}
