package com.example.update

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Locale

enum class UpdateType(val wireValue: String) {
    OPTIONAL("OPTIONAL"),
    CRITICAL("CRITICAL");

    companion object {
        fun fromWire(value: String?): UpdateType =
            when (value?.trim()?.uppercase(Locale.ROOT)) {
                "CRITICAL", "MANDATORY", "REQUIRED", "FORCE" -> CRITICAL
                else -> OPTIONAL
            }
    }
}

enum class UpdateStatusLabel(val displayText: String) {
    NEW_UPDATE_AVAILABLE("New Update Available"),
    DOWNLOADING_UPDATE("Downloading Update"),
    DOWNLOAD_COMPLETED("Download Completed"),
    READY_TO_INSTALL("Update Ready to Install"),
    SUCCESSFULLY_INSTALLED("Update Successfully Installed"),
    LATEST_VERSION("You're Using the Latest Version"),
    FAILED_RETRY("Update Failed – Retry"),
    IDLE("Update Check Idle"),
    CHECKING("Checking for Updates…"),
    VERIFYING("Verifying SHA-256 & Package Signature…"),
}

data class UpdateManifest(
    val latestVersion: String,
    val versionCode: Int,
    val apkDownloadUrl: String,
    val releaseNotes: String,
    val minSupportedVersion: Int,
    val updateType: UpdateType,
    val sha256Checksum: String,
    val releaseDate: String,
    val sha256AssetUrl: String? = null,
    val sourceLabel: String = "KIKO Update Server",
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState

    data class UpToDate(
        val versionName: String,
        val versionCode: Int,
    ) : UpdateUiState

    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val isMandatory: Boolean,
        val partialBytes: Long,
        val hasInstallPermission: Boolean,
    ) : UpdateUiState

    data class Downloading(
        val manifest: UpdateManifest,
        val isMandatory: Boolean,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isPaused: Boolean,
    ) : UpdateUiState

    data class Verifying(
        val manifest: UpdateManifest,
        val isMandatory: Boolean,
    ) : UpdateUiState

    data class ReadyToInstall(
        val manifest: UpdateManifest,
        val isMandatory: Boolean,
        val apkFilePath: String,
        val sha256Verified: Boolean,
        val signatureVerified: Boolean,
        val verificationDetail: String,
        val hasInstallPermission: Boolean,
    ) : UpdateUiState

    data class InstalledSuccess(
        val versionName: String,
        val versionCode: Int,
    ) : UpdateUiState

    data class Failed(
        val message: String,
        val canResume: Boolean,
        val manifest: UpdateManifest? = null,
        val isMandatory: Boolean = false,
    ) : UpdateUiState
}

object UpdateManifestParser {

    private val moshi: Moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
    private val BODY_SHA256_REGEX = Regex(
        pattern = "(?:SHA-?256|CHECKSUM)\\s*[:=]\\s*([a-fA-F0-9]{64})",
        option = RegexOption.IGNORE_CASE,
    )
    private val BODY_VERSION_CODE_REGEX = Regex(
        pattern = "(?:VERSION_CODE|BUILD_NUMBER)\\s*[:=]\\s*(\\d+)",
        option = RegexOption.IGNORE_CASE,
    )
    private val BODY_MIN_VERSION_REGEX = Regex(
        pattern = "(?:MIN_SUPPORTED_VERSION|MIN_VERSION)\\s*[:=]\\s*(\\d+)",
        option = RegexOption.IGNORE_CASE,
    )
    private val BODY_UPDATE_TYPE_REGEX = Regex(
        pattern = "(?:UPDATE_TYPE|PRIORITY)\\s*[:=]\\s*(OPTIONAL|CRITICAL|MANDATORY)",
        option = RegexOption.IGNORE_CASE,
    )

    fun isSecureHttpsUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("https://", ignoreCase = true) && trimmed.length > "https://".length
    }

    /**
     * Accepts a direct HTTPS manifest URL, a GitHub Releases API URL, or a standard
     * GitHub repository URL (`https://github.com/owner/repo`) and normalizes it to HTTPS.
     */
    fun normalizeEndpointUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        val githubRepoRegex = Regex("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git|/releases.*)?/?$", RegexOption.IGNORE_CASE)
        val match = githubRepoRegex.find(trimmed)
        if (match != null) {
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            return "https://api.github.com/repos/$owner/$repo/releases/latest"
        }
        return trimmed
    }

    fun isUpdateAvailable(manifest: UpdateManifest, installedVersionCode: Int): Boolean =
        manifest.versionCode > installedVersionCode

    fun isMandatoryUpdate(manifest: UpdateManifest, installedVersionCode: Int): Boolean =
        manifest.updateType == UpdateType.CRITICAL || installedVersionCode < manifest.minSupportedVersion

    fun isValidSha256Hex(checksum: String): Boolean =
        SHA256_REGEX.matches(checksum.trim())

    fun parseManifestJson(rawJson: String): Result<UpdateManifest> = runCatching {
        val root = mapAdapter.fromJson(rawJson.trim())
            ?: error("Manifest JSON is empty or malformed.")
        if (root.containsKey("tag_name") && root.containsKey("assets")) {
            parseGitHubRelease(root)
        } else {
            parseKikoServerManifest(root)
        }
    }

    private fun parseKikoServerManifest(root: Map<String, Any?>): UpdateManifest {
        val latestVersion = root.str("latestVersion").ifBlank { root.str("latest_version") }
        val versionCode = root.intOrZero("versionCode").takeIf { it > 0 } ?: root.intOrZero("version_code")
        val apkDownloadUrl = root.str("apkDownloadUrl").ifBlank { root.str("apk_download_url") }
        val releaseNotes = root.str("releaseNotes").ifBlank { root.str("release_notes") }
            .ifBlank { "Performance, stability, and security improvements." }
        val minSupportedVersion = root.intOrZero("minSupportedVersion")
            .takeIf { it > 0 }
            ?: root.intOrZero("min_supported_version").takeIf { it > 0 }
            ?: 1
        val updateType = UpdateType.fromWire(
            root.str("updateType").ifBlank { root.str("update_type") }
        )
        val sha256Checksum = root.str("sha256Checksum").ifBlank { root.str("sha256_checksum") }
            .lowercase(Locale.ROOT)
        val releaseDate = root.str("releaseDate").ifBlank { root.str("release_date") }
            .ifBlank { "Unspecified" }

        require(latestVersion.isNotBlank()) { "Manifest missing latestVersion." }
        require(versionCode > 0) { "Manifest missing valid positive versionCode." }
        require(isSecureHttpsUrl(apkDownloadUrl)) { "APK download URL must use secure HTTPS." }
        require(isValidSha256Hex(sha256Checksum)) { "Manifest missing valid 64-character SHA-256 checksum." }

        return UpdateManifest(
            latestVersion = latestVersion,
            versionCode = versionCode,
            apkDownloadUrl = apkDownloadUrl,
            releaseNotes = releaseNotes,
            minSupportedVersion = minSupportedVersion.coerceAtLeast(1),
            updateType = updateType,
            sha256Checksum = sha256Checksum,
            releaseDate = releaseDate,
            sourceLabel = "KIKO Update Server",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseGitHubRelease(root: Map<String, Any?>): UpdateManifest {
        val tagName = root.str("tag_name")
        val latestVersion = tagName.removePrefix("v").removePrefix("V").ifBlank { root.str("name") }
        val body = root.str("body")
        val assets = (root["assets"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

        var apkUrl = ""
        var apkName = ""
        var shaAssetUrl: String? = null
        var inlineDigestSha256 = ""

        for (asset in assets) {
            val name = asset.str("name")
            val url = asset.str("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && apkUrl.isEmpty()) {
                apkUrl = url
                apkName = name
                val digestField = asset.str("digest")
                if (digestField.startsWith("sha256:", ignoreCase = true)) {
                    inlineDigestSha256 = digestField.substringAfter(":").trim().lowercase(Locale.ROOT)
                }
            } else if ((name.endsWith(".sha256", ignoreCase = true) || name.equals("checksums.txt", ignoreCase = true)) && shaAssetUrl == null) {
                shaAssetUrl = url
            }
        }

        require(latestVersion.isNotBlank()) { "GitHub release missing tag_name." }
        require(isSecureHttpsUrl(apkUrl)) { "GitHub release does not contain an HTTPS .apk asset." }

        val versionCode = BODY_VERSION_CODE_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(?:vc|build|code)[_-]?(\\d+)", RegexOption.IGNORE_CASE)
                .find(apkName)?.groupValues?.get(1)?.toIntOrNull()
            ?: parseSemverToCode(latestVersion)

        require(versionCode > 0) { "Could not resolve versionCode from GitHub release." }

        val minSupported = BODY_MIN_VERSION_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val updateType = UpdateType.fromWire(
            BODY_UPDATE_TYPE_REGEX.find(body)?.groupValues?.get(1)
        )
        val bodySha256 = BODY_SHA256_REGEX.find(body)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        val resolvedSha256 = when {
            isValidSha256Hex(inlineDigestSha256) -> inlineDigestSha256
            bodySha256 != null && isValidSha256Hex(bodySha256) -> bodySha256
            else -> ""
        }

        require(isValidSha256Hex(resolvedSha256) || (shaAssetUrl != null && isSecureHttpsUrl(shaAssetUrl))) {
            "GitHub release must include SHA-256 checksum in release notes (SHA256: <hex>), asset digest, or a .sha256 asset."
        }

        val releaseDate = root.str("published_at").ifBlank { root.str("created_at") }
            .ifBlank { "Unspecified" }

        return UpdateManifest(
            latestVersion = latestVersion,
            versionCode = versionCode,
            apkDownloadUrl = apkUrl,
            releaseNotes = body.ifBlank { "GitHub Release $tagName" },
            minSupportedVersion = minSupported.coerceAtLeast(1),
            updateType = updateType,
            sha256Checksum = resolvedSha256,
            releaseDate = releaseDate,
            sha256AssetUrl = shaAssetUrl,
            sourceLabel = "GitHub Releases",
        )
    }

    private fun Map<String, Any?>.str(key: String): String =
        (this[key] as? String)?.trim().orEmpty()

    private fun Map<String, Any?>.intOrZero(key: String): Int =
        when (val v = this[key]) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull() ?: 0
            else -> 0
        }

    private fun parseSemverToCode(versionName: String): Int {
        val parts = versionName.split(".", "-", "+")
            .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        if (parts.isEmpty()) return 0
        val major = parts.getOrElse(0) { 0 }.coerceIn(0, 99)
        val minor = parts.getOrElse(1) { 0 }.coerceIn(0, 99)
        val patch = parts.getOrElse(2) { 0 }.coerceIn(0, 99)
        return (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
    }
}
