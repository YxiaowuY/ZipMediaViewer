package com.example.zipmedia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {

    /** 已有该路径则覆盖更新时间，否则插入 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY openedAt DESC")
    fun flowAll(): kotlinx.coroutines.flow.Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}