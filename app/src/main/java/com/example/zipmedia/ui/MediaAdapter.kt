package com.example.zipmedia.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zipmedia.data.ArchiveEntry
import com.example.zipmedia.data.MediaType
import com.example.zipmedia.databinding.ItemMediaBinding
import com.example.zipmedia.util.ThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaAdapter(
    private val scope: CoroutineScope,
    private val cacheFile: File,
    private val onClick: (ArchiveEntry) -> Unit
) : ListAdapter<ArchiveEntry, MediaAdapter.VH>(DIFF) {

    class VH(val binding: ItemMediaBinding) : RecyclerView.ViewHolder(binding.root) {
        var loadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.name
        val isVideo = item.type == MediaType.VIDEO
        holder.binding.ivPlay.visibility =
            if (isVideo) android.view.View.VISIBLE else android.view.View.GONE

        // 图片：异步加载缩略图；视频：深色占位
        holder.loadJob?.cancel()
        holder.binding.ivThumb.setImageDrawable(null)
        holder.binding.ivThumb.setBackgroundColor(if (isVideo) Color.parseColor("#2B2B2B") else Color.parseColor("#EEEEEE"))

        if (!isVideo) {
            holder.loadJob = scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    ThumbnailLoader.get(cacheFile, item)
                }
                bitmap?.let { holder.binding.ivThumb.setImageBitmap(it) }
            }
        }

        holder.binding.root.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ArchiveEntry>() {
            override fun areItemsTheSame(a: ArchiveEntry, b: ArchiveEntry) = a.path == b.path
            override fun areContentsTheSame(a: ArchiveEntry, b: ArchiveEntry) = a == b
        }
    }
}