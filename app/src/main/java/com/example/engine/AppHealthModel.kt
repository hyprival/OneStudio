package com.example.engine

import java.io.File

enum class HealthStatusTier {
    OPTIMAL,   // 90-100%
    GOOD,      // 75-89%
    WARNING,   // 50-74%
    CRITICAL   // <50%
}

enum class HealthCategory {
    SIGNATURE_INTEGRITY,
    DEVICE_COMPATIBILITY,
    RUNTIME_STABILITY,
    SANDBOX_SAFETY
}

data class HealthIssue(
    val category: HealthCategory,
    val title: String,
    val description: String,
    val isCritical: Boolean,
    val recommendation: String,
    val affectedDevices: String = "All devices"
)

data class SingleAppHealthReport(
    val appId: Long,
    val appName: String,
    val packageName: String,
    val apkFile: File,
    val healthScore: Int, // 0 to 100
    val statusTier: HealthStatusTier,
    val signatureMismatchRisk: Boolean,
    val signatureDetails: String,
    val deviceCompatibilitySummary: String,
    val samsungAseriesCompatible: Boolean,
    val samsungSseriesCompatible: Boolean,
    val runtimeStabilityDetails: String,
    val issues: List<HealthIssue>,
    val timestamp: Long = System.currentTimeMillis()
)

data class DashboardOverallHealth(
    val overallScore: Int,
    val statusTier: HealthStatusTier,
    val totalAppsScanned: Int,
    val optimalCount: Int,
    val warningCount: Int,
    val criticalCount: Int,
    val signatureMismatchRisksDetected: Int,
    val samsungCompatibilityCoverage: String, // e.g., "100% Compatible across Galaxy A & S Series"
    val appReports: List<SingleAppHealthReport>,
    val scanTimestamp: Long = System.currentTimeMillis()
)
