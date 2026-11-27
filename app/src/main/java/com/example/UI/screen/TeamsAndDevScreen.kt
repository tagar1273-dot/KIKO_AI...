package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AndroidControlManager
import com.example.model.KikoTeam
import com.example.ui.components.CodePreviewCard
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
import com.example.viewmodel.KikoMainViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamsAndDevScreen(
    viewModel: KikoMainViewModel,
    onDispatchTeamPrompt: (KikoTeam, String) -> Unit,
) {
    val context = LocalContext.current
    val selectedTeam by viewModel.selectedTeam.collectAsStateWithLifecycle()
    val proposalsState by viewModel.proposalsState.collectAsStateWithLifecycle()
    val isGeneratingProposal by viewModel.isGeneratingProposal.collectAsStateWithLifecycle()

    var subTab by rememberSaveable { mutableIntStateOf(0) }
    var devSpec by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp)) {
            Text(
                text = "KIKO TEAMS & AUTONOMOUS DEV",
                style = MaterialTheme.typography.titleLarge,
                color = KikoCyan,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("12 AI Teams", "Autonomous Dev Hub").forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = subTab == idx,
                        onClick = { subTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = KikoCyan.copy(alpha = 0.2f),
                            activeContentColor = KikoCyan,
                            inactiveContentColor = KikoTextSecondary,
                        ),
                    ) {
                        Text(label)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (subTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(KikoTeam.entries, key = { it.id }) { team ->
                    val active = team == selectedTeam
                    HudGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (active) KikoCyan else KikoOutline,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = team.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (active) KikoCyan else KikoTextPrimary,
                                )
                                Text(
                                    text = team.urduTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KikoAzure,
                                )
                            }
                            StatusIndicatorPill(
                                label = if (active) "ACTIVE" else "READY",
                                color = if (active) KikoEmerald else KikoCyan,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = team.roleSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = KikoTextSecondary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            team.capabilities.forEach { cap ->
                                StatusIndicatorPill(label = cap, color = KikoAzure)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                onDispatchTeamPrompt(
                                    team,
                                    "Activate ${team.title}: summarize your live status and how you can assist me right now."
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = KikoCyan)
                            Spacer(Modifier.width(6.dp))
                            Text("Dispatch ${team.title}", color = KikoCyan)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HudGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = KikoCyan.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = "AUTONOMOUS MODULE & PATCH GENERATOR",
                            style = MaterialTheme.typography.labelLarge,
                            color = KikoCyan,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Describe a Kotlin module, bug fix, or unit test. KIKO Development Team prepares a staged proposal—explicit user approval is required before marking any change approved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KikoTextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = devSpec,
                            onValueChange = { devSpec = it },
                            label = { Text("Module or error analysis prompt", color = KikoCyan) },
                            placeholder = {
                                Text(
                                    "e.g., Create a 4GB-RAM-safe LRU cache with unit tests",
                                    color = KikoTextMuted,
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KikoCyan,
                                unfocusedBorderColor = KikoOutline,
                                focusedContainerColor = KikoSurfaceElevated,
                                focusedTextColor = KikoTextPrimary,
                                unfocusedTextColor = KikoTextPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dev_spec_input"),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val spec = devSpec.trim()
                                if (spec.isNotEmpty()) {
                                    devSpec = ""
                                    viewModel.generateDevProposal(spec)
                                }
                            },
                            enabled = devSpec.isNotBlank() && !isGeneratingProposal,
                            colors = ButtonDefaults.buttonColors(containerColor = KikoCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("generate_proposal_button"),
                        ) {
                            if (isGeneratingProposal) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Architecting Proposal…")
                            } else {
                                Icon(Icons.Filled.AutoAwesome, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Generate Staged Proposal")
                            }
                        }
                    }
                }

                when (val state = proposalsState) {
                    is UiState.Loading -> {
                        item {
                            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text("Loading staged proposals…", color = KikoTextSecondary)
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = KikoCrimson) {
                                Text("Error loading proposals: ${state.message}", color = KikoCrimson)
                            }
                        }
                    }
                    is UiState.Success -> {
                        val list = state.data
                        if (list.isEmpty()) {
                            item {
                                HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "No staged proposals yet. Generate one above to review code with KIKO's approval gate.",
                                        color = KikoTextSecondary,
                                    )
                                }
                            }
                        } else {
                            items(list, key = { it.id }) { proposal ->
                                val statusColor = when (proposal.status) {
                                    "APPROVED" -> KikoEmerald
                                    "REJECTED" -> KikoCrimson
                                    else -> KikoAmber
                                }
                                HudGlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    borderColor = statusColor.copy(alpha = 0.55f),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(proposal.title, style = MaterialTheme.typography.titleMedium, color = KikoTextPrimary)
                                            Text(proposal.moduleName, style = MaterialTheme.typography.labelSmall, color = KikoCyan)
                                        }
                                        StatusIndicatorPill(label = proposal.status, color = statusColor)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(proposal.summary, style = MaterialTheme.typography.bodyMedium, color = KikoTextSecondary)
                                    Spacer(Modifier.height(8.dp))
                                    CodePreviewCard(
                                        code = proposal.codePreview,
                                        onCopy = {
                                            AndroidControlManager(context).copyToClipboard(
                                                proposal.moduleName,
                                                proposal.codePreview,
                                            )
                                        },
                                    )
                                    if (proposal.status == "PENDING_APPROVAL") {
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Button(
                                                onClick = { viewModel.decideDevProposal(proposal, true) },
                                                colors = ButtonDefaults.buttonColors(containerColor = KikoEmerald),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("approve_proposal_${proposal.id}"),
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Approve")
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.decideDevProposal(proposal, false) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("reject_proposal_${proposal.id}"),
                                            ) {
                                                Icon(Icons.Filled.Close, null, tint = KikoCrimson, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Reject", color = KikoCrimson)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
