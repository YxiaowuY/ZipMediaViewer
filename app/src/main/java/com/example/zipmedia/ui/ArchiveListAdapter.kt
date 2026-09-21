package com.example.zipmedia.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zipmedia.databinding.ItemArchiveBinding
import com.example.zipmedia.util.StorageUtils

/**
 * 预览文件夹内压缩包列表的 Adapter。
 * 列表已在上游按 uri 去重，这里只负责展示与点击。
 */
class ArchiveListAdapter(
    private val onClick: (StorageUtils.ArchiveFileInfo) -> Unit
) : ListAdapter<StorageUtils.ArchiveFileInfo, ArchiveListAdapter.VH>(DIFF) {

    class VH(val binding: ItemArchiveBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemArchiveBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.display
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<StorageUtils.ArchiveFileInfo>() {
            override fun areItemsTheSame(a: StorageUtils.ArchiveFileInfo, b: StorageUtils.ArchiveFileInfo) =
                a.uri == b.uri
            override fun areContentsTheSame(a: StorageUtils.ArchiveFileInfo, b: StorageUtils.ArchiveFileInfo) =
                a == b
        }
    }
}
