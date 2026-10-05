package com.example.zipmedia

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.zipmedia.data.ArchiveEntry
import com.example.zipmedia.data.ArchiveLoader
import com.example.zipmedia.data.MediaPositionRepository
import com.example.zipmedia.databinding.ActivityVideoPlayerBinding
import com.example.zipmedia.util.CacheUtils
import com.example.zipmedia.util.Extras
import com.example.zipmedia.util.Immersive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var cacheFile: File
    private var player: ExoPlayer? = null
    private var extractedFile: File? = null
    private var entryPath = ""            // 当前视频条目路径（播放进度记忆用）
    private var resumeMs = -1L            // 上次播放进度（毫秒），-1 表示从头开始
    private lateinit var mediaPositionRepo: MediaPositionRepository

    // 支持的最高倍速为 5 倍，可循环切换（0.5x ~ 5x）
    private val speedLevels = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f)
    private var speedIndex = 2 // 默认 1.0x

    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false       // 用户正在拖动或 seek 还未完成
    private var pendingSeekMs = -1L     // 用户 seek 的目标位置（毫秒）
    private var barsVisible = false

    // 屏幕手势 seek 状态
    private var downX = 0f              // 按下时的 X 坐标
    private var dragStartPosMs = 0L     // 拖拽起始播放位置（毫秒）
    private var lastSeekTargetMs = -1L  // 拖拽过程中的目标位置，-1 表示未拖拽
    private var flingSeeked = false     // 本次手势已通过 fling 快进/快退
    private val hideBarsRunnable = Runnable { hideBars() }
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cacheFile = File(intent.getStringExtra(Extras.CACHE_PATH) ?: "")
        entryPath = intent.getStringExtra(Extras.ENTRY_PATH) ?: ""
        val entryName = intent.getStringExtra(Extras.ENTRY_NAME) ?: "video"
        mediaPositionRepo = MediaPositionRepository(this)

        Immersive.enter(this)
        Immersive.keepScreenOn(this, true)

        binding.tvFileName.text = entryName
        binding.btnBack.setOnClickListener { finish() }

        updateSpeedLabel()

        binding.btnSpeed.setOnClickListener { cycleSpeed() }

        // 强制旋转按钮：每次顺时针 90 度（锁定屏幕方向时也生效）
        binding.btnRotate.setOnClickListener {
            val cur = binding.playerView.rotation
            binding.playerView.rotation = (cur + 90f) % 360f
            showBars()
        }

        // 底部按钮（快退/快进固定 10 秒，位置已在毫秒空间，计算正确）
        binding.btnRewind.setOnClickListener {
            player?.let {
                val pos = (it.currentPosition - 10_000L).coerceAtLeast(0L)
                it.seekTo(pos); showBars()
            }
        }
        binding.btnFastForward.setOnClickListener {
            player?.let {
                val pos = (it.currentPosition + 10_000L).coerceAtMost(it.duration.coerceAtLeast(0L))
                it.seekTo(pos); showBars()
            }
        }
        binding.btnPlayPause.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
            showBars()
        }

        // SeekBar 拖动
        // SeekBar max = duration/1000（单位：秒），progress 也是秒
        // seek 位置（毫秒）= progress * 1000L
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 秒 → 毫秒
                    binding.tvCurrentTime.text = formatTime(progress * 1000L)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                handler.removeCallbacks(updateProgressRunnable)
                showBars()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = player
                if (p != null && p.duration > 0) {
                    // progress 是秒，×1000 = 毫秒
                    val posMs = (seekBar?.progress ?: 0) * 1000L
                    pendingSeekMs = posMs
                    p.seekTo(posMs)
                    // seekTo 是异步的，保持 isSeeking=true 等 onSeekCompleted 回调再解锁
                } else {
                    isSeeking = false
                }
                showBars()
            }
        })

        // 屏幕手势：单击切换控制条；左右滑动快进/快退；按住水平拖拽可精确 seek
        binding.playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP
                || event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                finalizeDragSeek()
            }
            true
        }

        setupPlayer(entryPath)
    }

    private fun showBars() {
        barsVisible = true
        binding.topBar.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideBarsRunnable)
        handler.postDelayed(hideBarsRunnable, 3500)
    }

    private fun hideBars() {
        barsVisible = false
        binding.topBar.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
    }

    // ===== 屏幕手势 seek =====
    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                downX = e.x
                val p = player
                if (p != null && p.duration > 0) {
                    dragStartPosMs = p.currentPosition.coerceAtLeast(0L)
                    lastSeekTargetMs = -1L
                    isSeeking = true
                    handler.removeCallbacks(updateProgressRunnable)
                }
                return true
            }

            // 按住水平拖拽：手指移动映射到整段进度，实时预览
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                dragSeekPreview(e2.x)
                return true
            }

            // 快速左右滑动：快进/快退（向右快进、向左快退）固定 10 秒
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                flingSeek(velocityX)
                return true
            }

            // 单击：切换控制条
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (barsVisible) hideBars() else showBars()
                return true
            }
        })
    }

    /** 快速滑动：向右快进、向左快退，固定 10 秒 */
    private fun flingSeek(velocityX: Float) {
        val p = player ?: return
        val duration = p.duration
        if (duration <= 0) return
        val target = if (velocityX < 0)
            (p.currentPosition - 10_000L).coerceAtLeast(0L)
        else
            (p.currentPosition + 10_000L).coerceAtMost(duration.coerceAtLeast(0L))
        flingSeeked = true
        pendingSeekMs = target
        p.seekTo(target)
        binding.seekBar.progress = (target / 1000L).toInt().coerceAtMost(binding.seekBar.max)
        binding.tvCurrentTime.text = formatTime(target)
        showBars()
    }

    /** 按住拖拽：水平移动距离按比例映射到播放进度（右移快进、左移快退） */
    private fun dragSeekPreview(x: Float) {
        val p = player ?: return
        val duration = p.duration
        if (duration <= 0) return
        val width = binding.playerView.width.toFloat()
        if (width <= 0) return
        val moveX = x - downX
        val target = (dragStartPosMs + (moveX / width * duration).toLong()).coerceIn(0L, duration)
        lastSeekTargetMs = target
        binding.seekBar.progress = (target / 1000L).toInt().coerceAtMost(binding.seekBar.max)
        binding.tvCurrentTime.text = formatTime(target)
    }

    /** 手指抬起：执行一次最终 seek；纯点击则解除 seek 锁定并恢复进度刷新 */
    private fun finalizeDragSeek() {
        // fling 已执行 seek，只需复位标志，isSeeking 由 onPlaybackStateChanged 解锁
        if (flingSeeked) {
            flingSeeked = false
            showBars()
            return
        }
        val p = player
        val dragTarget = lastSeekTargetMs
        lastSeekTargetMs = -1L
        if (p != null && dragTarget >= 0 && p.duration > 0) {
            pendingSeekMs = dragTarget
            p.seekTo(dragTarget)
            binding.seekBar.progress = (dragTarget / 1000L).toInt().coerceAtMost(binding.seekBar.max)
            binding.tvCurrentTime.text = formatTime(dragTarget)
            // isSeeking 保持 true，等 onPlaybackStateChanged READY 解锁
        } else {
            // 纯点击/无 seek：解除锁定并恢复进度刷新
            isSeeking = false
            handler.post(updateProgressRunnable)
        }
        showBars()
    }

    private fun cycleSpeed() {
        if (player == null) return
        speedIndex = (speedIndex + 1) % speedLevels.size
        player?.setPlaybackSpeed(speedLevels[speedIndex])
        updateSpeedLabel()
        showBars()
    }

    private fun updateSpeedLabel() {
        val rate = speedLevels[speedIndex]
        val label = if (rate % 1f == 0f) "${rate.toInt()}x" else "${rate}x"
        binding.btnSpeed.text = label
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000L
        val m = s / 60; val sec = s % 60
        return "%02d:%02d".format(m, sec)
    }

    private fun updateProgress() {
        val p = player ?: return
        val duration = p.duration
        if (duration <= 0) return
        val pos = p.currentPosition.coerceAtLeast(0L)
        // max = duration / 1000，单位秒，只设一次
        val expectedMax = (duration / 1000L).toInt()
        if (binding.seekBar.max != expectedMax) {
            binding.seekBar.max = expectedMax
            binding.tvTotalTime.text = formatTime(duration)
        }
        // 用户拖动或 seek 未完成期间，不覆盖用户设置的位置
        if (!isSeeking) {
            binding.seekBar.progress = (pos / 1000L).toInt().coerceAtMost(binding.seekBar.max)
            binding.tvCurrentTime.text = formatTime(pos)
        }
    }

    private fun setupPlayer(entryPath: String) {
        if (!cacheFile.exists()) {
            toast("压缩包文件不存在"); finish(); return
        }
        binding.loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val dest = File(
                        CacheUtils.videoDir(this@VideoPlayerActivity),
                        "${UUID.randomUUID()}.mp4"
                    )
                    ArchiveLoader.open(cacheFile).use { reader -> reader.extractEntry(entryPath, dest) }
                    dest
                } catch (e: Exception) { null }
            }
            if (file == null) {
                toast(getString(R.string.extract_failed))
                binding.loadingOverlay.visibility = View.GONE
                finish()
                return@launch
            }
            extractedFile = file
            // 查询上次播放进度，用于自动续播（无记录则为 -1，从头开始）
            resumeMs = withContext(Dispatchers.IO) {
                mediaPositionRepo.getVideo(cacheFile.absolutePath, entryPath) ?: -1L
            }
            playFile(file)
        }
    }

    private fun playFile(file: File) {
        val exo = ExoPlayer.Builder(this).build().also { player = it }
        binding.playerView.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    binding.loadingOverlay.visibility = View.GONE
                    // seek 完成检测：seekTo 后 player 会先 BUFFERING 再回到 READY
                    // 此时 isSeeking=true 说明是 seek 完成，解锁让进度条开始跟随 player
                    if (isSeeking) {
                        isSeeking = false
                        pendingSeekMs = -1L
                    }
                    updateProgress()
                    handler.post(updateProgressRunnable)
                    showBars()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                binding.btnPlayPause.setImageResource(icon)
            }
        })
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        // 上次有播放进度则自动续播（ExoPlayer 支持 prepare 前设置初始位置）
        if (resumeMs > 0) {
            exo.seekTo(resumeMs)
            resumeMs = -1L
        }
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        savePlaybackProgress()
        player?.release()
        player = null
        extractedFile?.takeIf { it.exists() }?.let { runCatching { it.delete() } }
        Immersive.exit(this)
        super.onDestroy()
    }

    /** 退出时记录播放进度；接近结尾视为看完并清除记录 */
    private fun savePlaybackProgress() {
        val p = player ?: return
        val dur = p.duration
        val pos = p.currentPosition.coerceAtLeast(0L)
        if (dur <= 0 || entryPath.isEmpty()) return
        // 用独立作用域，确保 Activity 销毁时也能写入
        if (pos >= dur - 3000 && pos > 0) {
            CoroutineScope(Dispatchers.IO + Job()).launch {
                mediaPositionRepo.clearVideo(cacheFile.absolutePath, entryPath)
            }
        } else if (pos in 1 until dur - 3000) {
            CoroutineScope(Dispatchers.IO + Job()).launch {
                mediaPositionRepo.saveVideo(cacheFile.absolutePath, entryPath, pos)
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        fun start(context: Context, cachePath: String, entry: ArchiveEntry) {
            context.startActivity(
                Intent(context, VideoPlayerActivity::class.java)
                    .putExtra(Extras.CACHE_PATH, cachePath)
                    .putExtra(Extras.ENTRY_PATH, entry.path)
                    .putExtra(Extras.ENTRY_NAME, entry.name)
            )
        }
    }
}
