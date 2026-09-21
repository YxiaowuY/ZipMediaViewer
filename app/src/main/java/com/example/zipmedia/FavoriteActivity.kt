package com.example.zipmedia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.zipmedia.data.FavoriteFolderEntity
import com.example.zipmedia.data.FavoriteRepository
import com.example.zipmedia.databinding.ActivityFavoriteBinding
import com.example.zipmedia.ui.FavoriteFolderAdapter
import com.example.zipmedia.util.Extras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 收藏夹列表：可新建/重命名/删除收藏夹 */
class FavoriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteBinding
    private lateinit var repo: FavoriteRepository
    private lateinit var adapter: FavoriteFolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = FavoriteRepository(this)
        adapter = FavoriteFolderAdapter(
            onClick = { folder ->
                FavoriteDetailActivity.start(this, folder.id, folder.name)
            },
            onLongClick = { folder -> showFolderActions(folder) }
        )
        binding.rvFolders.layoutManager = LinearLayoutManager(this)
        binding.rvFolders.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.fabAdd.setOnClickListener { showNewFolderDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) { repo.getAllFolders() }
            adapter.submitList(folders)
            binding.emptyContainer.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 新建收藏夹弹窗 */
    private fun showNewFolderDialog() {
        val et = EditText(this).apply {
            hint = getString(R.string.enter_folder_name)
            setSingleLine()
        }
        val pad = dp(16)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(et)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_folder)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.folder_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.createFolder(name) }
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 长按收藏夹：重命名 / 删除 */
    private fun showFolderActions(folder: FavoriteFolderEntity) {
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete))) { _, which ->
                when (which) {
                    0 -> showRenameDialog(folder)
                    1 -> confirmDelete(folder)
                }
            }
            .show()
    }

    private fun showRenameDialog(folder: FavoriteFolderEntity) {
        val et = EditText(this).apply {
            setText(folder.name)
            setSingleLine()
        }
        val pad = dp(16)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(et)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.folder_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.renameFolder(folder.id, name) }
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(folder: FavoriteFolderEntity) {
        MaterialAlertDialogBuilder(this)
            .setMessage("确定删除收藏夹「${folder.name}」及其全部内容吗？")
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.deleteFolder(folder.id) }
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
}
