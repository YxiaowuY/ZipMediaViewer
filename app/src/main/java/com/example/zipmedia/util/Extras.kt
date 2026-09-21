package com.example.zipmedia.util

/** Intent 传递参数名统一管理 */
object Extras {
    const val CACHE_PATH = "cache_path"      // 压缩包缓存副本路径
    const val SOURCE_NAME = "source_name"    // 压缩包显示名（源文件名）
    const val ENTRY_PATH = "entry_path"      // 压缩包内条目路径
    const val ENTRY_NAME = "entry_name"      // 条目显示名
    const val IMAGES = "images"              // 图片条目列表（Parcelable ArrayList）
    const val INDEX = "index"                // 当前索引
}