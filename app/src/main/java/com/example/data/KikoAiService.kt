package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiProviderType
import com.example.model.DevProposal
import com.example.model.DeviceDiagnosticsSnapshot
import com.example.model.KikoLanguage
import com.example.model.KikoTeam
import com.example.model.MemoryEntry
import com.example.model.PendingMemorySuggestion
import com.example.model.VisionAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AiExecutionResult(
    val text: String,
    val team: KikoTeam,
    val providerUsed: String,
    val codeSnippet: String? = null,
    val memorySuggestion: PendingMemorySuggestion? = null,
)

class KikoAiService {

    companion object {
        private const val TAG = "KikoAiService"
        private const val AI_TIMEOUT_MS = 60_000L
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val flashModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val liteModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    private val devModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-4-argon")
    }

    private val visionStructuredModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(description = "Concise JARVIS-style visual/document intelligence summary"),
                "detectedObjects" to Schema.array(
                    items = Schema.string(),
                    description = "List of key objects, components, or document sections identified"
                ),
                "extractedText" to Schema.string(description = "Any legible text, labels, or code extracted from the image"),
                "recommendedAction" to Schema.string(description = "Practical next step or insight for the user"),
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val devProposalStructuredModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short descriptive title of the module or improvement"),
                "moduleName" to Schema.string(description = "Target Kotlin file name like KikoCacheManager.kt"),
                "summary" to Schema.string(description = "Technical rationale, 4GB RAM optimization notes, and test strategy"),
                "codePreview" to Schema.string(description = "Complete production-quality Kotlin source code snippet"),
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun detectTargetTeam(prompt: String, explicitTeam: KikoTeam): KikoTeam {
        if (explicitTeam != KikoTeam.INTELLIGENCE) return explicitTeam
        val lower = prompt.lowercase(Locale.ROOT)
        return when {
            lower.containsAny("flashlight", "torch", "volume", "alarm", "timer", "wifi", "wi-fi", "bluetooth", "settings", "brightness", "ٹارچ", "والیوم", "الارم") ->
                KikoTeam.ANDROID_CONTROL
            lower.containsAny("camera", "photo", "image", "scan", "object", "document", "ocr", "کیرا", "تصویر") ->
                KikoTeam.VISION
            lower.containsAny("code", "kotlin", "python", "bug", "error", "stacktrace", "refactor", "function", "gradle", "کوڈ") ->
                KikoTeam.CODING
            lower.containsAny("module", "architecture", "proposal", "update", "generate app", "unit test", "ماڈیول") ->
                KikoTeam.DEVELOPMENT
            lower.containsAny("remember", "memory", "recall", "forget", "یاد", "میموری") ->
                KikoTeam.MEMORY
            lower.containsAny("remind", "schedule", "routine", "automation", "notification", "یاد دہانی") ->
                KikoTeam.AUTOMATION
            lower.containsAny("security", "permission", "audit", "privacy", "encrypted", "app check", "سیکیورٹی") ->
                KikoTeam.SECURITY
            lower.containsAny("research", "http://", "https://", "analyze", "compare", "news", "web", "تحقیق") ->
                KikoTeam.RESEARCH
            lower.containsAny("battery", "ram", "memory info", "diagnostic", "certify", "health", "بیٹری", "تشخیص") ->
                KikoTeam.CERTIFICATION
            lower.containsAny("voice", "speak", "tts", "urdu", "pronounce", "آواز") ->
                KikoTeam.VOICE
            else -> KikoTeam.INTELLIGENCE
        }
    }

    fun streamAssistantResponse(
        userPrompt: String,
        team: KikoTeam,
        language: KikoLanguage,
        provider: AiProviderType,
        backendEndpoint: String,
        backendModel: String,
        bearerToken: String,
        memories: List<MemoryEntry>,
        diagnostics: DeviceDiagnosticsSnapshot,
        recentHistory: List<Pair<String, String>>,
    ): Flow<String> = flow {
        val targetTeam = detectTargetTeam(userPrompt, team)

        // Fetch web context if Research team detects a URL
        val fetchedWebContext = if (targetTeam == KikoTeam.RESEARCH && diagnostics.isOnline) {
            extractUrlFromPrompt(userPrompt)?.let { url ->
                fetchUrlSnippet(url)
            }
        } else null

        if (provider == AiProviderType.OFFLINE || !diagnostics.isOnline) {
            val offlineReply = runOfflineCommandEngine(userPrompt, targetTeam, language, memories, diagnostics)
            emit(offlineReply)
            return@flow
        }

        val systemInstruction = buildSystemPrompt(
            team = targetTeam,
            language = language,
            memories = memories,
            diagnostics = diagnostics,
            fetchedWebContext = fetchedWebContext,
        )

        val fullPrompt = buildString {
            append(systemInstruction)
            append("\n\n--- RECENT CONVERSATION ---\n")
            recentHistory.takeLast(6).forEach { (role, text) ->
                append("$role: ${text.take(400)}\n")
            }
            append("\nUSER REQUEST: $userPrompt")
        }

        if ((provider == AiProviderType.OPENAI_BACKEND || provider == AiProviderType.CUSTOM_BACKEND) &&
            backendEndpoint.isNotBlank()
        ) {
            val reply = callOpenAiCompatibleBackend(
                endpoint = backendEndpoint,
                model = backendModel.ifBlank { "gpt-4o-mini" },
                bearerToken = bearerToken,
                systemPrompt = systemInstruction,
                userPrompt = userPrompt,
            )
            emit(reply)
            return@flow
        }

        val selectedModel = when (targetTeam) {
            KikoTeam.DEVELOPMENT -> flashModel
            KikoTeam.VOICE -> liteModel
            else -> flashModel
        }

        val buffer = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            selectedModel.generateContentStream(fullPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    buffer.append(part)
                    emit(buffer.toString())
                }
            }
        }
        if (buffer.isBlank()) {
            throw IllegalStateException("Gemini returned an empty response.")
        }
    }

    suspend fun analyzeVisualInput(
        bitmap: Bitmap,
        userQuestion: String,
        language: KikoLanguage,
    ): VisionAnalysisResult {
        val scaledBitmap = scaleBitmapForMemoryBudget(bitmap, maxDim = 1024)
        val langHint = when (language) {
            KikoLanguage.URDU -> "Respond in clear Urdu (اردو) script."
            KikoLanguage.ENGLISH -> "Respond in crisp English."
            KikoLanguage.AUTO -> "Respond in English (or Urdu if the query/document is primarily Urdu)."
        }
        val promptText = """
            You are KIKO AI's Vision Team, inspired by JARVIS. Analyze this image or document carefully.
            $langHint
            User query: ${userQuestion.ifBlank { "Identify objects, extract any visible text/code/data, and summarize tactical insights." }}
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                visionStructuredModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(promptText)
                    }
                )
            }
            val rawJson = response.text ?: throw IllegalStateException("Vision model returned empty output.")
            val json = JSONObject(rawJson)
            val objectsJson = json.optJSONArray("detectedObjects") ?: JSONArray()
            val objects = (0 until objectsJson.length()).mapNotNull { idx ->
                objectsJson.optString(idx).takeIf { it.isNotBlank() }
            }
            VisionAnalysisResult(
                summary = json.optString("summary", "Analysis complete."),
                detectedObjects = objects,
                extractedText = json.optString("extractedText", ""),
                recommendedAction = json.optString("recommendedAction", ""),
            )
        } catch (e: CancellationException) {
            throw e
        } finally {
            if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
        }
    }

    suspend fun analyzeDocumentText(
        fileName: String,
        documentText: String,
        language: KikoLanguage,
    ): VisionAnalysisResult {
        val langHint = when (language) {
            KikoLanguage.URDU -> "Respond in Urdu (اردو)."
            else -> "Respond in clear English."
        }
        val prompt = """
            You are KIKO AI's Vision & Document Intelligence Team.
            $langHint
            Analyze document "$fileName":
            ${documentText.take(12000)}
        """.trimIndent()

        val response = withTimeout(AI_TIMEOUT_MS) {
            visionStructuredModel.generateContent(prompt)
        }
        val rawJson = response.text ?: throw IllegalStateException("Document analysis returned empty output.")
        val json = JSONObject(rawJson)
        val objectsJson = json.optJSONArray("detectedObjects") ?: JSONArray()
        val items = (0 until objectsJson.length()).mapNotNull { idx ->
            objectsJson.optString(idx).takeIf { it.isNotBlank() }
        }
        return VisionAnalysisResult(
            summary = json.optString("summary", "Document analyzed."),
            detectedObjects = items,
            extractedText = json.optString("extractedText", ""),
            recommendedAction = json.optString("recommendedAction", ""),
        )
    }

    suspend fun generateDevProposal(
        specification: String,
        diagnostics: DeviceDiagnosticsSnapshot,
    ): DevProposal {
        val prompt = """
            You are KIKO AI's Autonomous Development Team.
            Target platform: Android 15 (Kotlin + Jetpack Compose), optimized for 4 GB RAM devices.
            Device snapshot: ${diagnostics.deviceModel}, Available RAM: ${diagnostics.availableRamMb} MB / ${diagnostics.totalRamMb} MB.
            Generate a clean, modular, production-ready Kotlin proposal for:
            "$specification"
        """.trimIndent()

        val response = withTimeout(AI_TIMEOUT_MS) {
            devProposalStructuredModel.generateContent(prompt)
        }
        val rawJson = response.text ?: throw IllegalStateException("Development Team returned empty output.")
        val json = JSONObject(rawJson)
        return DevProposal(
            title = json.optString("title", "KIKO Module Proposal").take(160),
            moduleName = json.optString("moduleName", "KikoModule.kt").take(100),
            summary = json.optString("summary", "Generated module ready for user review.").take(2000),
            codePreview = json.optString("codePreview", "// No code generated").take(16000),
            status = "PENDING_APPROVAL",
        )
    }

    fun runOfflineCommandEngine(
        prompt: String,
        team: KikoTeam,
        language: KikoLanguage,
        memories: List<MemoryEntry>,
        diagnostics: DeviceDiagnosticsSnapshot,
    ): String {
        val lower = prompt.lowercase(Locale.ROOT)
        val isUrdu = language == KikoLanguage.URDU || prompt.any { it in '\u0600'..'\u06FF' }

        // Simple math expression evaluator offline
        val mathMatch = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)\\s*$").find(lower)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDouble()
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDouble()
            val result = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else Double.NaN
                else -> Double.NaN
            }
            return if (isUrdu) {
                "آف لائن کیلکولیٹر نتیجہ: $a $op $b = $result"
            } else {
                "Offline Calculation Matrix: $a $op $b = $result"
            }
        }

        if (lower.containsAny("time", "clock", "date", "وقت", "تاریخ")) {
            val formatted = SimpleDateFormat("EEEE, MMM d, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date())
            return if (isUrdu) {
                "جناب، موجودہ مقامی وقت اور تاریخ: $formatted"
            } else {
                "Local chronometer telemetry: $formatted."
            }
        }

        if (lower.containsAny("battery", "ram", "memory", "diagnostic", "status", "health", "بیٹری", "تشخیص", "حالت")) {
            return if (isUrdu) {
                "سسٹم تشخیص (آف لائن موڈ): بیٹری ${diagnostics.batteryPercent}% (${if (diagnostics.isCharging) "چارج ہو رہی ہے" else "ڈسچارج"})، " +
                    "دستیاب ریم ${diagnostics.availableRamMb} MB / ${diagnostics.totalRamMb} MB، نیٹ ورک: ${diagnostics.networkLabel}۔"
            } else {
                "System Diagnostics (${diagnostics.deviceModel} • ${diagnostics.androidVersion}): " +
                    "Battery at ${diagnostics.batteryPercent}% (${if (diagnostics.isCharging) "Charging" else "Discharging"}), " +
                    "Available RAM ${diagnostics.availableRamMb} MB of ${diagnostics.totalRamMb} MB (App Heap ${diagnostics.appHeapUsedMb}/${diagnostics.appHeapMaxMb} MB), " +
                    "Storage ${"%.1f".format(diagnostics.storageFreeGb)} GB free, Network: ${diagnostics.networkLabel}."
            }
        }

        if (lower.containsAny("remember", "recall", "memories", "یاد")) {
            val summary = if (memories.isEmpty()) {
                if (isUrdu) "کوئی محفوظ یادداشت نہیں ملی۔" else "No stored memories found in local cache."
            } else {
                memories.take(5).joinToString("\n") { "• ${it.title}: ${it.content}" }
            }
            return if (isUrdu) "محفوظ یادداشتیں:\n$summary" else "Cached Memory Bank:\n$summary"
        }

        return if (isUrdu) {
            "کیکو اے آئی آف لائن موڈ میں فعال ہے (${team.urduTitle})۔ میں وقت، بیٹری، ریم تشخیص، حساب کتاب، ٹارچ، والیوم، اور سسٹم پینلز آف لائن چلا سکتا ہوں۔ مکمل کلاؤڈ ذہانت کے لیے انٹرنیٹ یا جیمنائی کنکشن فعال کریں۔"
        } else {
            "KIKO AI Offline Core active [${team.title}]. I can execute local diagnostics, time/date checks, quick math, flashlight, volume, alarms, settings panels, and cached memory lookups without internet. Reconnect or switch to Gemini / Backend Proxy in Systems for full generative reasoning."
        }
    }

    private suspend fun callOpenAiCompatibleBackend(
        endpoint: String,
        model: String,
        bearerToken: String,
        systemPrompt: String,
        userPrompt: String,
    ): String = withContext(Dispatchers.IO) {
        val cleanBase = endpoint.trim().trimEnd('/')
        val targetUrl = if (cleanBase.endsWith("/chat/completions")) cleanBase else "$cleanBase/v1/chat/completions"

        val bodyJson = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
            put("temperature", 0.6)
        }

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")

        if (bearerToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("Backend provider HTTP ${response.code}: ${responseBody.take(240)}")
        }
        val parsed = JSONObject(responseBody)
        val choices = parsed.optJSONArray("choices")
        val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        if (content.isBlank()) {
            throw IllegalStateException("Backend provider returned empty completion.")
        }
        content
    }

    private suspend fun fetchUrlSnippet(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KikoAI-ResearchTeam/1.0")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext "HTTP ${resp.code} fetching $url"
                val raw = resp.body?.string().orEmpty()
                val stripped = raw
                    .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                "LIVE WEB FETCH ($url): ${stripped.take(6000)}"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Web research fetch failed for $url: ${e.message}")
            null
        }
    }

    private fun extractUrlFromPrompt(prompt: String): String? {
        return Regex("https?://[^\\s]+").find(prompt)?.value
    }

    private fun buildSystemPrompt(
        team: KikoTeam,
        language: KikoLanguage,
        memories: List<MemoryEntry>,
        diagnostics: DeviceDiagnosticsSnapshot,
        fetchedWebContext: String?,
    ): String {
        val langRule = when (language) {
            KikoLanguage.URDU -> "Respond primarily in natural, respectful Urdu (اردو script), keeping technical terms clear."
            KikoLanguage.ENGLISH -> "Respond in crisp, articulate English."
            KikoLanguage.AUTO -> "Detect the user's language: respond in Urdu (اردو) if addressed in Urdu or Roman Urdu, otherwise respond in English."
        }
        val memoryContext = if (memories.isEmpty()) {
            "None saved yet."
        } else {
            memories.sortedByDescending { it.pinned }.take(10).joinToString("\n") {
                "- [${it.category}] ${it.title}: ${it.content}"
            }
        }
        return buildString {
            appendLine("You are KIKO AI, an original, calm, loyal, intelligent, and occasionally witty holographic Android assistant inspired by JARVIS.")
            appendLine("Active Specialized Team: ${team.title} — ${team.roleSummary}")
            appendLine("Language Policy: $langRule")
            appendLine("Live Device Telemetry: ${diagnostics.deviceModel}, ${diagnostics.androidVersion}, Battery ${diagnostics.batteryPercent}%, Available RAM ${diagnostics.availableRamMb}/${diagnostics.totalRamMb} MB, Network: ${diagnostics.networkLabel}.")
            appendLine("Approved User Memories:\n$memoryContext")
            if (!fetchedWebContext.isNullOrBlank()) {
                appendLine("Research Context:\n$fetchedWebContext")
            }
            appendLine("Guidelines: Be concise, structured, and helpful. Never fabricate hardware actions you did not execute. If writing code, wrap it cleanly in markdown triple-backtick fences.")
        }
    }

    private fun scaleBitmapForMemoryBudget(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun String.containsAny(vararg tokens: String): Boolean =
        tokens.any { this.contains(it) }
}
