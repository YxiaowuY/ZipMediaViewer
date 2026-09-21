package com.example.zipmedia.data

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * RAR 读取实现：基于 junrar（纯 Java）。持有单个 Archive 实例。
 * 图片/视频均按字节或文件抽取（RAR 不支持条目级随机流式，逐条解压）。
 */
class RarArchiveReader(private val file: File) : ArchiveReader {

    private val archive: Archive = Archive(file)
    /** junrar 的 fileHeaders 在 Kotlin 中可能被推断为原始类型，这里显式断言为带泛型的列表 */
    private val headers: List<FileHeader> = archive.fileHeaders as List<FileHeader>

    override fun entries(): List<ArchiveEntry> {
        val result = ArrayList<ArchiveEntry>(64)
        for (header: FileHeader in headers) {
            val path = header.fileNameString
            val modified = runCatching { header.mTime?.timeInMillis }.getOrNull() ?: 0L
            result.add(
                ArchiveEntry(
                    path = path,
                    name = path.substringAfterLast('/', ""),
                    size = header.fullUnpackSize,
                    isDirectory = header.isDirectory,
                    modifiedMillis = modified,
                    type = path.detectMediaType(header.isDirectory)
                )
            )
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.path.lowercase() }))
    }

    private fun headerFor(path: String): FileHeader =
        headers.firstOrNull { it.fileNameString == path }
            ?: error("压缩包中不存在该条目: $path")

    /** RAR 无法按需随机读取，这里读出全部字节后包装为内存流 */
    override fun openEntryStream(path: String): InputStream =
        ByteArrayInputStream(readEntryBytes(path))

    override fun readEntryBytes(path: String, limitBytes: Long): ByteArray {
        val header = headerFor(path)
        if (header.fullUnpackSize > limitBytes) throw java.io.IOException("条目超出读取上限")
        val out = java.io.ByteArrayOutputStream()
        extract(header, out)
        return out.toByteArray()
    }

    override fun extractEntry(path: String, dest: File) {
        dest.outputStream().use { out -> extract(headerFor(path), out) }
    }

    private fun extract(header: FileHeader, os: OutputStream) {
        archive.extractFile(header, os)
    }

    override fun close() {
        runCatching { archive.close() }
    }
}