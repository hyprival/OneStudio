package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patch_history")
data class PatchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val formattedDate: String,
    val apkFileName: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSuccess: Boolean,
    val failureReason: String? = null,
    val originalTargetSdk: Int,
    val patchedTargetSdk: Int,
    val signatureReplaced: Boolean = false,
    val compatibilityModeApplied: Boolean = false,
    val antiTamperHash: String = "",
    val outputFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val isInfected: Boolean = false,
    val threatName: String? = null,
    val appliedFixesSummary: String = "",
    val logSnippet: String = ""
)
