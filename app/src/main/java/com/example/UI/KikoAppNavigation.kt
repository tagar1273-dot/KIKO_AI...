package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.AndroidControlManager
import com.example.core.DeviceDiagnosticsManager
import com.example.core.KikoVoiceEngine
import com.example.data.KikoAiService
import com.example.data.KikoFirestoreRepository
import com.example.data.LocalPreferencesStore
import com.example.ui.components.HolographicBackground
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CommandCenterScreen
import com.example.ui.screens.HudDashboardScreen
import com.example.ui.screens.MandatoryUpdateScreen
import com.example.ui.screens.SystemsScreen
import com.example.ui.screens.TeamsAndDevScreen
import com.example.ui.screens.VisionScreen
import com.example.ui.theme.KikoCyan
import com.example.ui.theme.KikoSurface
import com.example.ui.theme.KikoTextMuted
import com.example.ui.theme.KikoVoid
import com.example.update.UpdateRepository
import com.example.update.UpdateSettingsSnapshot
import com.example.update.UpdateUiState
import com.example.viewmodel.KikoMainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class KikoNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HUD("hud_core", "HUD", Icons.Filled.Radar, Icons.Outlined.Radar),
    COMMAND("command_center", "Command", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    VISION("vision_hud", "Vision", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt),
    TEAMS("teams_dev", "Teams", Icons.Filled.Hub, Icons.Outlined.Hub),
    SYSTEMS("systems_matrix", "Systems", Icons.Filled.Memory, Icons.Outlined.Memory),
}

@Composable
fun KikoAppNavigation() {
    val context = LocalContext.current
    val updateRepository = remember(context) { UpdateRepository.getInstance(context.applicationContext) }
    val updateUiState by updateRepository.uiState.collectAsStateWithLifecycle()
    val updateSettings by updateRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = UpdateSettingsSnapshot())

    LaunchedEffect(updateRepository) {
        updateRepository.initializeOnLaunch()
    }

    val mandatoryManifest = when (val state = updateUiState) {
        is UpdateUiState.UpdateAvailable -> if (state.isMandatory) state.manifest else null
        is UpdateUiState.Downloading -> if (state.isMandatory) state.manifest else null
        is UpdateUiState.Verifying -> if (state.isMandatory) state.manifest else null
        is UpdateUiState.ReadyToInstall -> if (state.isMandatory) state.manifest else null
        is UpdateUiState.Failed -> if (state.isMandatory) state.manifest else null
        else -> null
    }

    if (mandatoryManifest != null) {
        MandatoryUpdateScreen(
            manifest = mandatoryManifest,
            uiState = updateUiState,
            settings = updateSettings,
            repository = updateRepository,
        )
        return
    }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            KikoAuthenticatedShell(
                currentUserId = uid,
                updateRepository = updateRepository,
                onSignedOut = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        }
    }
}

@Composable
private fun KikoAuthenticatedShell(
    currentUserId: String,
    updateRepository: UpdateRepository,
    onSignedOut: () -> Unit,
) {
    val viewModel: KikoMainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                KikoMainViewModel(
                    currentUserId = currentUserId,
                    repository = KikoFirestoreRepository(db),
                    aiService = KikoAiService(),
                    diagnosticsManager = DeviceDiagnosticsManager(app),
                    controlManager = AndroidControlManager(app),
                    voiceEngine = KikoVoiceEngine(app),
                    localPrefs = LocalPreferencesStore(app),
                )
            }
        }
    )

    var activeDest by rememberSaveable { mutableStateOf(KikoNavDestination.HUD) }
    var pendingCommandPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSystemsSubTab by rememberSaveable { mutableStateOf<Int?>(null) }
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    if (activeDest != KikoNavDestination.HUD) {
        BackHandler {
            activeDest = KikoNavDestination.HUD
        }
    }

    HolographicBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 640.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = KikoSurface.copy(alpha = 0.96f),
                            windowInsets = WindowInsets.navigationBars,
                        ) {
                            KikoNavDestination.entries.forEach { dest ->
                                val selected = dest == activeDest
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { activeDest = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = KikoCyan,
                                        selectedTextColor = KikoCyan,
                                        indicatorColor = KikoCyan.copy(alpha = 0.16f),
                                        unselectedIconColor = KikoTextMuted,
                                        unselectedTextColor = KikoTextMuted,
                                    ),
                                    modifier = Modifier.testTag("nav_${dest.route}"),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = KikoVoid.copy(alpha = 0.92f),
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            KikoNavDestination.entries.forEach { dest ->
                                val selected = dest == activeDest
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { activeDest = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = KikoCyan,
                                        selectedTextColor = KikoCyan,
                                        indicatorColor = KikoCyan.copy(alpha = 0.16f),
                                        unselectedIconColor = KikoTextMuted,
                                        unselectedTextColor = KikoTextMuted,
                                    ),
                                    modifier = Modifier.testTag("rail_${dest.route}"),
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (activeDest) {
                            KikoNavDestination.HUD -> HudDashboardScreen(
                                viewModel = viewModel,
                                updateRepository = updateRepository,
                                onNavigateToCommand = { prompt ->
                                    pendingCommandPrompt = prompt
                                    activeDest = KikoNavDestination.COMMAND
                                },
                                onNavigateToVision = { activeDest = KikoNavDestination.VISION },
                                onNavigateToTeams = { activeDest = KikoNavDestination.TEAMS },
                                onNavigateToUpdates = {
                                    pendingSystemsSubTab = 3
                                    activeDest = KikoNavDestination.SYSTEMS
                                },
                            )
                            KikoNavDestination.COMMAND -> CommandCenterScreen(
                                viewModel = viewModel,
                                initialPrompt = pendingCommandPrompt,
                                onConsumeInitialPrompt = { pendingCommandPrompt = null },
                            )
                            KikoNavDestination.VISION -> VisionScreen(viewModel = viewModel)
                            KikoNavDestination.TEAMS -> TeamsAndDevScreen(
                                viewModel = viewModel,
                                onDispatchTeamPrompt = { team, prompt ->
                                    viewModel.selectTeam(team)
                                    pendingCommandPrompt = prompt
                                    activeDest = KikoNavDestination.COMMAND
                                },
                            )
                            KikoNavDestination.SYSTEMS -> SystemsScreen(
                                viewModel = viewModel,
                                updateRepository = updateRepository,
                                onSignedOut = onSignedOut,
                                initialSubTab = pendingSystemsSubTab,
                                onConsumeInitialSubTab = { pendingSystemsSubTab = null },
                            )
                        }
                    }
                }
            }
        }
    }
}
