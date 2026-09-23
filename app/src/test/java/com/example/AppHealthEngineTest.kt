package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ModifiedAppEntity
import com.example.engine.AppHealthEngine
import com.example.engine.HealthStatusTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppHealthEngineTest {

    private lateinit var context: Context
    private lateinit var healthEngine: AppHealthEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        healthEngine = AppHealthEngine(context)
    }

    private fun createTestEntity(
        id: Long = 0,
        appName: String = "Test App",
        packageName: String = "com.test.app",
        targetSdkOriginal: Int = 24,
        targetSdkPatched: Int = 34,
        signatureConflictResolved: Boolean = true,
        isAntiTamperValid: Boolean = true
    ): ModifiedAppEntity {
        return ModifiedAppEntity(
            id = id,
            packageName = packageName,
            appName = appName,
            versionName = "1.0.0",
            versionCode = 1L,
            originalApkName = "app-original.apk",
            patchedApkPath = "/dummy/patched.apk",
            fileSizeBytes = 1024L,
            minSdkOriginal = 21,
            minSdkPatched = 24,
            targetSdkOriginal = targetSdkOriginal,
            targetSdkPatched = targetSdkPatched,
            signatureConflictResolved = signatureConflictResolved,
            antiTamperHash = "abcdef1234567890",
            isAntiTamperValid = isAntiTamperValid,
            issuesFoundJson = "[]",
            fixesAppliedJson = "[]",
            plainEnglishSummary = "Test Summary",
            diagnosticLogText = "Log",
            updateVersionNote = "None"
        )
    }

    @Test
    fun testScanAllAppsEmptyReturnsOptimal() {
        val overall = healthEngine.scanAllApps(emptyList())
        assertEquals(100, overall.overallScore)
        assertEquals(HealthStatusTier.OPTIMAL, overall.statusTier)
        assertEquals(0, overall.totalAppsScanned)
    }

    @Test
    fun testScanSingleAppOptimalHealth() {
        val app = createTestEntity(
            appName = "Samsung Optimizer",
            packageName = "com.samsung.sample.optimizer",
            targetSdkOriginal = 24,
            targetSdkPatched = 34,
            signatureConflictResolved = true,
            isAntiTamperValid = true
        )

        val report = healthEngine.scanSingleApp(app)
        assertNotNull(report)
        assertEquals(100, report.healthScore)
        assertEquals(HealthStatusTier.OPTIMAL, report.statusTier)
        assertEquals(false, report.signatureMismatchRisk)
        assertTrue(report.samsungSseriesCompatible)
        assertTrue(report.samsungAseriesCompatible)
    }

    @Test
    fun testScanAppWithSignatureMismatchRisk() {
        val app = createTestEntity(
            appName = "Conflicting App",
            packageName = context.packageName, // host app package exists in Robolectric context
            targetSdkOriginal = 26,
            targetSdkPatched = 34,
            signatureConflictResolved = false, // Not reconciled!
            isAntiTamperValid = true
        )

        val report = healthEngine.scanSingleApp(app)
        assertTrue(report.signatureMismatchRisk)
        assertTrue(report.healthScore < 100)
    }

    @Test
    fun testScanOverallHealthWithMultipleApps() {
        val app1 = createTestEntity(
            id = 1L,
            appName = "Clean Utility",
            packageName = "com.test.clean",
            targetSdkOriginal = 30,
            targetSdkPatched = 34,
            signatureConflictResolved = true,
            isAntiTamperValid = true
        )

        val app2 = createTestEntity(
            id = 2L,
            appName = "Legacy App",
            packageName = "com.test.legacy",
            targetSdkOriginal = 22,
            targetSdkPatched = 25, // Below Android 14 API 28 gate
            signatureConflictResolved = true,
            isAntiTamperValid = true
        )

        val overall = healthEngine.scanAllApps(listOf(app1, app2))
        assertEquals(2, overall.totalAppsScanned)
        assertTrue(overall.overallScore in 50..95)
    }
}
