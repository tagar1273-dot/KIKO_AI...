package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.HolographicBackground
import com.example.ui.components.HudGlassCard
import com.example.ui.components.StatusIndicatorPill
import com.example.ui.components.TelemetryStatChip
import com.example.ui.theme.KikoAmber
import com.example.ui.theme.KikoAzure
import com.example.ui.theme.KikoCrimson
import com.example.ui.theme.KikoCyan
import com.example.ui.theme.KikoEmerald
import com.example.ui.theme.KikoOutline
import com.example.ui.theme.KikoSurfaceElevated
import com.example.ui.theme.KikoTextMuted
import com.example.ui.theme.KikoTextPrimary
import com.example.ui.theme.KikoTextSecondary
import com.example.ui.theme.KikoVoid
import com.example.update.UpdateManifest
import com.example.update.UpdateRepository
import com.example.update.UpdateSettingsSnapshot
import com.example.update.UpdateStatusLabel
import com.example.update.UpdateType
import com.example.update.UpdateUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KikoUpdateSectionCard(
    uiState: UpdateUiState,
    statusMessage: String,
    settings: UpdateSettingsSnapshot,
    repository: UpdateRepository,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var serverUrlInput by rememberSaveable { mutableStateOf(settings.serverUrl) }
    var serverUrlDirty by remember { mutableStateOf(false) }

    LaunchedEffect(settings.serverUrl) {
        if (!serverUrlDirty) {
            serverUrlInput = settings.serverUrl
        }
    }

    // Re-check unknown-sources install permission when user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repository.refreshInstallPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        repository.refreshInstallPermissionState()
    }

    val activeManifest: UpdateManifest? = when (uiState) {
        is UpdateUiState.UpdateAvailable -> uiState.manifest
        is UpdateUiState.Downloading -> uiState.manifest
        is UpdateUiState.Verifying -> uiState.manifest
        is UpdateUiState.ReadyToInstall -> uiState.manifest
        is UpdateUiState.Failed -> uiState.manifest ?: settings.cachedManifest
        else -> settings.cachedManifest
    }

    val statusColor = when (uiState) {
        is UpdateUiState.UpToDate, is UpdateUiState.InstalledSuccess -> KikoEmerald
        is UpdateUiState.ReadyToInstall -> KikoEmerald
        is UpdateUiState.UpdateAvailable -> if (uiState.isMandatory) KikoCrimson else KikoAmber
        is UpdateUiState.Downloading, is UpdateUiState.Verifying, is UpdateUiState.Checking -> KikoCyan
        is UpdateUiState.Failed -> KikoCrimson
        is UpdateUiState.Idle -> KikoTextSecondary
    }

    HudGlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = statusColor.copy(alpha = 0.55f),
    ) {
        // Branded Header with KIKO AI Logo + Status Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_kiko_hud_emblem),
                    contentDescription = "KIKO AI Software Update Emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, KikoCyan, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "KIKO SOFTWARE UPDATE",
                        style = MaterialTheme.typography.labelLarge,
                        color = KikoCyan,
                    )
                    Text(
                        text = "Auto & Manual OTA Integrity Matrix",
                        style = MaterialTheme.typography.labelSmall,
                        color = KikoTextSecondary,
                    )
                }
            }
            StatusIndicatorPill(label = statusMessage, color = statusColor)
        }

        Spacer(Modifier.height(12.dp))

        // Version Grid: Current Version vs Latest Version
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TelemetryStatChip(
                label = "CURRENT VERSION",
                value = "v${settings.installedVersionName} (${settings.installedVersionCode})",
                accent = KikoCyan,
                modifier = Modifier.weight(1f),
            )
            TelemetryStatChip(
                label = "LATEST VERSION",
                value = activeManifest?.let { "v${it.latestVersion} (${it.versionCode})" } ?: "Not checked",
                accent = if (activeManifest != null && activeManifest.versionCode > settings.installedVersionCode) {
                    KikoAmber
                } else {
                    KikoEmerald
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Last Checked: ${settings.formattedLastChecked}",
                style = MaterialTheme.typography.labelSmall,
                color = KikoTextMuted,
            )
            if (activeManifest != null) {
                StatusIndicatorPill(
                    label = if (activeManifest.updateType == UpdateType.CRITICAL ||
                        settings.installedVersionCode < activeManifest.minSupportedVersion
                    ) "CRITICAL" else "OPTIONAL",
                    color = if (activeManifest.updateType == UpdateType.CRITICAL) KikoCrimson else KikoAzure,
                )
            }
        }

        // Update Server HTTPS Endpoint Field
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = serverUrlInput,
            onValueChange = {
                serverUrlInput = it
                serverUrlDirty = true
            },
            label = { Text("HTTPS Update Server or GitHub Releases URL", color = KikoCyan) },
            placeholder = { Text("https://updates.example.com/api/v1/updates/latest", color = KikoTextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KikoCyan,
                unfocusedBorderColor = KikoOutline,
                focusedContainerColor = KikoSurfaceElevated,
                focusedTextColor = KikoTextPrimary,
                unfocusedTextColor = KikoTextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("update_server_url_input"),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        repository.saveUpdateServerUrl(serverUrlInput)
                            .onSuccess { saved ->
                                serverUrlInput = saved
                                serverUrlDirty = false
                                onFeedback(
                                    if (saved.isBlank()) "Update server URL cleared."
                                    else "HTTPS update endpoint saved."
                                )
                            }
                            .onFailure { err ->
                                onFeedback(err.message ?: "Invalid update URL.")
                            }
                    }
                },
                border = BorderStroke(1.dp, KikoOutline),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_update_url_button"),
            ) {
                Text("Save URL", color = KikoTextPrimary)
            }

            Button(
                onClick = {
                    scope.launch {
                        if (serverUrlDirty) {
                            val saveRes = repository.saveUpdateServerUrl(serverUrlInput)
                            if (saveRes.isFailure) {
                                onFeedback(saveRes.exceptionOrNull()?.message ?: "HTTPS URL required.")
                                return@launch
                            }
                            serverUrlInput = saveRes.getOrDefault(serverUrlInput)
                            serverUrlDirty = false
                        }
                        repository.checkForUpdates(userInitiated = true)
                    }
                },
                enabled = uiState !is UpdateUiState.Checking && uiState !is UpdateUiState.Verifying,
                colors = ButtonDefaults.buttonColors(containerColor = KikoCyan, contentColor = KikoVoid),
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .testTag("check_updates_button"),
            ) {
                if (uiState is UpdateUiState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = KikoVoid,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Check for updates")
                    Spacer(Modifier.width(6.dp))
                    Text("Check for Updates")
                }
            }
        }

        // Update Banner + Actions depending on real state
        AnimatedVisibility(
            visible = uiState !is UpdateUiState.Idle,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                UpdateActionStateBlock(
                    uiState = uiState,
                    onStartOrResumeDownload = { manifest ->
                        repository.startOrResumeDownload(manifest)
                    },
                    onPauseDownload = { repository.pauseDownload() },
                    onCancelDownload = { repository.cancelDownload() },
                    onRequestInstallPermission = {
                        installPermLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                    onInstallApk = { apkPath, manifest ->
                        scope.launch {
                            repository.launchOfficialInstaller(apkPath, manifest)
                                .onFailure { err ->
                                    onFeedback(err.message ?: "Unable to launch package installer.")
                                }
                        }
                    },
                    onRetryCheck = {
                        scope.launch { repository.checkForUpdates(userInitiated = true) }
                    },
                )
            }
        }

        // Release Notes Card
        if (activeManifest != null) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KikoSurfaceElevated.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, KikoOutline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "RELEASE NOTES • v${activeManifest.latestVersion}",
                            style = MaterialTheme.typography.labelMedium,
                            color = KikoCyan,
                        )
                        Text(
                            text = activeManifest.releaseDate.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = KikoTextMuted,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = activeManifest.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = KikoTextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "SHA-256: ${activeManifest.sha256Checksum.take(16)}… • Min Supported VC: ${activeManifest.minSupportedVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = KikoTextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Background checks run every 6h via WorkManager. Packages require SHA-256 + signing certificate verification and Android's official package installer.",
            style = MaterialTheme.typography.labelSmall,
            color = KikoTextMuted,
        )
    }
}

@Composable
private fun UpdateActionStateBlock(
    uiState: UpdateUiState,
    onStartOrResumeDownload: (UpdateManifest) -> Unit,
    onPauseDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRequestInstallPermission: () -> Unit,
    onInstallApk: (String, UpdateManifest) -> Unit,
    onRetryCheck: () -> Unit,
) {
    when (uiState) {
        is UpdateUiState.Idle, is UpdateUiState.Checking -> Unit

        is UpdateUiState.UpToDate -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KikoEmerald.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, KikoEmerald.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = KikoEmerald)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = UpdateStatusLabel.LATEST_VERSION.displayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = KikoEmerald,
                        )
                        Text(
                            text = "KIKO AI v${uiState.versionName} (${uiState.versionCode}) is up to date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KikoTextSecondary,
                        )
                    }
                }
            }
        }

        is UpdateUiState.InstalledSuccess -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KikoEmerald.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, KikoEmerald),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = KikoEmerald)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = UpdateStatusLabel.SUCCESSFULLY_INSTALLED.displayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = KikoEmerald,
                        )
                        Text(
                            text = "Running KIKO AI v${uiState.versionName} (build ${uiState.versionCode}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = KikoTextPrimary,
                        )
                    }
                }
            }
        }

        is UpdateUiState.UpdateAvailable -> {
            val accent = if (uiState.isMandatory) KikoCrimson else KikoAmber
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isMandatory) Icons.Filled.Warning else Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${UpdateStatusLabel.NEW_UPDATE_AVAILABLE.displayText}: v${uiState.manifest.latestVersion}",
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (uiState.partialBytes > 0L) {
                            "Resumable partial download found (${formatMb(uiState.partialBytes)} downloaded)."
                        } else {
                            "User approval required before downloading from ${uiState.manifest.sourceLabel}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = KikoTextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onStartOrResumeDownload(uiState.manifest) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = KikoVoid),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("download_update_button"),
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.partialBytes > 0L) "Resume Download" else "Download Update")
                    }
                }
            }
        }

        is UpdateUiState.Downloading -> {
            val animatedProgress by animateFloatAsState(
                targetValue = (uiState.progressPercent / 100f).coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 220),
                label = "ota_progress",
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KikoCyan.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, KikoCyan.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (uiState.isPaused) "Download Paused" else UpdateStatusLabel.DOWNLOADING_UPDATE.displayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = KikoCyan,
                        )
                        Text(
                            text = "${uiState.progressPercent}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = KikoCyan,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (uiState.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            color = KikoCyan,
                            trackColor = KikoSurfaceElevated,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .testTag("update_progress_bar"),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = KikoCyan,
                            trackColor = KikoSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .testTag("update_progress_bar"),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (uiState.totalBytes > 0L) {
                            "${formatMb(uiState.bytesDownloaded)} / ${formatMb(uiState.totalBytes)}"
                        } else {
                            "${formatMb(uiState.bytesDownloaded)} downloaded"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = KikoTextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (uiState.isPaused) onStartOrResumeDownload(uiState.manifest)
                                else onPauseDownload()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isPaused) KikoCyan else KikoAmber,
                                contentColor = KikoVoid,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pause_resume_download_button"),
                        ) {
                            Icon(
                                imageVector = if (uiState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.isPaused) "Resume" else "Pause")
                        }
                        OutlinedButton(
                            onClick = onCancelDownload,
                            border = BorderStroke(1.dp, KikoCrimson.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cancel_download_button"),
                        ) {
                            Text("Cancel", color = KikoCrimson)
                        }
                    }
                }
            }
        }

        is UpdateUiState.Verifying -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KikoAzure.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, KikoAzure),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = KikoCyan,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = UpdateStatusLabel.DOWNLOAD_COMPLETED.displayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = KikoCyan,
                        )
                        Text(
                            text = "Verifying SHA-256 checksum & APK signing certificate…",
                            style = MaterialTheme.typography.bodySmall,
                            color = KikoTextSecondary,
                        )
                    }
                }
            }
        }

        is UpdateUiState.ReadyToInstall -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KikoEmerald.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, KikoEmerald.copy(alpha = 0.75f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = KikoEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = UpdateStatusLabel.READY_TO_INSTALL.displayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = KikoEmerald,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiState.verificationDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = KikoTextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))

                    if (!uiState.hasInstallPermission) {
                        Text(
                            text = "Android 8.0+ requires permitting KIKO AI to launch the package installer.",
                            style = MaterialTheme.typography.labelSmall,
                            color = KikoAmber,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onRequestInstallPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = KikoAmber, contentColor = KikoVoid),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("grant_install_permission_button"),
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Allow App Installs in Settings")
                        }
                    } else {
                        Button(
                            onClick = { onInstallApk(uiState.apkFilePath, uiState.manifest) },
                            colors = ButtonDefaults.buttonColors(containerColor = KikoEmerald, contentColor = KikoVoid),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("install_update_button"),
                        ) {
                            Icon(Icons.Filled.InstallMobile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Install Update v${uiState.manifest.latestVersion}")
                        }
                    }
                }
            }
        }

        is UpdateUiState.Failed -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KikoCrimson.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, KikoCrimson.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = KikoCrimson)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = UpdateStatusLabel.FAILED_RETRY.displayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = KikoCrimson,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = KikoTextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (uiState.manifest != null && uiState.canResume) {
                                onStartOrResumeDownload(uiState.manifest)
                            } else if (uiState.manifest != null) {
                                onStartOrResumeDownload(uiState.manifest)
                            } else {
                                onRetryCheck()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KikoCrimson, contentColor = KikoTextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("retry_update_button"),
                    ) {
                        Icon(
                            imageVector = if (uiState.canResume) Icons.Filled.Download else Icons.Filled.Refresh,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.canResume) "Resume Interrupted Download" else "Retry Update")
                    }
                }
            }
        }
    }
}

@Composable
fun OptionalUpdateBanner(
    manifest: UpdateManifest,
    onOpenUpdates: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HudGlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = KikoAmber.copy(alpha = 0.75f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = KikoAmber)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${UpdateStatusLabel.NEW_UPDATE_AVAILABLE.displayText} • v${manifest.latestVersion}",
                        style = MaterialTheme.typography.titleSmall,
                        color = KikoAmber,
                    )
                    Text(
                        text = "Optional release ready. Tap to review notes & download.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KikoTextSecondary,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) {
                    Text("Later", color = KikoTextMuted)
                }
                Button(
                    onClick = onOpenUpdates,
                    colors = ButtonDefaults.buttonColors(containerColor = KikoAmber, contentColor = KikoVoid),
                    modifier = Modifier.height(48.dp).testTag("hud_open_update_button"),
                ) {
                    Text("Update")
                }
            }
        }
    }
}

@Composable
fun MandatoryUpdateScreen(
    manifest: UpdateManifest,
    uiState: UpdateUiState,
    settings: UpdateSettingsSnapshot,
    repository: UpdateRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var feedback by remember { mutableStateOf<String?>(null) }

    // Critical updates block navigation and cannot be bypassed via back press
    BackHandler(enabled = true) { }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repository.refreshInstallPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        repository.refreshInstallPermissionState()
    }

    HolographicBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_kiko_hud_emblem),
                    contentDescription = "KIKO AI Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .border(2.dp, KikoCrimson, CircleShape),
                )
                Spacer(Modifier.height(14.dp))
                StatusIndicatorPill(label = "CRITICAL MANDATORY UPDATE REQUIRED", color = KikoCrimson)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "KIKO AI Update Required",
                    style = MaterialTheme.typography.headlineMedium,
                    color = KikoTextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (settings.installedVersionCode < manifest.minSupportedVersion) {
                        "Your installed build (v${settings.installedVersionName}, code ${settings.installedVersionCode}) is below the minimum supported version (${manifest.minSupportedVersion}). Please update to v${manifest.latestVersion} to restore secure access to KIKO services."
                    } else {
                        "This release contains a critical security, protocol, or compatibility update required for all active KIKO AI nodes."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = KikoTextSecondary,
                )

                Spacer(Modifier.height(14.dp))

                HudGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = KikoCrimson.copy(alpha = 0.65f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TelemetryStatChip(
                            label = "INSTALLED",
                            value = "v${settings.installedVersionName} (${settings.installedVersionCode})",
                            accent = KikoCrimson,
                            modifier = Modifier.weight(1f),
                        )
                        TelemetryStatChip(
                            label = "REQUIRED",
                            value = "v${manifest.latestVersion} (${manifest.versionCode})",
                            accent = KikoEmerald,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "RELEASE NOTES",
                        style = MaterialTheme.typography.labelMedium,
                        color = KikoCyan,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = manifest.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = KikoTextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))

                    UpdateActionStateBlock(
                        uiState = uiState,
                        onStartOrResumeDownload = { repository.startOrResumeDownload(it) },
                        onPauseDownload = { repository.pauseDownload() },
                        onCancelDownload = { repository.cancelDownload() },
                        onRequestInstallPermission = {
                            installPermLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        },
                        onInstallApk = { apkPath, m ->
                            scope.launch {
                                repository.launchOfficialInstaller(apkPath, m)
                                    .onFailure { err -> feedback = err.message }
                            }
                        },
                        onRetryCheck = {
                            scope.launch { repository.checkForUpdates(userInitiated = true) }
                        },
                    )

                    if (!feedback.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = feedback!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = KikoCrimson,
                        )
                    }
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.2f MB".format(mb)
}
