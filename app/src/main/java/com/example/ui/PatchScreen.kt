package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.BatchItemStatus
import com.example.engine.BatchQueueItem
import com.example.engine.IssueSeverity
import com.example.engine.SampleApkGenerator
import com.example.engine.SampleApkPreset
import java.io.File

@Composable
fun PatchScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val patchMode by viewModel.patchMode.collectAsStateWithLifecycle()

    // Single mode states
    val currentFile by viewModel.currentApkFile.collectAsStateWithLifecycle()
    val report by viewModel.diagnosticReport.collectAsStateWithLifecycle()
    val patchConfig by viewModel.patchConfig.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val progress by viewModel.processingProgress.collectAsStateWithLifecycle()
    val statusText by viewModel.processingStatusText.collectAsStateWithLifecycle()
    val liveLogs by viewModel.liveLogs.collectAsStateWithLifecycle()
    val patchResult by viewModel.lastPatchResult.collectAsStateWithLifecycle()
    val showInfectedWarning by viewModel.showInfectedWarningDialog.collectAsStateWithLifecycle()
    val infectedInfo by viewModel.infectedThreatInfo.collectAsStateWithLifecycle()

    // Batch mode states
    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val isBatchRunning by viewModel.isBatchRunning.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchOverallProgress.collectAsStateWithLifecycle()
    val batchCurrentName by viewModel.batchCurrentItemName.collectAsStateWithLifecycle()
    val batchSummary by viewModel.batchSummaryReport.collectAsStateWithLifecycle()

    // File pickers
    val singleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.loadUserApk(uri)
        }
    }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addUrisToBatch(uris)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // One UI Header
        item {
            OneUiHeader(
                title = "Troubleshoot & Fix",
                subtitle = "Turn unsupported APKs into installable, verified apps with automatic signature reconciliation",
                statusPillText = if (patchMode == 1) "${batchQueue.size} in Batch Queue" else (if (currentFile != null) "APK: ${currentFile?.name}" else "Standby"),
                statusPillIcon = if (patchMode == 1) Icons.Default.Layers else (if (currentFile != null) Icons.Default.CheckCircle else Icons.Default.Description),
                statusPillColor = if (currentFile != null || batchQueue.isNotEmpty()) Color(0xFF00B074) else Color(0xFF64748B)
            )
        }

        // Mode Selector: Single APK vs Batch Queue
        item {
            OneUiPillTabs(
                tabs = listOf("Single APK", "Batch Queue (${batchQueue.size})"),
                selectedIndex = patchMode,
                onTabSelected = { viewModel.setPatchMode(it) },
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // ==============================================
        // BATCH QUEUE MODE
        // ==============================================
        if (patchMode == 1) {
            // Batch Actions & Import Card
            item {
                OneUiCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Batch Processing Queue",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select multiple APK files to diagnose and patch in one automated operation. OneAPK will resolve all signature conflicts and seal each file with anti-tamper verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OneUiButton(
                            text = "Add Multiple APKs",
                            icon = Icons.Default.PlaylistAdd,
                            onClick = { multiFilePicker.launch("application/vnd.android.package-archive") },
                            modifier = Modifier.weight(1f),
                            testTag = "batch_add_apks_button"
                        )
                        OneUiButton(
                            text = "Queue Presets",
                            icon = Icons.Default.Layers,
                            onClick = { viewModel.addAllPresetsToBatch() },
                            isPrimary = false,
                            modifier = Modifier.weight(0.9f),
                            testTag = "batch_add_presets_button"
                        )
                    }

                    if (batchQueue.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${batchQueue.size} applications in queue",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { viewModel.clearBatchQueue() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Queue",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Batch Overall Progress Card
            if (isBatchRunning) {
                item {
                    OneUiCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Processing Batch Queue...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Currently patching: $batchCurrentName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { batchProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }

            // Batch Summary Success Card
            if (batchSummary != null) {
                item {
                    OneUiCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        backgroundColor = Color(0xFF00B074).copy(alpha = 0.12f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00B074)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Batch Operation Completed",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = batchSummary!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Start Batch Execution Button
            if (batchQueue.isNotEmpty()) {
                item {
                    OneUiCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OneUiButton(
                            text = if (isBatchRunning) "Processing Batch..." else "Start Batch Patch (${batchQueue.size} APKs)",
                            icon = Icons.Default.AutoFixHigh,
                            onClick = { viewModel.startBatchProcessing() },
                            enabled = !isBatchRunning,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "start_batch_patch_button"
                        )
                    }
                }
            }

            // Queue Items List
            if (batchQueue.isEmpty()) {
                item {
                    OneUiCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(46.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Queue is Empty",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Queue Presets' or 'Add Multiple APKs' to populate the batch queue.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(batchQueue, key = { it.id }) { item ->
                    BatchQueueItemCard(
                        item = item,
                        onRemove = { viewModel.removeBatchItem(item.id) },
                        onInstall = {
                            item.patchResult?.outputFile?.let { file ->
                                viewModel.installApk(context, file)
                            }
                        },
                        onShare = {
                            item.patchResult?.outputFile?.let { file ->
                                viewModel.shareApk(context, file)
                            }
                        }
                    )
                }
            }
        }

        // ==============================================
        // SINGLE APK MODE
        // ==============================================
        if (patchMode == 0) {
            // File Selector Actions Card (Material3 file picker & conflict presets)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("file_picker_component_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Select APK File for Patching",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pick an APK from your device storage to inspect package headers, resolve signature conflicts, or test with common Android installer blockers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Device Storage Picker Hero Button
                        Surface(
                            onClick = { singleFilePicker.launch("application/vnd.android.package-archive") },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("choose_apk_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileOpen,
                                        contentDescription = "Pick APK",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Choose APK from Storage",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Select any .apk package from device storage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        // Active Selected File info (if loaded)
                        if (currentFile != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentFile?.name ?: "Selected APK",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1f", (currentFile?.length() ?: 0) / 1048576f)} MB • Ready for Patch Configuration",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OneUiButton(
                                        text = "Change",
                                        icon = Icons.Default.FileOpen,
                                        onClick = { singleFilePicker.launch("application/vnd.android.package-archive") },
                                        isPrimary = false,
                                        testTag = "change_apk_button"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Or Test With Conflict Presets:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SampleApkGenerator.PRESETS.forEach { preset ->
                                PresetItemButton(
                                    preset = preset,
                                    isSelected = report?.metadata?.packageName == preset.packageName,
                                    onClick = { viewModel.loadSamplePreset(preset) }
                                )
                            }
                        }
                    }
                }
            }

            // High Visibility Infection Alert Card if APK is infected
            if (report != null && report!!.isInfected) {
                item {
                    val rep = report!!
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("infected_apk_warning_card"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF7F1D1D).copy(alpha = 0.28f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Threat Detected",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "🚨 CRITICAL WARNING: INFECTED APK DETECTED",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFEF4444)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Threat: ${rep.threatName ?: "Trojan.AndroidOS.Generic"}\n${rep.threatDetails ?: "Embedded malicious payload detected."}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Attempting to make this APK work on your phone will expose your device to severe risks including private credential theft and unauthorized background access. OneAPK has locked direct phone installation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Single Progress Bar if running
            if (isProcessing) {
                item {
                    OneUiCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Processing APK...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        if (progress > 0f) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }

            // Diagnostic Report Card (Simple English & Technical Breakdown)
            if (report != null) {
                item {
                    val rep = report!!
                    OneUiCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = rep.metadata.appName,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${rep.metadata.packageName} (v${rep.metadata.versionName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OneUiStatusBadge(
                                text = if (rep.issues.isEmpty()) "Compatible" else "${rep.issues.size} Blockers",
                                color = if (rep.issues.isEmpty()) Color(0xFF00B074) else Color(0xFFEF4444),
                                icon = if (rep.issues.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Error
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Simple English Explanation Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Simple English Explanation",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = rep.plainEnglishSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Metadata metrics row
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetaMetric("Min SDK", "API ${rep.metadata.minSdk}")
                            MetaMetric("Target SDK", "API ${rep.metadata.targetSdk}")
                            MetaMetric("Size", "${rep.metadata.fileSizeBytes / 1024} KB")
                            MetaMetric("Arch", rep.metadata.architectures.firstOrNull() ?: "Universal")
                        }

                        // Detailed Issue breakdown
                        if (rep.issues.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Detected Installation Blockers:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                rep.issues.forEach { issue ->
                                    IssueRow(issue = issue)
                                }
                            }
                        }
                    }
                }

                // Patcher Configuration Options
                item {
                    OneUiCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("patch_configuration_options_card")
                    ) {
                        Text(
                            text = "Patch Configuration Options",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Customize signature replacement, compatibility mode, and anti-tamper security seals before patching.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Signature Replacement Toggle
                        OneUiSwitchRow(
                            title = "Signature Replacement",
                            description = "Replaces conflicting or untrusted developer signatures with a fresh local testkey to bypass signature mismatch conflicts.",
                            checked = patchConfig.replaceSignature || patchConfig.resolveSignatureConflict,
                            onCheckedChange = { viewModel.setReplaceSignature(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Compatibility Mode Toggle
                        OneUiSwitchRow(
                            title = "Compatibility Mode (Android 14+ Pass)",
                            description = "Adapts targetSdk to modern Android 14+ requirements (API 28+) and enables universal 32/64-bit ABI fallback.",
                            checked = patchConfig.compatibilityMode || patchConfig.liftSdkLimits,
                            onCheckedChange = { viewModel.setCompatibilityMode(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        OneUiSwitchRow(
                            title = "Sanitize ContentProvider Authorities",
                            description = "Removes authority collisions with other installed apps on the device.",
                            checked = patchConfig.sanitizeProviders,
                            onCheckedChange = { viewModel.setSanitizeProviders(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        OneUiSwitchRow(
                            title = "Apply Anti-Tamper Security Seal",
                            description = "Computes cryptographic SHA-256 seal to ensure binary integrity and prevent file corruption.",
                            checked = patchConfig.applyAntiTamperSeal,
                            onCheckedChange = { viewModel.setApplyAntiTamperSeal(it) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OneUiButton(
                            text = if (report?.isInfected == true) "Instant Fix (Infection Warning Required)" else "Instant Fix & Reconcile",
                            icon = if (report?.isInfected == true) Icons.Default.Warning else Icons.Default.AutoFixHigh,
                            onClick = { viewModel.executeInstantFix() },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "instant_fix_button"
                        )
                    }
                }
            }

            // Post-Patch Success Card
            if (patchResult != null && patchResult!!.success) {
                item {
                    val res = patchResult!!
                    val isInfected = res.isInfected || (report?.isInfected == true)
                    OneUiCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        backgroundColor = if (isInfected) Color(0xFF7F1D1D).copy(alpha = 0.2f) else Color(0xFF064E3B).copy(alpha = 0.2f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isInfected) Color(0xFFEF4444) else Color(0xFF00B074)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isInfected) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isInfected) "Quarantined in Sandbox Simulation" else "APK Successfully Made Compatible!",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isInfected) "Direct installation blocked for device safety" else "Completed in ${res.durationMs}ms • Sealed with Anti-Tamper",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isInfected) Color(0xFFEF4444) else Color(0xFF00B074)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = res.plainEnglishReport,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OneUiButton(
                                text = if (isInfected) "Install Blocked (Infected)" else "Install Now",
                                icon = if (isInfected) Icons.Default.Warning else Icons.Default.PlayArrow,
                                onClick = {
                                    if (isInfected) {
                                        Toast.makeText(context, "⚠️ Installation blocked: Malware threat detected!", Toast.LENGTH_LONG).show()
                                    } else {
                                        viewModel.installApk(context, res.outputFile)
                                    }
                                },
                                enabled = !isInfected,
                                modifier = Modifier.weight(1f),
                                testTag = "install_now_button"
                            )
                            OneUiButton(
                                text = "Share APK",
                                icon = Icons.Default.Share,
                                onClick = { viewModel.shareApk(context, res.outputFile) },
                                isPrimary = false,
                                modifier = Modifier.weight(0.9f),
                                testTag = "export_now_button"
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OneUiButton(
                            text = "Export Patch Diagnostic Log",
                            icon = Icons.Default.Description,
                            onClick = { viewModel.exportLivePatchLogs(context) },
                            isPrimary = false,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "export_patch_log_button"
                        )
                    }
                }
            }

            // Live Monospace Terminal Logs
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    OneUiTerminalBox(
                        logs = liveLogs,
                        onExportLogs = { viewModel.exportLivePatchLogs(context) }
                    )
                }
            }
        }
    }

    // Infection Warning Dialog when attempting to patch an infected APK
    if (showInfectedWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInfectedWarning() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "⚠️ Security Warning: Infected APK",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            },
            text = {
                Column {
                    Text(
                        text = "You are attempting to make an infected application work on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Identified Threat: ${infectedInfo?.first ?: "Trojan.AndroidOS.Generic"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Risk Details: ${infectedInfo?.second ?: "Contains high-risk Trojan or exploit payload."}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Installing or executing this file on your device can compromise private messages, passwords, and banking apps. OneAPK recommends aborting immediately to keep your device secure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissInfectedWarning() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("abort_infected_button")
                ) {
                    Text("Abort & Quarantine (Safe)")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.proceedWithInfectedSimulation() },
                    modifier = Modifier.testTag("simulate_infected_button")
                ) {
                    Text("Isolated Simulation Only (No Install)")
                }
            }
        )
    }
}

@Composable
private fun BatchQueueItemCard(
    item: BatchQueueItem,
    onRemove: () -> Unit,
    onInstall: () -> Unit,
    onShare: () -> Unit
) {
    OneUiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when (item.status) {
                            BatchItemStatus.COMPLETED -> Color(0xFF00B074).copy(alpha = 0.15f)
                            BatchItemStatus.PATCHING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            BatchItemStatus.FAILED -> Color(0xFFEF4444).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.status) {
                        BatchItemStatus.COMPLETED -> Icons.Default.CheckCircle
                        BatchItemStatus.PATCHING -> Icons.Default.AutoFixHigh
                        BatchItemStatus.FAILED -> Icons.Default.Error
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = when (item.status) {
                        BatchItemStatus.COMPLETED -> Color(0xFF00B074)
                        BatchItemStatus.PATCHING -> MaterialTheme.colorScheme.primary
                        BatchItemStatus.FAILED -> Color(0xFFEF4444)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (item.status) {
                            BatchItemStatus.QUEUED -> "Queued"
                            BatchItemStatus.DIAGNOSING -> "Analyzing..."
                            BatchItemStatus.READY_TO_PATCH -> "Ready"
                            BatchItemStatus.PATCHING -> "${(item.progress * 100).toInt()}%"
                            BatchItemStatus.COMPLETED -> "Sealed A+"
                            BatchItemStatus.FAILED -> "Failed"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            BatchItemStatus.COMPLETED -> Color(0xFF00B074)
                            BatchItemStatus.PATCHING -> MaterialTheme.colorScheme.primary
                            BatchItemStatus.FAILED -> Color(0xFFEF4444)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )

                if (item.status == BatchItemStatus.PATCHING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (item.diagnosticReport != null && item.status == BatchItemStatus.READY_TO_PATCH) {
                    val issueCount = item.diagnosticReport.issues.size
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (issueCount == 0) "Fully compatible" else "$issueCount blockers to reconcile",
                        fontSize = 10.sp,
                        color = if (issueCount == 0) Color(0xFF00B074) else Color(0xFFF59E0B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (item.status == BatchItemStatus.COMPLETED) {
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onInstall, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Install",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (item.status != BatchItemStatus.PATCHING) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetItemButton(
    preset: SampleApkPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = preset.failureCause,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun MetaMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun IssueRow(issue: com.example.engine.ApkIssue) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (issue.severity == IssueSeverity.CRITICAL) Color(0xFFEF4444).copy(alpha = 0.08f)
                else Color(0xFFF59E0B).copy(alpha = 0.08f)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (issue.severity == IssueSeverity.CRITICAL) Icons.Default.Error else Icons.Default.Warning,
            contentDescription = null,
            tint = if (issue.severity == IssueSeverity.CRITICAL) Color(0xFFEF4444) else Color(0xFFF59E0B),
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = issue.technicalCode,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = issue.simpleEnglishExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
