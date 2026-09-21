package com.example.zipmedia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.zipmedia.data.ArchiveLoader
import com.example.zipmedia.data.HistoryEntity
import com.example.zipmedia.data.HistoryRepository
import com.example.zipmedia.databinding.ActivityMainBinding
import com.example.zipmedia.ui.HistoryAdapter
import com.example.zipmedia.util.CacheUtils
import com.example.zipmedia.util.Prefs
import com.example.zipmedia.util.StorageUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: HistoryRepository
    private lateinit var adapter: HistoryAdapter

    private val openArchiveLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openFromUri(it, queryDisplayName(it) ?: "archive.zip") }
        }

    private val folderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                // 记忆选择的位置，下次点“打开预览文件夹”直接进入
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                Prefs.setLastFolderUri(this, it.toString())
                openFolder(it)
            }
        }

    private fun baseFolderUri(): Uri =
        Uri.parse("content://com.android.externalstorage.documents/")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = HistoryRepository(this)
        adapter = HistoryAdapter(
            onClick = { openFromCache(it) },
            onDelete = { lifecycleScope.launch { repo.delete(it.id) } }
        )
        binding.rvHistory.adapter = adapter

        binding.btnOpenArchive.setOnClickListener {
            openArchiveLauncher.launch(
                arrayOf("application/zip", "application/x-rar-compressed", "application/vnd.rar")
            )
        }

        // 主体按钮：若已记忆预览文件夹则直接打开它，否则先选择文件夹
        binding.btnOpenFolder.setOnClickListener {
            val saved = Prefs.lastFolderUri(this)
            if (saved != null) {
                openFolder(Uri.parse(saved))
            } else {
                folderLauncher.launch(baseFolderUri())
            }
        }
        // 修改预览文件夹位置
        binding.btnChangeFolder.setOnClickListener {
            folderLauncher.launch(baseFolderUri())
        }

        binding.tvClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.confirm_clear_history)
                .setPositiveButton(R.string.ok) { _, _ -> lifecycleScope.launch { repo.clear() } }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnClearCache.setOnClickListener { showClearCacheDialog() }

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        observeHistory()
        handleIncomingIntent()
    }

    /** 手动清理缓存：显示视频缓存占用，确认后清除临时视频（保留历史记录） */
    private fun showClearCacheDialog() {
        val size = CacheUtils.cacheSizeBytes(this)
        MaterialAlertDialogBuilder(this)
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

    override fun onResume() {
        super.onResume()
        // 诊断：返回主页时主动查一次数据库条数，Toast 提示
        lifecycleScope.launch {
            val total = withContext(Dispatchers.IO) { repo.count() }
            android.util.Log.d("ZipMedia", "onResume: 数据库当前共 $total 条历史")
            if (total == 0) {
                Toast.makeText(this@MainActivity, "历史为空（数据库 0 条）", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.all().collect { items ->
                    adapter.submitList(items)
                    binding.emptyContainer.visibility =
                        if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
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
                // 历史记录单独 try-catch，避免被外层吞掉错误
                val recorded = withContext(Dispatchers.IO) {
                    runCatching {
                        val rowId = repo.record(displayName, cache.absolutePath, count)
                        val total = repo.count()
                        android.util.Log.d("ZipMedia", "历史已记录 rowId=$rowId 当前共 $total 条, name=$displayName, path=${cache.absolutePath}")
                        // 显式反馈：让用户能立刻看到结果
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "已记录历史（共 $total 条）", Toast.LENGTH_LONG).show()
                        }
                        true
                    }.getOrElse { e ->
                        android.util.Log.e("ZipMedia", "历史记录写入失败: ${e.message}", e)
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "历史记录失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        false
                    }
                }
                ArchiveBrowserActivity.start(this@MainActivity, cache.absolutePath, displayName)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 从历史缓存路径直接重开 */
    private fun openFromCache(entity: HistoryEntity) {
        val f = java.io.File(entity.cachePath)
        if (!f.exists()) {
            Toast.makeText(this, "缓存文件已不存在，请重新选择压缩包", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { repo.delete(entity.id) }
            return
        }
        lifecycleScope.launch {
            repo.touch(entity)
            ArchiveBrowserActivity.start(this@MainActivity, f.absolutePath, entity.displayName)
        }
    }

    /** 扫描所选文件夹内的压缩包，弹出选择列表 */
    private fun openFolder(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        lifecycleScope.launch {
            val archives = withContext(Dispatchers.IO) {
                StorageUtils.collectArchives(this@MainActivity, uri)
            }
            if (archives.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.none_archive_found, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (archives.size == 1) {
                openFromUri(archives[0].uri, archives[0].display)
                return@launch
            }
            val names = archives.map { it.display }.toTypedArray()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.archive_pick_title)
                .setItems(names) { _, which ->
                    val a = archives[which]
                    openFromUri(a.uri, a.display)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
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