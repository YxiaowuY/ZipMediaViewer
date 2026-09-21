package com.example.zipmedia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
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
    private var barsVisible = true

    private val IMG_MAX = 80L * 1024 * 1024 // 单张超过约 80MB 则放弃整图加载

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

        binding.pager.adapter = PagerAdapter()
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateHeader(position)
            }
        })
        if (images.indices.contains(startIndex)) {
            binding.pager.setCurrentItem(startIndex, false)
        }
        updateHeader(startIndex)
    }

    private fun updateHeader(position: Int) {
        if (images.indices.contains(position)) {
            binding.tvFileName.text = images[position].name
            binding.tvCounter.text = "${position + 1}/${images.size}"
        }
    }

    override fun onDestroy() {
        Immersive.exit(this)
        super.onDestroy()
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        if (barsVisible) Immersive.enter(this) else Immersive.exit(this)
        val v = if (barsVisible) View.VISIBLE else View.GONE
        binding.topBar.visibility = v
        binding.tvHint.visibility = v
    }

    private inner class PagerAdapter : RecyclerView.Adapter<PageHolder>() {
        private val loaded = BooleanArray(images.size)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val holder = PageHolder(
                ItemImagePageBinding.inflate(layoutInflater, parent, false)
            )
            attachZoomAndTap(holder)
            return holder
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
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
                        holder.binding.imageView.setImage(ImageSource.bitmap(bmp))
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

    /** 为单页图片绑定单击切换界面；双击/双指缩放由库内置处理，避免手势冲突 */
    private fun attachZoomAndTap(holder: PageHolder) {
        val imageView = holder.binding.imageView
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleBars()
                return true
            }
        })
        holder.binding.root.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                holder.binding.root.performClick()
            }
            imageView.dispatchTouchEvent(event)
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