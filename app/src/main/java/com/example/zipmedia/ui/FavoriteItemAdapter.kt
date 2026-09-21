package com.example.zipmedia.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zipmedia.data.FavoriteItemEntity
import com.example.zipmedia.databinding.ItemHistoryBinding

class FavoriteItemAdapter(
    private val onClick: (FavoriteItemEntity) -> Unit,
    private val onDelete: (FavoriteItemEntity) -> Unit
) : ListAdapter<FavoriteItemEntity, FavoriteItemAdapter.VH>(DIFF) {

    class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.displayName
        holder.binding.tvSub.text = "${item.entryCount} 个条目"
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<FavoriteItemEntity>() {
            override fun areItemsTheSame(a: FavoriteItemEntity, b: FavoriteItemEntity) = a.id == b.id
            override fun areContentsTheSame(a: FavoriteItemEntity, b: FavoriteItemEntity) = a == b
        }
    }
}
