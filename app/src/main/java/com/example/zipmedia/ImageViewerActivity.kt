package com.example.zipmedia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.example.zipmedia.data.ArchiveEntry
import com.example.zipmedia.data.ArchiveLoader
import com.example.zipmedia.databinding.ActivityImageViewerBinding
import com.example.zipmedia.databinding.ItemImagePageBinding
import com.example.zipmedia.util.Extras
import com.example.zipmedia.util.Immersive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding
    private lateinit var cacheFile: java.io.File
    private lateinit var images: List<ArchiveEntry>
    private var startIndex: Int = 0
    private var barsVisible = false // 顶栏默认隐藏，全屏沉浸
    private var adapter: PagerAdapter? = null

    private val IMG_MAX = 80L * 1024 * 1024 // 单张超过约 80MB 则放弃整图加载

    /** 图片显示模式：智能=自适应完整显示；完整=原图1:1；铺满=铺满屏幕 */
    private enum class ImageMode(val label: String) {
        SMART("智能"), FULL("完整"), FILL("铺满")
    }

    private var imageMode = ImageMode.SMART
    private var sourceW = 0
    private var sourceH = 0
    private var rotation = 0 // 0/90/180/270 度

    private val hideBarsRunnable = Runnable { hideBars() }

    // 双击检测：记录上次点击时间，300ms 内再点即双击
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastClickTime = 0L
    private var lastClickRunnable: Runnable? = null
    private val DOUBLE_TAP_TIMEOUT = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cacheFile = java.io.File(intent.getStringExtra(Extras.CACHE_PATH) ?: "")
        images = intent.getParcelableArrayListExtra(Extras.IMAGES) ?: emptyList()
        startIndex = intent.getIntExtra(Extras.INDEX, 0)

        // 全屏沉浸 + 常亮
        Immersive.enter(this)
        Immersive.keepScreenOn(this, true)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnMode.text = imageMode.label
        binding.btnMode.setOnClickListener {
            imageMode = when (imageMode) {
                ImageMode.SMART -> ImageMode.FULL
                ImageMode.FULL -> ImageMode.FILL
                ImageMode.FILL -> ImageMode.SMART
            }
            binding.btnMode.text = imageMode.label
            applyMode();
        }

        // 强制旋转按钮：每次顺时针 90 度（锁定屏幕方向时也生效）
        binding.btnRotate.setOnClickListener {
            rotation = (rotation + 90) % 360
            val iv = adapter?.currentHolder?.binding?.imageView ?: return@setOnClickListener
            iv.setOrientation(rotation)
            applyMode()
        }

        adapter = PagerAdapter()
        binding.pager.adapter = adapter
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // 切换图片时重置旋转角度
                rotation = 0
                adapter?.currentHolder?.binding?.imageView?.setOrientation(0)
                updateHeader(position)
                applyMode()
            }
        })
        if (images.indices.contains(startIndex)) {
            binding.pager.setCurrentItem(startIndex, false)
        }
        updateHeader(startIndex)
        hideBars()
    }

    private fun updateHeader(position: Int) {
        if (images.indices.contains(position)) {
            binding.tvFileName.text = images[position].name
            binding.tvCounter.text = "${position + 1}/${images.size}"
        }
    }

    override fun onDestroy() {
        binding.topBar.removeCallbacks(hideBarsRunnable)
        mainHandler.removeCallbacksAndMessages(null)
        Immersive.exit(this)
        super.onDestroy()
    }

    // 顶栏与系统栏：默认全屏隐藏；单击(或点左上/右上区域)显示，3 秒后自动隐藏
    private fun toggleBars() {
        if (barsVisible) hideBars() else showBars()
    }

    private fun showBars() {
        barsVisible = true
        binding.topBar.visibility = View.VISIBLE
        binding.tvHint.visibility = View.VISIBLE
        binding.topBar.removeCallbacks(hideBarsRunnable)
        binding.topBar.postDelayed(hideBarsRunnable, 3000)
    }

    private fun hideBars() {
        barsVisible = false
        binding.topBar.visibility = View.GONE
        binding.tvHint.visibility = View.GONE
    }

    /** 把当前模式应用到当前页图片 */
    private fun applyMode() {
        val holder = adapter?.currentHolder ?: return
        val iv = holder.binding.imageView
        if (sourceW <= 0 || sourceH <= 0) return
        val vw = iv.width
        val vh = iv.height
        if (vw <= 0 || vh <= 0) return
        // 旋转 90/270 度时，宽高互换
        val (sw, sh) = if (rotation == 90 || rotation == 270) sourceH to sourceW else sourceW to sourceH
        val scale = when (imageMode) {
            ImageMode.SMART -> minOf(vw.toFloat() / sw, vh.toFloat() / sh)
            ImageMode.FILL -> maxOf(vw.toFloat() / sw, vh.toFloat() / sh)
            ImageMode.FULL -> 1f
        }
        iv.animateScaleAndCenter(scale.coerceAtLeast(0.01f), null)
    }

    private inner class PagerAdapter : RecyclerView.Adapter<PageHolder>() {
        private val loaded = BooleanArray(images.size)
        var currentHolder: PageHolder? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val holder = PageHolder(
                ItemImagePageBinding.inflate(layoutInflater, parent, false)
            )
            attachZoomAndTap(holder)
            return holder
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            currentHolder = holder
            if (loaded[position]) return
            holder.binding.progress.visibility = View.VISIBLE
            holder.binding.tvError.visibility = View.GONE
            lifecycleScope.launch {
                val entry = images[position]
                val data = withContext(Dispatchers.IO) {
                    runCatching {
                        if (entry.size > IMG_MAX) throw IllegalStateException("图片过大")
                        ArchiveLoader.open(cacheFile).use { reader ->
                            reader.readEntryBytes(entry.path, IMG_MAX)
                        }
                    }
                }
                data.onSuccess { bytes ->
                    loaded[position] = true
                    holder.binding.progress.visibility = View.GONE
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        sourceW = bmp.width
                        sourceH = bmp.height
                        holder.binding.imageView.setImage(ImageSource.bitmap(bmp))
                        // 应用当前旋转角度
                        holder.binding.imageView.setOrientation(rotation)
                        if (holder === currentHolder) applyMode()
                    } else {
                        holder.binding.tvError.visibility = View.VISIBLE
                    }
                }.onFailure {
                    holder.binding.progress.visibility = View.GONE
                    holder.binding.tvError.visibility = View.VISIBLE
                }
            }
        }

        override fun getItemCount(): Int = images.size
    }

    private class PageHolder(val binding: ItemImagePageBinding) : RecyclerView.ViewHolder(binding.root)

    /**
     * 为单页图片绑定单击/双击处理。
     * 不使用 GestureDetector——SubsamplingScaleImageView 内部有自己的双击缩放手势检测，
     * 会抢先消费双击事件，导致外层 GestureDetector 的 onDoubleTap 永远不触发。
     * 这里用"两次点击间隔 < 300ms 即双击"的原始方案，与库内部手势完全不冲突。
     */
    private fun attachZoomAndTap(holder: PageHolder) {
        val imageView = holder.binding.imageView
        imageView.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < DOUBLE_TAP_TIMEOUT) {
                // 双击：立即显示操作栏，并取消待执行的单击 toggleBars
                lastClickRunnable?.let { mainHandler.removeCallbacks(it) }
                lastClickRunnable = null
                lastClickTime = 0L
                showBars()
            } else {
                // 可能是单击（等 300ms 确认不是双击后再 toggleBars）
                lastClickTime = now
                lastClickRunnable = Runnable { toggleBars(); lastClickRunnable = null }
                mainHandler.postDelayed(lastClickRunnable!!, DOUBLE_TAP_TIMEOUT)
            }
        }
    }

    companion object {
        fun start(context: Context, cachePath: String, images: List<ArchiveEntry>, index: Int) {
            context.startActivity(
                Intent(context, ImageViewerActivity::class.java)
                    .putExtra(Extras.CACHE_PATH, cachePath)
                    .putParcelableArrayListExtra(Extras.IMAGES, ArrayList(images))
                    .putExtra(Extras.INDEX, index)
            )
        }
    }
}