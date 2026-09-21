package com.example.zipmedia.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zipmedia.data.FavoriteFolderEntity
import com.example.zipmedia.databinding.ItemFavoriteFolderBinding

class FavoriteFolderAdapter(
    private val onClick: (FavoriteFolderEntity) -> Unit,
    private val onLongClick: (FavoriteFolderEntity) -> Unit
) : ListAdapter<FavoriteFolderEntity, FavoriteFolderAdapter.VH>(DIFF) {

    class VH(val binding: ItemFavoriteFolderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFavoriteFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.name
        // count 由外部设置，这里先留空，由 Activity 异步更新
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.root.setOnLongClickListener { onLongClick(item); true }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<FavoriteFolderEntity>() {
            override fun areItemsTheSame(a: FavoriteFolderEntity, b: FavoriteFolderEntity) = a.id == b.id
            override fun areContentsTheSame(a: FavoriteFolderEntity, b: FavoriteFolderEntity) = a == b
        }
    }
}
