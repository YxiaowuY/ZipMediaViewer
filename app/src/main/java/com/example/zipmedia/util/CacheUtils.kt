package com.example.zipmedia.util

import android.content.Context
import java.io.File

/** 缓存目录与文件名工具 */
object CacheUtils {

    /** 归档文件缓存目录（复制用户所选压缩包） */
    fun archivesDir(context: Context): File =
        File(context.cacheDir, "archives").apply { mkdirs() }

    /** 视频抽取缓存目录（从压缩包抽取到本地再播放） */
    fun videoDir(context: Context): File =
        File(context.cacheDir, "videos").apply { mkdirs() }

    /** 清理临时视频缓存（保留压缩包副本，历史记录可继续重开） */
    fun clearVideoCache(context: Context) {
        videoDir(context).deleteRecursively()
    }

    /** 计算临时视频缓存占用字节数 */
    fun cacheSizeBytes(context: Context): Long {
        var total = 0L
        fun walk(dir: java.io.File) {
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) walk(f) else total += f.length()
            }
        }
        walk(videoDir(context))
        return total
    }

    /** 生成可用于文件名的安全字符 */
    fun safeName(name: String): String {
        val base = name.substringBeforeLast('.').replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return base.ifBlank { "archive" }
    }

    /** 文件大小的人类可读格式 */
    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.CHINA, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
        else -> String.format(java.util.Locale.CHINA, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    }
}