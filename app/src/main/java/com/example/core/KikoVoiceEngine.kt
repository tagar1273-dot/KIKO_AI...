package com.example.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.KikoLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.sin

class KikoVoiceEngine(private val context: Context) {

    companion object {
        private const val TAG = "KikoVoiceEngine"
        private const val BAR_COUNT = 28
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var currentPitch = 1.0f
    private var currentRate = 1.0f
    private var currentLanguage = KikoLanguage.AUTO
    private var continuousMode = false
    private var activeCallback: ((String) -> Unit)? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _waveformBars = MutableStateFlow(List(BAR_COUNT) { 0.08f })
    val waveformBars: StateFlow<List<Float>> = _waveformBars.asStateFlow()

    private val _voiceStatusNote = MutableStateFlow("Voice Core Ready")
    val voiceStatusNote: StateFlow<String> = _voiceStatusNote.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    applyLanguageAndTone(currentLanguage, currentPitch, currentRate)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                            _voiceStatusNote.value = "Synthesizing Voice…"
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            resetWaveform()
                            _voiceStatusNote.value = if (continuousMode) "Hands-Free Standby" else "Voice Core Ready"
                            if (continuousMode && activeCallback != null) {
                                mainHandler.postDelayed({
                                    if (continuousMode && !_isListening.value && !_isSpeaking.value) {
                                        startListening(currentLanguage, continuousMode, activeCallback!!)
                                    }
                                }, 450L)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            resetWaveform()
                        }
                    })
                } else {
                    Log.w(TAG, "TTS initialization returned status=$status")
                    _voiceStatusNote.value = "TTS Unavailable on Device"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    fun applyLanguageAndTone(language: KikoLanguage, pitch: Float, rate: Float) {
        currentLanguage = language
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        currentRate = rate.coerceIn(0.5f, 2.0f)
        if (!ttsReady) return
        val locale = when (language) {
            KikoLanguage.URDU -> Locale.forLanguageTag("ur-PK")
            KikoLanguage.ENGLISH, KikoLanguage.AUTO -> Locale.US
        }
        val res = tts?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
        tts?.setPitch(currentPitch)
        tts?.setSpeechRate(currentRate)
    }

    fun speak(text: String, forceLanguage: KikoLanguage = currentLanguage) {
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "Code block generated on screen.")
            .replace(Regex("[*#`_]"), "")
            .trim()
        if (cleanText.isEmpty()) return
        if (!ttsReady) {
            _voiceStatusNote.value = "Speech output unavailable on this device."
            return
        }
        val detectedUrdu = cleanText.any { it in '\u0600'..'\u06FF' }
        val effectiveLang = if (forceLanguage == KikoLanguage.AUTO && detectedUrdu) {
            KikoLanguage.URDU
        } else {
            forceLanguage
        }
        applyLanguageAndTone(effectiveLang, currentPitch, currentRate)
        pulseTtsWaveform()
        tts?.speak(cleanText.take(1200), TextToSpeech.QUEUE_FLUSH, null, "kiko_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
        resetWaveform()
    }

    fun startListening(
        language: KikoLanguage,
        continuous: Boolean,
        onFinalResult: (String) -> Unit,
    ) {
        currentLanguage = language
        continuousMode = continuous
        activeCallback = onFinalResult

        if (!isRecognitionAvailable) {
            _isListening.value = false
            _voiceStatusNote.value = "Speech recognition service unavailable on this device/emulator."
            return
        }

        mainHandler.post {
            try {
                stopSpeaking()
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListening.value = true
                            _partialTranscript.value = ""
                            _voiceStatusNote.value = "Listening (${language.label})…"
                        }

                        override fun onBeginningOfSpeech() {
                            _voiceStatusNote.value = "Voice Signal Locked…"
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                            val shifted = _waveformBars.value.drop(1) + normalized
                            _waveformBars.value = shifted
                        }

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() {
                            _voiceStatusNote.value = "Processing Speech…"
                        }

                        override fun onError(error: Int) {
                            _isListening.value = false
                            resetWaveform()
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Voice input timed out."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice network error."
                                else -> "Voice error ($error)"
                            }
                            _voiceStatusNote.value = msg
                            if (continuousMode && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                                mainHandler.postDelayed({
                                    if (continuousMode && !_isSpeaking.value && !_isListening.value) {
                                        startListening(currentLanguage, true, onFinalResult)
                                    }
                                }, 800L)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            _isListening.value = false
                            resetWaveform()
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spoken = matches?.firstOrNull()?.trim().orEmpty()
                            _partialTranscript.value = ""
                            if (spoken.isNotBlank()) {
                                _voiceStatusNote.value = "Voice Captured"
                                onFinalResult(spoken)
                            } else {
                                _voiceStatusNote.value = "Voice Core Ready"
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                _partialTranscript.value = partial
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocale)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "SpeechRecognizer start failed", e)
                _isListening.value = false
                _voiceStatusNote.value = e.localizedMessage ?: "Voice start failed."
            }
        }
    }

    fun stopListening() {
        continuousMode = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            _isListening.value = false
            _partialTranscript.value = ""
            resetWaveform()
            _voiceStatusNote.value = "Voice Core Standby"
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        continuousMode = enabled
        if (!enabled && _isListening.value) {
            stopListening()
        }
    }

    private fun pulseTtsWaveform() {
        val phase = (System.currentTimeMillis() % 1000L) / 1000.0
        _waveformBars.value = List(BAR_COUNT) { idx ->
            (0.25f + 0.55f * kotlin.math.abs(sin(phase * Math.PI * 2 + idx * 0.45)).toFloat())
        }
    }

    private fun resetWaveform() {
        _waveformBars.value = List(BAR_COUNT) { 0.08f }
    }

    fun release() {
        continuousMode = false
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (_: Exception) {
            }
        }
    }
}
