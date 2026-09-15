package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class FilvlessPreferencesTest {
    @Test fun defaultsDoNotConnectWithoutConsent() {
        val state = FilvlessPreferences()
        assertFalse(state.autoConnect)
        assertTrue(state.haptics)
        assertTrue(state.visualEffects)
        assertEquals("auto", state.language)
    }

    @Test fun eachSwitchChangesOnlyItsOwnPreference() {
        val original = FilvlessPreferences(language = "ru")
        assertEquals(original.copy(autoConnect = true), original.withPreference(FilvlessPreference.AUTO_CONNECT, true))
        assertEquals(original.copy(haptics = false), original.withPreference(FilvlessPreference.HAPTICS, false))
        assertEquals(original.copy(visualEffects = false), original.withPreference(FilvlessPreference.VISUAL_EFFECTS, false))
        assertEquals(original, original.withPreference(FilvlessPreference.HAPTICS, false).withPreference(FilvlessPreference.HAPTICS, true))
    }

    @Test fun autoConnectNeedsOptInASelectedServerAndNoExistingConnectionOrProviderDenial() {
        for (enabled in listOf(false, true)) for (running in listOf(false, true))
            for (denied in listOf(false, true)) for (guid in listOf(null, "", " ", "server-guid")) {
                val expected = enabled && !running && !denied && guid == "server-guid"
                assertEquals(expected, shouldAutoConnect(enabled, running, guid, denied))
            }
    }

    @Test fun countryFlagsUseProviderFlagFirstAndDoNotInventUnknownLocations() {
        assertEquals("🇩🇪", serverFlag("Германия 1"))
        assertEquals("🇫🇮", serverFlag("FINLAND"))
        assertEquals("🇸🇪", serverFlag("🇸🇪 Premium"))
        assertEquals("🌐", serverFlag("LTE AUTO"))
        assertEquals("🌐", serverFlag(""))
    }
}
