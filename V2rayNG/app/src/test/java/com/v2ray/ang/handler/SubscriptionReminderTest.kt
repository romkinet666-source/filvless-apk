package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class SubscriptionReminderTest {
    private val now = 1_800_000_000L
    @Test fun onlyRemindsInsideThreeDayWindow() {
        assertFalse(expiryReminderDue(null, now, 0))
        assertFalse(expiryReminderDue(now - 1, now, 0))
        assertFalse(expiryReminderDue(now, now, 0))
        assertFalse(expiryReminderDue(now + 259_201, now, 0))
        assertTrue(expiryReminderDue(now + 259_200, now, 0))
        assertTrue(expiryReminderDue(now + 1, now, 0))
    }
    @Test fun renewalGetsItsOwnReminderButRepeatedChecksDoNot() {
        val expiry = now + 259_200
        assertFalse(expiryReminderDue(expiry, now, expiry))
        assertTrue(expiryReminderDue(expiry + 86_400, now + 86_400, expiry))
    }
}
