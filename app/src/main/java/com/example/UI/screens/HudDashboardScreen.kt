package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.KikoLanguage
import com.example.model.KikoTeam
import com.example.ui.components.ArcCoreReactor
import com.example.ui.components.HudGlassCard
import com.example.ui.components.KikoLogoHeader
import com.example.ui.components.StatusIndicatorPill
import com.example.ui.components.TelemetryStatChip
import com.example.ui.components.VoiceWaveformBar
import com.example.ui.theme.KikoAmber
import com.example.ui.theme.KikoAzure
import com.example.ui.theme.KikoCyan
import com.example.ui.theme.KikoEmerald
import com.example.ui.theme.KikoTextMuted
import com.example.ui.theme.KikoTextPrimary
import com.example.ui.theme.KikoTextSecondary
import com.example.update.UpdateRepository
import com.example.update.UpdateUiState
import com.example.viewmodel.CoreReactorPhase
import com.example.viewmodel.KikoMainViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HudDashboardScreen(
    viewModel: KikoMainViewModel,
    updateRepository: UpdateRepository,
    onNavigateToCommand: (String?) -> Unit,
    onNavigateToVision: () -> Unit,
    onNavigateToTeams: () -> Unit,
    onNavigateToUpdates: () -> Unit,
) {
    val context = LocalContext.current
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedTeam by viewModel.selectedTeam.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val torchEnabled by viewModel.torchEnabled.collectAsStateWithLifecycle()
    val continuousVoice by viewModel.continuousVoice.collectAsStateWithLifecycle()
    val activitiesState by viewModel.activitiesState.collectAsStateWithLifecycle()

    val isListening by viewModel.voiceEngine.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.voiceEngine.isSpeaking.collectAsStateWithLifecycle()
    val waveformBars by viewModel.voiceEngine.waveformBars.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceEngine.partialTranscript.collectAsStateWithLifecycle()
    val voiceStatusNote by viewModel.voiceEngine.voiceStatusNote.collectAsStateWithLifecycle()
    val updateUiState by updateRepository.uiState.collectAsStateWithLifecycle()
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceCommand()
        }
    }

    val reactorPhase = when {
        isListening -> CoreReactorPhase.LISTENING
        isThinking -> CoreReactorPhase.THINKING
        isSpeaking -> CoreReactorPhase.SPEAKING
        !diagnostics.isOnline -> CoreReactorPhase.OFFLINE
        else -> CoreReactorPhase.IDLE
    }

    val phaseText = when (reactorPhase) {
        CoreReactorPhase.LISTENING -> "LISTENING"
        CoreReactorPhase.THINKING -> "PROCESSING"
        CoreReactorPhase.SPEAKING -> "VOCALIZING"
        CoreReactorPhase.OFFLINE -> "OFFLINE"
        CoreReactorPhase.IDLE -> "STANDBY"
    }

    val triggerVoice: () -> Unit = {
        if (isListening || isSpeaking) {
            viewModel.stopVoiceCommand()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                viewModel.startVoiceCommand()
            } else {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            KikoLogoHeader(
                subtitle = selectedTeam.title,
                isOnline = diagnostics.isOnline,
                providerLabel = selectedProvider.label.substringBefore(" "),
                selectedLanguage = selectedLanguage,
                onCycleLanguage = {
                    val next = when (selectedLanguage) {
                        KikoLanguage.AUTO -> KikoLanguage.ENGLISH
                        KikoLanguage.ENGLISH -> KikoLanguage.URDU
                        KikoLanguage.URDU -> KikoLanguage.AUTO
                    }
                    viewModel.setLanguage(next)
                },
                modifier = Modifier.widthIn(max = 800.dp),
            )
        }

        val availableManifest = when (val s = updateUiState) {
            is UpdateUiState.UpdateAvailable -> if (!s.isMandatory) s.manifest else null
            is UpdateUiState.ReadyToInstall -> if (!s.isMandatory) s.manifest else null
            else -> null
        }
        if (availableManifest != null && !bannerDismissed) {
            item {
                OptionalUpdateBanner(
                    manifest = availableManifest,
                    onOpenUpdates = onNavigateToUpdates,
                    onDismiss = { bannerDismissed = true },
                    modifier = Modifier.widthIn(max = 800.dp),
                )
            }
        }

        // Holographic Arc Core + Live Waveform
        item {
            HudGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp),
                borderColor = KikoCyan.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusIndicatorPill(label = voiceStatusNote, color = KikoCyan)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Hands-Free",
                                style = MaterialTheme.typography.labelSmall,
                                color = KikoTextSecondary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = continuousVoice,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        val hasMic = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasMic) viewModel.toggleContinuousVoice(true)
                                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.toggleContinuousVoice(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = KikoCyan,
                                    checkedTrackColor = KikoCyan.copy(alpha = 0.35f),
                                ),
                                modifier = Modifier.testTag("hands_free_switch"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    ArcCoreReactor(
                        phase = reactorPhase,
                        statusLabel = phaseText,
                        size = 196.dp,
                        onClick = triggerVoice,
                    )

                    if (partialTranscript.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "“$partialTranscript”",
                            style = MaterialTheme.typography.bodyLarge,
                            color = KikoEmerald,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    VoiceWaveformBar(
                        bars = waveformBars,
                        isActive = isListening || isSpeaking,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = triggerVoice,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListening || isSpeaking) KikoAmber else KikoCyan,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("voice_activate_button"),
                        ) {
                            Icon(
                                imageVector = if (isListening || isSpeaking) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = "Toggle voice core",
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isListening -> "Stop Listening"
                                    isSpeaking -> "Mute KIKO"
                                    else -> "Speak to KIKO"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        OutlinedButton(
                            onClick = { onNavigateToCommand(null) },
                            border = BorderStroke(1.dp, KikoCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("open_command_center_button"),
                        ) {
                            Text("Command", color = KikoCyan)
                        }
                    }
                }
            }
        }

        // System Monitoring Telemetry Grid
        item {
            HudGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SYSTEM DIAGNOSTICS",
                        style = MaterialTheme.typography.labelLarge,
                        color = KikoCyan,
                    )
                    Text(
                        text = "${diagnostics.deviceModel} • ${diagnostics.androidVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = KikoTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TelemetryStatChip(
                        label = "BATTERY",
                        value = "${diagnostics.batteryPercent}% ${if (diagnostics.isCharging) "⚡" else ""}",
                        accent = if (diagnostics.batteryPercent < 20) KikoAmber else KikoEmerald,
                        modifier = Modifier.weight(1f),
                    )
                    TelemetryStatChip(
                        label = "RAM FREE",
                        value = "${diagnostics.availableRamMb}/${diagnostics.totalRamMb} MB",
                        accent = if (diagnostics.isLowMemory) KikoAmber else KikoCyan,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TelemetryStatChip(
                        label = "STORAGE",
                        value = "${"%.1f".format(diagnostics.storageFreeGb)} GB Free",
                        accent = KikoAzure,
                        modifier = Modifier.weight(1f),
                    )
                    TelemetryStatChip(
                        label = "APP HEAP",
                        value = "${diagnostics.appHeapUsedMb}/${diagnostics.appHeapMaxMb} MB",
                        accent = KikoCyan,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Floating Quick Tactical Controls
        item {
            HudGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp),
            ) {
                Text(
                    text = "TACTICAL QUICK ACTIONS",
                    style = MaterialTheme.typography.labelLarge,
                    color = KikoCyan,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { viewModel.toggleTorch() },
                        label = { Text(if (torchEnabled) "Torch: ON" else "Torch: OFF") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                contentDescription = null,
                                tint = if (torchEnabled) KikoAmber else KikoCyan,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(labelColor = KikoTextPrimary),
                        modifier = Modifier.testTag("quick_torch_chip"),
                    )
                    AssistChip(
                        onClick = onNavigateToVision,
                        label = { Text("Vision Scan") },
                        leadingIcon = { Icon(Icons.Filled.CameraAlt, null, tint = KikoCyan) },
                    )
                    AssistChip(
                        onClick = { viewModel.runAutomationRoutine("Morning Briefing") },
                        label = { Text("Briefing") },
                        leadingIcon = { Icon(Icons.Filled.NotificationsActive, null, tint = KikoEmerald) },
                    )
                    AssistChip(
                        onClick = {
                            viewModel.selectTeam(KikoTeam.CODING)
                            onNavigateToCommand("Generate a clean Kotlin coroutine helper optimized for 4GB RAM")
                        },
                        label = { Text("Code Assist") },
                        leadingIcon = { Icon(Icons.Filled.Code, null, tint = KikoCyan) },
                    )
                    AssistChip(
                        onClick = {
                            viewModel.setLanguage(KikoLanguage.URDU)
                            onNavigateToCommand("السلام علیکم کیکو، سسٹم کی حالت بتائیں")
                        },
                        label = { Text("اردو بریفنگ") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = KikoAmber) },
                    )
                    AssistChip(
                        onClick = onNavigateToTeams,
                        label = { Text("12 AI Teams") },
                        leadingIcon = { Icon(Icons.Filled.Bolt, null, tint = KikoAzure) },
                    )
                    AssistChip(
                        onClick = { viewModel.runAutomationRoutine("Security Sweep") },
                        label = { Text("Security Sweep") },
                        leadingIcon = { Icon(Icons.Filled.Security, null, tint = KikoEmerald) },
                    )
                }
            }
        }

        // Recent Activity History
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ACTIVITY & TELEMETRY LOG",
                    style = MaterialTheme.typography.labelLarge,
                    color = KikoCyan,
                )
                Text(
                    text = "Refresh",
                    style = MaterialTheme.typography.labelMedium,
                    color = KikoAzure,
                    modifier = Modifier
                        .clickable { viewModel.refreshDiagnostics() }
                        .padding(8.dp),
                )
            }
        }

        when (val state = activitiesState) {
            is UiState.Loading -> {
                item {
                    HudGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
                        Text("Synchronizing activity stream…", color = KikoTextSecondary)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    HudGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
                        Text("Activity telemetry notice: ${state.message}", color = KikoAmber)
                    }
                }
            }
            is UiState.Success -> {
                val logs = state.data.take(8)
                if (logs.isEmpty()) {
                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
                            Text(
                                text = "No telemetry events recorded yet. Tap Speak to KIKO or run a Tactical Quick Action.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KikoTextSecondary,
                            )
                        }
                    }
                } else {
                    items(logs, key = { it.id }) { log ->
                        HudGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = log.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KikoTextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusIndicatorPill(
                                    label = log.teamId,
                                    color = if (log.status == "ALERT") KikoAmber else KikoCyan,
                                )
                            }
                            if (log.detail.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = log.detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KikoTextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
