package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AndroidControlManager
import com.example.model.KikoTeam
import com.example.ui.components.CodePreviewCard
import com.example.ui.components.HudGlassCard
import com.example.ui.components.StatusIndicatorPill
import com.example.ui.theme.KikoAmber
import com.example.ui.theme.KikoAzure
import com.example.ui.theme.KikoCyan
import com.example.ui.theme.KikoEmerald
import com.example.ui.theme.KikoOutline
import com.example.ui.theme.KikoSurface
import com.example.ui.theme.KikoSurfaceElevated
import com.example.ui.theme.KikoTextMuted
import com.example.ui.theme.KikoTextPrimary
import com.example.ui.theme.KikoTextSecondary
import com.example.viewmodel.KikoMainViewModel
import com.example.viewmodel.UiState

@Composable
fun CommandCenterScreen(
    viewModel: KikoMainViewModel,
    initialPrompt: String? = null,
    onConsumeInitialPrompt: () -> Unit = {},
) {
    val context = LocalContext.current
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val selectedTeam by viewModel.selectedTeam.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val autoSpeak by viewModel.autoSpeak.collectAsStateWithLifecycle()
    val pendingMemory by viewModel.pendingMemorySuggestion.collectAsStateWithLifecycle()
    val isListening by viewModel.voiceEngine.isListening.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceCommand()
    }

    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            viewModel.sendCommand(initialPrompt)
            onConsumeInitialPrompt()
        }
    }

    val messages = (chatState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size, streamingReply) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    val submitCommand: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotEmpty()) {
            inputText = ""
            viewModel.sendCommand(text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header + Auto-Speak Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 820.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "COMMAND CENTER",
                    style = MaterialTheme.typography.titleLarge,
                    color = KikoCyan,
                )
                Text(
                    text = "Active Team: ${selectedTeam.title} (${selectedTeam.urduTitle})",
                    style = MaterialTheme.typography.labelSmall,
                    color = KikoTextSecondary,
                )
            }
            IconButton(
                onClick = { viewModel.setAutoSpeak(!autoSpeak) },
                modifier = Modifier.testTag("toggle_auto_speak_button"),
            ) {
                Icon(
                    imageVector = if (autoSpeak) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Toggle automatic voice response",
                    tint = if (autoSpeak) KikoCyan else KikoTextMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Team Selector Pill Strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 820.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(KikoTeam.entries) { team ->
                val selected = team == selectedTeam
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectTeam(team) },
                    label = {
                        Text(
                            text = team.title.removeSuffix(" Team"),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KikoCyan.copy(alpha = 0.2f),
                        selectedLabelColor = KikoCyan,
                        labelColor = KikoTextSecondary,
                    ),
                )
            }
        }

        // User-Approved Memory Suggestion Banner
        if (pendingMemory != null) {
            val suggestion = pendingMemory!!
            Spacer(modifier = Modifier.height(8.dp))
            HudGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 820.dp),
                borderColor = KikoAmber,
            ) {
                Text(
                    text = "MEMORY APPROVAL REQUEST",
                    style = MaterialTheme.typography.labelMedium,
                    color = KikoAmber,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Save to personal memory: “${suggestion.content}”?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KikoTextPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.approvePendingMemory() },
                        colors = ButtonDefaults.buttonColors(containerColor = KikoEmerald),
                    ) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve & Save")
                    }
                    OutlinedButton(onClick = { viewModel.dismissPendingMemory() }) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dismiss")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Conversation Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 820.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && streamingReply == null) {
                item {
                    HudGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = KikoCyan.copy(alpha = 0.4f),
                    ) {
                        Text(
                            text = "KIKO NEURAL LINK READY",
                            style = MaterialTheme.typography.labelLarge,
                            color = KikoCyan,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Good day. Ask me in English or اردو, trigger Android device controls, request code modules, or analyze a topic.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KikoTextSecondary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val samples = listOf(
                                "System status & 4GB RAM check",
                                "اردو میں اپنا تعارف کروائیں",
                                "Turn on flashlight",
                                "Write a Kotlin ViewModel state pattern",
                            )
                            items(samples) { sample ->
                                AssistChip(
                                    onClick = { viewModel.sendCommand(sample) },
                                    label = { Text(sample, style = MaterialTheme.typography.labelMedium) },
                                )
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "USER"
                val isSystem = msg.role == "SYSTEM"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 640.dp),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp,
                        ),
                        color = when {
                            isUser -> KikoAzure.copy(alpha = 0.24f)
                            isSystem -> KikoAmber.copy(alpha = 0.14f)
                            else -> KikoSurface.copy(alpha = 0.92f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isUser -> KikoAzure.copy(alpha = 0.65f)
                                isSystem -> KikoAmber
                                else -> KikoCyan.copy(alpha = 0.35f)
                            }
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isUser) "YOU" else "KIKO • ${msg.teamId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) KikoAzure else KikoCyan,
                                )
                                StatusIndicatorPill(
                                    label = msg.provider,
                                    color = if (msg.provider == "OFFLINE") KikoAmber else KikoEmerald,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = KikoTextPrimary,
                            )
                            if (!msg.codeSnippet.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                CodePreviewCard(
                                    code = msg.codeSnippet,
                                    onCopy = {
                                        val control = AndroidControlManager(context)
                                        control.copyToClipboard("KIKO Code", msg.codeSnippet)
                                    },
                                    onStageProposal = {
                                        viewModel.stageCodeSnippetAsProposal(msg.codeSnippet)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (streamingReply != null || isThinking) {
                item {
                    HudGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = KikoCyan,
                    ) {
                        Text(
                            text = "KIKO STREAMING…",
                            style = MaterialTheme.typography.labelSmall,
                            color = KikoCyan,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = streamingReply?.ifBlank { "Synthesizing neural response…" }
                                ?: "Synthesizing neural response…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = KikoTextPrimary,
                        )
                    }
                }
            }
        }

        // Composer Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 820.dp)
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = {
                    if (isListening) {
                        viewModel.stopVoiceCommand()
                    } else {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) viewModel.startVoiceCommand()
                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.testTag("command_mic_button"),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = "Voice command input",
                    tint = if (isListening) KikoAmber else KikoCyan,
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "Ask KIKO in English or اردو…",
                        color = KikoTextMuted,
                    )
                },
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitCommand() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KikoCyan,
                    unfocusedBorderColor = KikoOutline,
                    focusedContainerColor = KikoSurfaceElevated,
                    unfocusedContainerColor = KikoSurface,
                    focusedTextColor = KikoTextPrimary,
                    unfocusedTextColor = KikoTextPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
            )

            Button(
                onClick = submitCommand,
                enabled = inputText.isNotBlank() && !isThinking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KikoCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("command_send_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send command",
                )
            }
        }
    }
}
