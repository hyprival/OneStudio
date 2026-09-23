package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatchHistoryDao {
    @Query("SELECT * FROM patch_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<PatchHistoryEntity>>

    @Query("SELECT * FROM patch_history WHERE isSuccess = 1 ORDER BY timestamp DESC")
    fun getSuccessfulHistory(): Flow<List<PatchHistoryEntity>>

    @Query("SELECT * FROM patch_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): PatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: PatchHistoryEntity): Long

    @Query("DELETE FROM patch_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM patch_history WHERE id IN (:ids)")
    suspend fun deleteHistoryByIds(ids: List<Long>)

    @Query("DELETE FROM patch_history")
    suspend fun clearAll()
}
