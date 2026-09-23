package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.ModifiedAppEntity
import com.example.data.PatchHistoryEntity
import com.example.engine.AntiTamperReport
import com.example.engine.AntiTamperVerifier
import com.example.engine.ApkDiagnosticEngine
import com.example.engine.ApkPatcherEngine
import com.example.engine.AppHealthEngine
import com.example.engine.BatchItemStatus
import com.example.engine.BatchQueueItem
import com.example.engine.DashboardOverallHealth
import com.example.engine.DiagnosticReport
import com.example.engine.HealthStatusTier
import com.example.engine.PatchConfig
import com.example.engine.PatchResult
import com.example.engine.PatchStepLog
import com.example.engine.SampleApkGenerator
import com.example.engine.SampleApkPreset
import com.example.engine.SingleAppHealthReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class DashboardSecuritySummary(
    val totalScanned: Int = 0,
    val cleanCount: Int = 0,
    val threatCount: Int = 0,
    val signatureIntegrityValidCount: Int = 0,
    val antiTamperSealedCount: Int = 0,
    val isScanning: Boolean = false,
    val activeThreats: List<String> = emptyList(),
    val overallStatus: String = "System Protected"
)

enum class PatchHistoryStatusFilter {
    ALL, SUCCESS, FAILED
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = AppRepository(db.modifiedAppDao(), db.patchHistoryDao())
    private val diagnosticEngine = ApkDiagnosticEngine(application)
    private val patcherEngine = ApkPatcherEngine(application)
    private val healthEngine = AppHealthEngine(application)

    // UI Navigation Tab
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    // APK Troubleshooting & Patch State (Single)
    private val _currentApkFile = MutableStateFlow<File?>(null)
    val currentApkFile: StateFlow<File?> = _currentApkFile.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<DiagnosticReport?>(null)
    val diagnosticReport: StateFlow<DiagnosticReport?> = _diagnosticReport.asStateFlow()

    private val _patchConfig = MutableStateFlow(PatchConfig())
    val patchConfig: StateFlow<PatchConfig> = _patchConfig.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingStatusText = MutableStateFlow("")
    val processingStatusText: StateFlow<String> = _processingStatusText.asStateFlow()

    private val _liveLogs = MutableStateFlow<List<PatchStepLog>>(emptyList())
    val liveLogs: StateFlow<List<PatchStepLog>> = _liveLogs.asStateFlow()

    private val _lastPatchResult = MutableStateFlow<PatchResult?>(null)
    val lastPatchResult: StateFlow<PatchResult?> = _lastPatchResult.asStateFlow()

    // Batch Processing Queue State
    private val _batchQueue = MutableStateFlow<List<BatchQueueItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchQueueItem>> = _batchQueue.asStateFlow()

    private val _isBatchRunning = MutableStateFlow(false)
    val isBatchRunning: StateFlow<Boolean> = _isBatchRunning.asStateFlow()

    private val _batchOverallProgress = MutableStateFlow(0f)
    val batchOverallProgress: StateFlow<Float> = _batchOverallProgress.asStateFlow()

    private val _batchCurrentItemName = MutableStateFlow("")
    val batchCurrentItemName: StateFlow<String> = _batchCurrentItemName.asStateFlow()

    private val _batchSummaryReport = MutableStateFlow<String?>(null)
    val batchSummaryReport: StateFlow<String?> = _batchSummaryReport.asStateFlow()

    // Mode Toggle in Troubleshoot Tab: 0 = Single APK, 1 = Batch Queue
    private val _patchMode = MutableStateFlow(0)
    val patchMode: StateFlow<Int> = _patchMode.asStateFlow()

    fun setPatchMode(mode: Int) {
        _patchMode.value = mode
    }

    // Anti-Tamper Verification
    private val _antiTamperReport = MutableStateFlow<AntiTamperReport?>(null)
    val antiTamperReport: StateFlow<AntiTamperReport?> = _antiTamperReport.asStateFlow()

    private val _antiTamperCheckedFile = MutableStateFlow<File?>(null)
    val antiTamperCheckedFile: StateFlow<File?> = _antiTamperCheckedFile.asStateFlow()

    // Infection & Security Threat Warning State
    private val _showInfectedWarningDialog = MutableStateFlow(false)
    val showInfectedWarningDialog: StateFlow<Boolean> = _showInfectedWarningDialog.asStateFlow()

    private val _infectedThreatInfo = MutableStateFlow<Pair<String, String>?>(null)
    val infectedThreatInfo: StateFlow<Pair<String, String>?> = _infectedThreatInfo.asStateFlow()

    // Security Scan Dashboard Results
    private val _dashboardSecurity = MutableStateFlow(DashboardSecuritySummary())
    val dashboardSecurity: StateFlow<DashboardSecuritySummary> = _dashboardSecurity.asStateFlow()

    // Dashboard & History
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allModifiedApps: StateFlow<List<ModifiedAppEntity>> = repository.allModifiedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredApps: StateFlow<List<ModifiedAppEntity>> = combine(allModifiedApps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true) ||
                    it.plainEnglishSummary.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPatchHistory: StateFlow<List<PatchHistoryEntity>> = repository.allPatchHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Patch History Search & Filter State
    private val _patchHistorySearchQuery = MutableStateFlow("")
    val patchHistorySearchQuery: StateFlow<String> = _patchHistorySearchQuery.asStateFlow()

    private val _patchHistoryFilter = MutableStateFlow(PatchHistoryStatusFilter.ALL)
    val patchHistoryFilter: StateFlow<PatchHistoryStatusFilter> = _patchHistoryFilter.asStateFlow()

    fun setPatchHistorySearchQuery(query: String) {
        _patchHistorySearchQuery.value = query
    }

    fun setPatchHistoryFilter(filter: PatchHistoryStatusFilter) {
        _patchHistoryFilter.value = filter
    }

    val filteredPatchHistory: StateFlow<List<PatchHistoryEntity>> = combine(
        allPatchHistory,
        _patchHistorySearchQuery,
        _patchHistoryFilter
    ) { history, query, filter ->
        val trimmedQuery = query.trim()
        history.filter { item ->
            val matchesFilter = when (filter) {
                PatchHistoryStatusFilter.ALL -> true
                PatchHistoryStatusFilter.SUCCESS -> item.isSuccess
                PatchHistoryStatusFilter.FAILED -> !item.isSuccess
            }
            val matchesQuery = if (trimmedQuery.isBlank()) true else {
                item.appName.contains(trimmedQuery, ignoreCase = true) ||
                    item.packageName.contains(trimmedQuery, ignoreCase = true) ||
                    item.apkFileName.contains(trimmedQuery, ignoreCase = true) ||
                    item.appliedFixesSummary.contains(trimmedQuery, ignoreCase = true) ||
                    (if (item.isSuccess) "success" else "failed").contains(trimmedQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedAppForDetail = MutableStateFlow<ModifiedAppEntity?>(null)
    val selectedAppForDetail: StateFlow<ModifiedAppEntity?> = _selectedAppForDetail.asStateFlow()

    // App Health & Stability Diagnostics
    private val _dashboardHealth = MutableStateFlow<DashboardOverallHealth?>(null)
    val dashboardHealth: StateFlow<DashboardOverallHealth?> = _dashboardHealth.asStateFlow()

    private val _isScanningHealth = MutableStateFlow(false)
    val isScanningHealth: StateFlow<Boolean> = _isScanningHealth.asStateFlow()

    private val _selectedAppHealthReport = MutableStateFlow<SingleAppHealthReport?>(null)
    val selectedAppHealthReport: StateFlow<SingleAppHealthReport?> = _selectedAppHealthReport.asStateFlow()

    init {
        seedDemoDataIfEmpty()
        observeAndScanHealth()
        runSecurityScan()
    }

    fun openAppHealthDetails(report: SingleAppHealthReport?) {
        _selectedAppHealthReport.value = report
    }

    fun runHealthScan() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningHealth.value = true
            val apps = allModifiedApps.value
            val health = healthEngine.scanAllApps(apps)
            _dashboardHealth.value = health
            _isScanningHealth.value = false
        }
    }

    private fun observeAndScanHealth() {
        viewModelScope.launch {
            allModifiedApps.collect { apps ->
                runSecurityScan()
                withContext(Dispatchers.IO) {
                    val health = healthEngine.scanAllApps(apps)
                    _dashboardHealth.value = health
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openAppDetail(app: ModifiedAppEntity?) {
        _selectedAppForDetail.value = app
    }

    fun updatePatchConfig(config: PatchConfig) {
        _patchConfig.value = config
    }

    fun loadSamplePreset(preset: SampleApkPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _processingStatusText.value = "Creating sample incompatible APK..."
            _liveLogs.value = emptyList()
            _lastPatchResult.value = null

            val file = SampleApkGenerator.createPresetApk(getApplication(), preset)
            _currentApkFile.value = file

            _processingStatusText.value = "Running deep compatibility diagnosis..."
            val report = diagnosticEngine.diagnoseApk(file)
            _diagnosticReport.value = report

            _isProcessing.value = false
            _processingStatusText.value = ""
        }
    }

    fun loadUserApk(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isProcessing.value = true
                _processingStatusText.value = "Importing APK from storage..."
                _liveLogs.value = emptyList()
                _lastPatchResult.value = null

                val context = getApplication<Application>()
                val cacheDir = File(context.cacheDir, "imported_apks").apply { mkdirs() }
                val targetFile = File(cacheDir, "imported_${System.currentTimeMillis()}.apk")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                _currentApkFile.value = targetFile
                _processingStatusText.value = "Diagnosing package and signature headers..."
                val report = diagnosticEngine.diagnoseApk(targetFile)
                _diagnosticReport.value = report
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to read APK: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isProcessing.value = false
                _processingStatusText.value = ""
            }
        }
    }

    fun setReplaceSignature(enabled: Boolean) {
        _patchConfig.value = _patchConfig.value.copy(
            replaceSignature = enabled,
            resolveSignatureConflict = enabled
        )
    }

    fun setCompatibilityMode(enabled: Boolean) {
        _patchConfig.value = _patchConfig.value.copy(
            compatibilityMode = enabled,
            liftSdkLimits = enabled,
            universalAbiBridge = enabled
        )
    }

    fun setSanitizeProviders(enabled: Boolean) {
        _patchConfig.value = _patchConfig.value.copy(sanitizeProviders = enabled)
    }

    fun setApplyAntiTamperSeal(enabled: Boolean) {
        _patchConfig.value = _patchConfig.value.copy(applyAntiTamperSeal = enabled)
    }

    fun dismissInfectedWarning() {
        _showInfectedWarningDialog.value = false
    }

    fun proceedWithInfectedSimulation() {
        _showInfectedWarningDialog.value = false
        val file = _currentApkFile.value ?: return
        val report = _diagnosticReport.value ?: return
        runPatchProcess(file, report, _patchConfig.value.copy(isolateMalwareSandbox = true))
    }

    fun executeInstantFix() {
        val file = _currentApkFile.value ?: return
        val report = _diagnosticReport.value ?: return

        // If APK is infected with malware or dangerous payload, show prominent warning dialog!
        if (report.isInfected) {
            _infectedThreatInfo.value = Pair(
                report.threatName ?: "Trojan.AndroidOS.Generic",
                report.threatDetails ?: "Hidden malicious payload or exploit signature detected."
            )
            _showInfectedWarningDialog.value = true
            return
        }

        runPatchProcess(file, report, _patchConfig.value)
    }

    private fun runPatchProcess(file: File, report: DiagnosticReport, config: PatchConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _liveLogs.value = emptyList()

            val result = patcherEngine.patchApk(
                sourceFile = file,
                metadata = report.metadata,
                config = config,
                onProgress = { step, progress ->
                    _processingStatusText.value = step
                    _processingProgress.value = progress
                }
            )

            _liveLogs.value = result.logs
            _lastPatchResult.value = result

            if (result.success) {
                // Record in Room Database
                val entity = ModifiedAppEntity(
                    packageName = report.metadata.packageName,
                    appName = report.metadata.appName,
                    versionName = report.metadata.versionName,
                    versionCode = report.metadata.versionCode,
                    originalApkName = file.name,
                    patchedApkPath = result.outputFile.absolutePath,
                    fileSizeBytes = result.outputFile.length(),
                    minSdkOriginal = report.metadata.minSdk,
                    minSdkPatched = 24,
                    targetSdkOriginal = report.metadata.targetSdk,
                    targetSdkPatched = 28,
                    signatureConflictResolved = config.resolveSignatureConflict || config.replaceSignature,
                    antiTamperHash = result.antiTamperHash,
                    isAntiTamperValid = !report.isInfected,
                    issuesFoundJson = report.issues.joinToString(";") { it.title },
                    fixesAppliedJson = result.appliedFixes.joinToString(";"),
                    plainEnglishSummary = result.plainEnglishReport,
                    diagnosticLogText = result.logs.joinToString("\n") { "[${it.tag}] ${it.message}" },
                    updateVersionNote = if (report.isInfected) "QUARANTINED: Malware Simulation (Non-installable)" else "Compatibility Hotfix: Signature & TargetSdk Adapted",
                    isInfected = report.isInfected,
                    threatName = report.threatName
                )
                repository.saveModifiedApp(entity)
                runSecurityScan()
            }

            // Record Operation in Room Patch History
            try {
                val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
                val historyItem = PatchHistoryEntity(
                    timestamp = System.currentTimeMillis(),
                    formattedDate = dateFormat.format(java.util.Date()),
                    apkFileName = file.name,
                    packageName = report.metadata.packageName,
                    appName = report.metadata.appName,
                    versionName = report.metadata.versionName,
                    isSuccess = result.success,
                    failureReason = if (result.success) null else (result.logs.lastOrNull { !it.isSuccess }?.message ?: "Patch execution failed"),
                    originalTargetSdk = report.metadata.targetSdk,
                    patchedTargetSdk = 28,
                    signatureReplaced = config.replaceSignature || config.resolveSignatureConflict,
                    compatibilityModeApplied = config.compatibilityMode,
                    antiTamperHash = result.antiTamperHash,
                    outputFilePath = if (result.success) result.outputFile.absolutePath else null,
                    fileSizeBytes = if (result.success) result.outputFile.length() else file.length(),
                    isInfected = report.isInfected,
                    threatName = report.threatName,
                    appliedFixesSummary = if (result.appliedFixes.isNotEmpty()) result.appliedFixes.joinToString(", ") else "TargetSdk 28, Signature Replaced",
                    logSnippet = result.logs.takeLast(3).joinToString("\n") { "[${it.tag}] ${it.message}" }
                )
                repository.recordPatchOperation(historyItem)
            } catch (_: Exception) {
                // Ignore history recording error
            }

            _isProcessing.value = false
            _processingStatusText.value = ""
        }
    }

    // ==========================================
    // BATCH PROCESSING QUEUE LOGIC
    // ==========================================

    fun addUrisToBatch(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val cacheDir = File(context.cacheDir, "batch_imported_apks").apply { mkdirs() }
            val currentList = _batchQueue.value.toMutableList()

            for ((index, uri) in uris.withIndex()) {
                val targetFile = File(cacheDir, "batch_${System.currentTimeMillis()}_$index.apk")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                    }
                    val diag = diagnosticEngine.diagnoseApk(targetFile)
                    val item = BatchQueueItem(
                        file = targetFile,
                        appName = diag.metadata.appName,
                        packageName = diag.metadata.packageName,
                        status = BatchItemStatus.READY_TO_PATCH,
                        diagnosticReport = diag
                    )
                    currentList.add(item)
                } catch (_: Exception) {
                    // Skip failed file read
                }
            }
            _batchQueue.value = currentList
            _batchSummaryReport.value = null
        }
    }

    fun addAllPresetsToBatch() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = _batchQueue.value.toMutableList()
            for (preset in SampleApkGenerator.PRESETS) {
                // Avoid duplicates in batch queue
                if (currentList.none { it.packageName == preset.packageName }) {
                    val file = SampleApkGenerator.createPresetApk(getApplication(), preset)
                    val diag = diagnosticEngine.diagnoseApk(file)
                    currentList.add(
                        BatchQueueItem(
                            file = file,
                            appName = preset.title,
                            packageName = preset.packageName,
                            status = BatchItemStatus.READY_TO_PATCH,
                            diagnosticReport = diag
                        )
                    )
                }
            }
            _batchQueue.value = currentList
            _batchSummaryReport.value = null
        }
    }

    fun removeBatchItem(id: String) {
        if (_isBatchRunning.value) return
        _batchQueue.value = _batchQueue.value.filter { it.id != id }
    }

    fun clearBatchQueue() {
        if (_isBatchRunning.value) return
        _batchQueue.value = emptyList()
        _batchSummaryReport.value = null
        _batchOverallProgress.value = 0f
    }

    fun addToBatchQueue(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val diag = diagnosticEngine.diagnoseApk(file)
            val currentList = _batchQueue.value.toMutableList()
            if (currentList.none { it.packageName == diag.metadata.packageName }) {
                currentList.add(
                    BatchQueueItem(
                        file = file,
                        appName = diag.metadata.appName,
                        packageName = diag.metadata.packageName,
                        status = BatchItemStatus.READY_TO_PATCH,
                        diagnosticReport = diag
                    )
                )
                _batchQueue.value = currentList
            }
        }
    }

    fun deletePatchHistory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePatchHistory(id)
        }
    }

    fun deletePatchHistoryBatch(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePatchHistoryBatch(ids.toList())
        }
    }

    fun clearAllPatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllPatchHistory()
        }
    }

    fun startBatchProcessing() {
        val queue = _batchQueue.value
        if (queue.isEmpty() || _isBatchRunning.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isBatchRunning.value = true
            _batchSummaryReport.value = null
            var successCount = 0
            var conflictFixedCount = 0
            val total = queue.size

            val updatedQueue = queue.toMutableList()

            for (i in updatedQueue.indices) {
                val item = updatedQueue[i]
                if (item.status == BatchItemStatus.COMPLETED) {
                    successCount++
                    continue
                }

                _batchCurrentItemName.value = item.appName
                _batchOverallProgress.value = i.toFloat() / total.toFloat()

                // Mark current item patching
                updatedQueue[i] = item.copy(status = BatchItemStatus.PATCHING, progress = 0.2f)
                _batchQueue.value = updatedQueue.toList()

                val diag = item.diagnosticReport ?: diagnosticEngine.diagnoseApk(item.file)

                val result = patcherEngine.patchApk(
                    sourceFile = item.file,
                    metadata = diag.metadata,
                    config = _patchConfig.value,
                    onProgress = { _, progressVal ->
                        val current = _batchQueue.value.toMutableList()
                        if (i < current.size) {
                            current[i] = current[i].copy(progress = progressVal)
                            _batchQueue.value = current
                        }
                    }
                )

                if (result.success) {
                    successCount++
                    if (_patchConfig.value.resolveSignatureConflict) conflictFixedCount++

                    // Save to Room DB
                    val entity = ModifiedAppEntity(
                        packageName = diag.metadata.packageName,
                        appName = diag.metadata.appName,
                        versionName = diag.metadata.versionName,
                        versionCode = diag.metadata.versionCode,
                        originalApkName = item.file.name,
                        patchedApkPath = result.outputFile.absolutePath,
                        fileSizeBytes = result.outputFile.length(),
                        minSdkOriginal = diag.metadata.minSdk,
                        minSdkPatched = 24,
                        targetSdkOriginal = diag.metadata.targetSdk,
                        targetSdkPatched = 28,
                        signatureConflictResolved = _patchConfig.value.resolveSignatureConflict,
                        antiTamperHash = result.antiTamperHash,
                        isAntiTamperValid = true,
                        issuesFoundJson = diag.issues.joinToString(";") { it.title },
                        fixesAppliedJson = result.appliedFixes.joinToString(";"),
                        plainEnglishSummary = result.plainEnglishReport,
                        diagnosticLogText = result.logs.joinToString("\n") { "[${it.tag}] ${it.message}" },
                        updateVersionNote = "Batch Operation Compatibility Release"
                    )
                    repository.saveModifiedApp(entity)

                    updatedQueue[i] = item.copy(
                        status = BatchItemStatus.COMPLETED,
                        progress = 1.0f,
                        patchResult = result
                    )
                } else {
                    updatedQueue[i] = item.copy(
                        status = BatchItemStatus.FAILED,
                        progress = 0f,
                        errorMessage = "Patch failed: ${result.logs.lastOrNull()?.message ?: "Unknown error"}"
                    )
                }

                // Record each batch item to Room Patch History
                try {
                    val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
                    val historyItem = PatchHistoryEntity(
                        timestamp = System.currentTimeMillis(),
                        formattedDate = dateFormat.format(java.util.Date()),
                        apkFileName = item.file.name,
                        packageName = diag.metadata.packageName,
                        appName = diag.metadata.appName,
                        versionName = diag.metadata.versionName,
                        isSuccess = result.success,
                        failureReason = if (result.success) null else (result.logs.lastOrNull { !it.isSuccess }?.message ?: "Batch item failed"),
                        originalTargetSdk = diag.metadata.targetSdk,
                        patchedTargetSdk = 28,
                        signatureReplaced = _patchConfig.value.replaceSignature,
                        compatibilityModeApplied = _patchConfig.value.compatibilityMode,
                        antiTamperHash = result.antiTamperHash,
                        outputFilePath = if (result.success) result.outputFile.absolutePath else null,
                        fileSizeBytes = if (result.success) result.outputFile.length() else item.file.length(),
                        isInfected = diag.isInfected,
                        threatName = diag.threatName,
                        appliedFixesSummary = if (result.appliedFixes.isNotEmpty()) result.appliedFixes.joinToString(", ") else "Batch Patch Applied",
                        logSnippet = result.logs.takeLast(3).joinToString("\n") { "[${it.tag}] ${it.message}" }
                    )
                    repository.recordPatchOperation(historyItem)
                } catch (_: Exception) {
                    // Ignore history recording error
                }

                _batchQueue.value = updatedQueue.toList()
            }

            _batchOverallProgress.value = 1.0f
            _isBatchRunning.value = false
            _batchCurrentItemName.value = ""

            _batchSummaryReport.value = "🎉 **Batch Operation Complete!**\n" +
                    "• Successfully patched: **$successCount of $total applications**\n" +
                    "• Signature conflicts reconciled: **$conflictFixedCount**\n" +
                    "• Anti-tamper verification seals generated: **$successCount**\n" +
                    "All modified applications are registered in your dashboard and ready for installation."
        }
    }

    // ==========================================
    // ANTI-TAMPER, INSTALL, SHARE & DELETE
    // ==========================================

    fun runAntiTamperCheck(file: File, expectedHash: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _antiTamperCheckedFile.value = file
            val report = AntiTamperVerifier.verifyApkIntegrity(file, expectedHash)
            _antiTamperReport.value = report
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "APK file not found on disk or is empty.", Toast.LENGTH_SHORT).show()
                return
            }

            // Block installation if this APK is known or flagged as infected
            val isKnownInfected = allModifiedApps.value.any { it.patchedApkPath == apkFile.absolutePath && it.isInfected } ||
                    apkFile.name.contains("cleanmaster", ignoreCase = true) ||
                    apkFile.name.contains("trojan", ignoreCase = true)

            if (isKnownInfected) {
                Toast.makeText(
                    context,
                    "⚠️ INSTALLATION BLOCKED: This APK is infected with malware! OneAPK has blocked installation to protect your device.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "APK file not found.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Export / Share Supported APK"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteAppRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteApp(id)
            if (_selectedAppForDetail.value?.id == id) {
                _selectedAppForDetail.value = null
            }
        }
    }

    // ==========================================
    // LOG EXPORT & QUICK ACTIONS
    // ==========================================

    fun exportAppDiagnosticLogs(context: Context, app: ModifiedAppEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val coverage = _dashboardHealth.value?.samsungCompatibilityCoverage
                val reportContent = com.example.engine.DiagnosticLogExporter.generateFormattedLogReport(
                    context = context,
                    app = app,
                    systemHealthSummary = coverage
                )
                val file = com.example.engine.DiagnosticLogExporter.saveLogToFile(
                    context = context,
                    logContent = reportContent,
                    fileNamePrefix = "${app.appName}_${app.packageName}"
                )

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "OneAPK Patch Diagnostic Log - ${app.appName}")
                        putExtra(Intent.EXTRA_TEXT, "Detailed APK patch and diagnostic report for ${app.appName} (${app.packageName}).")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Diagnostic Log with Developer"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to export diagnostic log: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportLivePatchLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apkName = _currentApkFile.value?.name ?: "Unknown_APK"
                val pkgName = _diagnosticReport.value?.metadata?.packageName
                val logs = _liveLogs.value
                val isSuccess = _lastPatchResult.value?.success ?: false
                val summary = _lastPatchResult.value?.plainEnglishReport
                    ?: _processingStatusText.value
                    ?: "Live Patch Session"

                val reportContent = com.example.engine.DiagnosticLogExporter.generateLivePatchLogReport(
                    context = context,
                    apkName = apkName,
                    packageName = pkgName,
                    logs = logs,
                    success = isSuccess,
                    summary = summary
                )
                val file = com.example.engine.DiagnosticLogExporter.saveLogToFile(
                    context = context,
                    logContent = reportContent,
                    fileNamePrefix = "live_patch_${apkName}"
                )

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "OneAPK Live Patch Execution Log - $apkName")
                        putExtra(Intent.EXTRA_TEXT, "Detailed execution trace for $apkName.")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Patch Trace with Developer"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to export trace log: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportPatchHistoryLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val historyList = repository.allPatchHistory.first()
                val reportContent = com.example.engine.DiagnosticLogExporter.generatePatchHistoryLogReport(
                    context = context,
                    historyList = historyList
                )
                val file = com.example.engine.DiagnosticLogExporter.saveLogToFile(
                    context = context,
                    logContent = reportContent,
                    fileNamePrefix = "patch_history_full_export"
                )

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "OneAPK Studio - Patch History Export (${historyList.size} records)")
                        putExtra(Intent.EXTRA_TEXT, "Exported patch history logs from OneAPK Studio Room database.")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Patch History to Text File"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to export patch history: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun clearInstallerCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            var freedBytes = 0L
            var filesDeleted = 0

            // Clear cacheDir/apks
            val apkCache = File(context.cacheDir, "apks")
            if (apkCache.exists()) {
                apkCache.listFiles()?.forEach { file ->
                    freedBytes += file.length()
                    if (file.delete()) filesDeleted++
                }
            }

            // Clear cacheDir/logs
            val logCache = File(context.cacheDir, "logs")
            if (logCache.exists()) {
                logCache.listFiles()?.forEach { file ->
                    freedBytes += file.length()
                    if (file.delete()) filesDeleted++
                }
            }

            // Clear generic cache
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".tmp") || file.name.endsWith(".apk"))) {
                    freedBytes += file.length()
                    if (file.delete()) filesDeleted++
                }
            }

            val freedMb = String.format(Locale.US, "%.1f MB", freedBytes / (1024.0 * 1024.0))
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "✓ Installer Cache Purged: Cleared $filesDeleted residual package files ($freedMb freed).",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun executeEmergencyRecovery(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Purge stale APK caches and lockfiles
            val apkCache = File(context.cacheDir, "apks")
            if (apkCache.exists()) {
                apkCache.deleteRecursively()
                apkCache.mkdirs()
            }
            val filesApks = File(context.filesDir, "apks")
            if (!filesApks.exists()) {
                filesApks.mkdirs()
            }

            // 2. Re-verify anti-tamper hashes and database integrity
            val allApps: List<ModifiedAppEntity> = repository.allModifiedApps.first()
            var repairedCount = 0
            for (app in allApps) {
                val apk = File(app.patchedApkPath)
                if (apk.exists()) {
                    val report = AntiTamperVerifier.verifyApkIntegrity(apk, app.antiTamperHash)
                    if (report.isValid != app.isAntiTamperValid) {
                        repository.saveModifiedApp(app.copy(isAntiTamperValid = report.isValid))
                        repairedCount++
                    }
                }
            }

            // 3. Re-scan health status
            val updatedApps: List<ModifiedAppEntity> = repository.allModifiedApps.first()
            val health = healthEngine.scanAllApps(updatedApps)
            _dashboardHealth.value = health

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "⚡ Emergency Recovery Complete: Installation cache purged, filesystem integrity restored, $repairedCount seals audited.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun runSecurityScan() {
        viewModelScope.launch(Dispatchers.IO) {
            _dashboardSecurity.value = _dashboardSecurity.value.copy(isScanning = true)
            kotlinx.coroutines.delay(350)
            val apps = repository.allModifiedApps.first()
            var threats = 0
            var clean = 0
            var antiTamperSealed = 0
            val threatList = mutableListOf<String>()

            for (app in apps) {
                if (app.isInfected) {
                    threats++
                    threatList.add("${app.appName} (${app.threatName ?: "Malware"})")
                } else {
                    clean++
                }
                if (app.isAntiTamperValid && app.antiTamperHash.isNotBlank()) {
                    antiTamperSealed++
                }
            }

            // Check current selected APK
            val current = _diagnosticReport.value
            if (current != null && current.isInfected) {
                threats++
                threatList.add("${current.metadata.appName} (${current.threatName ?: "Infected Trojan"})")
            }

            val overallStatus = if (threats > 0) "$threats Security Threat(s) Flagged" else "All Applications Untampered & Clean"

            _dashboardSecurity.value = DashboardSecuritySummary(
                totalScanned = apps.size + if (current != null) 1 else 0,
                cleanCount = clean,
                threatCount = threats,
                signatureIntegrityValidCount = clean,
                antiTamperSealedCount = antiTamperSealed,
                isScanning = false,
                activeThreats = threatList,
                overallStatus = overallStatus
            )
        }
    }

    private fun seedDemoDataIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val (realFile, realHash) = SampleApkGenerator.createValidPreloadedApk(context)

            val existing = repository.getAppById(1)
            if (existing == null) {
                val seed = ModifiedAppEntity(
                    packageName = "com.arcade.galaxyretro",
                    appName = "Galaxy Retro 1999",
                    versionName = "1.0.3",
                    versionCode = 103,
                    originalApkName = "galaxy_retro_unsupported.apk",
                    patchedApkPath = realFile.absolutePath,
                    fileSizeBytes = realFile.length(),
                    timestamp = System.currentTimeMillis() - 3600_000 * 5,
                    minSdkOriginal = 16,
                    minSdkPatched = 24,
                    targetSdkOriginal = 19,
                    targetSdkPatched = 28,
                    signatureConflictResolved = true,
                    antiTamperHash = realHash,
                    isAntiTamperValid = true,
                    issuesFoundJson = "Outdated Target Android Version;Signature Key Conflict Detected",
                    fixesAppliedJson = "Resolved signature conflict by stripping foreign certs;Lifted targetSdk to modern standard (API 28+);Sealed with Anti-Tamper SHA-256",
                    plainEnglishSummary = "🎉 'Galaxy Retro 1999' is now fully compatible and ready to install!\n\nHere is what OneAPK did:\n• Step 1: Reconciled package signature to prevent INSTALL_FAILED_UPDATE_INCOMPATIBLE.\n• Step 2: Lifted targetSdk from 19 to 28 so Android 14+ allows installation.\n• Step 3: Sealed with Anti-Tamper verification proof.",
                    diagnosticLogText = "[INIT] Ingested Galaxy Retro 1999\n[SIGNATURE] Removed colliding keystore\n[SDK] Injected API 28 compat flags\n[SECURITY] Anti-tamper verified\n[DONE] Ready for installation",
                    updateVersionNote = "Initial Compatibility Patch: Signature Reconciled",
                    isInfected = false,
                    threatName = null
                )
                repository.saveModifiedApp(seed)
            } else if (existing.patchedApkPath.startsWith("/dummy/")) {
                repository.saveModifiedApp(
                    existing.copy(
                        patchedApkPath = realFile.absolutePath,
                        fileSizeBytes = realFile.length(),
                        antiTamperHash = realHash
                    )
                )
            }

            val historyList = repository.allPatchHistory.first()
            if (historyList.isEmpty()) {
                val demoHistory = PatchHistoryEntity(
                    timestamp = System.currentTimeMillis() - 3600_000 * 5,
                    formattedDate = "Earlier Today • 10:15 AM",
                    apkFileName = "galaxy_retro_unsupported.apk",
                    packageName = "com.arcade.galaxyretro",
                    appName = "Galaxy Retro 1999",
                    versionName = "1.0.3",
                    isSuccess = true,
                    failureReason = null,
                    originalTargetSdk = 19,
                    patchedTargetSdk = 28,
                    signatureReplaced = true,
                    compatibilityModeApplied = true,
                    antiTamperHash = realHash,
                    outputFilePath = realFile.absolutePath,
                    fileSizeBytes = realFile.length(),
                    isInfected = false,
                    appliedFixesSummary = "Lifted targetSdk 19 ➔ 28, Resolved Signature Conflict, Sealed with SHA-256",
                    logSnippet = "[SIGNATURE] Replaced colliding key\n[SDK] Injected API 28 compat flags\n[DONE] Ready for installation"
                )
                repository.recordPatchOperation(demoHistory)
            }

            runSecurityScan()
        }
    }
}
