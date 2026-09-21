package com.example.zipmedia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 收藏夹：用户可自定义名称 */
@Entity(tableName = "favorite_folders")
data class FavoriteFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
