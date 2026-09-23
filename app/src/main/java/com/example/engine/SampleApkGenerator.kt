package com.example.engine

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SampleApkPreset(
    val id: String,
    val title: String,
    val packageName: String,
    val versionName: String,
    val failureCause: String,
    val iconName: String,
    val targetSdk: Int,
    val minSdk: Int,
    val hasAbis: Boolean,
    val isInfected: Boolean = false,
    val threatName: String? = null,
    val threatDetails: String? = null
)

object SampleApkGenerator {

    val PRESETS = listOf(
        SampleApkPreset(
            id = "flappy_legacy",
            title = "Flappy Retro Classic",
            packageName = "com.dotgears.flappyretro",
            versionName = "1.2.0",
            failureCause = "targetSdk 19 (Android 14+ blocks install) + Signature Key Conflict",
            iconName = "gamepad",
            targetSdk = 19,
            minSdk = 14,
            hasAbis = false
        ),
        SampleApkPreset(
            id = "quick_scanner",
            title = "QuickScanner Pro",
            packageName = "com.utility.quickscanner",
            versionName = "2.4.1",
            failureCause = "Conflicting Provider Authority + Deprecated sharedUserId",
            iconName = "document",
            targetSdk = 22,
            minSdk = 19,
            hasAbis = false
        ),
        SampleApkPreset(
            id = "retro_racer",
            title = "Retro Pixel Racer 3D",
            packageName = "com.arcade.retroracer",
            versionName = "1.0.4",
            failureCause = "Outdated 32-bit ABI (armeabi-v7a) on 64-bit device + Signature Conflict",
            iconName = "speed",
            targetSdk = 21,
            minSdk = 16,
            hasAbis = true
        ),
        SampleApkPreset(
            id = "infected_trojan_sample",
            title = "Cleaner Master [Infected Demo]",
            packageName = "com.trojan.cleanmaster.fake",
            versionName = "2.1.8",
            failureCause = "CRITICAL: Trojan.AndroidOS.FakeClean payload + Permission Exploit detected",
            iconName = "warning",
            targetSdk = 22,
            minSdk = 19,
            hasAbis = false,
            isInfected = true,
            threatName = "Trojan.AndroidOS.FakeClean.A",
            threatDetails = "Contains hidden payload (assets/payload.bin) with unauthorized background SMS interception, fake system overlay injection, and permission privilege escalation signatures."
        )
    )

    fun createPresetApk(context: Context, preset: SampleApkPreset): File {
        val dir = File(context.cacheDir, "sample_apks").apply { mkdirs() }
        val file = File(dir, "${preset.packageName}.apk")

        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. AndroidManifest.xml (binary header simulation containing package name)
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                val manifestContent = buildBinaryManifestStub(preset.packageName, preset.targetSdk, preset.minSdk, preset.isInfected)
                zos.write(manifestContent)
                zos.closeEntry()

                // 2. classes.dex
                zos.putNextEntry(ZipEntry("classes.dex"))
                val dexStub = "dex\n035\u0000OneAPK_DEX_STUB_BYTECODE_CONTENT_${preset.packageName}".toByteArray()
                zos.write(dexStub)
                zos.closeEntry()

                // 3. resources.arsc
                zos.putNextEntry(ZipEntry("resources.arsc"))
                val arscStub = "ARSC_HEADER_${preset.title}".toByteArray()
                zos.write(arscStub)
                zos.closeEntry()

                // 4. Native libs if applicable
                if (preset.hasAbis) {
                    zos.putNextEntry(ZipEntry("lib/armeabi-v7a/libgame_engine.so"))
                    zos.write("ELF_32_BIT_ARM_LIB".toByteArray())
                    zos.closeEntry()
                }

                // If infected, inject payload markers
                if (preset.isInfected) {
                    zos.putNextEntry(ZipEntry("assets/payload.bin"))
                    zos.write("MALWARE_INJECTED_PAYLOAD_TROJAN_EICAR_BYTECODE_SUSPICIOUS".toByteArray())
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("assets/threat_signature.txt"))
                    zos.write("THREAT_TYPE=TROJAN_BANKING_OVERLAY\nRISK_LEVEL=CRITICAL".toByteArray())
                    zos.closeEntry()
                }

                // 5. Old colliding META-INF signatures
                zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                zos.write("Manifest-Version: 1.0\nCreated-By: IncompatibleOldTool 2.0\n\n".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                zos.write("OLD_FOREIGN_CERTIFICATE_KEY_BYTES_MISMATCH".toByteArray())
                zos.closeEntry()
            }
        }

        return file
    }

    fun createValidPreloadedApk(context: Context): Pair<File, String> {
        val dir = File(context.filesDir, "apks").apply { mkdirs() }
        val file = File(dir, "galaxy_retro_supported.apk")

        if (!file.exists() || file.length() == 0L) {
            FileOutputStream(file).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                    zos.write(buildBinaryManifestStub("com.arcade.galaxyretro", 28, 24, false))
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("classes.dex"))
                    zos.write("dex\n035\u0000OneAPK_PATCHED_DEX_GALAXY_RETRO".toByteArray())
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("resources.arsc"))
                    zos.write("ARSC_GALAXY_RETRO_1999".toByteArray())
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                    zos.write("Manifest-Version: 1.0\nCreated-By: OneAPK Studio\n\n".toByteArray())
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
                    zos.write("Signature-Version: 1.0\nCreated-By: OneAPK Security\n\n".toByteArray())
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                    zos.write("OneAPK_SIGNATURE_KEY_CONTAINER_VERIFIED".toByteArray())
                    zos.closeEntry()
                }
            }
        }

        val hash = AntiTamperVerifier.calculateSha256(file)
        return Pair(file, hash)
    }

    private fun buildBinaryManifestStub(packageName: String, targetSdk: Int, minSdk: Int, isInfected: Boolean = false): ByteArray {
        val sb = StringBuilder()
        sb.append("<manifest package=\"$packageName\" ")
        sb.append("minSdkVersion=\"$minSdk\" targetSdkVersion=\"$targetSdk\">\n")
        if (isInfected) {
            sb.append("  <uses-permission android:name=\"android.permission.SEND_SMS\" />\n")
            sb.append("  <uses-permission android:name=\"android.permission.RECEIVE_SMS\" />\n")
            sb.append("  <uses-permission android:name=\"android.permission.SYSTEM_ALERT_WINDOW\" />\n")
            sb.append("  <uses-permission android:name=\"android.permission.BIND_ACCESSIBILITY_SERVICE\" />\n")
        }
        sb.append("  <application label=\"$packageName\">\n")
        sb.append("    <activity name=\".MainActivity\" />\n")
        if (packageName.contains("scanner")) {
            sb.append("    <provider authority=\"$packageName.provider\" />\n")
            sb.append("    <shared-user-id name=\"android.uid.system\" />\n")
        }
        sb.append("  </application>\n")
        sb.append("</manifest>")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}
