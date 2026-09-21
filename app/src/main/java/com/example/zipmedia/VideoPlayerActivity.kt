package com.example.zipmedia

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.zipmedia.databinding.ActivityVideoPlayerBinding
import com.example.zipmedia.util.CacheUtils
import com.example.zipmedia.util.Extras
import com.example.zipmedia.util.Immersive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var cacheFile: File
    private var player: ExoPlayer? = null
    private var extractedFile: File? = null

    // 支持的最高倍速为 5 倍，可循环切换（0.5x ~ 5x）
    private val speedLevels = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f)
    private var speedIndex = 2 // 默认 1.0x

    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false
    private var barsVisible = false
    private val hideBarsRunnable = Runnable { hideBars() }
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            if (!isSeeking) handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cacheFile = File(intent.getStringExtra(Extras.CACHE_PATH) ?: "")
        val entryPath = intent.getStringExtra(Extras.ENTRY_PATH) ?: ""
        val entryName = intent.getStringExtra(Extras.ENTRY_NAME) ?: "video"

        Immersive.enter(this)
        Immersive.keepScreenOn(this, true)

        binding.tvFileName.text = entryName
        binding.btnBack.setOnClickListener { finish() }

        updateSpeedLabel()

        // 顶部栏：返回 + 文件名 + 倍速胶囊
        binding.btnSpeed.setOnClickListener { cycleSpeed() }

        // 底部按钮
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
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player?.duration ?: 0L
                    val pos = (progress * duration / 1000L).coerceAtLeast(0L)
                    binding.tvCurrentTime.text = formatTime(pos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                handler.removeCallbacks(updateProgressRunnable)
                showBars()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 保持 isSeeking=true，防止 updateProgress 用旧 currentPosition 回拉 SeekBar
                // 等 seekTo 异步生效后再恢复进度更新
                val duration = player?.duration ?: 0L
                if (duration > 0) {
                    val pos = (seekBar?.progress ?: 0) * duration / 1000L
                    player?.seekTo(pos)
                }
                // 延迟 600ms 恢复进度更新，给 seekTo 足够时间生效
                handler.postDelayed({
                    isSeeking = false
                    handler.post(updateProgressRunnable)
                }, 600)
                showBars()
            }
        })

        // 点击视频画面切换控制条显隐
        binding.playerView.setOnClickListener {
            if (barsVisible) hideBars() else showBars()
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
        // max 只在 duration 就绪时设一次
        if (binding.seekBar.max != (duration / 1000L).toInt()) {
            binding.seekBar.max = (duration / 1000L).toInt()
            binding.tvTotalTime.text = formatTime(duration)
        }
        // 拖动期间不覆盖用户设置的 SeekBar progress
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
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        extractedFile?.takeIf { it.exists() }?.let { runCatching { it.delete() } }
        Immersive.exit(this)
        super.onDestroy()
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
