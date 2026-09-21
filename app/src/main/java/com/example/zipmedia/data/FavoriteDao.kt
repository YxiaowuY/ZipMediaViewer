package com.example.zipmedia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    // ---- 收藏夹 ----
    @Insert
    suspend fun insertFolder(folder: FavoriteFolderEntity): Long

    @Query("SELECT * FROM favorite_folders ORDER BY createdAt DESC")
    fun flowFolders(): Flow<List<FavoriteFolderEntity>>

    @Query("SELECT * FROM favorite_folders ORDER BY createdAt DESC")
    suspend fun getAllFolders(): List<FavoriteFolderEntity>

    @Query("UPDATE favorite_folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM favorite_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("DELETE FROM favorite_items WHERE folderId = :folderId")
    suspend fun deleteItemsByFolder(folderId: Long)

    @Query("SELECT COUNT(*) FROM favorite_items WHERE folderId = :folderId")
    suspend fun countItems(folderId: Long): Int

    // ---- 收藏项 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: FavoriteItemEntity): Long

    @Query("SELECT * FROM favorite_items WHERE folderId = :folderId ORDER BY addedAt DESC")
    fun flowItems(folderId: Long): Flow<List<FavoriteItemEntity>>

    @Query("SELECT * FROM favorite_items WHERE folderId = :folderId ORDER BY addedAt DESC")
    suspend fun getAllItems(folderId: Long): List<FavoriteItemEntity>

    @Query("DELETE FROM favorite_items WHERE id = :id")
    suspend fun deleteItem(id: Long)
}
