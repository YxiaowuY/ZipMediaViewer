package com.example.zipmedia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.zipmedia.data.ArchiveLoader
import com.example.zipmedia.data.HistoryRepository
import com.example.zipmedia.databinding.ActivityFolderPreviewBinding
import com.example.zipmedia.util.Prefs
import com.example.zipmedia.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 窗口2：预览文件夹列表。
 * 显示所选文件夹内全部压缩包（按 uri 去重），点击进入 ArchiveBrowserActivity。
 */
class FolderPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderPreviewBinding
    private lateinit var repo: HistoryRepository
    private lateinit var adapter: ArchiveListAdapter

    private val folderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                Prefs.setLastFolderUri(this, it.toString())
                loadFolder()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = HistoryRepository(this)
        adapter = ArchiveListAdapter { info -> openArchive(info) }
        binding.rvArchives.layoutManager = LinearLayoutManager(this)
        binding.rvArchives.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnChangeFolder.setOnClickListener {
            folderLauncher.launch(Uri.parse("content://com.android.externalstorage.documents/"))
        }

        loadFolder()
    }

    override fun onResume() {
        super.onResume()
        // 从 ArchiveBrowserActivity 返回时刷新列表（缓存可能已更新）
        loadFolder()
    }

    private fun loadFolder() {
        val saved = Prefs.lastFolderUri(this)
        if (saved == null) {
            binding.tvFolderPath.text = getString(R.string.folder_not_set)
            adapter.submitList(emptyList())
            binding.emptyContainer.visibility = View.VISIBLE
            return
        }
        val uri = Uri.parse(saved)
        binding.tvFolderPath.text = uri.toString()
        lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) {
                StorageUtils.collectArchives(this@FolderPreviewActivity, uri)
            }
            // 按 uri 去重，解决"预览窗口内容有很多重复"的问题
            val deduped = raw.distinctBy { it.uri }
            adapter.submitList(deduped)
            binding.emptyContainer.visibility =
                if (deduped.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 打开选中的压缩包：复制到缓存 → 记录历史 → 进入浏览页 */
    private fun openArchive(info: StorageUtils.ArchiveFileInfo) {
        lifecycleScope.launch {
            try {
                val cache = withContext(Dispatchers.IO) {
                    ArchiveLoader.copyToCache(this@FolderPreviewActivity, info.uri, info.display)
                }
                val count = withContext(Dispatchers.IO) {
                    runCatching {
                        ArchiveLoader.open(cache).use { it.entries().size }
                    }.getOrDefault(0)
                }
                // 历史记录静默写入，失败不影响打开流程
                withContext(Dispatchers.IO) {
                    runCatching { repo.record(info.display, cache.absolutePath, count) }
                }
                ArchiveBrowserActivity.start(this@FolderPreviewActivity, cache.absolutePath, info.display)
            } catch (e: Exception) {
                Toast.makeText(this@FolderPreviewActivity, "打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
