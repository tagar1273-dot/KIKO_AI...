package com.example

import com.example.update.UpdateManifestParser
import com.example.update.UpdateSecurityVerifier
import com.example.update.UpdateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KikoUpdateUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parseKikoServerManifest_parsesAllRequiredFields() {
        val validSha = "a".repeat(64)
        val raw = """
            {
              "latestVersion": "2.1.0",
              "versionCode": 5,
              "apkDownloadUrl": "https://updates.example.com/api/v1/updates/download/kiko-v2.1.0.apk",
              "releaseNotes": "Added Urdu voice pipeline and 4GB RAM tuning.",
              "minSupportedVersion": 3,
              "updateType": "CRITICAL",
              "sha256Checksum": "$validSha",
              "releaseDate": "2026-11-26T12:00:00Z"
            }
        """.trimIndent()

        val result = UpdateManifestParser.parseManifestJson(raw)
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals("2.1.0", manifest.latestVersion)
        assertEquals(5, manifest.versionCode)
        assertEquals(UpdateType.CRITICAL, manifest.updateType)
        assertEquals(3, manifest.minSupportedVersion)
        assertEquals(validSha, manifest.sha256Checksum)
        assertTrue(UpdateManifestParser.isUpdateAvailable(manifest, installedVersionCode = 1))
        assertTrue(UpdateManifestParser.isMandatoryUpdate(manifest, installedVersionCode = 2))
    }

    @Test
    fun parseKikoServerManifest_rejectsInsecureHttpDownloadUrl() {
        val validSha = "b".repeat(64)
        val raw = """
            {
              "latestVersion": "2.0.0",
              "versionCode": 2,
              "apkDownloadUrl": "http://insecure.example.com/kiko.apk",
              "releaseNotes": "Unsafe HTTP test",
              "minSupportedVersion": 1,
              "updateType": "OPTIONAL",
              "sha256Checksum": "$validSha",
              "releaseDate": "2026-11-26T12:00:00Z"
            }
        """.trimIndent()

        val result = UpdateManifestParser.parseManifestJson(raw)
        assertTrue(result.isFailure)
    }

    @Test
    fun parseGitHubReleaseManifest_extractsApkAssetAndMetadata() {
        val validSha = "0123456789abcdef".repeat(4)
        val raw = """
            {
              "tag_name": "v1.4.2",
              "published_at": "2026-11-26T09:00:00Z",
              "body": "VERSION_CODE: 4\nMIN_SUPPORTED_VERSION: 2\nUPDATE_TYPE: OPTIONAL\nSHA256: $validSha\n- Improved HUD graphics.",
              "assets": [
                {
                  "name": "kiko-ai-v1.4.2.apk",
                  "browser_download_url": "https://github.com/org/kiko/releases/download/v1.4.2/kiko-ai-v1.4.2.apk"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateManifestParser.parseManifestJson(raw)
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals("1.4.2", manifest.latestVersion)
        assertEquals(4, manifest.versionCode)
        assertEquals(UpdateType.OPTIONAL, manifest.updateType)
        assertFalse(UpdateManifestParser.isMandatoryUpdate(manifest, installedVersionCode = 2))
        assertTrue(UpdateManifestParser.isMandatoryUpdate(manifest, installedVersionCode = 1))
    }

    @Test
    fun normalizeEndpointUrl_convertsGitHubRepoLinkToReleasesApi() {
        assertEquals(
            "https://api.github.com/repos/starklabs/kiko-ai/releases/latest",
            UpdateManifestParser.normalizeEndpointUrl("https://github.com/starklabs/kiko-ai"),
        )
        assertEquals(
            "https://updates.example.com/api/v1/updates/latest",
            UpdateManifestParser.normalizeEndpointUrl("https://updates.example.com/api/v1/updates/latest"),
        )
    }

    @Test
    fun sha256ChecksumVerifier_detectsValidAndTamperedFiles() {
        val file: File = tempFolder.newFile("sample.apk")
        file.writeText("KIKO-AI-VERIFIED-BINARY")

        val digest = UpdateSecurityVerifier.computeSha256(file)
        assertEquals(64, digest.length)
        assertTrue(UpdateSecurityVerifier.verifyChecksum(file, digest).first)

        // Tamper with file content
        file.writeText("KIKO-AI-TAMPERED-BINARY")
        assertFalse(UpdateSecurityVerifier.verifyChecksum(file, digest).first)
    }
}
