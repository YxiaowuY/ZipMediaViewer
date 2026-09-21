package com.example.zipmedia

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
        binding.topBar.visibility = View.GONE

        updateSpeedLabel()

        // 禁用 PlayerView 内置的快退/快进按钮（改用下方独立按钮栏）
        binding.playerView.setShowRewindButton(false)
        binding.playerView.setShowFastForwardButton(false)
        binding.playerView.setShowNextButton(false)
        binding.playerView.setShowPreviousButton(false)
        // 隐藏 PlayerView 内置的播放/暂停按钮（避免与下方重复）
        binding.playerView.setShowPlayButton(false)

        // 顶部信息栏 + 底部控制栏 与 PlayerView 内置控制条联动显隐
        binding.playerView.setControllerVisibilityListener(
            object : PlayerView.ControllerVisibilityListener {
                override fun onVisibilityChanged(visibility: Int) {
                    binding.topBar.visibility = visibility
                    binding.bottomControlBar.visibility = visibility
                }
            }
        )
        binding.playerView.setControllerAutoShow(true)

        // 底部按钮：快退 10 秒 / 暂停-播放 / 快进 10 秒
        binding.btnRewind.setOnClickListener {
            player?.let {
                val pos = (it.currentPosition - 10_000L).coerceAtLeast(0L)
                it.seekTo(pos)
                binding.playerView.showController()
            }
        }
        binding.btnFastForward.setOnClickListener {
            player?.let {
                val pos = (it.currentPosition + 10_000L).coerceAtMost(it.duration.coerceAtLeast(0L))
                it.seekTo(pos)
                binding.playerView.showController()
            }
        }
        binding.btnPlayPause.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
            binding.playerView.showController()
        }

        binding.btnSpeed.setOnClickListener { cycleSpeed() }

        setupPlayer(entryPath)
    }

    private fun cycleSpeed() {
        if (player == null) return
        speedIndex = (speedIndex + 1) % speedLevels.size
        player?.setPlaybackSpeed(speedLevels[speedIndex])
        updateSpeedLabel()
    }

    private fun updateSpeedLabel() {
        val rate = speedLevels[speedIndex]
        val label = if (rate % 1f == 0f) "${rate.toInt()}x" else "${rate}x"
        binding.btnSpeed.text = getString(R.string.speed_hint) + " " + label
    }

    private fun setupPlayer(entryPath: String) {
        if (!cacheFile.exists()) {
            toast("压缩包文件不存在")
            finish()
            return
        }
        binding.loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val dest = File(
                        CacheUtils.videoDir(this@VideoPlayerActivity),
                        "${UUID.randomUUID()}.mp4"
                    )
                    ArchiveLoader.open(cacheFile).use { reader ->
                        reader.extractEntry(entryPath, dest)
                    }
                    dest
                } catch (e: Exception) {
                    null
                }
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
        val exo = ExoPlayer.Builder(this).build().also {
            player = it
        }
        binding.playerView.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.playerView.showController()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 播放中显示暂停图标；暂停时显示播放图标
                val iconRes = if (isPlaying)
                    android.R.drawable.ic_media_pause
                else
                    android.R.drawable.ic_media_play
                binding.btnPlayPause.setImageResource(iconRes)
            }
        })
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onDestroy() {
        player?.release()
        player = null
        // 播放结束后清理抽取的临时视频文件
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