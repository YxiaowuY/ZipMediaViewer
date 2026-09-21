package com.example.zipmedia.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** 收藏夹仓储 */
class FavoriteRepository(context: Context) {

    private val dao = AppDatabase.get(context).favoriteDao()

    // ---- 收藏夹 ----
    fun foldersFlow(): Flow<List<FavoriteFolderEntity>> = dao.flowFolders()

    suspend fun getAllFolders(): List<FavoriteFolderEntity> = dao.getAllFolders()

    suspend fun createFolder(name: String): Long =
        dao.insertFolder(FavoriteFolderEntity(name = name))

    suspend fun renameFolder(id: Long, name: String) = dao.renameFolder(id, name)

    suspend fun deleteFolder(id: Long) {
        dao.deleteItemsByFolder(id)
        dao.deleteFolder(id)
    }

    suspend fun countItems(folderId: Long): Int = dao.countItems(folderId)

    // ---- 收藏项 ----
    fun itemsFlow(folderId: Long): Flow<List<FavoriteItemEntity>> = dao.flowItems(folderId)

    suspend fun getAllItems(folderId: Long): List<FavoriteItemEntity> = dao.getAllItems(folderId)

    suspend fun addItem(folderId: Long, displayName: String, cachePath: String, entryCount: Int): Long =
        dao.insertItem(
            FavoriteItemEntity(
                folderId = folderId,
                displayName = displayName,
                cachePath = cachePath,
                entryCount = entryCount
            )
        )

    suspend fun deleteItem(id: Long) = dao.deleteItem(id)
}
