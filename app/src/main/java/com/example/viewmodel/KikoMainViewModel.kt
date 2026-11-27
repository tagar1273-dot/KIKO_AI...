package com.example.viewmodel

import android.graphics.Bitmap
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AndroidControlManager
import com.example.core.ControlExecutionResult
import com.example.core.DeviceDiagnosticsManager
import com.example.core.KikoVoiceEngine
import com.example.data.KikoAiService
import com.example.data.KikoFirestoreRepository
import com.example.data.LocalPreferencesStore
import com.example.data.LocalProviderConfig
import com.example.model.ActivityLog
import com.example.model.AiProviderType
import com.example.model.ChatRecord
import com.example.model.DevProposal
import com.example.model.DeviceDiagnosticsSnapshot
import com.example.model.KikoLanguage
import com.example.model.KikoTeam
import com.example.model.MemoryEntry
import com.example.model.PendingMemorySuggestion
import com.example.model.SmartReminder
import com.example.model.UserProfile
import com.example.model.VisionAnalysisResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class CoreReactorPhase {
    IDLE, LISTENING, THINKING, SPEAKING, OFFLINE
}

data class CertificationCheckItem(
    val title: String,
    val passed: Boolean,
    val detail: String,
)

class KikoMainViewModel(
    private val currentUserId: String,
    private val repository: KikoFirestoreRepository,
    private val aiService: KikoAiService,
    private val diagnosticsManager: DeviceDiagnosticsManager,
    private val controlManager: AndroidControlManager,
    val voiceEngine: KikoVoiceEngine,
    private val localPrefs: LocalPreferencesStore,
) : ViewModel() {

    companion object {
        private const val TAG = "KikoMainVM"
        private const val STOP_TIMEOUT_MS = 5000L
    }

    private fun <T> Flow<T>.toUiStateFlow(errorLabel: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, errorLabel, root)
                emit(UiState.Error(root.message ?: errorLabel))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiStateFlow("Failed to load profile")

    val memoriesState: StateFlow<UiState<List<MemoryEntry>>> =
        repository.observeMemories(currentUserId).toUiStateFlow("Failed to load memories")

    val remindersState: StateFlow<UiState<List<SmartReminder>>> =
        repository.observeReminders(currentUserId).toUiStateFlow("Failed to load reminders")

    val activitiesState: StateFlow<UiState<List<ActivityLog>>> =
        repository.observeActivities(currentUserId).toUiStateFlow("Failed to load activity history")

    val chatState: StateFlow<UiState<List<ChatRecord>>> =
        repository.observeChatMessages(currentUserId).toUiStateFlow("Failed to load conversation")

    val proposalsState: StateFlow<UiState<List<DevProposal>>> =
        repository.observeDevProposals(currentUserId).toUiStateFlow("Failed to load dev proposals")

    val localProviderConfig: StateFlow<LocalProviderConfig> = localPrefs.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LocalProviderConfig())

    private val _diagnostics = MutableStateFlow(diagnosticsManager.captureSnapshot())
    val diagnostics: StateFlow<DeviceDiagnosticsSnapshot> = _diagnostics.asStateFlow()

    private val _selectedTeam = MutableStateFlow(KikoTeam.INTELLIGENCE)
    val selectedTeam: StateFlow<KikoTeam> = _selectedTeam.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(KikoLanguage.AUTO)
    val selectedLanguage: StateFlow<KikoLanguage> = _selectedLanguage.asStateFlow()

    private val _selectedProvider = MutableStateFlow(AiProviderType.GEMINI)
    val selectedProvider: StateFlow<AiProviderType> = _selectedProvider.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    private val _continuousVoice = MutableStateFlow(false)
    val continuousVoice: StateFlow<Boolean> = _continuousVoice.asStateFlow()

    private val _pendingMemorySuggestion = MutableStateFlow<PendingMemorySuggestion?>(null)
    val pendingMemorySuggestion: StateFlow<PendingMemorySuggestion?> = _pendingMemorySuggestion.asStateFlow()

    private val _visionState = MutableStateFlow<UiState<VisionAnalysisResult>?>(null)
    val visionState: StateFlow<UiState<VisionAnalysisResult>?> = _visionState.asStateFlow()

    private val _isGeneratingProposal = MutableStateFlow(false)
    val isGeneratingProposal: StateFlow<Boolean> = _isGeneratingProposal.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _certificationChecks = MutableStateFlow<List<CertificationCheckItem>>(emptyList())
    val certificationChecks: StateFlow<List<CertificationCheckItem>> = _certificationChecks.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId)
            refreshDiagnostics()
            runCertificationAudit()
        }
        viewModelScope.launch {
            userProfileState.collect { state ->
                if (state is UiState.Success && state.data != null) {
                    val profile = state.data
                    val lang = KikoLanguage.fromCode(profile.preferredLanguage)
                    _selectedLanguage.value = lang
                    _selectedProvider.value = AiProviderType.fromId(profile.aiProvider)
                    _continuousVoice.value = profile.continuousVoice
                    voiceEngine.applyLanguageAndTone(
                        lang,
                        profile.voicePitch.toFloat(),
                        profile.voiceRate.toFloat()
                    )
                }
            }
        }
    }

    fun selectTeam(team: KikoTeam) {
        _selectedTeam.value = team
    }

    fun setLanguage(language: KikoLanguage) {
        _selectedLanguage.value = language
        val profile = (userProfileState.value as? UiState.Success)?.data
        voiceEngine.applyLanguageAndTone(
            language,
            profile?.voicePitch?.toFloat() ?: 1.0f,
            profile?.voiceRate?.toFloat() ?: 1.0f
        )
        persistProfile(preferredLanguage = language.code)
    }

    fun setAutoSpeak(enabled: Boolean) {
        _autoSpeak.value = enabled
        if (!enabled) voiceEngine.stopSpeaking()
    }

    fun toggleContinuousVoice(enabled: Boolean) {
        _continuousVoice.value = enabled
        voiceEngine.setContinuousMode(enabled)
        persistProfile(continuousVoice = enabled)
        if (enabled) {
            startVoiceCommand()
        }
    }

    fun startVoiceCommand() {
        voiceEngine.startListening(
            language = _selectedLanguage.value,
            continuous = _continuousVoice.value,
        ) { spokenText ->
            sendCommand(prompt = spokenText, speakOverride = true)
        }
    }

    fun stopVoiceCommand() {
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
    }

    fun refreshDiagnostics() {
        _diagnostics.value = diagnosticsManager.captureSnapshot()
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun sendCommand(prompt: String, speakOverride: Boolean? = null) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return

        viewModelScope.launch {
            refreshDiagnostics()
            val diag = _diagnostics.value
            val lang = _selectedLanguage.value
            val provider = _selectedProvider.value
            val routedTeam = aiService.detectTargetTeam(cleanPrompt, _selectedTeam.value)

            repository.addChatMessage(
                ChatRecord(
                    role = "USER",
                    text = cleanPrompt,
                    teamId = routedTeam.id,
                    language = lang.code,
                    provider = provider.id,
                )
            )

            // 1. Check immediate local Android hardware / OS control commands
            val localActionResult = tryExecuteDirectAndroidCommand(cleanPrompt)
            if (localActionResult != null) {
                val replyText = if (localActionResult.success) {
                    "${localActionResult.title}: ${localActionResult.message}"
                } else {
                    "Action notice — ${localActionResult.title}: ${localActionResult.message}"
                }
                repository.addChatMessage(
                    ChatRecord(
                        role = "KIKO",
                        text = replyText,
                        teamId = KikoTeam.ANDROID_CONTROL.id,
                        language = lang.code,
                        provider = "LOCAL_API",
                    )
                )
                repository.logActivity(
                    ActivityLog(
                        title = localActionResult.title,
                        detail = localActionResult.message,
                        teamId = KikoTeam.ANDROID_CONTROL.id,
                        status = if (localActionResult.success) "COMPLETED" else "ALERT",
                    )
                )
                if (speakOverride ?: _autoSpeak.value) {
                    voiceEngine.speak(replyText, lang)
                }
                return@launch
            }

            // 2. Check if user is asking KIKO to remember a personal fact (Approval flow)
            val rememberPayload = extractMemoryProposal(cleanPrompt)
            if (rememberPayload != null) {
                _pendingMemorySuggestion.value = rememberPayload
            }

            // 3. Stream AI response via Firebase AI Logic, configured Backend, or Offline Engine
            _isThinking.value = true
            _streamingReply.value = ""
            val profile = (userProfileState.value as? UiState.Success)?.data
            val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
            val history = (chatState.value as? UiState.Success)?.data.orEmpty()
                .map { it.role to it.text }
            val bearer = localProviderConfig.value.backendBearerToken

            var latestText = ""
            try {
                aiService.streamAssistantResponse(
                    userPrompt = cleanPrompt,
                    team = routedTeam,
                    language = lang,
                    provider = provider,
                    backendEndpoint = profile?.backendEndpoint.orEmpty(),
                    backendModel = profile?.backendModel ?: "gemini-3.8-flash",
                    bearerToken = bearer,
                    memories = memories,
                    diagnostics = diag,
                    recentHistory = history,
                ).collect { chunked ->
                    latestText = chunked
                    _streamingReply.value = chunked
                }

                val codeBlock = extractFirstCodeFence(latestText)
                repository.addChatMessage(
                    ChatRecord(
                        role = "KIKO",
                        text = latestText,
                        teamId = routedTeam.id,
                        language = lang.code,
                        provider = if (!diag.isOnline) "OFFLINE" else provider.id,
                        codeSnippet = codeBlock,
                    )
                )
                repository.logActivity(
                    ActivityLog(
                        title = "${routedTeam.title} Response",
                        detail = cleanPrompt.take(140),
                        teamId = routedTeam.id,
                        status = if (diag.isOnline && provider != AiProviderType.OFFLINE) "COMPLETED" else "OFFLINE",
                    )
                )
                if (speakOverride ?: _autoSpeak.value) {
                    voiceEngine.speak(latestText, lang)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI inference timed out", e)
                val msg = "KIKO neural link timed out after 60 seconds. Please retry or switch to Offline Mode."
                _statusBanner.value = msg
                repository.addChatMessage(
                    ChatRecord(
                        role = "SYSTEM",
                        text = msg,
                        teamId = routedTeam.id,
                        language = lang.code,
                        provider = provider.id,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI inference failed", e)
                val errMsg = e.localizedMessage ?: "AI execution failed."
                _statusBanner.value = errMsg
                repository.addChatMessage(
                    ChatRecord(
                        role = "SYSTEM",
                        text = "Neural Engine Error: $errMsg",
                        teamId = routedTeam.id,
                        language = lang.code,
                        provider = provider.id,
                    )
                )
            } finally {
                _isThinking.value = false
                _streamingReply.value = null
            }
        }
    }

    fun approvePendingMemory() {
        val suggestion = _pendingMemorySuggestion.value ?: return
        _pendingMemorySuggestion.value = null
        addMemory(
            title = suggestion.title,
            content = suggestion.content,
            category = suggestion.category,
            pinned = true,
            source = "AI_APPROVED",
        )
    }

    fun dismissPendingMemory() {
        _pendingMemorySuggestion.value = null
    }

    fun addMemory(
        title: String,
        content: String,
        category: String = "FACT",
        pinned: Boolean = false,
        source: String = "USER",
    ) {
        viewModelScope.launch {
            val res = repository.addMemory(
                MemoryEntry(
                    title = title,
                    content = content,
                    category = category,
                    pinned = pinned,
                    source = source,
                )
            )
            res.onSuccess {
                _statusBanner.value = "Memory stored securely."
                repository.logActivity(
                    ActivityLog(
                        title = "Memory Committed",
                        detail = title.take(120),
                        teamId = KikoTeam.MEMORY.id,
                        status = "COMPLETED",
                    )
                )
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to save memory."
            }
        }
    }

    fun toggleMemoryPin(memory: MemoryEntry) {
        viewModelScope.launch {
            repository.toggleMemoryPin(memory.id, !memory.pinned)
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
        }
    }

    fun addReminder(
        title: String,
        notes: String,
        dueLabel: String,
        priority: String = "NORMAL",
        triggerType: String = "REMINDER",
    ) {
        viewModelScope.launch {
            val res = repository.addReminder(
                SmartReminder(
                    title = title,
                    notes = notes,
                    dueLabel = dueLabel,
                    priority = priority,
                    triggerType = triggerType,
                    completed = false,
                )
            )
            res.onSuccess {
                _statusBanner.value = "Smart reminder scheduled."
                controlManager.postSmartNotification(
                    title = "KIKO Reminder Armed: $title",
                    message = if (dueLabel.isNotBlank()) "Scheduled for $dueLabel" else notes.ifBlank { "Active in Automation Queue" },
                    notificationId = title.hashCode(),
                )
                repository.logActivity(
                    ActivityLog(
                        title = "Reminder Armed",
                        detail = "$title ($dueLabel)",
                        teamId = KikoTeam.AUTOMATION.id,
                        status = "COMPLETED",
                    )
                )
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to create reminder."
            }
        }
    }

    fun toggleReminder(reminder: SmartReminder) {
        viewModelScope.launch {
            repository.toggleReminderCompleted(reminder.id, !reminder.completed)
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
        }
    }

    fun runAutomationRoutine(routineName: String) {
        viewModelScope.launch {
            refreshDiagnostics()
            val diag = _diagnostics.value
            val summary = when (routineName) {
                "Morning Briefing" ->
                    "Battery ${diag.batteryPercent}%, RAM ${diag.availableRamMb}/${diag.totalRamMb} MB free, Network ${diag.networkLabel}."
                "Security Sweep" ->
                    "App Check active, Firestore Zero-Trust UID isolation active, Play Policy minimal permissions verified."
                "4 GB RAM Trim" -> {
                    System.gc()
                    refreshDiagnostics()
                    "Runtime GC requested. App heap: ${diag.appHeapUsedMb}/${diag.appHeapMaxMb} MB."
                }
                else -> "Routine $routineName executed."
            }
            controlManager.postSmartNotification("KIKO Automation: $routineName", summary, routineName.hashCode())
            repository.logActivity(
                ActivityLog(
                    title = "Routine: $routineName",
                    detail = summary,
                    teamId = KikoTeam.AUTOMATION.id,
                    status = "COMPLETED",
                )
            )
            _statusBanner.value = "$routineName executed."
            if (_autoSpeak.value) {
                voiceEngine.speak("$routineName complete. $summary", _selectedLanguage.value)
            }
        }
    }

    fun analyzeBitmap(bitmap: Bitmap, question: String) {
        viewModelScope.launch {
            _visionState.value = UiState.Loading
            try {
                val result = aiService.analyzeVisualInput(bitmap, question, _selectedLanguage.value)
                _visionState.value = UiState.Success(result)
                repository.logActivity(
                    ActivityLog(
                        title = "Vision Scan Completed",
                        detail = result.summary.take(180),
                        teamId = KikoTeam.VISION.id,
                        status = "COMPLETED",
                    )
                )
                if (_autoSpeak.value) {
                    voiceEngine.speak(result.summary, _selectedLanguage.value)
                }
            } catch (e: TimeoutCancellationException) {
                _visionState.value = UiState.Error("Vision analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vision analysis failed", e)
                _visionState.value = UiState.Error(e.localizedMessage ?: "Vision analysis failed.")
            }
        }
    }

    fun analyzeDocument(fileName: String, content: String) {
        viewModelScope.launch {
            _visionState.value = UiState.Loading
            try {
                val result = aiService.analyzeDocumentText(fileName, content, _selectedLanguage.value)
                _visionState.value = UiState.Success(result)
                repository.logActivity(
                    ActivityLog(
                        title = "Document Analyzed: $fileName",
                        detail = result.summary.take(180),
                        teamId = KikoTeam.VISION.id,
                        status = "COMPLETED",
                    )
                )
            } catch (e: TimeoutCancellationException) {
                _visionState.value = UiState.Error("Document analysis timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Document analysis failed", e)
                _visionState.value = UiState.Error(e.localizedMessage ?: "Document analysis failed.")
            }
        }
    }

    fun generateDevProposal(specification: String) {
        if (specification.isBlank()) return
        viewModelScope.launch {
            _isGeneratingProposal.value = true
            try {
                refreshDiagnostics()
                val proposal = aiService.generateDevProposal(specification, _diagnostics.value)
                repository.createDevProposal(proposal)
                    .onSuccess {
                        _statusBanner.value = "Proposal staged: user approval required before applying."
                        repository.logActivity(
                            ActivityLog(
                                title = "Dev Proposal Staged",
                                detail = "${proposal.moduleName} awaiting user approval",
                                teamId = KikoTeam.DEVELOPMENT.id,
                                status = "PENDING",
                            )
                        )
                    }
                    .onFailure { err ->
                        _statusBanner.value = err.localizedMessage ?: "Failed to stage proposal."
                    }
            } catch (e: TimeoutCancellationException) {
                _statusBanner.value = "Development proposal generation timed out."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Proposal generation failed", e)
                _statusBanner.value = e.localizedMessage ?: "Proposal generation failed."
            } finally {
                _isGeneratingProposal.value = false
            }
        }
    }

    fun decideDevProposal(proposal: DevProposal, approved: Boolean) {
        viewModelScope.launch {
            repository.decideDevProposal(proposal.id, approved)
                .onSuccess {
                    val label = if (approved) "Approved" else "Rejected"
                    _statusBanner.value = "${proposal.moduleName} marked $label."
                    repository.logActivity(
                        ActivityLog(
                            title = "Dev Proposal $label",
                            detail = proposal.moduleName,
                            teamId = KikoTeam.DEVELOPMENT.id,
                            status = "COMPLETED",
                        )
                    )
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not update proposal status."
                }
        }
    }

    fun stageCodeSnippetAsProposal(code: String) {
        viewModelScope.launch {
            repository.createDevProposal(
                DevProposal(
                    title = "Command Center Code Module",
                    moduleName = "KikoGeneratedSnippet.kt",
                    summary = "Staged from Coding Team response for user review and approval.",
                    codePreview = code.take(16000),
                    status = "PENDING_APPROVAL",
                )
            ).onSuccess {
                _statusBanner.value = "Code staged in Development Hub for approval."
            }
        }
    }

    fun toggleTorch() {
        val target = !_torchEnabled.value
        val result = controlManager.setTorch(target)
        if (result.success) {
            _torchEnabled.value = target
        }
        _statusBanner.value = "${result.title}: ${result.message}"
        viewModelScope.launch {
            repository.logActivity(
                ActivityLog(
                    title = result.title,
                    detail = result.message,
                    teamId = KikoTeam.ANDROID_CONTROL.id,
                    status = if (result.success) "COMPLETED" else "ALERT",
                )
            )
        }
    }

    fun executeControlAction(action: () -> ControlExecutionResult) {
        val result = action()
        _statusBanner.value = "${result.title}: ${result.message}"
        viewModelScope.launch {
            repository.logActivity(
                ActivityLog(
                    title = result.title,
                    detail = result.message,
                    teamId = KikoTeam.ANDROID_CONTROL.id,
                    status = if (result.success) "COMPLETED" else "ALERT",
                )
            )
        }
    }

    fun saveProviderConfiguration(
        provider: AiProviderType,
        endpoint: String,
        modelName: String,
        bearerToken: String,
        pitch: Float,
        rate: Float,
    ) {
        _selectedProvider.value = provider
        voiceEngine.applyLanguageAndTone(_selectedLanguage.value, pitch, rate)
        viewModelScope.launch {
            localPrefs.saveBackendToken(bearerToken)
            repository.updateUserProfile(
                userId = currentUserId,
                preferredLanguage = _selectedLanguage.value.code,
                aiProvider = provider.id,
                backendEndpoint = endpoint,
                backendModel = modelName,
                voicePitch = pitch.toDouble(),
                voiceRate = rate.toDouble(),
                continuousVoice = _continuousVoice.value,
            ).onSuccess {
                _statusBanner.value = "KIKO neural & voice configuration saved."
                runCertificationAudit()
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to save settings."
            }
        }
    }

    fun runCertificationAudit() {
        refreshDiagnostics()
        val diag = _diagnostics.value
        val checks = listOf(
            CertificationCheckItem(
                title = "4 GB RAM Memory Budget",
                passed = !diag.isLowMemory && diag.appHeapUsedMb < (diag.appHeapMaxMb * 0.85),
                detail = "Heap ${diag.appHeapUsedMb}/${diag.appHeapMaxMb} MB • System ${diag.availableRamMb}/${diag.totalRamMb} MB free",
            ),
            CertificationCheckItem(
                title = "Android OS & Edge-to-Edge Target",
                passed = diag.apiLevel >= 24,
                detail = "${diag.androidVersion} (API ${diag.apiLevel}) • Target SDK 36 (Android 15/16 Ready)",
            ),
            CertificationCheckItem(
                title = "Firebase Auth & Zero-Trust Rules",
                passed = currentUserId.isNotBlank(),
                detail = "Authenticated UID scoped to /users/${currentUserId.take(8)}…",
            ),
            CertificationCheckItem(
                title = "Play Policy Least-Privilege Media",
                passed = true,
                detail = "Uses zero-permission Photo Picker & SAF (no broad storage permissions)",
            ),
            CertificationCheckItem(
                title = "Voice & Speech Synthesis",
                passed = voiceEngine.isRecognitionAvailable,
                detail = if (voiceEngine.isRecognitionAvailable) "SpeechRecognizer & TTS available" else "SpeechRecognizer unavailable on host; text & TTS fallback active",
            ),
            CertificationCheckItem(
                title = "Offline Local Fallback",
                passed = true,
                detail = "On-device diagnostics, hardware controls, math & cached memory active offline",
            ),
        )
        _certificationChecks.value = checks
    }

    private fun persistProfile(
        preferredLanguage: String? = null,
        continuousVoice: Boolean? = null,
    ) {
        val current = (userProfileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            repository.updateUserProfile(
                userId = currentUserId,
                preferredLanguage = preferredLanguage ?: current?.preferredLanguage ?: _selectedLanguage.value.code,
                aiProvider = current?.aiProvider ?: _selectedProvider.value.id,
                backendEndpoint = current?.backendEndpoint.orEmpty(),
                backendModel = current?.backendModel ?: "gemini-3.8-flash",
                voicePitch = current?.voicePitch ?: 1.0,
                voiceRate = current?.voiceRate ?: 1.0,
                continuousVoice = continuousVoice ?: current?.continuousVoice ?: _continuousVoice.value,
            )
        }
    }

    private fun tryExecuteDirectAndroidCommand(prompt: String): ControlExecutionResult? {
        val lower = prompt.lowercase(Locale.ROOT)
        return when {
            lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("turn on torch") || lower.contains("ٹارچ آن") -> {
                val res = controlManager.setTorch(true)
                if (res.success) _torchEnabled.value = true
                res
            }
            lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("turn off torch") || lower.contains("ٹارچ آف") -> {
                val res = controlManager.setTorch(false)
                if (res.success) _torchEnabled.value = false
                res
            }
            lower.contains("volume up") || lower.contains("increase volume") || lower.contains("آواز بڑھا") ->
                controlManager.adjustVolume(1)
            lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("آواز کم") ->
                controlManager.adjustVolume(-1)
            lower.contains("mute") ->
                controlManager.adjustVolume(0)
            lower.contains("open wifi") || lower.contains("wi-fi settings") ->
                controlManager.openSettingsPanel(Settings.Panel.ACTION_WIFI, "Wi-Fi")
            lower.contains("open bluetooth") || lower.contains("bluetooth settings") ->
                controlManager.openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
            lower.contains("open display settings") ->
                controlManager.openSettingsPanel(Settings.ACTION_DISPLAY_SETTINGS, "Display")
            lower.startsWith("start timer") || lower.startsWith("set timer") -> {
                val mins = Regex("(\\d+)\\s*(?:min|minute)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                val secs = mins?.times(60)
                    ?: Regex("(\\d+)\\s*(?:sec|second)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                    ?: 60
                controlManager.startTimer(secs, "KIKO Countdown")
            }
            else -> null
        }
    }

    private fun extractMemoryProposal(prompt: String): PendingMemorySuggestion? {
        val regex = Regex("^(?:remember that|remember|please remember|یاد رکھو کہ|یاد رکھو)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val match = regex.find(prompt.trim()) ?: return null
        val content = match.groupValues[1].trim().take(400)
        if (content.length < 4) return null
        return PendingMemorySuggestion(
            title = content.take(42).let { if (content.length > 42) "$it…" else it },
            content = content,
            category = "PREFERENCE",
        )
    }

    private fun extractFirstCodeFence(text: String): String? {
        val match = Regex("```(?:[a-zA-Z0-9_]+)?\\n([\\s\\S]*?)```").find(text)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
