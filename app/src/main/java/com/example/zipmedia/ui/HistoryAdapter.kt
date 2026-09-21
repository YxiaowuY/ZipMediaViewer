package com.example.zipmedia.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zipmedia.data.HistoryEntity
import com.example.zipmedia.databinding.ItemHistoryBinding
import com.example.zipmedia.util.CacheUtils
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val onClick: (HistoryEntity) -> Unit,
    private val onDelete: (HistoryEntity) -> Unit
) : ListAdapter<HistoryEntity, HistoryAdapter.VH>(DIFF) {

    class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.displayName
        val sub = buildString {
            append(item.entryCount).append(" 个条目")
            append(" · ")
            append(timestamp(item.openedAt))
        }
        holder.binding.tvSub.text = sub
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }

    private fun timestamp(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HistoryEntity>() {
            override fun areItemsTheSame(a: HistoryEntity, b: HistoryEntity) = a.id == b.id
            override fun areContentsTheSame(a: HistoryEntity, b: HistoryEntity) = a == b
        }
    }
}