package com.example.zipmedia.data

import android.content.Context
import android.net.Uri
import com.example.zipmedia.util.CacheUtils
import java.io.File

/**
 * 归档文件的打开与分发。
 * 打开流程：
 *  1. 将用户选择的 uri 内容复制到应用内部缓存（保证后续可随机访问 + 历史记录可重开）
 *  2. 依据扩展名选择 ZIP/RAR 读取器
 * 历史记录重开时直接使用缓存文件路径，不依赖系统 uri 长期权限。
 */
object ArchiveLoader {

    /** 把一个内容 uri 复制为内部缓存文件，返回缓存文件。 */
    fun copyToCache(context: Context, uri: Uri, sourceName: String): File {
        val dir = CacheUtils.archivesDir(context)
        val ext = sourceName.substringAfterLast('.', "zip").lowercase()
        val cacheFile = File(dir, "${CacheUtils.safeName(sourceName)}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val r = input.read(buffer)
                    if (r == -1) break
                    output.write(buffer, 0, r)
                }
            }
        } ?: error("无法读取所选文件")
        return cacheFile
    }

    /** 判断扩展名是否支持打开 */
    fun isSupported(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext == "zip" || ext == "rar"
    }

    /** 依据文件扩展名分发读取器；仅支持 zip / rar */
    fun open(file: File): ArchiveReader {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "zip" -> ZipArchiveReader(file)
            "rar" -> RarArchiveReader(file)
            else -> error("暂不支持的压缩格式: .$ext")
        }
    }
}