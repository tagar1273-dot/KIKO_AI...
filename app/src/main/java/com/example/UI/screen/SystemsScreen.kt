package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AndroidControlManager
import com.example.core.signOutUser
import com.example.model.AiProviderType
import com.example.model.KikoLanguage
import com.example.model.MemoryEntry
import com.example.model.SmartReminder
import com.example.ui.components.HudGlassCard
import com.example.ui.components.StatusIndicatorPill
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
import com.example.update.UpdateRepository
import com.example.update.UpdateSettingsSnapshot
import com.example.viewmodel.KikoMainViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemsScreen(
    viewModel: KikoMainViewModel,
    updateRepository: UpdateRepository,
    onSignedOut: () -> Unit,
    initialSubTab: Int? = null,
    onConsumeInitialSubTab: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val controlManager = remember(context) { AndroidControlManager(context) }

    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val localConfig by viewModel.localProviderConfig.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val torchEnabled by viewModel.torchEnabled.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val certificationChecks by viewModel.certificationChecks.collectAsStateWithLifecycle()
    val updateUiState by updateRepository.uiState.collectAsStateWithLifecycle()
    val updateStatusMessage by updateRepository.statusMessage.collectAsStateWithLifecycle()
    val updateSettings by updateRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = UpdateSettingsSnapshot())

    var subTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(initialSubTab) {
        if (initialSubTab != null && initialSubTab in 0..3) {
            subTab = initialSubTab
            onConsumeInitialSubTab()
        }
    }

    // Memory Form State
    var memTitle by rememberSaveable { mutableStateOf("") }
    var memContent by rememberSaveable { mutableStateOf("") }
    var memCategory by rememberSaveable { mutableStateOf("FACT") }

    // Reminder Form State
    var remTitle by rememberSaveable { mutableStateOf("") }
    var remDue by rememberSaveable { mutableStateOf("Today • 18:00") }
    var remPriority by rememberSaveable { mutableStateOf("NORMAL") }

    // Config Form State
    val profile = (userProfileState as? UiState.Success)?.data
    var providerChoice by remember { mutableStateOf(selectedProvider) }
    var endpointText by rememberSaveable { mutableStateOf("") }
    var modelText by rememberSaveable { mutableStateOf("gemini-3.8-flash") }
    var tokenText by rememberSaveable { mutableStateOf("") }
    var pitchSlider by rememberSaveable { mutableFloatStateOf(1.0f) }
    var rateSlider by rememberSaveable { mutableFloatStateOf(1.0f) }

    LaunchedEffect(profile, localConfig) {
        if (profile != null) {
            providerChoice = AiProviderType.fromId(profile.aiProvider)
            endpointText = profile.backendEndpoint
            modelText = profile.backendModel.ifBlank { "gemini-3.8-flash" }
            pitchSlider = profile.voicePitch.toFloat()
            rateSlider = profile.voiceRate.toFloat()
        }
        if (localConfig.backendBearerToken.isNotBlank() && tokenText.isBlank()) {
            tokenText = localConfig.backendBearerToken
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp)) {
            Text(
                text = "SYSTEMS & MEMORY MATRIX",
                style = MaterialTheme.typography.titleLarge,
                color = KikoCyan,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Memory", "Auto", "Controls", "Config").forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = subTab == idx,
                        onClick = { subTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 4),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = KikoCyan.copy(alpha = 0.2f),
                            activeContentColor = KikoCyan,
                            inactiveContentColor = KikoTextSecondary,
                        ),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (subTab) {
            // TAB 0: PERSONAL MEMORY & CONTEXT
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = KikoCyan.copy(alpha = 0.45f)) {
                            Text("ADD APPROVED MEMORY", style = MaterialTheme.typography.labelLarge, color = KikoCyan)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = memTitle,
                                onValueChange = { memTitle = it },
                                label = { Text("Title", color = KikoCyan) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KikoCyan,
                                    unfocusedBorderColor = KikoOutline,
                                    focusedContainerColor = KikoSurfaceElevated,
                                    focusedTextColor = KikoTextPrimary,
                                    unfocusedTextColor = KikoTextPrimary,
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("memory_title_input"),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = memContent,
                                onValueChange = { memContent = it },
                                label = { Text("Context / Fact", color = KikoCyan) },
                                minLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KikoCyan,
                                    unfocusedBorderColor = KikoOutline,
                                    focusedContainerColor = KikoSurfaceElevated,
                                    focusedTextColor = KikoTextPrimary,
                                    unfocusedTextColor = KikoTextPrimary,
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("memory_content_input"),
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MemoryEntry.VALID_CATEGORIES.forEach { cat ->
                                    FilterChip(
                                        selected = memCategory == cat,
                                        onClick = { memCategory = cat },
                                        label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (memTitle.isNotBlank() && memContent.isNotBlank()) {
                                        viewModel.addMemory(memTitle, memContent, memCategory, pinned = true)
                                        memTitle = ""
                                        memContent = ""
                                    }
                                },
                                enabled = memTitle.isNotBlank() && memContent.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = KikoCyan),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_memory_button"),
                            ) {
                                Icon(Icons.Filled.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Commit to Memory Bank")
                            }
                        }
                    }

                    when (val state = memoriesState) {
                        is UiState.Loading -> item { Text("Loading memories…", color = KikoTextSecondary) }
                        is UiState.Error -> item { Text(state.message, color = KikoCrimson) }
                        is UiState.Success -> {
                            if (state.data.isEmpty()) {
                                item {
                                    HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "Memory Bank empty. Save personal preferences, project details, or protocols so KIKO remembers your context.",
                                            color = KikoTextSecondary,
                                        )
                                    }
                                }
                            } else {
                                items(state.data, key = { it.id }) { mem ->
                                    HudGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        borderColor = if (mem.pinned) KikoCyan else KikoOutline,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(mem.title, style = MaterialTheme.typography.titleMedium, color = KikoTextPrimary)
                                                StatusIndicatorPill(label = "${mem.category} • ${mem.source}", color = KikoAzure)
                                            }
                                            Row {
                                                IconButton(onClick = { viewModel.toggleMemoryPin(mem) }) {
                                                    Icon(
                                                        Icons.Filled.PushPin,
                                                        "Pin memory",
                                                        tint = if (mem.pinned) KikoCyan else KikoTextMuted,
                                                    )
                                                }
                                                IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                                    Icon(Icons.Filled.Delete, "Delete memory", tint = KikoCrimson)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(mem.content, style = MaterialTheme.typography.bodyMedium, color = KikoTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: SMART REMINDERS & AUTOMATION
            1 -> {
                val hasNotifPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED

                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        item {
                            HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = KikoAmber) {
                                Text("NOTIFICATION ACCESS", style = MaterialTheme.typography.labelLarge, color = KikoAmber)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Allow notification permission so KIKO Automation Team can deliver smart reminders and diagnostic alerts.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KikoTextSecondary,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = KikoAmber),
                                ) {
                                    Text("Enable Notifications")
                                }
                            }
                        }
                    }

                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("ONE-TAP AUTOMATION ROUTINES", style = MaterialTheme.typography.labelLarge, color = KikoCyan)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Morning Briefing", "Security Sweep", "4 GB RAM Trim").forEach { routine ->
                                    AssistChip(
                                        onClick = { viewModel.runAutomationRoutine(routine) },
                                        label = { Text(routine) },
                                        leadingIcon = { Icon(Icons.Filled.NotificationsActive, null, tint = KikoEmerald) },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = KikoCyan.copy(alpha = 0.45f)) {
                            Text("CREATE SMART REMINDER", style = MaterialTheme.typography.labelLarge, color = KikoCyan)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = remTitle,
                                onValueChange = { remTitle = it },
                                label = { Text("Reminder Title", color = KikoCyan) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KikoCyan,
                                    unfocusedBorderColor = KikoOutline,
                                    focusedContainerColor = KikoSurfaceElevated,
                                    focusedTextColor = KikoTextPrimary,
                                    unfocusedTextColor = KikoTextPrimary,
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("reminder_title_input"),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = remDue,
                                onValueChange = { remDue = it },
                                label = { Text("Schedule / Trigger", color = KikoCyan) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KikoCyan,
                                    unfocusedBorderColor = KikoOutline,
                                    focusedContainerColor = KikoSurfaceElevated,
                                    focusedTextColor = KikoTextPrimary,
                                    unfocusedTextColor = KikoTextPrimary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmartReminder.VALID_PRIORITIES.forEach { prio ->
                                    FilterChip(
                                        selected = remPriority == prio,
                                        onClick = { remPriority = prio },
                                        label = { Text(prio, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (remTitle.isNotBlank()) {
                                        viewModel.addReminder(remTitle, "Scheduled via KIKO Automation Team", remDue, remPriority)
                                        remTitle = ""
                                    }
                                },
                                enabled = remTitle.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = KikoCyan),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_reminder_button"),
                            ) {
                                Icon(Icons.Filled.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Arm Smart Reminder")
                            }
                        }
                    }

                    when (val state = remindersState) {
                        is UiState.Loading -> item { Text("Loading reminders…", color = KikoTextSecondary) }
                        is UiState.Error -> item { Text(state.message, color = KikoCrimson) }
                        is UiState.Success -> {
                            items(state.data, key = { it.id }) { rem ->
                                HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = rem.completed,
                                            onCheckedChange = { viewModel.toggleReminder(rem) },
                                            colors = CheckboxDefaults.colors(checkedColor = KikoEmerald),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rem.title, style = MaterialTheme.typography.titleMedium, color = KikoTextPrimary)
                                            Text("${rem.dueLabel} • ${rem.priority}", style = MaterialTheme.typography.labelSmall, color = KikoTextSecondary)
                                        }
                                        IconButton(onClick = { viewModel.deleteReminder(rem.id) }) {
                                            Icon(Icons.Filled.Delete, "Delete reminder", tint = KikoCrimson)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 2: ANDROID DEVICE CONTROLS & CERTIFICATION DIAGNOSTICS
            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = KikoCyan.copy(alpha = 0.45f)) {
                            Text("ANDROID DEVICE CONTROL API", style = MaterialTheme.typography.labelLarge, color = KikoCyan)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { viewModel.toggleTorch() },
                                    label = { Text(if (torchEnabled) "Flashlight ON" else "Flashlight OFF") },
                                    leadingIcon = {
                                        Icon(
                                            if (torchEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                            null,
                                            tint = if (torchEnabled) KikoAmber else KikoCyan,
                                        )
                                    },
                                )
                                AssistChip(
                                    onClick = { viewModel.executeControlAction { controlManager.adjustVolume(1) } },
                                    label = { Text("Volume +") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = KikoCyan) },
                                )
                                AssistChip(
                                    onClick = { viewModel.executeControlAction { controlManager.adjustVolume(-1) } },
                                    label = { Text("Volume -") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = KikoCyan) },
                                )
                                AssistChip(
                                    onClick = { viewModel.executeControlAction { controlManager.adjustVolume(0) } },
                                    label = { Text("Mute Toggle") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeMute, null, tint = KikoAmber) },
                                )
                                AssistChip(
                                    onClick = { viewModel.executeControlAction { controlManager.startTimer(300, "KIKO Focus Timer") } },
                                    label = { Text("5m Timer") },
                                    leadingIcon = { Icon(Icons.Filled.Timer, null, tint = KikoEmerald) },
                                )
                                AssistChip(
                                    onClick = { viewModel.executeControlAction { controlManager.setAlarm(7, 30, "KIKO Morning Briefing") } },
                                    label = { Text("07:30 Alarm") },
                                    leadingIcon = { Icon(Icons.Filled.Alarm, null, tint = KikoEmerald) },
                                )
                                AssistChip(
                                    onClick = { viewModel.executeControlAction { controlManager.openSettingsPanel(Settings.Panel.ACTION_WIFI, "Wi-Fi") } },
                                    label = { Text("Wi-Fi Panel") },
                                    leadingIcon = { Icon(Icons.Filled.Wifi, null, tint = KikoAzure) },
                                )
                                AssistChip(
                                    onClick = { viewModel.executeControlAction { controlManager.openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth") } },
                                    label = { Text("Bluetooth") },
                                    leadingIcon = { Icon(Icons.Filled.Bluetooth, null, tint = KikoAzure) },
                                )
                                AssistChip(
                                    onClick = { viewModel.executeControlAction { controlManager.openSettingsPanel(Settings.ACTION_DISPLAY_SETTINGS, "Display") } },
                                    label = { Text("Display") },
                                    leadingIcon = { Icon(Icons.Filled.DisplaySettings, null, tint = KikoAzure) },
                                )
                            }
                        }
                    }

                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("CERTIFICATION & DIAGNOSTIC AUDIT", style = MaterialTheme.typography.labelLarge, color = KikoCyan)
                                Text(
                                    text = "Re-Run",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KikoAzure,
                                    modifier = Modifier.clickable { viewModel.runCertificationAudit() }.padding(6.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            certificationChecks.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (item.passed) Icons.Filled.Verified else Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = if (item.passed) KikoEmerald else KikoAmber,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = KikoTextPrimary)
                                        Text(item.detail, style = MaterialTheme.typography.labelSmall, color = KikoTextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = KikoAmber.copy(alpha = 0.5f)) {
                            Text("HARDWARE, API & SERVICE DISCLOSURES", style = MaterialTheme.typography.labelLarge, color = KikoAmber)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "• Hardware Torch & Live CameraX: Requires physical camera/flash unit (${if (diagnostics.hasTorchHardware) "Flash detected" else "No flash unit reported on host"}). Zero-permission Photo Picker & Document Scan work on all devices.\n" +
                                    "• Voice Recognition: Uses Android SpeechRecognizer (${if (viewModel.voiceEngine.isRecognitionAvailable) "Available" else "Unavailable on current host; use text input + TTS"}).\n" +
                                    "• AI Providers: Built-in Gemini via Firebase AI Logic + App Check (no API key). Optional OpenAI/Custom providers require your own backend proxy URL so API keys never live inside the APK.\n" +
                                    "• Direct OTA Updates: Uses WorkManager + HTTPS manifest + SHA-256/signature verification + FileProvider & REQUEST_INSTALL_PACKAGES via Android's official package installer (direct/enterprise APK distribution).\n" +
                                    "• Sensors detected on host: ${diagnostics.sensorCount} • Battery Temp: ${diagnostics.batteryTempCelsius}°C.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KikoTextSecondary,
                            )
                        }
                    }
                }
            }

            // TAB 3: LANGUAGE, VOICE & AI PROVIDER CONFIGURATION
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        KikoUpdateSectionCard(
                            uiState = updateUiState,
                            statusMessage = updateStatusMessage,
                            settings = updateSettings,
                            repository = updateRepository,
                            onFeedback = { viewModel.showBanner(it) },
                        )
                    }

                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("MULTILINGUAL & VOICE SYNTHESIS", style = MaterialTheme.typography.labelLarge, color = KikoCyan)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KikoLanguage.entries.forEach { lang ->
                                    FilterChip(
                                        selected = selectedLanguage == lang,
                                        onClick = { viewModel.setLanguage(lang) },
                                        label = { Text(lang.label) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Voice Pitch: ${"%.2f".format(pitchSlider)}x", style = MaterialTheme.typography.labelMedium, color = KikoTextSecondary)
                            Slider(
                                value = pitchSlider,
                                onValueChange = { pitchSlider = it },
                                valueRange = 0.6f..1.6f,
                                colors = SliderDefaults.colors(thumbColor = KikoCyan, activeTrackColor = KikoCyan),
                            )
                            Text("Speech Rate: ${"%.2f".format(rateSlider)}x", style = MaterialTheme.typography.labelMedium, color = KikoTextSecondary)
                            Slider(
                                value = rateSlider,
                                onValueChange = { rateSlider = it },
                                valueRange = 0.6f..1.6f,
                                colors = SliderDefaults.colors(thumbColor = KikoCyan, activeTrackColor = KikoCyan),
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.voiceEngine.applyLanguageAndTone(selectedLanguage, pitchSlider, rateSlider)
                                    val testLine = if (selectedLanguage == KikoLanguage.URDU) {
                                        "السلام علیکم، کیکو اے آئی مکمل طور پر فعال ہے۔"
                                    } else {
                                        "KIKO AI voice calibration nominal. All twelve specialized teams standing by."
                                    }
                                    viewModel.voiceEngine.speak(testLine, selectedLanguage)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.RecordVoiceOver, null, tint = KikoCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Test Voice Calibration", color = KikoCyan)
                            }
                        }
                    }

                    item {
                        HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = KikoCyan.copy(alpha = 0.45f)) {
                            Text("AI PROVIDER & BACKEND PROXY", style = MaterialTheme.typography.labelLarge, color = KikoCyan)
                            Spacer(Modifier.height(6.dp))
                            AiProviderType.entries.forEach { provider ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { providerChoice = provider }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = providerChoice == provider,
                                        onClick = { providerChoice = provider },
                                        colors = RadioButtonDefaults.colors(selectedColor = KikoCyan),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(provider.label, style = MaterialTheme.typography.titleMedium, color = KikoTextPrimary)
                                        Text(provider.description, style = MaterialTheme.typography.labelSmall, color = KikoTextSecondary)
                                    }
                                }
                            }

                            if (providerChoice == AiProviderType.OPENAI_BACKEND || providerChoice == AiProviderType.CUSTOM_BACKEND) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = endpointText,
                                    onValueChange = { endpointText = it },
                                    label = { Text("Backend Relay URL (e.g. https://api.yourdomain.com)", color = KikoCyan) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = KikoCyan,
                                        unfocusedBorderColor = KikoOutline,
                                        focusedContainerColor = KikoSurfaceElevated,
                                        focusedTextColor = KikoTextPrimary,
                                        unfocusedTextColor = KikoTextPrimary,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = modelText,
                                    onValueChange = { modelText = it },
                                    label = { Text("Model ID (e.g. gpt-4o-mini)", color = KikoCyan) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = KikoCyan,
                                        unfocusedBorderColor = KikoOutline,
                                        focusedContainerColor = KikoSurfaceElevated,
                                        focusedTextColor = KikoTextPrimary,
                                        unfocusedTextColor = KikoTextPrimary,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = tokenText,
                                    onValueChange = { tokenText = it },
                                    label = { Text("Optional Backend Session Token (API keys stay on backend)", color = KikoCyan) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = KikoCyan,
                                        unfocusedBorderColor = KikoOutline,
                                        focusedContainerColor = KikoSurfaceElevated,
                                        focusedTextColor = KikoTextPrimary,
                                        unfocusedTextColor = KikoTextPrimary,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.saveProviderConfiguration(
                                        provider = providerChoice,
                                        endpoint = endpointText,
                                        modelName = modelText,
                                        bearerToken = tokenText,
                                        pitch = pitchSlider,
                                        rate = rateSlider,
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KikoCyan),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_config_button"),
                            ) {
                                Icon(Icons.Filled.CheckCircle, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save Configuration")
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = onSignedOut,
                                )
                            },
                            border = BorderStroke(1.dp, KikoCrimson.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = KikoCrimson)
                            Spacer(Modifier.width(8.dp))
                            Text("Disconnect Google Session", color = KikoCrimson)
                        }
                    }
                }
            }
        }
    }
}
