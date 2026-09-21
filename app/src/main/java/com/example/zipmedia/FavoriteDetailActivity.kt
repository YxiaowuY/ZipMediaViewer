package com.example.zipmedia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.zipmedia.data.FavoriteItemEntity
import com.example.zipmedia.data.FavoriteRepository
import com.example.zipmedia.data.HistoryEntity
import com.example.zipmedia.data.HistoryRepository
import com.example.zipmedia.databinding.ActivityFavoriteDetailBinding
import com.example.zipmedia.ui.FavoriteItemAdapter
import com.example.zipmedia.util.Extras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 收藏夹详情：显示夹内压缩包，可批量从历史记录添加 */
class FavoriteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteDetailBinding
    private lateinit var repo: FavoriteRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var adapter: FavoriteItemAdapter
    private var folderId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderId = intent.getLongExtra(Extras.FOLDER_ID, 0L)
        val folderName = intent.getStringExtra(Extras.FOLDER_NAME) ?: ""

        repo = FavoriteRepository(this)
        historyRepo = HistoryRepository(this)

        binding.toolbar.title = folderName
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = FavoriteItemAdapter(
            onClick = { openItem(it) },
            onDelete = { lifecycleScope.launch { repo.deleteItem(it.id); refresh() } }
        )
        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddFromHistoryDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { repo.getAllItems(folderId) }
            adapter.submitList(items)
            binding.emptyContainer.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openItem(item: FavoriteItemEntity) {
        val f = File(item.cachePath)
        if (!f.exists()) {
            Toast.makeText(this, R.string.cache_file_missing, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { repo.deleteItem(item.id); refresh() }
            return
        }
        ArchiveBrowserActivity.start(this, f.absolutePath, item.displayName)
    }

    /** 从历史记录批量添加：弹出多选列表 */
    private fun showAddFromHistoryDialog() {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) { historyRepo.getAllNow() }
            if (history.isEmpty()) {
                Toast.makeText(this@FavoriteDetailActivity, "历史记录为空，暂无压缩包可添加", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 已存在的 displayName 集合，避免重复
            val existing = withContext(Dispatchers.IO) {
                repo.getAllItems(folderId).map { it.displayName }.toSet()
            }
            // 只显示还没加入此收藏夹的历史
            val candidates = history.filter { it.displayName !in existing }
            if (candidates.isEmpty()) {
                Toast.makeText(this@FavoriteDetailActivity, "历史记录中的压缩包已全部在此收藏夹中", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val names = candidates.map { it.displayName }.toTypedArray()
            val checked = BooleanArray(candidates.size) { false }
            MaterialAlertDialogBuilder(this@FavoriteDetailActivity)
                .setTitle(R.string.add_from_history)
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton(R.string.ok) { _, _ ->
                    val selected = candidates.filterIndexed { i, _ -> checked[i] }
                    if (selected.isEmpty()) return@setPositiveButton
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            selected.forEach { h ->
                                repo.addItem(folderId, h.displayName, h.cachePath, h.entryCount)
                            }
                        }
                        refresh()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    companion object {
        fun start(context: Context, folderId: Long, folderName: String) {
            context.startActivity(
                Intent(context, FavoriteDetailActivity::class.java)
                    .putExtra(Extras.FOLDER_ID, folderId)
                    .putExtra(Extras.FOLDER_NAME, folderName)
            )
        }
    }
}
