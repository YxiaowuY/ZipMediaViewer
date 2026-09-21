package com.example.zipmedia.data

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 通用压缩包读取接口。
 * ZIP 与 RAR 各自实现；图片直接流式读取，视频抽取到缓存文件后播放。
 */
interface ArchiveReader : Closeable {

    /** 所有条目 */
    fun entries(): List<ArchiveEntry>

    /** 打开某条目的输入流（用于图片直接解码） */
    fun openEntryStream(path: String): InputStream

    /** 读出某条目全部字节，可用 limit 限制大小防止内存溢出 */
    fun readEntryBytes(path: String, limitBytes: Long = Long.MAX_VALUE): ByteArray

    /** 将某条目抽取到目标文件（用于视频播放缓存） */
    fun extractEntry(path: String, dest: File)

    /** 条目总数（媒体计算的辅助） */
    fun count(): Int = entries().size
}

/** 让字节数组变成可随机/多次读取的流包装，方便图片解码 */
fun ByteArray.openInputStream(): InputStream = java.io.ByteArrayInputStream(this)

internal fun OutputStream.writeFully(input: InputStream, limit: Long = Long.MAX_VALUE) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val r = input.read(buffer)
        if (r == -1) break
        total += r
        if (total > limit) throw java.io.IOException("条目过大，超出读取上限")
        write(buffer, 0, r)
    }
}