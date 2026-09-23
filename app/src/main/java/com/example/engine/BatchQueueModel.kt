package com.example.engine

import java.io.File
import java.util.UUID

enum class BatchItemStatus {
    QUEUED,
    DIAGNOSING,
    READY_TO_PATCH,
    PATCHING,
    COMPLETED,
    FAILED
}

data class BatchQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val appName: String,
    val packageName: String,
    val status: BatchItemStatus = BatchItemStatus.QUEUED,
    val progress: Float = 0f,
    val diagnosticReport: DiagnosticReport? = null,
    val patchResult: PatchResult? = null,
    val errorMessage: String? = null
)
