package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

enum class KikoTeam(
    val id: String,
    val title: String,
    val urduTitle: String,
    val roleSummary: String,
    val capabilities: List<String>,
) {
    INTELLIGENCE(
        id = "INTELLIGENCE",
        title = "Intelligence Team",
        urduTitle = "ذہانت ٹیم",
        roleSummary = "Central orchestrator for reasoning, task planning, and EN/UR synthesis.",
        capabilities = listOf("Intent Routing", "Multi-step Planning", "Bilingual EN/UR"),
    ),
    VOICE(
        id = "VOICE",
        title = "Voice Team",
        urduTitle = "وائس ٹیم",
        roleSummary = "Real-time speech recognition, live dB waveform, and custom TTS synthesis.",
        capabilities = listOf("SpeechRecognizer", "Live Waveform", "EN/UR TTS"),
    ),
    VISION(
        id = "VISION",
        title = "Vision Team",
        urduTitle = "وژن ٹیم",
        roleSummary = "CameraX live feed, Photo Picker, and multimodal object & document analysis.",
        capabilities = listOf("CameraX Live", "Photo Picker", "OCR & Scene AI"),
    ),
    ANDROID_CONTROL(
        id = "ANDROID_CONTROL",
        title = "Android Control Team",
        urduTitle = "اینڈرائیڈ کنٹرول",
        roleSummary = "Executes permitted Android OS hardware controls, alarms, panels, and intents.",
        capabilities = listOf("Torch API", "AudioManager", "Alarm & Panels"),
    ),
    RESEARCH(
        id = "RESEARCH",
        title = "Research Team",
        urduTitle = "تحقیق ٹیم",
        roleSummary = "Web page fetching, deep topic synthesis, and structured intelligence briefs.",
        capabilities = listOf("Live URL Fetch", "Fact Synthesis", "Executive Briefs"),
    ),
    CODING(
        id = "CODING",
        title = "Coding Team",
        urduTitle = "کوڈنگ ٹیم",
        roleSummary = "Generates Kotlin/Python/JS modules, explains stack traces, and optimizes code.",
        capabilities = listOf("Code Generation", "Bug Triage", "Architecture"),
    ),
    SECURITY(
        id = "SECURITY",
        title = "Security Team",
        urduTitle = "سیکیورٹی ٹیم",
        roleSummary = "Audits permissions, App Check attestation, Firestore isolation, and approval gates.",
        capabilities = listOf("Permission Audit", "Zero-Trust Rules", "App Check"),
    ),
    AUTOMATION(
        id = "AUTOMATION",
        title = "Automation Team",
        urduTitle = "آٹومیشن ٹیم",
        roleSummary = "Manages smart reminders, routines, and system notifications.",
        capabilities = listOf("Smart Reminders", "Routines", "Notifications"),
    ),
    MEMORY(
        id = "MEMORY",
        title = "Memory Team",
        urduTitle = "یادداشت ٹیم",
        roleSummary = "Stores user-approved facts, preferences, and persistent context in Firestore.",
        capabilities = listOf("Context Recall", "Pinned Facts", "User Approval"),
    ),
    LEARNING(
        id = "LEARNING",
        title = "Learning Team",
        urduTitle = "سیکھنے کی ٹیم",
        roleSummary = "Adapts to preferred language, frequent workflows, and assistant tone.",
        capabilities = listOf("Language Prefs", "Usage Patterns", "Adaptive Tone"),
    ),
    DEVELOPMENT(
        id = "DEVELOPMENT",
        title = "Development Team",
        urduTitle = "ڈویلپمنٹ ٹیم",
        roleSummary = "Autonomous module proposals, diagnostics, and test-ready updates with user approval.",
        capabilities = listOf("Module Proposals", "Error Analysis", "Approval Gate"),
    ),
    CERTIFICATION(
        id = "CERTIFICATION",
        title = "Certification Team",
        urduTitle = "سرٹیفیکیشن ٹیم",
        roleSummary = "Validates 4 GB RAM budget, Android 15 compatibility, Play compliance, and readiness.",
        capabilities = listOf("4 GB RAM Guard", "Play Compliance", "Health Audit"),
    );

    companion object {
        fun fromId(raw: String): KikoTeam =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: INTELLIGENCE
    }
}

enum class AiProviderType(val id: String, val label: String, val description: String) {
    GEMINI("GEMINI", "Gemini (Firebase AI Logic)", "Zero-key cloud AI via App Check attestation"),
    OPENAI_BACKEND("OPENAI_BACKEND", "OpenAI Backend Proxy", "Calls your secure backend /v1/chat/completions"),
    CUSTOM_BACKEND("CUSTOM_BACKEND", "Custom AI Backend", "Configurable OpenAI-compatible relay server"),
    OFFLINE("OFFLINE", "Offline Local Engine", "On-device command & diagnostics mode (no internet)");

    companion object {
        fun fromId(raw: String): AiProviderType =
            entries.firstOrNull { it.id == raw } ?: GEMINI
    }
}

enum class KikoLanguage(val code: String, val label: String, val speechLocale: String) {
    AUTO("auto", "Auto (EN / اردو)", "en-US"),
    ENGLISH("en", "English", "en-US"),
    URDU("ur", "اردو (Urdu)", "ur-PK");

    companion object {
        fun fromCode(raw: String): KikoLanguage =
            entries.firstOrNull { it.code == raw } ?: AUTO
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val preferredLanguage: String = "auto",
    val aiProvider: String = "GEMINI",
    val backendEndpoint: String = "",
    val backendModel: String = "gemini-3.8-flash",
    val voicePitch: Double = 1.0,
    val voiceRate: Double = 1.0,
    val continuousVoice: Boolean = false,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "preferredLanguage" to preferredLanguage,
        "aiProvider" to aiProvider,
        "backendEndpoint" to backendEndpoint.take(300),
        "backendModel" to backendModel.ifBlank { "gemini-3.8-flash" }.take(100),
        "voicePitch" to voicePitch.coerceIn(0.5, 2.0),
        "voiceRate" to voiceRate.coerceIn(0.5, 2.0),
        "continuousVoice" to continuousVoice,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

@Keep
@IgnoreExtraProperties
data class MemoryEntry(
    @DocumentId val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "FACT",
    val pinned: Boolean = false,
    val source: String = "USER",
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(120),
        "content" to content.trim().take(2000),
        "category" to if (category in VALID_CATEGORIES) category else "FACT",
        "pinned" to pinned,
        "source" to if (source in VALID_SOURCES) source else "USER",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        val VALID_CATEGORIES = listOf("FACT", "PREFERENCE", "PROJECT", "CONTACT", "PROTOCOL")
        val VALID_SOURCES = listOf("USER", "AI_APPROVED")
    }
}

@Keep
@IgnoreExtraProperties
data class SmartReminder(
    @DocumentId val id: String = "",
    val title: String = "",
    val notes: String = "",
    val dueLabel: String = "",
    val priority: String = "NORMAL",
    val triggerType: String = "REMINDER",
    val completed: Boolean = false,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(160),
        "notes" to notes.trim().take(1000),
        "dueLabel" to dueLabel.trim().take(100),
        "priority" to if (priority in VALID_PRIORITIES) priority else "NORMAL",
        "triggerType" to if (triggerType in VALID_TRIGGERS) triggerType else "REMINDER",
        "completed" to completed,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        val VALID_PRIORITIES = listOf("LOW", "NORMAL", "HIGH", "CRITICAL")
        val VALID_TRIGGERS = listOf("REMINDER", "ROUTINE", "AUTOMATION")
    }
}

@Keep
@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val title: String = "",
    val detail: String = "",
    val teamId: String = "INTELLIGENCE",
    val status: String = "COMPLETED",
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(160),
        "detail" to detail.trim().take(2000),
        "teamId" to teamId.trim().take(40).ifBlank { "INTELLIGENCE" },
        "status" to if (status in VALID_STATUSES) status else "COMPLETED",
        "createdAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        val VALID_STATUSES = listOf("COMPLETED", "PENDING", "ALERT", "OFFLINE")
    }
}

@Keep
@IgnoreExtraProperties
data class ChatRecord(
    @DocumentId val id: String = "",
    val role: String = "USER",
    val text: String = "",
    val teamId: String = "INTELLIGENCE",
    val language: String = "en",
    val provider: String = "GEMINI",
    val codeSnippet: String? = null,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("role", if (role in listOf("USER", "KIKO", "SYSTEM")) role else "KIKO")
        put("text", text.trim().take(12000).ifBlank { "..." })
        put("teamId", teamId.trim().take(40).ifBlank { "INTELLIGENCE" })
        put("language", if (language in listOf("en", "ur", "auto")) language else "en")
        put("provider", provider.trim().take(40).ifBlank { "GEMINI" })
        if (!codeSnippet.isNullOrBlank()) {
            put("codeSnippet", codeSnippet.take(16000))
        }
        put("createdAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class DevProposal(
    @DocumentId val id: String = "",
    val title: String = "",
    val moduleName: String = "",
    val summary: String = "",
    val codePreview: String = "",
    val status: String = "PENDING_APPROVAL",
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(160),
        "moduleName" to moduleName.trim().take(100),
        "summary" to summary.trim().take(2000),
        "codePreview" to codePreview.trim().take(16000),
        "status" to "PENDING_APPROVAL",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

data class DeviceDiagnosticsSnapshot(
    val deviceModel: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 0f,
    val totalRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val appHeapUsedMb: Long = 0L,
    val appHeapMaxMb: Long = 0L,
    val isLowMemory: Boolean = false,
    val storageFreeGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val networkLabel: String = "Checking…",
    val isOnline: Boolean = false,
    val sensorCount: Int = 0,
    val hasTorchHardware: Boolean = false,
)

data class PendingMemorySuggestion(
    val title: String,
    val content: String,
    val category: String = "PREFERENCE",
)

data class VisionAnalysisResult(
    val summary: String,
    val detectedObjects: List<String>,
    val extractedText: String,
    val recommendedAction: String,
)
