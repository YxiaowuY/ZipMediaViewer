package com.example.zipmedia.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 历史记录：一个压缩包一条 */
@Entity(tableName = "history", indices = [Index(value = ["cachePath"], unique = true)])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val cachePath: String,       // 缓存的压缩包副本路径（用于重开）
    val openedAt: Long,          // 上次打开时间（毫秒）
    val entryCount: Int = 0      // 总条目数（展示用）
)