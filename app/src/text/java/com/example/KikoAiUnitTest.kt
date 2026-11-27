package com.example

import com.example.data.KikoAiService
import com.example.model.DevProposal
import com.example.model.DeviceDiagnosticsSnapshot
import com.example.model.KikoLanguage
import com.example.model.KikoTeam
import com.example.model.MemoryEntry
import com.example.model.SmartReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KikoAiUnitTest {

    private val aiService = KikoAiService()

    @Test
    fun `intent router routes specialized commands to the 12 KIKO teams`() {
        assertEquals(KikoTeam.ANDROID_CONTROL, aiService.detectTargetTeam("Turn on flashlight", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.CODING, aiService.detectTargetTeam("Write a Kotlin coroutine function", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.VISION, aiService.detectTargetTeam("Scan this document image", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.RESEARCH, aiService.detectTargetTeam("Research https://developer.android.com", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.DEVELOPMENT, aiService.detectTargetTeam("Generate a new module proposal", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.MEMORY, aiService.detectTargetTeam("Remember that I prefer Urdu", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.SECURITY, aiService.detectTargetTeam("Run security audit", KikoTeam.INTELLIGENCE))
        assertEquals(KikoTeam.AUTOMATION, aiService.detectTargetTeam("Remind me at 8am", KikoTeam.INTELLIGENCE))
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
            status = "APPROVED",
        ).toCreateMap()
        assertEquals("PENDING_APPROVAL", propMap["status"])
        assertFalse(propMap.containsKey("id"))
    }
}
