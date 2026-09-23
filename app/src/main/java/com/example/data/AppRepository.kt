package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val appDao: ModifiedAppDao,
    private val patchHistoryDao: PatchHistoryDao
) {
    val allModifiedApps: Flow<List<ModifiedAppEntity>> = appDao.getAllModifiedApps()
    val allPatchHistory: Flow<List<PatchHistoryEntity>> = patchHistoryDao.getAllHistory()

    fun getHistoryForPackage(packageName: String): Flow<List<ModifiedAppEntity>> {
        return appDao.getHistoryForPackage(packageName)
    }

    suspend fun getAppById(id: Long): ModifiedAppEntity? = appDao.getAppById(id)

    suspend fun saveModifiedApp(app: ModifiedAppEntity): Long = appDao.insertApp(app)

    suspend fun updateApp(app: ModifiedAppEntity) = appDao.updateApp(app)

    suspend fun deleteApp(id: Long) = appDao.deleteAppById(id)

    suspend fun clearAll() = appDao.clearAll()

    suspend fun recordPatchOperation(historyItem: PatchHistoryEntity): Long =
        patchHistoryDao.insertHistory(historyItem)

    suspend fun deletePatchHistory(id: Long) = patchHistoryDao.deleteHistoryById(id)

    suspend fun deletePatchHistoryBatch(ids: List<Long>) = patchHistoryDao.deleteHistoryByIds(ids)

    suspend fun clearAllPatchHistory() = patchHistoryDao.clearAll()
}
