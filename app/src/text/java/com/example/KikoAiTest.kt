package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.core.DeviceDiagnosticsManager
import com.example.data.KikoAiService
import com.example.model.DevProposal
import com.example.model.DeviceDiagnosticsSnapshot
import com.example.model.KikoLanguage
import com.example.model.KikoTeam
import com.example.model.MemoryEntry
import com.example.model.SmartReminder
import com.example.ui.components.ArcCoreReactor
import com.example.ui.theme.KikoAiTheme
import com.example.viewmodel.CoreReactorPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KikoAiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val aiService = KikoAiService()

    @Test
    fun `app name matches KIKO AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("KIKO AI", context.getString(R.string.app_name))
    }

    @Test
    fun `intent router routes specialized commands to the 12 KIKO teams`() {
        assertEquals(KikoTeam.ANDROID_CONTROL, aiService.detectTargetTeam("Turn on flashlight", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.CODING, aiService.detectTargetTeam("Write a Kotlin coroutine function", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.VISION, aiService.detectTargetTeam("Scan this document image", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.RESEARCH, aiService.detectTargetTeam("Research https://developer.android.com", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.DEVELOPMENT, aiService.detectTargetTeam("Generate a new module proposal", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.MEMORY, aiService.detectTargetTeam("Remember that I prefer Urdu", KikoTeam.INTELLIGENCE))
    }

    @Test
    fun `offline engine handles English, Urdu, diagnostics, and math locally`() {
        val diag = DeviceDiagnosticsSnapshot(
            deviceModel = "TEST PHONE",
            androidVersion = "Android 15",
            apiLevel = 35,
            batteryPercent = 88,
            isCharging = true,
            totalRamMb = 4096,
            availableRamMb = 1850,
            appHeapUsedMb = 42,
            appHeapMaxMb = 256,
            storageFreeGb = 24.5f,
            networkLabel = "Offline",
            isOnline = false,
        )
        val mathReply = aiService.runOfflineCommandEngine("144 / 12", KikoTeam.INTELLIGENCE, KikoLanguage.ENGLISH, emptyList(), diag)
        assertTrue(mathReply.contains("12"))

        val urduDiag = aiService.runOfflineCommandEngine("سسٹم کی حالت بتائیں", KikoTeam.CERTIFICATION, KikoLanguage.URDU, emptyList(), diag)
        assertTrue(urduDiag.contains("88%"))
        assertTrue(urduDiag.contains("1850"))
    }

    @Test
    fun `firestore write maps omit DocumentId id and enforce approval status`() {
        val memMap = MemoryEntry(id = "doc_1", title = "Pref", content = "Use Urdu", category = "PREFERENCE").toCreateMap()
        assertFalse(memMap.containsKey("id"))
        assertEquals("Pref", memMap["title"])

        val remMap = SmartReminder(id = "rem_1", title = "Briefing", priority = "HIGH").toCreateMap()
        assertFalse(remMap.containsKey("id"))

        val propMap = DevProposal(
            id = "p_1",
            title = "Cache",
            moduleName = "Cache.kt",
            summary = "LRU",
            codePreview = "class Cache",
            status = "APPROVED", // Should always start as PENDING_APPROVAL in create map
        ).toCreateMap()
        assertEquals("PENDING_APPROVAL", propMap["status"])
        assertFalse(propMap.containsKey("id"))
    }

    @Test
    fun `device diagnostics manager captures valid snapshot`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = DeviceDiagnosticsManager(context).captureSnapshot()
        assertTrue(snapshot.appHeapMaxMb > 0)
        assertTrue(snapshot.batteryPercent in 0..100)
    }

    @Test
    fun `arc core reactor renders holographic HUD label`() {
        composeTestRule.setContent {
            KikoAiTheme {
                ArcCoreReactor(
                    phase = CoreReactorPhase.IDLE,
                    statusLabel = "STANDBY",
                    onClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("arc_core_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("KIKO").assertIsDisplayed()
        composeTestRule.onNodeWithText("STANDBY").assertIsDisplayed()
    }
}
