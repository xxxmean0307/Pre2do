package com.pastelle.todo.ui.more

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.pastelle.todo.R
import com.pastelle.todo.core.PastelleConstants
import com.pastelle.todo.ui.components.BlackPillButton
import com.pastelle.todo.ui.components.PastelBackground
import com.pastelle.todo.ui.components.PastelleCard
import com.pastelle.todo.ui.components.PermissionGuideCard
import com.pastelle.todo.ui.components.PillTrailingIcon
import com.pastelle.todo.ui.rememberRepository
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleType
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 「我的」页（规格 7.4 / 9.1）：
 * 权限引导（通知 / 精确闹钟 / 电池白名单）→ 计时规则只读展示 → JSON 导出（分享 / 保存到文件）→ 关于。
 */
@Composable
fun MoreScreen() {
    val context = LocalContext.current
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()

    // 导出对象：今天；今天无数据则取最近有日程的一天。
    // ★ 观察今日任务 Flow：当天新建任务后导出日期会跟着刷新，不再只在首次进入算一次。
    val today = LocalDate.now().toString()
    val todayTasks by repo.observeTasks(today).collectAsState(initial = emptyList())
    var exportDate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(todayTasks.size) {
        exportDate =
            if (repo.exportDayJson(today) != null) today
            else repo.latestScheduleDate()
    }

    suspend fun buildExport(): String? = exportDate?.let { repo.exportDayJson(it) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = buildExport() ?: return@launch
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        if (ok) R.string.more_export_saved else R.string.more_export_failed,
                    ),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    PastelBackground(flowerSeed = 53) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 104.dp)   // 末项可滑出悬浮胶囊区
        ) {
            Text(stringResource(R.string.more_title), style = PastelleType.PageTitle, color = Ink)
            Spacer(Modifier.height(16.dp))

            // 权限引导（规格 9.1）
            PermissionGuideCard(Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            // 计时规则（只读）
            PastelleCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.more_const_title), style = PastelleType.CardTitle, color = Ink)
                Spacer(Modifier.height(8.dp))
                ConstRow(stringResource(R.string.more_const_prep_fmt, PastelleConstants.PREP_SECONDS / 60))
                ConstRow(stringResource(R.string.more_const_focus_fmt, PastelleConstants.FOCUS_SECONDS / 60))
                ConstRow(stringResource(R.string.more_const_break_fmt, PastelleConstants.BREAK_SECONDS / 60))
                ConstRow(
                    stringResource(
                        R.string.more_const_window_fmt,
                        (PastelleConstants.EARLY_FACTOR * 100).roundToInt(),
                        (PastelleConstants.LATE_FACTOR * 100).roundToInt(),
                    )
                )
                ConstRow(
                    stringResource(
                        R.string.more_const_gears_fmt,
                        PastelleConstants.COMPLETION_GEARS.joinToString("・") { "${(it * 100).roundToInt()}%" },
                    )
                )
            }
            Spacer(Modifier.height(16.dp))

            // JSON 导出（往返格式：可直接重新导入）
            PastelleCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.more_export_title), style = PastelleType.CardTitle, color = Ink)
                Spacer(Modifier.height(6.dp))
                val date = exportDate
                if (date == null) {
                    Text(stringResource(R.string.more_export_empty), style = PastelleType.Body, color = InkSoft)
                } else {
                    Text(
                        stringResource(R.string.more_export_pick, date),
                        style = PastelleType.Caption,
                        color = InkSoft,
                    )
                    Spacer(Modifier.height(12.dp))
                    BlackPillButton(
                        label = stringResource(R.string.more_export_share),
                        onClick = {
                            scope.launch {
                                val text = buildExport() ?: return@launch
                                runCatching {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, null))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = PillTrailingIcon.Arrow,
                        height = 52.dp,
                    )
                    Spacer(Modifier.height(10.dp))
                    BlackPillButton(
                        label = stringResource(R.string.more_export_save),
                        onClick = {
                            saveLauncher.launch("pastelle-schedule-$date.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = PillTrailingIcon.Check,
                        height = 52.dp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 桌面小组件入口
            val widgetHint = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            PastelleCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.more_widget_title), style = PastelleType.CardTitle, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.more_widget_body), style = PastelleType.Body, color = InkSoft)
                Spacer(Modifier.height(12.dp))
                BlackPillButton(
                    label = stringResource(R.string.more_widget_add),
                    onClick = {
                        val mgr = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                        val cn = android.content.ComponentName(context, com.pastelle.todo.widget.TodayWidget::class.java)
                        // 无论桌面是否声明支持都发起；结果永远给反馈（旧版支持判定为假时静默无动作＝"没反应"）
                        val supported = mgr?.isRequestPinAppWidgetSupported == true
                        val sent = runCatching {
                            if (mgr != null) mgr.requestPinAppWidget(cn, null, null) else false
                        }.getOrDefault(false)
                        widgetHint.value = if (sent && supported) {
                            context.getString(R.string.more_widget_sent)
                        } else {
                            context.getString(R.string.more_widget_fallback)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = PillTrailingIcon.Arrow,
                    height = 52.dp,
                )
                val hint = widgetHint.value
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(hint, style = PastelleType.Caption, color = InkSoft)
                }
            }
            Spacer(Modifier.height(16.dp))

            // 关于
            PastelleCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.more_about_title), style = PastelleType.CardTitle, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.more_about_body), style = PastelleType.Body, color = InkSoft)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConstRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text, style = PastelleType.Body, color = Ink)
        Spacer(Modifier.width(8.dp))
    }
}
