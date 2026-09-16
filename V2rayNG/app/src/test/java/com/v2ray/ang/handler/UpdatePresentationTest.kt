package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class UpdatePresentationTest {
    @Test fun limitsReleaseTextAndStopsBeforeInstallationInstructions() {
        val notes = conciseReleaseNotes("## Новое\n- Исправление\n\n## Установка\nDo not include this")
        assertTrue(notes.contains("Исправление"))
        assertFalse(notes.contains("Do not include"))
        assertEquals("", conciseReleaseNotes(null))
        assertTrue(conciseReleaseNotes("a".repeat(9000)).length <= 1800)
        assertTrue(conciseReleaseNotes((1..50).joinToString("\n")).lines().size <= 9)
    }
    @Test fun tomorrowSnoozeExpiresAndCannotBePermanentAfterClockChange() {
        assertFalse(updateReminderDue(2000, 1000))
        assertTrue(updateReminderDue(1000, 1000))
        assertTrue(updateReminderDue(999, 1000))
        assertTrue(updateReminderDue(100_000_000, 1000))
    }
}
