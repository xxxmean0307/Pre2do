package com.pastelle.todo.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pastelle.todo.R
import com.pastelle.todo.ui.theme.BlackPill
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleType

/** 通知权限是否已开 */
fun notificationsEnabled(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

/** 精确闹钟是否可用（API < 31 恒为可用） */
fun exactAlarmAllowed(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    } else {
        true
    }

/** 通知权限状态：ON_RESUME 时刷新（从系统设置返回后立即反映真实状态） */
@Composable
private fun rememberNotifEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = notificationsEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

/** 精确闹钟状态：ON_RESUME 时刷新 */
@Composable
private fun rememberExactAlarmAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(exactAlarmAllowed(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = exactAlarmAllowed(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return allowed
}

/** 电池优化白名单真实状态（M32：此前这一行硬编码「已开启」） */
fun batteryWhitelisted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 23) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else {
        true
    }

/** 电池白名单状态：ON_RESUME 时刷新（从系统设置返回后立即反映真实状态） */
@Composable
private fun rememberBatteryWhitelisted(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(batteryWhitelisted(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = batteryWhitelisted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return allowed
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra("android.provider.extra.APP_PACKAGE", context.packageName),
            )
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:" + context.packageName)),
            )
        }
    }
}

private fun openExactAlarmSettings(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(android.net.Uri.parse("package:" + context.packageName)),
            )
        }
    }
}

private fun openBatterySettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/** 权限行：说明 + 状态 + 「去开启」 */
@Composable
private fun PermRow(desc: String, granted: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(desc, style = PastelleType.Body, color = Ink)
            Text(
                text = stringResource(if (granted) R.string.more_perm_granted else R.string.more_perm_missing),
                style = PastelleType.Caption,
                color = if (granted) BlackPill else InkSoft,
            )
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.more_perm_open),
                style = PastelleType.ButtonLabel,
                color = BlackPill,
                modifier = Modifier
                    .clickablePlain(onOpen)
                    .padding(8.dp),
            )
        }
    }
}

private fun Modifier.clickablePlain(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/**
 * 权限引导卡（规格 9.1）：通知 / 精确闹钟 / 电池白名单三项的状态与入口。
 * 用于今日页顶部（可关闭）与「我的」页常驻。
 */
@Composable
fun PermissionGuideCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val notifGranted = rememberNotifEnabled()
    val alarmGranted = rememberExactAlarmAllowed()
    val batteryGranted = rememberBatteryWhitelisted()
    PastelleCard(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.more_perm_title), style = PastelleType.CardTitle, color = Ink)
        Spacer(Modifier.height(8.dp))
        PermRow(
            desc = stringResource(R.string.more_perm_notif),
            granted = notifGranted,
            onOpen = { openNotificationSettings(context) },
        )
        PermRow(
            desc = stringResource(R.string.more_perm_alarm),
            granted = alarmGranted,
            onOpen = { openExactAlarmSettings(context) },
        )
        if (!compact) {
            PermRow(
                desc = stringResource(R.string.more_perm_battery),
                granted = batteryGranted,
                onOpen = { openBatterySettings(context) },
            )
        }
    }
}

/** 今日页权限引导（规格 9.1）：仅在通知未开启时出现 */
@Composable
fun HomePermissionCard(modifier: Modifier = Modifier) {
    if (!rememberNotifEnabled()) {
        PermissionGuideCard(modifier, compact = true)
    }
}
