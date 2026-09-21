package com.example.zipmedia.data

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * ZIP 读取实现：持有单个打开状态的 ZipFile，支持随机读取。
 * 图片直接从中取流解码；视频抽取到缓存文件后播放。
 */
class ZipArchiveReader(private val file: File) : ArchiveReader {

    private val zip: ZipFile = ZipFile(file)

    override fun entries(): List<ArchiveEntry> {
        val result = ArrayList<ArchiveEntry>(64)
        val seen = HashSet<String>()
        zip.entries()?.asSequence()?.forEach { entry ->
            val path = entry.name
            // 某些 ZIP 工具会写入重名条目，按 path 去重避免列表重复
            if (!seen.add(path)) return@forEach
            result.add(
                ArchiveEntry(
                    path = path,
                    name = path.substringAfterLast('/', ""),
                    size = entry.size,
                    isDirectory = entry.isDirectory,
                    modifiedMillis = entry.time.takeIf { it > 0 } ?: 0L,
                    type = path.detectMediaType(entry.isDirectory)
                )
            )
        }
        // 目录排在前面，其余按路径稳定排序，便于浏览
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.path.lowercase() }))
    }

    private fun zipEntry(path: String): ZipEntry =
        zip.getEntry(path) ?: error("压缩包中不存在该条目: $path")

    /** 返回的流在 ZipFile 关闭前有效；由调用方负责关闭 */
    override fun openEntryStream(path: String): InputStream =
        zip.getInputStream(zipEntry(path))

    override fun readEntryBytes(path: String, limitBytes: Long): ByteArray {
        if (zipEntry(path).size > limitBytes) throw java.io.IOException("条目超出读取上限")
        return zip.getInputStream(zipEntry(path)).use { ins ->
            val out = java.io.ByteArrayOutputStream()
            out.writeFully(ins, limitBytes)
            out.toByteArray()
        }
    }

    override fun extractEntry(path: String, dest: File) {
        zip.getInputStream(zipEntry(path)).use { ins ->
            dest.outputStream().use { out -> out.writeFully(ins) }
        }
    }

    override fun close() {
        runCatching { zip.close() }
    }
}