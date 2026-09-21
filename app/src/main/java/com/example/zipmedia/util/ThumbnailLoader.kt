package com.example.zipmedia.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.example.zipmedia.data.ArchiveEntry
import com.example.zipmedia.data.ArchiveLoader
import java.io.File

/**
 * 从压缩包读取图片字节并缩略解码。每次独立打开读取器，线程安全。
 * 内存缓存避免重复解码。
 */
object ThumbnailLoader {

    private const val MAX_EDGE = 480
    // 限制单个缩略图最多读取的字节（4MB），过大图跳过缩略图以省内存
    private const val MAX_BYTES = 4L * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(Math.max(48, cacheKb() / 6)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun cacheKb(): Int {
        val mb = Runtime.getRuntime().maxMemory() / 1024 / 1024
        return (mb * 1024).toInt().coerceAtMost(48 * 1024)
    }

    fun get(cacheArchive: File, entry: ArchiveEntry): Bitmap? {
        val key = "${entry.path}|${entry.size}"
        cache.get(key)?.let { return it }

        if (entry.size > MAX_BYTES) return null // 太大不生成缩略图，直接看原图
        if (entry.size <= 0) return null

        val bitmap = runCatching {
            ArchiveLoader.open(cacheArchive).use { reader ->
                val bytes = reader.readEntryBytes(entry.path, MAX_BYTES)
                decodeSampled(bytes)
            }
        }.getOrNull() ?: return null

        cache.put(key, bitmap)
        return bitmap
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        var w = opts.outWidth
        while (w / 2 >= MAX_EDGE) {
            sample *= 2
            w /= 2
        }
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)
    }
}