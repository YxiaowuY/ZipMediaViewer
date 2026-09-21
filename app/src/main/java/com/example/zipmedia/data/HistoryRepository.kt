package com.example.zipmedia.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** 历史记录仓储 */
class HistoryRepository(context: Context) {

    private val dao = AppDatabase.get(context).historyDao()

    fun all(): Flow<List<HistoryEntity>> = dao.flowAll()

    suspend fun record(displayName: String, cachePath: String, entryCount: Int): Long {
        return dao.upsert(
            HistoryEntity(
                displayName = displayName,
                cachePath = cachePath,
                openedAt = System.currentTimeMillis(),
                entryCount = entryCount
            )
        )
    }

    /** 诊断用：返回当前数据库条数 */
    suspend fun count(): Int = dao.count()

    /** 重开历史压缩包时刷新时间 */
    suspend fun touch(entity: HistoryEntity) {
        dao.upsert(entity.copy(openedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()
}