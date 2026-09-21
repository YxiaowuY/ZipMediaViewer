package com.example.zipmedia.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 收藏夹内的压缩包项 */
@Entity(
    tableName = "favorite_items",
    indices = [Index(value = ["folderId"]), Index(value = ["folderId", "displayName"], unique = true)]
)
data class FavoriteItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val folderId: Long,
    val displayName: String,
    val cachePath: String,
    val entryCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
