package com.pastelle.todo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.pastelle.todo.ui.nav.AppNav
import com.pastelle.todo.ui.theme.PastelleTheme

/** 单 Activity 架构入口（含系统分享菜单接收日程文本 / .json、桌面小组件直达路由） */
class MainActivity : ComponentActivity() {

    private var shareText: String? by mutableStateOf(null)
    private var pendingRoute: String? by mutableStateOf(null)

    companion object {
        /** 桌面小组件点击时携带的目标路由 */
        const val EXTRA_ROUTE = "pre2do.route"
        const val ROUTE_EXECUTION = "execution"
        const val ROUTE_SCHEDULE_EDIT = "schedule_edit"
        private const val PREF = "pre2do_prefs"
        private const val KEY_NOTIF_ASKED = "notification_permission_requested"
    }

    /**
     * 提醒是这类 App 的核心能力：首次启动主动申请通知权限，而不是等用户
     * 翻到「我的」页手动去系统设置。拒绝后不再重复打扰，权限卡仍保留入口。
     */
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果由权限卡自行反映 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shareText = extractSharedText(intent)
        pendingRoute = extractRoute(intent)
        requestNotificationIfNeeded()
        setContent {
            PastelleTheme {
                AppNav(
                    pendingShareText = shareText,
                    onShareConsumed = { shareText = null },
                    pendingRoute = pendingRoute,
                    onRouteConsumed = { pendingRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedText(intent)?.let { shareText = it }
        extractRoute(intent)?.let { pendingRoute = it }
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 规格 9.5：每次 onResume 用墙钟重新校准计时状态
        (application as PastelleApp).timingEngine.recalibrate()
    }

    private fun extractRoute(intent: Intent?): String? = intent?.getStringExtra(EXTRA_ROUTE)

    private fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences(PREF, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIF_ASKED, false)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
            return
        }
        prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** 分享入口：文本直接读取；.json 以附件流读取 */
    private fun extractSharedText(intent: Intent?): String? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { return it }
        val stream: Uri = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_STREAM,
            Uri::class.java,
        ) ?: return null
        return try {
            contentResolver.openInputStream(stream)?.use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            null
        }
    }
}
