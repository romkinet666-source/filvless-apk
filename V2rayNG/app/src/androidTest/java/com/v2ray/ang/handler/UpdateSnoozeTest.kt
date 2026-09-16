package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class UpdateSnoozeTest {
    @Test fun postponesOnlyChosenVersionAndRearmsNotification() {
        val version = "qa-snooze"
        val previous = MmkvManager.decodeSettingsString("filvless_notified_version")
        try {
            MmkvManager.encodeSettings("filvless_notified_version", version)
            UpdateSnooze.postpone(version)
            assertTrue(UpdateSnooze.isActive(version))
            assertFalse(UpdateSnooze.isActive("qa-other"))
            assertEquals("", MmkvManager.decodeSettingsString("filvless_notified_version"))
            MmkvManager.encodeSettings("filvless_update_later_$version", "1")
            assertFalse(UpdateSnooze.isActive(version))
        } finally {
            MmkvManager.encodeSettings("filvless_update_later_$version", "")
            MmkvManager.encodeSettings("filvless_notified_version", previous)
        }
    }
}
