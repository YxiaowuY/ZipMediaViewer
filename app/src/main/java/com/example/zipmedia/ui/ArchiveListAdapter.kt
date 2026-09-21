package com.example.zipmedia.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zipmedia.databinding.ItemArchiveBinding
import com.example.zipmedia.util.ArchiveFileInfo

/**
 * 预览文件夹内压缩包列表的 Adapter。
 * 列表已在上游按 uri 去重，这里只负责展示与点击。
 */
class ArchiveListAdapter(
    private val onClick: (ArchiveFileInfo) -> Unit
) : ListAdapter<ArchiveFileInfo, ArchiveListAdapter.VH>(DIFF) {

    class VH(val binding: ItemArchiveBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemArchiveBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.display
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ArchiveFileInfo>() {
            override fun areItemsTheSame(a: ArchiveFileInfo, b: ArchiveFileInfo) =
                a.uri == b.uri
            override fun areContentsTheSame(a: ArchiveFileInfo, b: ArchiveFileInfo) =
                a == b
        }
    }
}
