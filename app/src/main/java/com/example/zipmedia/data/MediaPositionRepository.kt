package com.example.zipmedia.data

import android.content.Context

/** 观看位置记忆仓储：图片位置（最后浏览的图片）与视频播放进度 */
class MediaPositionRepository(context: Context) {

    private val dao = AppDatabase.get(context).mediaPositionDao()

    /** 记录图片位置：某压缩包里最后浏览的图片条目 */
    suspend fun saveImage(cachePath: String, entryPath: String) {
        dao.upsert(
            MediaPositionEntity(
                cachePath = cachePath,
                entryPath = entryPath,
                type = "IMAGE",
                position = 0L,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 记录视频播放进度（毫秒） */
    suspend fun saveVideo(cachePath: String, entryPath: String, positionMs: Long) {
        dao.upsert(
            MediaPositionEntity(
                cachePath = cachePath,
                entryPath = entryPath,
                type = "VIDEO",
                position = positionMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 读取视频上次播放进度（毫秒），无记录返回 null */
    suspend fun getVideo(cachePath: String, entryPath: String): Long? =
        dao.getByMedia(cachePath, entryPath)?.takeIf { it.type == "VIDEO" }?.position

    /** 读取某压缩包最后浏览的图片条目路径，无记录返回 null */
    suspend fun getLastImage(cachePath: String): String? =
        dao.getLastImage(cachePath)?.entryPath

    /** 清除视频进度（看完后调用） */
    suspend fun clearVideo(cachePath: String, entryPath: String) =
        dao.deleteByMedia(cachePath, entryPath)
}
