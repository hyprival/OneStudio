package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModifiedAppDao {
    @Query("SELECT * FROM modified_apps ORDER BY timestamp DESC")
    fun getAllModifiedApps(): Flow<List<ModifiedAppEntity>>

    @Query("SELECT * FROM modified_apps WHERE id = :id")
    suspend fun getAppById(id: Long): ModifiedAppEntity?

    @Query("SELECT * FROM modified_apps WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getHistoryForPackage(packageName: String): Flow<List<ModifiedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: ModifiedAppEntity): Long

    @Update
    suspend fun updateApp(app: ModifiedAppEntity)

    @Query("DELETE FROM modified_apps WHERE id = :id")
    suspend fun deleteAppById(id: Long)

    @Query("DELETE FROM modified_apps")
    suspend fun clearAll()
}
