package com.example.zipmedia

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.zipmedia.data.HistoryEntity
import com.example.zipmedia.data.HistoryRepository
import com.example.zipmedia.databinding.ActivityHistoryBinding
import com.example.zipmedia.ui.HistoryAdapter
import com.example.zipmedia.util.Extras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 窗口3：历史记录独立窗口。
 * onResume 时主动拉取一次数据（不依赖 Flow 自动触发），保证返回时立刻刷新。
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var repo: HistoryRepository
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = HistoryRepository(this)
        adapter = HistoryAdapter(
            onClick = { openFromCache(it) },
            onDelete = { lifecycleScope.launch { repo.delete(it.id); refresh() } }
        )
        // 显式设置 LinearLayoutManager（旧版历史不显示的根因之一就是没设 layoutManager）
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.confirm_clear_history)
                .setPositiveButton(R.string.ok) { _, _ ->
                    lifecycleScope.launch { repo.clear(); refresh() }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
    }

    override fun onResume() {
        super.onResume()
        // 主动拉取一次，解决从 ArchiveBrowserActivity 返回后历史列表不刷新的问题
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { repo.getAllNow() }
            adapter.submitList(items)
            binding.emptyContainer.visibility =
                if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 从历史缓存路径直接重开 */
    private fun openFromCache(entity: HistoryEntity) {
        val f = File(entity.cachePath)
        if (!f.exists()) {
            Toast.makeText(this, R.string.cache_file_missing, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { repo.delete(entity.id); refresh() }
            return
        }
        lifecycleScope.launch {
            repo.touch(entity)
            ArchiveBrowserActivity.start(this@HistoryActivity, f.absolutePath, entity.displayName)
        }
    }
}
