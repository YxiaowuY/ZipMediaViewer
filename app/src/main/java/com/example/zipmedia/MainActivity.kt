package com.example.zipmedia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.zipmedia.data.ArchiveLoader
import com.example.zipmedia.data.HistoryRepository
import com.example.zipmedia.databinding.ActivityMainBinding
import com.example.zipmedia.util.CacheUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 窗口1（主页）：
 *  - 打开压缩包（系统选择器）
 *  - 打开预览文件夹（跳转 FolderPreviewActivity）
 *  - 历史记录（跳转 HistoryActivity）
 *  - 清理缓存
 *
 * 历史记录列表与文件夹预览已拆分到独立窗口，主页只保留入口按钮。
 * 所有诊断 Toast 已移除。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: HistoryRepository

    private val openArchiveLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openFromUri(it, queryDisplayName(it) ?: "archive.zip") }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = HistoryRepository(this)

        binding.btnOpenArchive.setOnClickListener {
            openArchiveLauncher.launch(
                arrayOf("application/zip", "application/x-rar-compressed", "application/vnd.rar")
            )
        }

        // 跳转到预览文件夹窗口
        binding.btnOpenFolder.setOnClickListener {
            startActivity(Intent(this, FolderPreviewActivity::class.java))
        }

        // 跳转到历史记录窗口
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // 跳转到收藏夹窗口
        binding.btnFavorites.setOnClickListener {
            startActivity(Intent(this, FavoriteActivity::class.java))
        }

        binding.btnClearCache.setOnClickListener { showClearCacheDialog() }

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        handleIncomingIntent()
    }

    /** 手动清理缓存 */
    private fun showClearCacheDialog() {
        val size = CacheUtils.cacheSizeBytes(this)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.confirm_clear_cache, CacheUtils.humanSize(size)))
            .setPositiveButton(R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        CacheUtils.clearVideoCache(this@MainActivity)
                    }
                    Toast.makeText(this@MainActivity, R.string.clear_cache_done, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 从内容 Uri 打开压缩包：复制到缓存 → 记录历史 → 进入浏览页 */
    private fun openFromUri(uri: Uri, displayName: String) {
        lifecycleScope.launch {
            try {
                val cache = withContext(Dispatchers.IO) {
                    ArchiveLoader.copyToCache(this@MainActivity, uri, displayName)
                }
                val count = withContext(Dispatchers.IO) {
                    runCatching {
                        ArchiveLoader.open(cache).use { it.entries().size }
                    }.getOrDefault(0)
                }
                // 历史记录静默写入，不弹 Toast
                withContext(Dispatchers.IO) {
                    runCatching { repo.record(displayName, cache.absolutePath, count) }
                }
                ArchiveBrowserActivity.start(this@MainActivity, cache.absolutePath, displayName)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        }

    /** 处理通过文件管理器直接打开 zip/rar 的 Intent */
    private fun handleIncomingIntent() {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                openFromUri(uri, queryDisplayName(uri) ?: "archive.zip")
            }
        }
    }
}
