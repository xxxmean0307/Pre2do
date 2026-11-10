package com.pastelle.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.core.HeroPhase
import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.core.TaskStatus
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Dimens
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.Track
import com.pastelle.todo.ui.theme.mainColor
import com.pastelle.todo.ui.theme.softColor

/**
 * 当前任务英雄卡（规格 4.1 第 3 项）：
 * 任务主题色 Soft 渐变底、任务标题、阶段标签、当前状态、大号倒计时、番茄钟圆点。
 * 无任务时显示花朵插画 + "今天没有安排，享受留白"。
 */
@Composable
fun HeroTaskCard(
    task: TaskEntity?,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    if (task == null) {
        EmptyHeroCard(modifier)
        return
    }
    val theme = (TaskColor.fromKey(task.color) ?: TaskColor.Pink)
    val countdown = TaskStatus.heroCountdownFor(task, nowMs)
    val statusLabel = when (countdown.phase) {
        HeroPhase.Soon -> stringResource(R.string.hero_status_soon)
        HeroPhase.Prep -> stringResource(R.string.hero_status_prep)
        HeroPhase.Focus -> stringResource(R.string.hero_status_focus)
        HeroPhase.Break -> stringResource(R.string.hero_status_break)
        HeroPhase.Due -> stringResource(R.string.hero_status_due)
    }

    Box(
        modifier
            .pastelleShadow()
            .clip(PastelleShapes.Card)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(theme.softColor(), CardWhite, 0.12f),
                        lerp(theme.softColor(), CardWhite, 0.55f),
                    )
                )
            )
            .clickable(onClick = onOpen)
            .padding(Dimens.CardPadding)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 阶段标签
                if (task.stage != null) {
                    Box(
                        Modifier
                            .clip(PastelleShapes.Chip)
                            .background(CardWhite.copy(alpha = 0.75f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(task.stage, style = PastelleType.Caption, color = theme.mainColor())
                    }
                }
                Spacer(Modifier.weight(1f))
                // 当前状态
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(theme.mainColor())
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(statusLabel, style = PastelleType.Caption, color = Ink)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(task.title, style = PastelleType.PageTitle, color = Ink)
            Spacer(Modifier.height(6.dp))
            // 大号倒计时
            Text(
                text = TimeUtils.formatMmSs(countdown.remainingMs),
                style = PastelleType.Timer,
                color = Ink,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PomodoroDots(
                    completed = task.pomodoroCount,
                    total = TaskStatus.plannedPomodoros(task.durationMin),
                    color = theme.mainColor(),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    TimeUtils.minutesLabel(task.durationMin),
                    style = PastelleType.Caption,
                    color = InkSoft,
                )
            }
            if (task.location != null || task.note != null) {
                Spacer(Modifier.height(6.dp))
                val detail = listOfNotNull(task.location, task.note).joinToString("・")
                Text(detail, style = PastelleType.Caption, color = InkSoft, maxLines = 2)
            }
        }
    }
}

/** 空状态：花朵插画 + "今天没有安排，享受留白" */
@Composable
private fun EmptyHeroCard(modifier: Modifier = Modifier) {
    Box(
        modifier
            .pastelleShadow()
            .clip(PastelleShapes.Card)
            .background(CardWhite)
            .padding(Dimens.CardPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FlowerBloom(
                size = 110.dp,
                petalColor = com.pastelle.todo.ui.theme.PinkSoft,
                centerColor = com.pastelle.todo.ui.theme.Pink,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_hero_empty),
                style = PastelleType.CardTitle,
                color = InkSoft,
            )
        }
    }
}

/** 番茄钟圆点：完成的专注段填实（●●○） */
@Composable
fun PomodoroDots(
    completed: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(Dimens.PomodoroDot)
                    .clip(CircleShape)
                    .background(if (index < completed) color else Track)
            )
        }
    }
}
