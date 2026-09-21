package com.example.zipmedia.util

import android.content.Context

/** 排序方式 */
enum class SortMode(val label: String) {
    NAME("按名称"),
    SIZE("按大小"),
    TIME("按时间");

    companion object {
        fun from(value: String): SortMode = entries.firstOrNull { it.name == value } ?: NAME
    }
}

/** 列表筛选 */
enum class FilterMode(val label: String) {
    ALL("全部媒体"),
    IMAGE("仅图片"),
    VIDEO("仅视频")
}

/** 轻量偏好存储 */
object Prefs {
    private const val NAME = "zipmedia_prefs"
    const val KEY_SORT = "sort_mode"
    const val KEY_FILTER = "filter_mode"
    const val KEY_LAST_FOLDER = "last_folder_uri"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun sort(context: Context): SortMode =
        SortMode.from(sp(context).getString(KEY_SORT, SortMode.NAME.name)!!)

    fun setSort(context: Context, mode: SortMode) {
        sp(context).edit().putString(KEY_SORT, mode.name).apply()
    }

    fun filter(context: Context): FilterMode =
        FilterMode.valueOf(sp(context).getString(KEY_FILTER, FilterMode.ALL.name) ?: FilterMode.ALL.name)

    fun setFilter(context: Context, mode: FilterMode) {
        sp(context).edit().putString(KEY_FILTER, mode.name).apply()
    }

    fun lastFolderUri(context: Context): String? =
        sp(context).getString(KEY_LAST_FOLDER, null)

    fun setLastFolderUri(context: Context, uri: String) {
        sp(context).edit().putString(KEY_LAST_FOLDER, uri).apply()
    }
}