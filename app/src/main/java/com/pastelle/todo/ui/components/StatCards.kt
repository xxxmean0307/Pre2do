package com.pastelle.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.PeachSoft
import com.pastelle.todo.ui.theme.PinkSoft

/**
 * KPI 小卡三连（参考图一 Mood Calendar 页的 Activity / Therapy / Discipline 卡样式）：
 * 小字标题 + 大号数字 + 小字单位。
 */
@Composable
fun StatMiniCard(
    caption: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .pastelleShadow()
            .background(CardWhite, PastelleShapes.Card)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(caption, style = PastelleType.Caption, color = InkSoft)
            Spacer(Modifier.height(6.dp))
            Text(value, style = PastelleType.Percent, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text(unit, style = PastelleType.Caption, color = InkSoft)
        }
    }
}

/**
 * 月度总结渐变卡（参考图一的 Monthly Mood Summary 卡）：
 * 粉→蜜桃渐变底 + 小字标题 + 大字评价 + 一句文案 + 右侧大表情脸。
 */
@Composable
fun MonthSummaryCard(
    title: String,
    word: String,
    description: String,
    level: MoodLevel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .pastelleShadow()
            .background(
                Brush.linearGradient(listOf(PinkSoft, PeachSoft)),
                PastelleShapes.Card,
            )
            .padding(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = PastelleType.Caption, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(word, style = PastelleType.PageTitle, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(description, style = PastelleType.Caption, color = Ink, maxLines = 3)
            }
            Spacer(Modifier.width(12.dp))
            MoodFace(level = level, faceSize = 72.dp)
        }
    }
}
