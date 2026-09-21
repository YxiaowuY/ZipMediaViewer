package com.example.zipmedia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.zipmedia.data.ArchiveEntry
import com.example.zipmedia.data.ArchiveLoader
import com.example.zipmedia.data.MediaType
import com.example.zipmedia.databinding.ActivityBrowserBinding
import com.example.zipmedia.ui.MediaAdapter
import com.example.zipmedia.util.Extras
import com.example.zipmedia.util.FilterMode
import com.example.zipmedia.util.Prefs
import com.example.zipmedia.util.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ArchiveBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var cacheFile: File
    private var allEntries: List<ArchiveEntry> = emptyList()
    private var sorted: List<ArchiveEntry> = emptyList()
    private var sortMode: SortMode = SortMode.NAME
    private var filterMode: FilterMode = FilterMode.ALL
    private lateinit var adapter: MediaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cachePath = intent.getStringExtra(Extras.CACHE_PATH)
            ?: run { toast("缺少压缩包信息"); finish(); return }
        val sourceName = intent.getStringExtra(Extras.SOURCE_NAME) ?: cachePath
        cacheFile = File(cachePath)
        if (!cacheFile.exists()) {
            toast("压缩包文件不存在")
            finish()
            return
        }

        sortMode = Prefs.sort(this)
        filterMode = Prefs.filter(this)

        binding.tvTitle.text = sourceName
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSort.setOnClickListener { cycleSort() }
        binding.btnFilter.setOnClickListener { cycleFilter() }
        updateChips()

        adapter = MediaAdapter(lifecycleScope, cacheFile) { entry -> openEntry(entry) }
        binding.rvMedia.adapter = adapter
        binding.rvMedia.layoutManager = GridLayoutManager(this, gridColumns())

        loadEntries()
    }

    private fun gridColumns(): Int {
        val density = resources.displayMetrics.density
        val widthDp = resources.displayMetrics.widthPixels / density
        return (widthDp / 130f).toInt().coerceIn(2, 6)
    }

    /** 旋转时重建网格列数，实现横竖屏自适应 */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.rvMedia.layoutManager = GridLayoutManager(this, gridColumns())
    }

    private fun loadEntries() {
        binding.loadingContainer.visibility = View.VISIBLE
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    ArchiveLoader.open(cacheFile).use { reader ->
                        reader.entries().filter { it.type.isMedia }
                    }
                }.getOrDefault(emptyList())
            }
            allEntries = list
            applySortFilter()
            binding.loadingContainer.visibility = View.GONE
        }
    }

    private fun applySortFilter() {
        sorted = allEntries
            .filter { entry ->
                when (filterMode) {
                    FilterMode.ALL -> true
                    FilterMode.IMAGE -> entry.type == MediaType.IMAGE
                    FilterMode.VIDEO -> entry.type == MediaType.VIDEO
                }
            }
            .sortedWith(
                when (sortMode) {
                    SortMode.NAME -> compareBy({ it.name.lowercase() })
                    SortMode.SIZE -> compareByDescending { it.size }
                    SortMode.TIME -> compareByDescending { it.modifiedMillis }
                }
            )
        adapter.submitList(sorted)
        binding.tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        if (sorted.isEmpty()) {
            toast("没有符合当前筛选的媒体")
        }
    }

    private fun cycleSort() {
        sortMode = when (sortMode) {
            SortMode.NAME -> SortMode.SIZE
            SortMode.SIZE -> SortMode.TIME
            SortMode.TIME -> SortMode.NAME
        }
        Prefs.setSort(this, sortMode)
        updateChips()
        applySortFilter()
    }

    private fun cycleFilter() {
        filterMode = when (filterMode) {
            FilterMode.ALL -> FilterMode.IMAGE
            FilterMode.IMAGE -> FilterMode.VIDEO
            FilterMode.VIDEO -> FilterMode.ALL
        }
        Prefs.setFilter(this, filterMode)
        updateChips()
        applySortFilter()
    }

    private fun updateChips() {
        binding.btnSort.text = getString(R.string.sort_cycle_hint) + "·" + sortMode.label
        binding.btnFilter.text = getString(R.string.filter_cycle_hint) + "·" + filterMode.label
    }

    private fun openEntry(entry: ArchiveEntry) {
        when (entry.type) {
            MediaType.IMAGE -> {
                val images = sorted.filter { it.type == MediaType.IMAGE }
                val index = images.indexOf(entry).takeIf { it >= 0 } ?: -1
                ImageViewerActivity.start(this, cacheFile.absolutePath, images, index.coerceAtLeast(0))
            }
            MediaType.VIDEO -> VideoPlayerActivity.start(this, cacheFile.absolutePath, entry)
            else -> Toast.makeText(this, "暂不支持该类型", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        fun start(context: Context, cachePath: String, sourceName: String) {
            context.startActivity(
                Intent(context, ArchiveBrowserActivity::class.java)
                    .putExtra(Extras.CACHE_PATH, cachePath)
                    .putExtra(Extras.SOURCE_NAME, sourceName)
            )
        }
    }
}