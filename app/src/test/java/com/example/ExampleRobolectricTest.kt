package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ApkDiagnosticEngine
import com.example.engine.ApkPatcherEngine
import com.example.engine.PatchConfig
import com.example.engine.SampleApkGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OneAPK Studio", appName)
  }

  @Test
  fun `detect infected APK preset and flag security danger`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val infectedPreset = SampleApkGenerator.PRESETS.first { it.isInfected }
    val apkFile = SampleApkGenerator.createPresetApk(context, infectedPreset)

    val diagnosticEngine = ApkDiagnosticEngine(context)
    val report = diagnosticEngine.diagnoseApk(apkFile)

    assertTrue("Report should be flagged as infected", report.isInfected)
    assertTrue("Threat name should indicate Trojan", report.threatName?.contains("Trojan") == true)
  }

  @Test
  fun `patch clean APK with signature replacement and compatibility mode`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cleanPreset = SampleApkGenerator.PRESETS.first { !it.isInfected }
    val apkFile = SampleApkGenerator.createPresetApk(context, cleanPreset)

    val diagnosticEngine = ApkDiagnosticEngine(context)
    val report = diagnosticEngine.diagnoseApk(apkFile)

    val patchEngine = ApkPatcherEngine(context)
    val config = PatchConfig(
      replaceSignature = true,
      compatibilityMode = true,
      resolveSignatureConflict = true,
      liftSdkLimits = true,
      applyAntiTamperSeal = true
    )

    val result = patchEngine.patchApk(apkFile, report.metadata, config) { _, _ -> }

    assertTrue("Patch should succeed", result.success)
    assertNotNull("Output file should be generated", result.outputFile)
    assertTrue("Anti-tamper seal should be calculated", result.antiTamperHash.isNotEmpty())
    assertFalse("Clean app should not be flagged as infected", result.isInfected)
  }

  @Test
  fun `instantiate MainViewModel and verify initialization without crash`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.MainViewModel(application)
    assertNotNull(viewModel.allModifiedApps)
    assertNotNull(viewModel.allPatchHistory)
    assertNotNull(viewModel.dashboardSecurity.value)
    assertEquals(0, viewModel.selectedTab.value)
  }

  @Test
  fun `verify Room PatchHistoryDao persists and retrieves patch operation records`() = runBlocking {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val db = com.example.data.AppDatabase.getInstance(application)
    val dao = db.patchHistoryDao()

    val testItem = com.example.data.PatchHistoryEntity(
      apkFileName = "test_app.apk",
      packageName = "com.test.app",
      appName = "Test Application",
      versionName = "1.0",
      formattedDate = "Today 12:00 PM",
      isSuccess = true,
      originalTargetSdk = 19,
      patchedTargetSdk = 28,
      signatureReplaced = true,
      compatibilityModeApplied = true,
      antiTamperHash = "hash1234567890abcdef"
    )

    val id = dao.insertHistory(testItem)
    assertTrue("Inserted record ID should be positive", id > 0)

    val fetched = dao.getHistoryById(id)
    assertNotNull("Fetched item should not be null", fetched)
    assertEquals("Test Application", fetched?.appName)
    assertTrue("Should be marked as successful", fetched?.isSuccess == true)
  }

  @Test
  fun `verify batch queue adds presets and manages queue items`() = runBlocking {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.MainViewModel(application)

    viewModel.addAllPresetsToBatch()
    // Give background coroutine time to complete
    kotlinx.coroutines.delay(800)

    val queue = viewModel.batchQueue.value
    assertTrue("Queue should contain presets", queue.isNotEmpty())

    val firstId = queue.first().id
    viewModel.removeBatchItem(firstId)
    assertFalse("Removed item should not be in queue", viewModel.batchQueue.value.any { it.id == firstId })

    viewModel.clearBatchQueue()
    assertTrue("Queue should be empty after clear", viewModel.batchQueue.value.isEmpty())
  }

  @Test
  fun `verify export patch history generates formatted log text and file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testList = listOf(
      com.example.data.PatchHistoryEntity(
        id = 1L,
        apkFileName = "retro_game.apk",
        packageName = "com.retro.game",
        appName = "Retro Game",
        versionName = "1.2.0",
        formattedDate = "2026-09-23 10:00:00",
        isSuccess = true,
        originalTargetSdk = 19,
        patchedTargetSdk = 28,
        signatureReplaced = true,
        compatibilityModeApplied = true,
        antiTamperHash = "abcdef0123456789",
        appliedFixesSummary = "TargetSDK Lifted, Signature Neutralized"
      )
    )

    val reportText = com.example.engine.DiagnosticLogExporter.generatePatchHistoryLogReport(context, testList)
    assertTrue("Report should contain title header", reportText.contains("ONEAPK STUDIO - COMPLETE PATCH OPERATION HISTORY"))
    assertTrue("Report should contain app name", reportText.contains("Retro Game"))
    assertTrue("Report should contain package", reportText.contains("com.retro.game"))
    assertTrue("Report should contain anti-tamper hash", reportText.contains("abcdef0123456789"))

    val file = com.example.engine.DiagnosticLogExporter.saveLogToFile(context, reportText, "test_history_export")
    assertTrue("Saved file should exist", file.exists())
    assertTrue("Saved file should not be empty", file.length() > 0)
  }

  @Test
  fun `verify patch history search and status filter`() = runBlocking {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val database = com.example.data.AppDatabase.getInstance(application)
    val dao = database.patchHistoryDao()

    val item1 = com.example.data.PatchHistoryEntity(
      apkFileName = "alpha_game.apk",
      packageName = "com.alpha.game",
      appName = "Alpha Game",
      versionName = "1.0",
      formattedDate = "Today 10:00 AM",
      isSuccess = true,
      originalTargetSdk = 19,
      patchedTargetSdk = 28
    )
    val item2 = com.example.data.PatchHistoryEntity(
      apkFileName = "beta_utility.apk",
      packageName = "com.beta.util",
      appName = "Beta Utility",
      versionName = "2.0",
      formattedDate = "Today 11:00 AM",
      isSuccess = false,
      failureReason = "Signature Parse Error",
      originalTargetSdk = 21,
      patchedTargetSdk = 21
    )

    val id1 = dao.insertHistory(item1)
    val id2 = dao.insertHistory(item2)

    val viewModel = com.example.ui.MainViewModel(application)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    kotlinx.coroutines.delay(400)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()

    // Test search by name
    viewModel.setPatchHistorySearchQuery("Alpha")
    kotlinx.coroutines.delay(200)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    val searchResults = viewModel.filteredPatchHistory.value
    assertTrue("Search for 'Alpha' should include Alpha Game", searchResults.any { it.appName == "Alpha Game" })
    assertFalse("Search for 'Alpha' should not include Beta Utility", searchResults.any { it.appName == "Beta Utility" })

    // Test filter by success status
    viewModel.setPatchHistorySearchQuery("")
    viewModel.setPatchHistoryFilter(com.example.ui.PatchHistoryStatusFilter.SUCCESS)
    kotlinx.coroutines.delay(200)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    val successResults = viewModel.filteredPatchHistory.value
    assertTrue("Success filter should only include successful patches", successResults.all { it.isSuccess })
    assertTrue("Success filter should include item1", successResults.any { it.id == id1 })

    // Test filter by failed status
    viewModel.setPatchHistoryFilter(com.example.ui.PatchHistoryStatusFilter.FAILED)
    kotlinx.coroutines.delay(200)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    val failedResults = viewModel.filteredPatchHistory.value
    assertTrue("Failed filter should only include failed patches", failedResults.all { !it.isSuccess })
    assertTrue("Failed filter should include item2", failedResults.any { it.id == id2 })

    // Clean up
    dao.deleteHistoryByIds(listOf(id1, id2))
  }

  @Test
  fun `verify bulk delete removes multiple records from database`() = runBlocking {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val database = com.example.data.AppDatabase.getInstance(application)
    val dao = database.patchHistoryDao()

    val item1 = com.example.data.PatchHistoryEntity(
      apkFileName = "bulk_1.apk",
      packageName = "com.bulk.one",
      appName = "Bulk App One",
      versionName = "1.0",
      formattedDate = "Today 10:00 AM",
      isSuccess = true,
      originalTargetSdk = 19,
      patchedTargetSdk = 28
    )
    val item2 = com.example.data.PatchHistoryEntity(
      apkFileName = "bulk_2.apk",
      packageName = "com.bulk.two",
      appName = "Bulk App Two",
      versionName = "2.0",
      formattedDate = "Today 11:00 AM",
      isSuccess = true,
      originalTargetSdk = 22,
      patchedTargetSdk = 28
    )
    val item3 = com.example.data.PatchHistoryEntity(
      apkFileName = "bulk_3.apk",
      packageName = "com.bulk.three",
      appName = "Bulk App Three",
      versionName = "3.0",
      formattedDate = "Today 12:00 PM",
      isSuccess = true,
      originalTargetSdk = 23,
      patchedTargetSdk = 28
    )

    val id1 = dao.insertHistory(item1)
    val id2 = dao.insertHistory(item2)
    val id3 = dao.insertHistory(item3)

    val viewModel = com.example.ui.MainViewModel(application)
    kotlinx.coroutines.delay(400)

    // Execute bulk delete for id1 and id2
    viewModel.deletePatchHistoryBatch(setOf(id1, id2))
    kotlinx.coroutines.delay(500)

    val remaining1 = dao.getHistoryById(id1)
    val remaining2 = dao.getHistoryById(id2)
    val remaining3 = dao.getHistoryById(id3)

    assertNull("Deleted item 1 should no longer exist", remaining1)
    assertNull("Deleted item 2 should no longer exist", remaining2)
    assertNotNull("Item 3 should still exist", remaining3)

    // Clean up item3
    dao.deleteHistoryById(id3)
  }
}
