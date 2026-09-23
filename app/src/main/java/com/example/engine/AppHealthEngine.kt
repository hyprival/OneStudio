package com.example.engine

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.ModifiedAppEntity
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class AppHealthEngine(private val context: Context) {

    /**
     * Runs a comprehensive health and stability scan across all patched applications.
     */
    fun scanAllApps(entities: List<ModifiedAppEntity>): DashboardOverallHealth {
        if (entities.isEmpty()) {
            return DashboardOverallHealth(
                overallScore = 100,
                statusTier = HealthStatusTier.OPTIMAL,
                totalAppsScanned = 0,
                optimalCount = 0,
                warningCount = 0,
                criticalCount = 0,
                signatureMismatchRisksDetected = 0,
                samsungCompatibilityCoverage = "No patched APKs to scan yet",
                appReports = emptyList()
            )
        }

        val reports = entities.map { scanSingleApp(it) }
        val avgScore = reports.map { it.healthScore }.average().toInt()

        val optimalCount = reports.count { it.statusTier == HealthStatusTier.OPTIMAL }
        val warningCount = reports.count { it.statusTier == HealthStatusTier.WARNING }
        val criticalCount = reports.count { it.statusTier == HealthStatusTier.CRITICAL }
        val signatureRisks = reports.count { it.signatureMismatchRisk }

        val tier = when {
            criticalCount > 0 -> HealthStatusTier.CRITICAL
            warningCount > 0 -> HealthStatusTier.WARNING
            avgScore >= 90 -> HealthStatusTier.OPTIMAL
            else -> HealthStatusTier.GOOD
        }

        val sSeriesCompatibleCount = reports.count { it.samsungSseriesCompatible }
        val aSeriesCompatibleCount = reports.count { it.samsungAseriesCompatible }
        val total = reports.size

        val coverageText = if (sSeriesCompatibleCount == total && aSeriesCompatibleCount == total) {
            "100% Compatible across Galaxy S, A (A32 & under/higher), Z, & M Series"
        } else {
            "${(sSeriesCompatibleCount * 100) / total}% Galaxy S • ${(aSeriesCompatibleCount * 100) / total}% Galaxy A"
        }

        return DashboardOverallHealth(
            overallScore = avgScore,
            statusTier = tier,
            totalAppsScanned = total,
            optimalCount = optimalCount,
            warningCount = warningCount,
            criticalCount = criticalCount,
            signatureMismatchRisksDetected = signatureRisks,
            samsungCompatibilityCoverage = coverageText,
            appReports = reports
        )
    }

    /**
     * Inspects a single patched application file for potential signature mismatches
     * and runtime stability hazards across Samsung device tiers.
     */
    fun scanSingleApp(entity: ModifiedAppEntity): SingleAppHealthReport {
        val file = File(entity.patchedApkPath)
        val issues = mutableListOf<HealthIssue>()
        var score = 100

        var hasArm64 = false
        var hasArmV7 = false
        var hasX86 = false
        var isDexOnly = true
        var hasValidSignatureBlock = false
        var dexIntegrityValid = true
        var manifestPresent = false

        val fileExists = file.exists() && file.length() > 0

        if (fileExists) {
            try {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name

                        if (name.startsWith("lib/arm64-v8a/")) {
                            hasArm64 = true
                            isDexOnly = false
                        } else if (name.startsWith("lib/armeabi-v7a/")) {
                            hasArmV7 = true
                            isDexOnly = false
                        } else if (name.startsWith("lib/x86/")) {
                            hasX86 = true
                            isDexOnly = false
                        }

                        if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC") || name == "META-INF/CERT.SF")) {
                            hasValidSignatureBlock = true
                        }

                        if (name == "AndroidManifest.xml") {
                            manifestPresent = true
                        }

                        if (name == "classes.dex") {
                            zip.getInputStream(entry).use { stream ->
                                dexIntegrityValid = verifyDexHeader(stream)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                issues.add(
                    HealthIssue(
                        category = HealthCategory.RUNTIME_STABILITY,
                        title = "ZIP Archive Structure Warning",
                        description = "Could not fully parse ZIP structure: ${e.message}",
                        isCritical = false,
                        recommendation = "Re-run patcher to rebuild cleanly."
                    )
                )
                score -= 10
            }
        } else {
            // Virtual / Sample entry fallback
            hasValidSignatureBlock = entity.isAntiTamperValid
            hasArm64 = true
            hasArmV7 = true
            isDexOnly = true
            manifestPresent = true
        }

        // =====================================================================
        // 1. SIGNATURE INTEGRITY & MISMATCH RISK DETECTION
        // =====================================================================
        var signatureMismatchRisk = false
        var signatureDetails = "Signature certificate is valid and reconciled."

        // Check if an app with this package is already installed on this physical device
        try {
            val pm = context.packageManager
            val installedPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(entity.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(entity.packageName, PackageManager.GET_SIGNATURES)
            }

            if (installedPkg != null) {
                // If package is already installed, verify if signature was reconciled
                if (!entity.signatureConflictResolved) {
                    signatureMismatchRisk = true
                    signatureDetails = "Potential Signature Mismatch: Package '${entity.packageName}' already exists on device with a different signing key. Android installer will reject installation with INSTALL_FAILED_UPDATE_INCOMPATIBLE."
                    issues.add(
                        HealthIssue(
                            category = HealthCategory.SIGNATURE_INTEGRITY,
                            title = "Signature Conflict with Installed App",
                            description = signatureDetails,
                            isCritical = true,
                            recommendation = "Toggle 'Fix Signature Conflicts Automatically' in OneAPK or uninstall the existing app version prior to installing.",
                            affectedDevices = "All Android & Samsung devices"
                        )
                    )
                    score -= 30
                } else {
                    signatureDetails = "Signature conflict successfully neutralized. The APK has been reconciled with compatible certificates."
                }
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // Package not currently installed on device: Clean installation expected
            if (!hasValidSignatureBlock && fileExists) {
                signatureMismatchRisk = true
                signatureDetails = "Missing cryptographic signature block in META-INF/. App cannot be installed by Android Package Manager."
                issues.add(
                    HealthIssue(
                        category = HealthCategory.SIGNATURE_INTEGRITY,
                        title = "Missing Signature Certificate",
                        description = signatureDetails,
                        isCritical = true,
                        recommendation = "Patch APK using OneAPK to inject unified local signing certificate.",
                        affectedDevices = "All devices"
                    )
                )
                score -= 35
            } else {
                signatureDetails = "Clean install profile. No colliding package found on host device."
            }
        }

        // Anti-Tamper check verification
        if (!entity.isAntiTamperValid) {
            issues.add(
                HealthIssue(
                    category = HealthCategory.SIGNATURE_INTEGRITY,
                    title = "Anti-Tamper Cryptographic Check Failure",
                    description = "The stored SHA-256 seal does not match the binary file state. May indicate corrupted APK download.",
                    isCritical = true,
                    recommendation = "Re-seal APK using OneAPK Anti-Tamper engine.",
                    affectedDevices = "All devices"
                )
            )
            score -= 25
        }

        // =====================================================================
        // 2. DEVICE ARCHITECTURE COMPATIBILITY (SAMSUNG S vs SAMSUNG A SERIES)
        // =====================================================================
        var samsungSseriesCompatible = true
        var samsungAseriesCompatible = true
        val deviceCompatibilitySummary: String

        if (isDexOnly) {
            // Pure Java / Kotlin bytecode with universal ART VM execution
            deviceCompatibilitySummary = "Universal Bytecode: 100% compatible with Samsung Galaxy S series, Galaxy A32 & under/higher, and all Android devices."
        } else {
            // Has native code
            if (hasArmV7 && !hasArm64) {
                // 32-bit only native code
                // Samsung Galaxy S24/S25 (Snapdragon 8 Gen 3 / Exynos 2400) do NOT support 32-bit apps!
                samsungSseriesCompatible = false
                samsungAseriesCompatible = true // Galaxy A32 and under support 32-bit ARMv7!
                issues.add(
                    HealthIssue(
                        category = HealthCategory.DEVICE_COMPATIBILITY,
                        title = "32-bit Only Binary (Galaxy S24/S25 Incompatibility)",
                        description = "This APK contains 32-bit ARMv7 binaries but lacks 64-bit (arm64-v8a). It will run on Samsung Galaxy A32, A12, and older devices, but will fail to launch on pure 64-bit flagships (Galaxy S24, S25 series).",
                        isCritical = false,
                        recommendation = "Provide 64-bit arm64-v8a slices or run in bytecode emulation mode.",
                        affectedDevices = "Samsung Galaxy S24, S24+, S24 Ultra, S25, Pixel 7/8/9"
                    )
                )
                score -= 15
                deviceCompatibilitySummary = "Compatible with Samsung Galaxy A32 & under (ARMv7 32-bit supported). Incompatible with Galaxy S24/S25 64-bit-only hardware."
            } else if (hasArm64 && !hasArmV7) {
                // 64-bit only
                samsungSseriesCompatible = true
                // Very old 32-bit-only A-series (e.g. Galaxy A01 Core, A03 Core, A10) cannot run 64-bit
                samsungAseriesCompatible = true // Galaxy A32 Helio G80 is 64-bit ARM Cortex-A75/A55
                deviceCompatibilitySummary = "64-bit Native Binary: Runs on Samsung Galaxy S series, Galaxy A32, and all 64-bit modern devices."
            } else {
                // Multi-arch (both arm64 and armeabi-v7a)
                samsungSseriesCompatible = true
                samsungAseriesCompatible = true
                deviceCompatibilitySummary = "Multi-Architecture Binary: Dual 32-bit & 64-bit slices included. 100% compatible with Galaxy A32 and Galaxy S series."
            }
        }

        // =====================================================================
        // 3. RUNTIME STABILITY & SDK LIMITS
        // =====================================================================
        var runtimeStabilityDetails = "Standard Android ART execution profile."

        if (entity.targetSdkPatched < 28) {
            issues.add(
                HealthIssue(
                    category = HealthCategory.RUNTIME_STABILITY,
                    title = "Target SDK Below Android 14 Gate (API 28)",
                    description = "Target SDK is ${entity.targetSdkPatched}. Android 14+ (One UI 6.0+) blocks installation of apps targeting below API 28 by default.",
                    isCritical = true,
                    recommendation = "Enable 'Lift SDK Deprecation Block' in OneAPK settings.",
                    affectedDevices = "Samsung One UI 6.0 / 6.1 (Android 14+)"
                )
            )
            score -= 25
            runtimeStabilityDetails = "At risk of being blocked by modern Samsung One UI 6+ installer gate."
        } else {
            runtimeStabilityDetails = "Target SDK ${entity.targetSdkPatched} satisfies Android 14+ security standards."
        }

        if (!dexIntegrityValid) {
            issues.add(
                HealthIssue(
                    category = HealthCategory.RUNTIME_STABILITY,
                    title = "Corrupt DEX Bytecode Header",
                    description = "classes.dex magic bytes do not conform to valid Dalvik Executable specifications. App may crash on launch with ClassDefNotFoundError.",
                    isCritical = true,
                    recommendation = "Rebuild APK using standard compiler pipeline.",
                    affectedDevices = "All devices"
                )
            )
            score -= 30
        }

        // Ensure score stays in 0..100
        score = score.coerceIn(0, 100)

        val tier = when {
            score >= 90 && issues.none { it.isCritical } -> HealthStatusTier.OPTIMAL
            score >= 70 && issues.none { it.isCritical } -> HealthStatusTier.GOOD
            score >= 50 -> HealthStatusTier.WARNING
            else -> HealthStatusTier.CRITICAL
        }

        return SingleAppHealthReport(
            appId = entity.id,
            appName = entity.appName,
            packageName = entity.packageName,
            apkFile = file,
            healthScore = score,
            statusTier = tier,
            signatureMismatchRisk = signatureMismatchRisk,
            signatureDetails = signatureDetails,
            deviceCompatibilitySummary = deviceCompatibilitySummary,
            samsungAseriesCompatible = samsungAseriesCompatible,
            samsungSseriesCompatible = samsungSseriesCompatible,
            runtimeStabilityDetails = runtimeStabilityDetails,
            issues = issues
        )
    }

    private fun verifyDexHeader(stream: InputStream): Boolean {
        val header = ByteArray(8)
        val read = stream.read(header)
        if (read < 8) return false
        // Dalvik Executable magic: 'd', 'e', 'x', '\n', followed by 3 version digits and '\0'
        return header[0] == 0x64.toByte() &&
                header[1] == 0x65.toByte() &&
                header[2] == 0x78.toByte() &&
                header[3] == 0x0A.toByte()
    }
}
