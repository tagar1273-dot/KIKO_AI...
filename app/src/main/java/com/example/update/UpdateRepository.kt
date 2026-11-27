package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val Context.kikoUpdateDataStore: DataStore<Preferences> by preferencesDataStore(name = "kiko_update_prefs")

data class UpdateSettingsSnapshot(
    val serverUrl: String = "",
    val lastCheckedMillis: Long = 0L,
    val cachedManifest: UpdateManifest? = null,
    val installedVersionName: String = BuildConfig.VERSION_NAME,
    val installedVersionCode: Int = BuildConfig.VERSION_CODE,
    val hasInstallPermission: Boolean = true,
) {
    val formattedLastChecked: String
        get() = if (lastCheckedMillis <= 0L) {
            "Never checked"
        } else {
            SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault()).format(Date(lastCheckedMillis))
        }
}

class UpdateRepository private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "KikoUpdateRepo"
        private const val UPDATES_SUBDIR = "updates"

        @Volatile
        private var instance: UpdateRepository? = null

        fun getInstance(context: Context): UpdateRepository =
            instance ?: synchronized(this) {
                instance ?: UpdateRepository(context.applicationContext).also { instance = it }
            }
    }

    private object Keys {
        val UPDATE_SERVER_URL = stringPreferencesKey("update_server_url")
        val LAST_CHECKED_MILLIS = longPreferencesKey("last_checked_millis")
        val CACHED_MANIFEST_JSON = stringPreferencesKey("cached_manifest_json")
        val PENDING_INSTALL_VERSION_CODE = intPreferencesKey("pending_install_version_code")
        val PENDING_INSTALL_VERSION_NAME = stringPreferencesKey("pending_install_version_name")
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _statusMessage = MutableStateFlow(UpdateStatusLabel.IDLE.displayText)
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _installPermissionTick = MutableStateFlow(0)

    private var downloadJob: Job? = null
    @Volatile
    private var activeCall: Call? = null
    private val pauseRequested = AtomicBoolean(false)

    val settingsFlow: Flow<UpdateSettingsSnapshot> = appContext.kikoUpdateDataStore.data.map { prefs ->
        val rawManifest = prefs[Keys.CACHED_MANIFEST_JSON].orEmpty()
        val parsed = if (rawManifest.isNotBlank()) {
            UpdateManifestParser.parseManifestJson(rawManifest).getOrNull()
        } else null

        UpdateSettingsSnapshot(
            serverUrl = prefs[Keys.UPDATE_SERVER_URL].orEmpty(),
            lastCheckedMillis = prefs[Keys.LAST_CHECKED_MILLIS] ?: 0L,
            cachedManifest = parsed,
            installedVersionName = BuildConfig.VERSION_NAME,
            installedVersionCode = BuildConfig.VERSION_CODE,
            hasInstallPermission = canRequestPackageInstalls(),
        )
    }

    fun refreshInstallPermissionState() {
        _installPermissionTick.value++
        val allowed = canRequestPackageInstalls()
        when (val current = _uiState.value) {
            is UpdateUiState.UpdateAvailable -> {
                _uiState.value = current.copy(hasInstallPermission = allowed)
            }
            is UpdateUiState.ReadyToInstall -> {
                _uiState.value = current.copy(hasInstallPermission = allowed)
            }
            else -> Unit
        }
    }

    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()

    fun createManageInstallPermissionIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${appContext.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    suspend fun saveUpdateServerUrl(rawUrl: String): Result<String> = runCatching {
        val normalized = UpdateManifestParser.normalizeEndpointUrl(rawUrl)
        if (normalized.isNotEmpty() && !UpdateManifestParser.isSecureHttpsUrl(normalized)) {
            error("Update server URL must use a secure HTTPS connection (https://).")
        }
        appContext.kikoUpdateDataStore.edit { prefs ->
            if (normalized.isEmpty()) {
                prefs.remove(Keys.UPDATE_SERVER_URL)
            } else {
                prefs[Keys.UPDATE_SERVER_URL] = normalized
            }
        }
        normalized
    }

    suspend fun initializeOnLaunch() {
        UpdateNotificationHelper.ensureChannels(appContext)
        val installedCode = BuildConfig.VERSION_CODE
        val prefs = appContext.kikoUpdateDataStore.data.first()

        // 1. Detect completed installation after Android package installer finishes
        val pendingCode = prefs[Keys.PENDING_INSTALL_VERSION_CODE] ?: 0
        val pendingName = prefs[Keys.PENDING_INSTALL_VERSION_NAME].orEmpty()
        if (pendingCode > 0 && installedCode >= pendingCode) {
            appContext.kikoUpdateDataStore.edit { edit ->
                edit.remove(Keys.PENDING_INSTALL_VERSION_CODE)
                edit.remove(Keys.PENDING_INSTALL_VERSION_NAME)
            }
            cleanupUpdateFiles()
            val displayVersion = pendingName.ifBlank { BuildConfig.VERSION_NAME }
            _statusMessage.value = UpdateStatusLabel.SUCCESSFULLY_INSTALLED.displayText
            _uiState.value = UpdateUiState.InstalledSuccess(displayVersion, installedCode)
            UpdateNotificationHelper.notifyInstallSucceeded(appContext, displayVersion)
        }

        // 2. Restore cached mandatory update immediately even before network returns
        val cachedJson = prefs[Keys.CACHED_MANIFEST_JSON].orEmpty()
        if (cachedJson.isNotBlank()) {
            val cached = UpdateManifestParser.parseManifestJson(cachedJson).getOrNull()
            if (cached != null && UpdateManifestParser.isUpdateAvailable(cached, installedCode)) {
                applyAvailableManifestState(cached, notifyUser = false)
            }
        }

        // 3. Perform automatic silent update check on launch if server URL is configured
        val serverUrl = prefs[Keys.UPDATE_SERVER_URL].orEmpty()
        if (serverUrl.isNotBlank()) {
            checkForUpdates(userInitiated = false)
        }
    }

    suspend fun checkForUpdates(userInitiated: Boolean): Result<UpdateManifest?> = withContext(Dispatchers.IO) {
        val prefs = appContext.kikoUpdateDataStore.data.first()
        val serverUrl = prefs[Keys.UPDATE_SERVER_URL].orEmpty().trim()

        if (serverUrl.isBlank()) {
            val msg = "Configure your HTTPS Update Server or GitHub Releases URL below to check for updates."
            if (userInitiated) {
                _statusMessage.value = UpdateStatusLabel.FAILED_RETRY.displayText
                _uiState.value = UpdateUiState.Failed(message = msg, canResume = false)
            }
            return@withContext Result.failure(IllegalStateException(msg))
        }

        if (!UpdateManifestParser.isSecureHttpsUrl(serverUrl)) {
            val msg = "Insecure update URL rejected. HTTPS is required."
            _statusMessage.value = UpdateStatusLabel.FAILED_RETRY.displayText
            _uiState.value = UpdateUiState.Failed(message = msg, canResume = false)
            if (userInitiated) UpdateNotificationHelper.notifyUpdateFailed(appContext, msg)
            return@withContext Result.failure(SecurityException(msg))
        }

        // Do not clobber an active download unless user explicitly checks
        if (!userInitiated && (_uiState.value is UpdateUiState.Downloading || _uiState.value is UpdateUiState.Verifying)) {
            return@withContext Result.success(null)
        }

        _statusMessage.value = UpdateStatusLabel.CHECKING.displayText
        _uiState.value = UpdateUiState.Checking

        return@withContext try {
            val request = Request.Builder()
                .url(serverUrl)
                .header("Accept", "application/vnd.github+json, application/json")
                .header("User-Agent", "KIKO-AI-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            val rawBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Update server returned HTTP ${response.code}")
                }
                response.body?.string()?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Empty response from update server.")
            }

            var manifest = UpdateManifestParser.parseManifestJson(rawBody).getOrThrow()

            // Resolve .sha256 sidecar asset from GitHub Releases if SHA wasn't inline in release notes
            if (manifest.sha256Checksum.isBlank() && !manifest.sha256AssetUrl.isNullOrBlank()) {
                val resolvedSha = fetchSha256FromSidecar(manifest.sha256AssetUrl!!)
                manifest = manifest.copy(sha256Checksum = resolvedSha)
            }

            val now = System.currentTimeMillis()
            appContext.kikoUpdateDataStore.edit { edit ->
                edit[Keys.LAST_CHECKED_MILLIS] = now
                edit[Keys.CACHED_MANIFEST_JSON] = rawBody
            }

            val installedCode = BuildConfig.VERSION_CODE
            if (UpdateManifestParser.isUpdateAvailable(manifest, installedCode)) {
                applyAvailableManifestState(manifest, notifyUser = true)
            } else {
                cleanupUpdateFiles()
                _statusMessage.value = UpdateStatusLabel.LATEST_VERSION.displayText
                _uiState.value = UpdateUiState.UpToDate(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = installedCode,
                )
                if (userInitiated) {
                    UpdateNotificationHelper.notifyLatestVersion(appContext, BuildConfig.VERSION_NAME)
                }
            }
            Result.success(manifest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            val errMsg = e.localizedMessage ?: "Network error while checking for updates."
            _statusMessage.value = UpdateStatusLabel.FAILED_RETRY.displayText
            _uiState.value = UpdateUiState.Failed(
                message = errMsg,
                canResume = hasPartialDownloadForCachedManifest(),
            )
            if (userInitiated) {
                UpdateNotificationHelper.notifyUpdateFailed(appContext, errMsg)
            }
            Result.failure(e)
        }
    }

    private fun fetchSha256FromSidecar(shaUrl: String): String {
        require(UpdateManifestParser.isSecureHttpsUrl(shaUrl)) {
            "SHA-256 checksum asset URL must use HTTPS."
        }
        val req = Request.Builder()
            .url(shaUrl)
            .header("User-Agent", "KIKO-AI-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        val content = httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Failed to fetch SHA-256 asset (HTTP ${resp.code})")
            resp.body?.string().orEmpty()
        }
        val hex = Regex("([a-fA-F0-9]{64})").find(content)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
            ?: throw IOException("Invalid SHA-256 file format on release server.")
        return hex
    }

    private fun applyAvailableManifestState(manifest: UpdateManifest, notifyUser: Boolean) {
        val mandatory = UpdateManifestParser.isMandatoryUpdate(manifest, BuildConfig.VERSION_CODE)
        val apkFile = finalApkFile(manifest.versionCode)
        val partFile = partialApkFile(manifest.versionCode)

        if (apkFile.exists() && apkFile.length() > 0L) {
            val verifyResult = UpdateSecurityVerifier.verifyDownloadedApk(
                context = appContext,
                apkFile = apkFile,
                expectedSha256Hex = manifest.sha256Checksum,
                expectedMinVersionCode = manifest.versionCode,
            )
            if (verifyResult.passed) {
                _statusMessage.value = UpdateStatusLabel.READY_TO_INSTALL.displayText
                _uiState.value = UpdateUiState.ReadyToInstall(
                    manifest = manifest,
                    isMandatory = mandatory,
                    apkFilePath = apkFile.absolutePath,
                    sha256Verified = true,
                    signatureVerified = true,
                    verificationDetail = verifyResult.summary,
                    hasInstallPermission = canRequestPackageInstalls(),
                )
                return
            } else {
                apkFile.delete()
            }
        }

        val partialBytes = if (partFile.exists()) partFile.length() else 0L
        _statusMessage.value = UpdateStatusLabel.NEW_UPDATE_AVAILABLE.displayText
        _uiState.value = UpdateUiState.UpdateAvailable(
            manifest = manifest,
            isMandatory = mandatory,
            partialBytes = partialBytes,
            hasInstallPermission = canRequestPackageInstalls(),
        )
        if (notifyUser) {
            UpdateNotificationHelper.notifyNewUpdateAvailable(appContext, manifest, mandatory)
        }
    }

    fun startOrResumeDownload(manifest: UpdateManifest) {
        if (downloadJob?.isActive == true) return
        val mandatory = UpdateManifestParser.isMandatoryUpdate(manifest, BuildConfig.VERSION_CODE)

        if (!UpdateManifestParser.isSecureHttpsUrl(manifest.apkDownloadUrl)) {
            val msg = "Blocked insecure APK download URL (HTTPS required)."
            _statusMessage.value = UpdateStatusLabel.FAILED_RETRY.displayText
            _uiState.value = UpdateUiState.Failed(msg, canResume = false, manifest = manifest, isMandatory = mandatory)
            UpdateNotificationHelper.notifyUpdateFailed(appContext, msg)
            return
        }

        pauseRequested.set(false)
        downloadJob = repoScope.launch {
            executeDownload(manifest, mandatory)
        }
    }

    fun pauseDownload() {
        val current = _uiState.value as? UpdateUiState.Downloading ?: return
        pauseRequested.set(true)
        activeCall?.cancel()
        UpdateNotificationHelper.clearProgressNotification(appContext)
        _statusMessage.value = "Download Paused (${current.progressPercent}%)"
        _uiState.value = current.copy(isPaused = true)
    }

    fun cancelDownload() {
        pauseRequested.set(true)
        activeCall?.cancel()
        downloadJob?.cancel()
        UpdateNotificationHelper.clearProgressNotification(appContext)
        repoScope.launch {
            cleanupUpdateFiles()
            val prefs = appContext.kikoUpdateDataStore.data.first()
            val cached = prefs[Keys.CACHED_MANIFEST_JSON]?.let {
                UpdateManifestParser.parseManifestJson(it).getOrNull()
            }
            if (cached != null && UpdateManifestParser.isUpdateAvailable(cached, BuildConfig.VERSION_CODE)) {
                applyAvailableManifestState(cached, notifyUser = false)
            } else {
                _statusMessage.value = UpdateStatusLabel.IDLE.displayText
                _uiState.value = UpdateUiState.Idle
            }
        }
    }

    private suspend fun executeDownload(manifest: UpdateManifest, mandatory: Boolean) {
        val dir = updatesDir()
        val partFile = partialApkFile(manifest.versionCode)
        val finalFile = finalApkFile(manifest.versionCode)

        // If already downloaded & verified, jump directly to ReadyToInstall
        if (finalFile.exists()) {
            val preCheck = UpdateSecurityVerifier.verifyDownloadedApk(
                context = appContext,
                apkFile = finalFile,
                expectedSha256Hex = manifest.sha256Checksum,
                expectedMinVersionCode = manifest.versionCode,
            )
            if (preCheck.passed) {
                _statusMessage.value = UpdateStatusLabel.READY_TO_INSTALL.displayText
                _uiState.value = UpdateUiState.ReadyToInstall(
                    manifest = manifest,
                    isMandatory = mandatory,
                    apkFilePath = finalFile.absolutePath,
                    sha256Verified = true,
                    signatureVerified = true,
                    verificationDetail = preCheck.summary,
                    hasInstallPermission = canRequestPackageInstalls(),
                )
                return
            } else {
                finalFile.delete()
            }
        }

        var existingBytes = if (partFile.exists()) partFile.length() else 0L
        _statusMessage.value = UpdateStatusLabel.DOWNLOADING_UPDATE.displayText
        _uiState.value = UpdateUiState.Downloading(
            manifest = manifest,
            isMandatory = mandatory,
            progressPercent = 0,
            bytesDownloaded = existingBytes,
            totalBytes = -1L,
            isPaused = false,
        )

        try {
            val reqBuilder = Request.Builder()
                .url(manifest.apkDownloadUrl)
                .header("User-Agent", "KIKO-AI-Android/${BuildConfig.VERSION_NAME}")
                .get()

            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }

            val call = httpClient.newCall(reqBuilder.build())
            activeCall = call

            call.execute().use { response ->
                // Server rejected stale range -> clear partial file and start clean
                if (response.code == 416) {
                    partFile.delete()
                    existingBytes = 0L
                    throw IOException("Server reported stale byte range. Tap Retry to download cleanly.")
                }

                if (!response.isSuccessful) {
                    throw IOException("Download failed with HTTP ${response.code}")
                }

                val isPartialAppend = response.code == 206 && existingBytes > 0L
                if (!isPartialAppend && partFile.exists()) {
                    partFile.delete()
                    existingBytes = 0L
                }

                val body = response.body ?: throw IOException("Empty download stream from server.")
                val contentLength = body.contentLength()
                val totalBytes = if (contentLength > 0L) {
                    if (isPartialAppend) existingBytes + contentLength else contentLength
                } else {
                    -1L
                }

                var downloaded = existingBytes
                var lastUiEmitMs = 0L
                var lastNotifPct = -1

                val buffer = ByteArray(8192)
                body.byteStream().use { input ->
                    FileOutputStream(partFile, isPartialAppend).use { output ->
                        while (true) {
                            if (pauseRequested.get()) {
                                val pct = calculatePercent(downloaded, totalBytes)
                                _statusMessage.value = "Download Paused ($pct%)"
                                _uiState.value = UpdateUiState.Downloading(
                                    manifest = manifest,
                                    isMandatory = mandatory,
                                    progressPercent = pct,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    isPaused = true,
                                )
                                return
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            val pct = calculatePercent(downloaded, totalBytes)
                            if (now - lastUiEmitMs >= 180L || pct == 100) {
                                lastUiEmitMs = now
                                _uiState.value = UpdateUiState.Downloading(
                                    manifest = manifest,
                                    isMandatory = mandatory,
                                    progressPercent = pct,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    isPaused = false,
                                )
                                if (pct != lastNotifPct && (pct % 2 == 0 || pct == 100)) {
                                    lastNotifPct = pct
                                    UpdateNotificationHelper.notifyDownloadingUpdate(
                                        appContext,
                                        manifest.latestVersion,
                                        pct,
                                    )
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (pauseRequested.get()) return

            if (!dir.exists()) dir.mkdirs()
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                throw IOException("Failed to finalize downloaded APK file.")
            }

            _statusMessage.value = UpdateStatusLabel.DOWNLOAD_COMPLETED.displayText
            UpdateNotificationHelper.notifyDownloadCompleted(appContext, manifest.latestVersion)

            _statusMessage.value = UpdateStatusLabel.VERIFYING.displayText
            _uiState.value = UpdateUiState.Verifying(manifest, mandatory)

            val verifyResult = UpdateSecurityVerifier.verifyDownloadedApk(
                context = appContext,
                apkFile = finalFile,
                expectedSha256Hex = manifest.sha256Checksum,
                expectedMinVersionCode = manifest.versionCode,
            )

            if (!verifyResult.passed) {
                finalFile.delete()
                _statusMessage.value = UpdateStatusLabel.FAILED_RETRY.displayText
                _uiState.value = UpdateUiState.Failed(
                    message = verifyResult.summary,
                    canResume = false,
                    manifest = manifest,
                    isMandatory = mandatory,
                )
                UpdateNotificationHelper.notifyUpdateFailed(appContext, verifyResult.summary)
                return
            }

            _statusMessage.value = UpdateStatusLabel.READY_TO_INSTALL.displayText
            _uiState.value = UpdateUiState.ReadyToInstall(
                manifest = manifest,
                isMandatory = mandatory,
                apkFilePath = finalFile.absolutePath,
                sha256Verified = verifyResult.sha256Verified,
                signatureVerified = verifyResult.signatureVerified,
                verificationDetail = verifyResult.summary,
                hasInstallPermission = canRequestPackageInstalls(),
            )
            UpdateNotificationHelper.notifyReadyToInstall(appContext, manifest.latestVersion)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (pauseRequested.get()) {
                val currentDl = _uiState.value as? UpdateUiState.Downloading
                val partial = if (partFile.exists()) partFile.length() else 0L
                val preservedPct = currentDl?.progressPercent ?: 0
                val preservedTotal = currentDl?.totalBytes ?: -1L
                _statusMessage.value = "Download Paused ($preservedPct%)"
                _uiState.value = UpdateUiState.Downloading(
                    manifest = manifest,
                    isMandatory = mandatory,
                    progressPercent = preservedPct,
                    bytesDownloaded = partial,
                    totalBytes = preservedTotal,
                    isPaused = true,
                )
                return
            }
            Log.e(TAG, "APK download failed", e)
            val canResume = partFile.exists() && partFile.length() > 0L
            val errMsg = e.localizedMessage ?: "Interrupted download. You can resume or retry."
            _statusMessage.value = UpdateStatusLabel.FAILED_RETRY.displayText
            _uiState.value = UpdateUiState.Failed(
                message = errMsg,
                canResume = canResume,
                manifest = manifest,
                isMandatory = mandatory,
            )
            UpdateNotificationHelper.notifyUpdateFailed(appContext, errMsg)
        } finally {
            activeCall = null
        }
    }

    suspend fun launchOfficialInstaller(apkFilePath: String, manifest: UpdateManifest): Result<Unit> {
        if (!canRequestPackageInstalls()) {
            return Result.failure(
                SecurityException("Install permission required. Grant 'Allow from this source' in Android Settings."),
            )
        }

        val apkFile = File(apkFilePath)
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return Result.failure(IOException("Verified APK package missing. Please download again."))
        }

        // Re-verify integrity right before handing off to Android's package installer
        val recheck = withContext(Dispatchers.IO) {
            UpdateSecurityVerifier.verifyDownloadedApk(
                context = appContext,
                apkFile = apkFile,
                expectedSha256Hex = manifest.sha256Checksum,
                expectedMinVersionCode = manifest.versionCode,
            )
        }
        if (!recheck.passed) {
            apkFile.delete()
            return Result.failure(SecurityException(recheck.summary))
        }

        appContext.kikoUpdateDataStore.edit { edit ->
            edit[Keys.PENDING_INSTALL_VERSION_CODE] = manifest.versionCode
            edit[Keys.PENDING_INSTALL_VERSION_NAME] = manifest.latestVersion
        }

        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile,
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        appContext.startActivity(installIntent)
        return Result.success(Unit)
    }

    private fun calculatePercent(downloaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun updatesDir(): File = File(appContext.filesDir, UPDATES_SUBDIR).apply {
        if (!exists()) mkdirs()
    }

    private fun partialApkFile(versionCode: Int): File =
        File(updatesDir(), "kiko-update-v$versionCode.apk.part")

    private fun finalApkFile(versionCode: Int): File =
        File(updatesDir(), "kiko-update-v$versionCode.apk")

    private fun cleanupUpdateFiles() {
        val dir = File(appContext.filesDir, UPDATES_SUBDIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private suspend fun hasPartialDownloadForCachedManifest(): Boolean {
        val json = appContext.kikoUpdateDataStore.data.first()[Keys.CACHED_MANIFEST_JSON].orEmpty()
        val manifest = UpdateManifestParser.parseManifestJson(json).getOrNull() ?: return false
        val part = partialApkFile(manifest.versionCode)
        return part.exists() && part.length() > 0L
    }
}
