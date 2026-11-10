package com.pastelle.todo.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pastelle.todo.R

/**
 * 全 App 唯一的视频动态背景（M28.18）。
 *
 * 素材：res/raw/bg_flow.mp4 —— 由用户提供的「9月25日.mp4」裁去两侧黑边、
 * 降帧、正放＋倒放拼成无缝乒乓循环后转码的 H.264/Main/yuv420p 静音片。
 *
 * 实现要点：
 * - 挂在 AppNav 最底层、NavHost 之外：所有页面共享同一个解码器，切页不重启、不黑闪。
 * - TextureView（而非 SurfaceView）：普通 View 层级，无打洞 / z-order 问题，
 *   可被上层半透明调和层与毛玻璃底栏正常透出。
 * - VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING：centerCrop，横屏素材竖屏满铺无黑边。
 * - 静音 + 不申请音频焦点；随生命周期 ON_PAUSE/ON_STOP 暂停、ON_RESUME 恢复。
 * - 任何环节失败都只是「没有视频」，下层静态渐变兜底照常显示，不影响功能。
 */
@Composable
fun VideoBackground(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { VideoBackdropHolder() }

    AndroidView(
        factory = { ctx -> VideoBackdropView(ctx).also { holder.view = it } },
        modifier = modifier.fillMaxSize(),
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.view?.resume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> holder.view?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.view?.release()
            holder.view = null
        }
    }
}

/** 让 Lifecycle 观察者能拿到 AndroidView 创建出来的 View，又不触发重组。 */
private class VideoBackdropHolder {
    var view: VideoBackdropView? = null
}

private class VideoBackdropView(context: Context) :
    TextureView(context),
    TextureView.SurfaceTextureListener {

    private var player: MediaPlayer? = null
    private var wantPlaying = true
    private var disposed = false

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (disposed) return
        start(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // Surface 消失（转场 / 后台被回收）时释放解码器；重新 available 时会重建。
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun start(surface: SurfaceTexture) {
        releasePlayer()
        val mp = MediaPlayer()
        try {
            // openRawResourceFd 比 android.resource:// URI 在 API 26 上更可靠
            context.resources.openRawResourceFd(R.raw.bg_flow).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setSurface(Surface(surface))
            mp.setVolume(0f, 0f)
            mp.isLooping = true
            mp.setOnPreparedListener { p ->
                runCatching {
                    p.setVideoScalingMode(
                        MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    )
                }
                if (wantPlaying && !disposed) runCatching { p.start() }
            }
            mp.setOnErrorListener { _, _, _ -> true } // 吞掉错误，静态兜底即可
            mp.prepareAsync()
            player = mp
        } catch (t: Throwable) {
            runCatching { mp.release() }
        }
    }

    fun resume() {
        wantPlaying = true
        val p = player ?: return
        runCatching { if (!p.isPlaying) p.start() }
    }

    fun pause() {
        wantPlaying = false
        val p = player ?: return
        runCatching { if (p.isPlaying) p.pause() }
    }

    fun release() {
        disposed = true
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let { p ->
            runCatching { p.reset() }
            runCatching { p.release() }
        }
        player = null
    }
}
