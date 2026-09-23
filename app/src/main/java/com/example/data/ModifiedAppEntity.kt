package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "modified_apps")
data class ModifiedAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val originalApkName: String,
    val patchedApkPath: String,
    val fileSizeBytes: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val minSdkOriginal: Int,
    val minSdkPatched: Int,
    val targetSdkOriginal: Int,
    val targetSdkPatched: Int,
    val signatureConflictResolved: Boolean,
    val antiTamperHash: String,
    val isAntiTamperValid: Boolean,
    val issuesFoundJson: String, // Comma or newline separated issues
    val fixesAppliedJson: String, // Comma or newline separated fixes
    val plainEnglishSummary: String,
    val diagnosticLogText: String,
    val updateVersionNote: String,
    val isInfected: Boolean = false,
    val threatName: String? = null
)
