package com.pastelle.todo

import android.app.Application
import androidx.room.Room
import com.pastelle.todo.core.audio.IosSounds
import com.pastelle.todo.data.PastelleRepository
import com.pastelle.todo.data.avatar.AvatarStore
import com.pastelle.todo.data.db.PastelleDatabase
import com.pastelle.todo.timing.TimingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 应用级容器：数据库、仓库与计时引擎（杀进程重启后从 Room 恢复任务与计时状态） */
class PastelleApp : Application() {

    val database: PastelleDatabase by lazy {
        Room.databaseBuilder(this, PastelleDatabase::class.java, "pastelle.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val repository: PastelleRepository by lazy {
        PastelleRepository(database, this)
    }

    /** 拟我形象与养成（DataStore） */
    val avatarStore: AvatarStore by lazy {
        AvatarStore(this)
    }

    val timingEngine: TimingEngine by lazy {
        TimingEngine(this)
    }

    /**
     * 应用级协程域：用于「页面已经销毁但写库必须完成」的场景。
     * 周轮盘编辑的防抖保存以前挂在页面 LaunchedEffect 上，切 Tab / 返回 / 进程回收
     * 会直接取消协程导致改动丢失；改挂这里后落库与 UI 生命周期解耦。
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        timingEngine.start()
        // iOS 风格音效预热（后台合成七枚音色 + 静态轨，点按零延迟）
        Thread({ IosSounds.warmUp() }, "ios-sounds-warmup").start()
    }
}
