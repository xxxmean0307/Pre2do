package com.pastelle.todo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pastelle.todo.PastelleApp
import com.pastelle.todo.data.PastelleRepository
import com.pastelle.todo.timing.TimingEngine

/** 在 Compose 中取得应用级仓库 */
@Composable
fun rememberRepository(): PastelleRepository {
    val context = LocalContext.current
    return (context.applicationContext as PastelleApp).repository
}

/** 在 Compose 中取得计时引擎 */
@Composable
fun rememberTimingEngine(): TimingEngine {
    val context = LocalContext.current
    return (context.applicationContext as PastelleApp).timingEngine
}
