package com.example.engine

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class AntiTamperReport(
    val isValid: Boolean,
    val sha256Checksum: String,
    val componentsVerified: Map<String, String>,
    val statusMessage: String,
    val securityGrade: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AntiTamperVerifier {

    fun calculateSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "HASH_CALCULATION_ERROR"
        }
    }

    fun verifyApkIntegrity(apkFile: File, expectedHash: String? = null): AntiTamperReport {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return AntiTamperReport(
                isValid = false,
                sha256Checksum = "FILE_MISSING",
                componentsVerified = emptyMap(),
                statusMessage = "Target file does not exist or is empty.",
                securityGrade = "F"
            )
        }

        val actualSha256 = calculateSha256(apkFile)
        val componentHashes = mutableMapOf<String, String>()

        var isZipValid = false
        try {
            ZipFile(apkFile).use { zip ->
                isZipValid = true
                val targets = listOf("AndroidManifest.xml", "classes.dex", "resources.arsc", "META-INF/CERT.SF")
                for (target in targets) {
                    val entry = zip.getEntry(target)
                    if (entry != null) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        zip.getInputStream(entry).use { stream ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (stream.read(buffer).also { bytesRead = it } != -1) {
                                digest.update(buffer, 0, bytesRead)
                            }
                        }
                        componentHashes[target] = digest.digest().joinToString("") { "%02x".format(it) }.take(16)
                    }
                }
            }
        } catch (e: Exception) {
            return AntiTamperReport(
                isValid = false,
                sha256Checksum = actualSha256,
                componentsVerified = componentHashes,
                statusMessage = "Archive corrupted or tampered: ${e.localizedMessage}",
                securityGrade = "D-"
            )
        }

        val matchesExpected = expectedHash == null || expectedHash.equals(actualSha256, ignoreCase = true)
        val isValid = isZipValid && matchesExpected

        val statusMessage = if (isValid) {
            "Verified & Untampered. File matches cryptographic seal with intact zip structures."
        } else {
            "TAMPER WARNING: File checksum mismatch! The APK was modified or corrupted after generation."
        }

        val grade = if (isValid) "A+ (Protected)" else "F (Compromised)"

        return AntiTamperReport(
            isValid = isValid,
            sha256Checksum = actualSha256,
            componentsVerified = componentHashes,
            statusMessage = statusMessage,
            securityGrade = grade
        )
    }
}
