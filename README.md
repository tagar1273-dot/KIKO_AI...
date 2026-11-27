# KIKO AI — Holographic Personal AI Assistant

KIKO AI is an original, JARVIS-inspired personal AI assistant for Android (Kotlin + Jetpack Compose, Android 15 / API 36 target, optimized for 4 GB RAM devices).

## Core Architecture

- **UI & Holographic HUD**: Jetpack Compose Material 3 dark blue holographic interface (`#040A14` void, `#00E5FF` electric cyan, `#2979FF` azure) featuring an animated multi-ring `ArcCoreReactor`, live dB `VoiceWaveformBar`, adaptive `NavigationBar` / `NavigationRail`, and Orbitron / Space Grotesk / JetBrains Mono local typography.
- **Central Brain & 12 Specialized Teams** (`KikoMainViewModel` + `KikoAiService`):
  1. Intelligence Team
  2. Voice Team (`SpeechRecognizer` + `TextToSpeech` in English & Urdu)
  3. Vision Team (CameraX live preview, Android Photo Picker, Document Scanner + Gemini Multimodal)
  4. Android Control Team (`CameraManager` Torch, `AudioManager` volume, `AlarmClock`, Settings Panels, Clipboard, Smart Notifications)
  5. Research Team (Live OkHttp URL fetching + structured briefings)
  6. Coding Team (Code generation, debugging, stageable snippets)
  7. Security Team (App Check attestation, Firestore zero-trust UID isolation)
  8. Automation Team (Smart reminders, routines, notifications)
  9. Memory Team (User-approved persistent facts & context in Firestore)
  10. Learning Team (Adaptive language & interaction preferences)
  11. Development Team (Autonomous Kotlin module proposals with mandatory User Approval Gate)
  12. Certification Team (4 GB RAM heap audit, Android 15 readiness, Play Policy compliance)
- **Cloud AI & Providers**:
  - **Default**: Gemini (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`) via **Firebase AI Logic** (`Firebase.ai`) and **Firebase App Check** (no client-side Gemini API key required).
  - **Configurable OpenAI / Custom Backend Proxy**: Configure an OpenAI-compatible backend URL (e.g., `/v1/chat/completions`) inside **Systems → Config** so API keys stay securely on your backend server.
  - **Offline Local Engine**: Works without internet for diagnostics, time/date, calculator math, flashlight, volume, alarms/timers, settings panels, and cached memory lookups in English and Urdu.

## Hardware, Permissions & External Service Requirements

- **Camera & Torch (`CAMERA`)**: Real-time CameraX vision and flashlight toggle require a physical camera/flash unit. On devices/emulators without camera flash, Photo Picker (`PickVisualMedia`) and Document Scan (`OpenDocument`) work with zero storage permissions.
- **Microphone (`RECORD_AUDIO`)**: Live speech recognition and dB waveform require `RECORD_AUDIO` and an active Android `RecognitionService`.
- **Notifications (`POST_NOTIFICATIONS`)**: Required on Android 13+ for smart reminder and software update alerts.
- **Google Sign-In & Firestore**: Uses Jetpack Credential Manager (`GetSignInWithGoogleOption`) and user-isolated Firestore collections under `/users/{userId}/...`.
- **Direct OTA Updates (`REQUEST_INSTALL_PACKAGES`)**: Used exclusively for user-approved APK updates verified via SHA-256 checksum and Android signing certificate match before launching Android's official `FileProvider` package installer.

## Auto & Manual Update System

1. **Android Update Architecture**:
   - **Automatic Checks**: Checks on launch (`UpdateRepository.initializeOnLaunch`) and periodically every 6 hours in the background via WorkManager (`UpdateCheckWorker`).
   - **Manual Controls**: Open **Systems → Config → KIKO SOFTWARE UPDATE** to view current/latest version, last checked timestamp, release notes, update status, and download/install controls.
   - **Update Sources**: Supports both the **KIKO Update Backend** JSON API and **GitHub Releases API** (`https://github.com/<owner>/<repo>` or `https://api.github.com/repos/<owner>/<repo>/releases/latest`) over HTTPS.
   - **Mandatory vs Optional**: Supports `OPTIONAL` updates (dismissible banner) and `CRITICAL` updates (or `versionCode < minSupportedVersion`), showing a non-dismissible mandatory update screen.
2. **Running the Secure Update Backend (`update-backend/`)**:
   - Place signed release APKs inside `update-backend/artifacts/`.
   - Start with Node 18+:
     ```bash
     cd update-backend
     PUBLIC_BASE_URL="https://updates.yourdomain.com" \
     UPDATE_ADMIN_TOKEN="replace-with-strong-secret" \
     TLS_KEY_PATH="/path/to/privkey.pem" \
     TLS_CERT_PATH="/path/to/fullchain.pem" \
     node server.js
     ```
   - Publish a release (computes SHA-256 automatically from `artifacts/`):
     ```bash
     curl -X POST https://updates.yourdomain.com/api/v1/updates/publish \
       -H "Authorization: Bearer replace-with-strong-secret" \
       -H "Content-Type: application/json" \
       -d '{"latestVersion":"1.1.0","versionCode":2,"artifactFileName":"kiko-ai-v1.1.0.apk","minSupportedVersion":1,"updateType":"OPTIONAL","releaseNotes":"Bug fixes and voice improvements."}'
     ```

## Verification & Testing

- **Firestore Security Rules**: `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
- **Update Backend Tests**: `node --test update-backend/update-backend.test.js`
- **Unit Tests**: `gradle :app:testDebugUnitTest --tests "com.example.KikoAiUnitTest" --tests "com.example.KikoUpdateUnitTest" --tests "com.example.ExampleUnitTest"`
- **APK Build**: `gradle :app:assembleDebug`

