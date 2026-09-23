package com.example.engine

import android.content.Context
import android.os.Build
import com.example.data.ModifiedAppEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLogExporter {

    /**
     * Formats a complete, production-grade developer triage report for a modified app entity.
     */
    fun generateFormattedLogReport(
        context: Context,
        app: ModifiedAppEntity,
        systemHealthSummary: String? = null
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        val timestampStr = dateFormat.format(Date(app.timestamp))
        val currentExportTime = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.appendLine("================================================================================")
        sb.appendLine("                       ONEAPK STUDIO - PATCH & DIAGNOSTIC REPORT                ")
        sb.appendLine("================================================================================")
        sb.appendLine("Generated: $currentExportTime")
        sb.appendLine("Application: ${app.appName} (${app.packageName})")
        sb.appendLine("Version: ${app.versionName} (VersionCode: ${app.versionCode})")
        sb.appendLine("Original APK: ${app.originalApkName}")
        sb.appendLine("Patched APK File: ${app.patchedApkPath}")
        sb.appendLine("File Size: ${formatBytes(app.fileSizeBytes)}")
        sb.appendLine("Patch Recorded Date: $timestampStr")
        sb.appendLine()

        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("                      HOST DEVICE & ENVIRONMENT SPECIFICATIONS                  ")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("Device Manufacturer: ${Build.MANUFACTURER.uppercase(Locale.getDefault())}")
        sb.appendLine("Device Model: ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("Android OS Version: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine("Build Fingerprint: ${Build.FINGERPRINT}")
        if (systemHealthSummary != null) {
            sb.appendLine("Hardware Coverage: $systemHealthSummary")
        }
        sb.appendLine()

        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("                        SDK & SIGNATURE COMPATIBILITY AUDIT                     ")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("Min SDK: Original API ${app.minSdkOriginal} -> Patched API ${app.minSdkPatched}")
        sb.appendLine("Target SDK: Original API ${app.targetSdkOriginal} -> Patched API ${app.targetSdkPatched}")
        sb.appendLine("Target SDK Android 14 Gate (API 28+): ${if (app.targetSdkPatched >= 28) "PASSED" else "WARNING - BELOW API 28"}")
        sb.appendLine("Signature Conflict Reconciled: ${if (app.signatureConflictResolved) "YES (Local Neutral Key Injected)" else "NO"}")
        sb.appendLine("Anti-Tamper Status: ${if (app.isAntiTamperValid) "VERIFIED VALID (Seal Intact)" else "TAMPER WARNING"}")
        sb.appendLine("Anti-Tamper SHA-256 Hash: ${app.antiTamperHash}")
        sb.appendLine()

        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("                          IDENTIFIED ISSUES & MITIGATIONS                       ")
        sb.appendLine("--------------------------------------------------------------------------------")
        val issues = app.issuesFoundJson.split(";").filter { it.isNotBlank() }
        if (issues.isNotEmpty()) {
            sb.appendLine("Detected Installation Incompatibilities:")
            issues.forEachIndexed { idx, issue ->
                sb.appendLine("  [${idx + 1}] $issue")
            }
        } else {
            sb.appendLine("No blocking structural issues detected during ingestion.")
        }
        sb.appendLine()

        val fixes = app.fixesAppliedJson.split(";").filter { it.isNotBlank() }
        if (fixes.isNotEmpty()) {
            sb.appendLine("Engine Transformations Applied:")
            fixes.forEachIndexed { idx, fix ->
                sb.appendLine("  [${idx + 1}] $fix")
            }
        }
        sb.appendLine()

        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("                             PLAIN-ENGLISH SUMMARY                              ")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(app.plainEnglishSummary.trim())
        sb.appendLine()

        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("                         DETAILED ENGINE EXECUTION TRACE                        ")
        sb.appendLine("--------------------------------------------------------------------------------")
        if (app.diagnosticLogText.isNotBlank()) {
            sb.appendLine(app.diagnosticLogText.trim())
        } else {
            sb.appendLine("[LOG TRACE EMPTY] - Patch executed under high-speed standard pipeline.")
        }
        sb.appendLine()
        sb.appendLine("================================================================================")
        sb.appendLine("                       END OF ONEAPK DIAGNOSTIC REPORT                          ")
        sb.appendLine("================================================================================")

        return sb.toString()
    }

    /**
     * Formats a log report from an active single-run patch result (live logs).
     */
    fun generateLivePatchLogReport(
        context: Context,
        apkName: String,
        packageName: String?,
        logs: List<PatchStepLog>,
        success: Boolean,
        summary: String
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        val currentExportTime = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.appendLine("================================================================================")
        sb.appendLine("                       ONEAPK STUDIO - LIVE PATCH TRACE LOG                     ")
        sb.appendLine("================================================================================")
        sb.appendLine("Generated: $currentExportTime")
        sb.appendLine("Target APK: $apkName")
        sb.appendLine("Target Package: ${packageName ?: "Pending Detection"}")
        sb.appendLine("Overall Patch Status: ${if (success) "SUCCESS" else "FAILED / INCOMPLETE"}")
        sb.appendLine("Host Device: ${Build.MANUFACTURER.uppercase(Locale.getDefault())} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Host Architectures: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine()

        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("                             PATCH RESULT SUMMARY                               ")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(summary.trim())
        sb.appendLine()

        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("                              CHRONOLOGICAL LOGS                                ")
        sb.appendLine("--------------------------------------------------------------------------------")
        if (logs.isEmpty()) {
            sb.appendLine("No logs recorded.")
        } else {
            logs.forEach { log ->
                val statusMarker = if (log.isSuccess) "✓ PASS" else "✗ FAIL"
                sb.appendLine("[${log.timestamp}] [$statusMarker] [${log.tag}] ${log.message}")
            }
        }
        sb.appendLine()
        sb.appendLine("================================================================================")
        sb.appendLine("                       END OF ONEAPK LIVE TRACE LOG                             ")
        sb.appendLine("================================================================================")

        return sb.toString()
    }

    /**
     * Formats all Room database patch history records into a comprehensive text file export for external record keeping.
     */
    fun generatePatchHistoryLogReport(
        context: Context,
        historyList: List<com.example.data.PatchHistoryEntity>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        val currentExportTime = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.appendLine("================================================================================")
        sb.appendLine("                 ONEAPK STUDIO - COMPLETE PATCH OPERATION HISTORY               ")
        sb.appendLine("================================================================================")
        sb.appendLine("Export Generated: $currentExportTime")
        sb.appendLine("Host Device: ${Build.MANUFACTURER.uppercase(Locale.getDefault())} ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("OS Environment: Android ${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})")
        sb.appendLine("Processor ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine("Total History Records Exported: ${historyList.size}")
        sb.appendLine()

        val successCount = historyList.count { it.isSuccess }
        val failedCount = historyList.count { !it.isSuccess }
        val signatureFixedCount = historyList.count { it.signatureReplaced }
        val antiTamperCount = historyList.count { it.antiTamperHash.isNotBlank() }

        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("                             AUDIT METRICS OVERVIEW                             ")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("• Successful Operations: $successCount (${if (historyList.isNotEmpty()) (successCount * 100) / historyList.size else 0}%)")
        sb.appendLine("• Failed Operations: $failedCount")
        sb.appendLine("• Signature Conflicts Reconciled: $signatureFixedCount")
        sb.appendLine("• Cryptographically Sealed with SHA-256: $antiTamperCount")
        sb.appendLine()

        sb.appendLine("================================================================================")
        sb.appendLine("                        INDIVIDUAL PATCH OPERATION RECORDS                      ")
        sb.appendLine("================================================================================")

        if (historyList.isEmpty()) {
            sb.appendLine("No patch operations recorded in the local Room database.")
        } else {
            historyList.forEachIndexed { index, record ->
                val recordNum = index + 1
                sb.appendLine("--------------------------------------------------------------------------------")
                sb.appendLine("RECORD #$recordNum | ID: ${record.id} | Timestamp: ${record.formattedDate}")
                sb.appendLine("--------------------------------------------------------------------------------")
                sb.appendLine("App Name: ${record.appName}")
                sb.appendLine("Package: ${record.packageName}")
                sb.appendLine("Version: ${record.versionName}")
                sb.appendLine("Original APK File: ${record.apkFileName}")
                sb.appendLine("File Size: ${formatBytes(record.fileSizeBytes)}")
                sb.appendLine("Status: ${if (record.isSuccess) "✓ SUCCESS" else "✗ FAILED"}")
                if (!record.isSuccess && record.failureReason != null) {
                    sb.appendLine("Failure Cause: ${record.failureReason}")
                }
                sb.appendLine("Target SDK: API ${record.originalTargetSdk} -> API ${record.patchedTargetSdk}")
                sb.appendLine("Signature Key Replaced: ${if (record.signatureReplaced) "YES (Harmonized unified key)" else "NO"}")
                sb.appendLine("Compatibility Mode: ${if (record.compatibilityModeApplied) "ACTIVE" else "STANDARD"}")
                sb.appendLine("Anti-Tamper Seal: ${if (record.antiTamperHash.isNotBlank()) record.antiTamperHash else "NONE"}")
                if (record.isInfected) {
                    sb.appendLine("SECURITY ALERT: Flagged infected with '${record.threatName ?: "Malware"}'")
                }
                if (record.outputFilePath != null) {
                    sb.appendLine("Patched Binary Location: ${record.outputFilePath}")
                }
                if (record.appliedFixesSummary.isNotBlank()) {
                    sb.appendLine("Summary of Fixes: ${record.appliedFixesSummary}")
                }
                if (record.logSnippet.isNotBlank()) {
                    sb.appendLine("Trace Snippet:")
                    record.logSnippet.lines().forEach { line ->
                        sb.appendLine("    $line")
                    }
                }
                sb.appendLine()
            }
        }

        sb.appendLine("================================================================================")
        sb.appendLine("                           END OF PATCH HISTORY EXPORT                          ")
        sb.appendLine("================================================================================")
        return sb.toString()
    }

    /**
     * Exports text into a .txt file inside the application's shared logs directory.
     */
    fun saveLogToFile(context: Context, logContent: String, fileNamePrefix: String): File {
        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        val safePrefix = fileNamePrefix.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(logsDir, "${safePrefix}_diag_log_${timestamp}.txt")

        FileOutputStream(logFile).use { fos ->
            fos.write(logContent.toByteArray(Charsets.UTF_8))
        }

        return logFile
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.US, "%.2f %s", value, units[index])
    }
}
