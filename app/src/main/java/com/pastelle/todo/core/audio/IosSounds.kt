package com.pastelle.todo.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 苹果 iOS 系统音效风格交互反馈（全 App 统一音源）。
 *
 * 音色为**程序化合成近似**（Tock / Tink / KeyClick / Pop / Ack / Thunk / Swoosh），
 * 不含苹果版权音频素材；播放走 USAGE_GAME / 媒体音量。
 * 合成与静态 AudioTrack 在启动时预热，点按零延迟；任何失败静默兜底不打断交互。
 */
object IosSounds {

    /** 交互音色清单 */
    enum class Cue { Tick, Tock, Tink, Pop, Ack, Thunk, Swoosh }

    private const val SR = 44100

    @Volatile
    private var ready = false
    private val pools = HashMap<Cue, Array<AudioTrack>>()
    private val rr = HashMap<Cue, Int>()

    /** 后台预热：合成七枚音色 + 各双缓冲静态轨（PastelleApp.onCreate 调用） */
    fun warmUp() {
        synchronized(this) {
            if (ready) return
            Cue.entries.forEach { cue ->
                val data = synth(cue)
                pools[cue] = Array(2) { buildTrack(data) }
                rr[cue] = 0
            }
            ready = true
        }
    }

    /** 音频专用线程：AudioTrack.stop/play 在部分机型（含模拟器）可阻塞数百毫秒，绝不占用主线程 */
    private val audioThread = HandlerThread("ios-sounds").apply { start() }
    private val audioQueue = Handler(audioThread.looper)

    /**
     * 播放一枚音效（异步投递：交互零卡顿；同音色双轨轮转，连点不吞声）。
     * 复位用 pause() 而非 stop()：stop() 同步回收播放线程会造成可感停顿。
     */
    fun play(cue: Cue) {
        audioQueue.post {
            try {
                if (!ready) warmUp()
                val pool = pools[cue] ?: return@post
                val i = rr[cue] ?: 0
                rr[cue] = (i + 1) % pool.size
                val t = pool[i]
                synchronized(t) {
                    t.pause()
                    t.setPlaybackHeadPosition(0)
                    t.play()
                }
            } catch (_: Throwable) {
                // 音效永远不打断交互
            }
        }
    }

    private fun buildTrack(data: ShortArray): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, data.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        return track
    }

    // ── 合成器 ────────────────────────────────────────────────────────

    private fun render(ms: Int, gen: (t: Double, nz: Double) -> Double): ShortArray {
        val n = SR * ms / 1000
        val out = ShortArray(n)
        val rnd = Random(7)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val v = (gen(t, rnd.nextDouble() * 2.0 - 1.0) * 11000.0).toInt()
            out[i] = v.coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun synth(cue: Cue): ShortArray = when (cue) {
        // KeyClick：键盘轻点（16ms 噪声嗒 + 3.3k 嗡尾）
        Cue.Tick -> render(16) { t, nz ->
            0.30 * nz * exp(-t / 0.003) + 0.20 * sin(2.0 * PI * 3300.0 * t) * exp(-t / 0.006)
        }
        // Tock：开关 / Tab 切换（清脆木质嗒）
        Cue.Tock -> render(48) { t, nz ->
            0.24 * nz * exp(-t / 0.002) +
                0.42 * sin(2.0 * PI * 1120.0 * t) * exp(-t / 0.013) +
                0.12 * sin(2.0 * PI * 2240.0 * t) * exp(-t / 0.008)
        }
        // Tink：轻按钮（玻璃轻铃）
        Cue.Tink -> render(95) { t, nz ->
            0.32 * sin(2.0 * PI * 2150.0 * t) * exp(-t / 0.028) +
                0.11 * sin(2.0 * PI * 3400.0 * t) * exp(-t / 0.018) +
                0.10 * nz * exp(-t / 0.002)
        }
        // Pop：弹出 / 进入编辑（音高下滑的噗）
        Cue.Pop -> {
            var ph = 0.0
            render(65) { t, nz ->
                val f = 170.0 + 390.0 * exp(-t / 0.018)
                ph += 2.0 * PI * f / SR
                0.44 * sin(ph) * exp(-t / 0.020) + 0.16 * nz * exp(-t / 0.0015)
            }
        }
        // Ack：确认 / 成功（双音木琴 C6→G6）
        Cue.Ack -> render(240) { t, nz ->
            val first = 0.30 * sin(2.0 * PI * 1046.5 * t) * exp(-t / 0.055)
            val second = if (t < 0.07) 0.0 else {
                val u = t - 0.07
                0.34 * sin(2.0 * PI * 1568.0 * u) * exp(-u / 0.070) +
                    0.07 * sin(2.0 * PI * 3136.0 * u) * exp(-u / 0.030)
            }
            first + second + 0.03 * nz * exp(-t / 0.002)
        }
        // Thunk：删除 / 低沉否定
        Cue.Thunk -> render(120) { t, nz ->
            0.46 * sin(2.0 * PI * 190.0 * t) * exp(-t / 0.035) +
                0.22 * nz * exp(-t / 0.012)
        }
        // Swoosh：沉入 / 浮出转场风声（钟形包络低通噪声）
        Cue.Swoosh -> {
            var lp = 0.0
            render(260) { t, nz ->
                lp += (nz - lp) * 0.055
                0.34 * lp * sin(PI * (t / 0.26).coerceIn(0.0, 1.0))
            }
        }
    }
}
