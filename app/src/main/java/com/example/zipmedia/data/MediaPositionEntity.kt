package com.example.zipmedia.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** 观看位置记忆：一个压缩包内的一张图片 / 一个视频对应一条记录 */
@Entity(
    tableName = "media_positions",
    indices = [Index(value = ["cachePath", "entryPath"], unique = true)]
)
data class MediaPositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cachePath: String,      // 压缩包缓存副本路径（标识某个压缩包）
    val entryPath: String,      // 压缩包内条目路径（标识某张图 / 某个视频）
    val type: String,           // "IMAGE" 或 "VIDEO"
    val position: Long = 0L,    // 视频为毫秒进度；图片恒为 0
    val updatedAt: Long = 0L    // 记录时间
)

@Dao
interface MediaPositionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaPositionEntity)

    @Query("SELECT * FROM media_positions WHERE cachePath = :cachePath AND entryPath = :entryPath LIMIT 1")
    suspend fun getByMedia(cachePath: String, entryPath: String): MediaPositionEntity?

    @Query("SELECT * FROM media_positions WHERE cachePath = :cachePath AND type = 'IMAGE' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLastImage(cachePath: String): MediaPositionEntity?

    @Query("DELETE FROM media_positions WHERE cachePath = :cachePath AND entryPath = :entryPath")
    suspend fun deleteByMedia(cachePath: String, entryPath: String)
}
