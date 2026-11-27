package com.example.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

data class ApkVerificationResult(
    val passed: Boolean,
    val sha256Verified: Boolean,
    val signatureVerified: Boolean,
    val sha256Actual: String,
    val summary: String,
)

object UpdateSecurityVerifier {

    private const val STREAM_BUFFER_SIZE = 8192

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    fun verifyChecksum(file: File, expectedSha256Hex: String): Pair<Boolean, String> {
        if (!file.exists() || file.length() <= 0L) {
            return false to ""
        }
        val normalizedExpected = expectedSha256Hex.trim().lowercase(Locale.ROOT)
        if (!UpdateManifestParser.isValidSha256Hex(normalizedExpected)) {
            return false to ""
        }
        val actual = computeSha256(file).lowercase(Locale.ROOT)
        return (actual == normalizedExpected) to actual
    }

    fun verifyDownloadedApk(
        context: Context,
        apkFile: File,
        expectedSha256Hex: String,
        expectedMinVersionCode: Int,
    ): ApkVerificationResult {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return ApkVerificationResult(
                passed = false,
                sha256Verified = false,
                signatureVerified = false,
                sha256Actual = "",
                summary = "Downloaded APK file is missing or empty.",
            )
        }

        val (shaValid, actualSha) = verifyChecksum(apkFile, expectedSha256Hex)
        if (!shaValid) {
            return ApkVerificationResult(
                passed = false,
                sha256Verified = false,
                signatureVerified = false,
                sha256Actual = actualSha,
                summary = "SHA-256 checksum mismatch. File may be corrupted or tampered.",
            )
        }

        val pm = context.packageManager
        val archiveInfo = getArchivePackageInfo(pm, apkFile.absolutePath)
            ?: return ApkVerificationResult(
                passed = false,
                sha256Verified = true,
                signatureVerified = false,
                sha256Actual = actualSha,
                summary = "Unable to parse APK package archive metadata.",
            )

        if (archiveInfo.packageName != context.packageName) {
            return ApkVerificationResult(
                passed = false,
                sha256Verified = true,
                signatureVerified = false,
                sha256Actual = actualSha,
                summary = "Package ID mismatch (${archiveInfo.packageName} != ${context.packageName}).",
            )
        }

        val archiveVersionCode = extractLongVersionCode(archiveInfo)
        if (archiveVersionCode < expectedMinVersionCode.toLong()) {
            return ApkVerificationResult(
                passed = false,
                sha256Verified = true,
                signatureVerified = false,
                sha256Actual = actualSha,
                summary = "APK versionCode ($archiveVersionCode) is lower than manifest ($expectedMinVersionCode).",
            )
        }

        val installedInfo = getInstalledPackageInfo(pm, context.packageName)
            ?: return ApkVerificationResult(
                passed = false,
                sha256Verified = true,
                signatureVerified = false,
                sha256Actual = actualSha,
                summary = "Unable to inspect installed KIKO AI signing certificates.",
            )

        val installedCertDigests = extractSigningCertificateSha256s(installedInfo)
        val archiveCertDigests = extractSigningCertificateSha256s(archiveInfo)

        val signatureMatches = installedCertDigests.isNotEmpty() &&
            archiveCertDigests.isNotEmpty() &&
            archiveCertDigests.any { it in installedCertDigests }

        if (!signatureMatches) {
            return ApkVerificationResult(
                passed = false,
                sha256Verified = true,
                signatureVerified = false,
                sha256Actual = actualSha,
                summary = "APK signing certificate does not match the installed KIKO AI signature.",
            )
        }

        return ApkVerificationResult(
            passed = true,
            sha256Verified = true,
            signatureVerified = true,
            sha256Actual = actualSha,
            summary = "SHA-256 & signing certificate verified (${actualSha.take(12)}…).",
        )
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(pm: PackageManager, archivePath: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                archivePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfo(pm: PackageManager, packageName: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun extractLongVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }

    @Suppress("DEPRECATION")
    private fun extractSigningCertificateSha256s(info: PackageInfo): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            info.signatures ?: emptyArray()
        }

        val md = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
