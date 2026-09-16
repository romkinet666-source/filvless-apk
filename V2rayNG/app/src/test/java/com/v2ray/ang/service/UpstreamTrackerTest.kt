package com.v2ray.ang.service

import org.junit.Assert.*
import org.junit.Test

class UpstreamTrackerTest {
    @Test fun lateLossOfMobileDoesNotEraseNewWifi() {
        val tracker = UpstreamTracker<String>()
        assertFalse(tracker.available("mobile"))
        assertTrue(tracker.available("wifi"))
        assertFalse(tracker.lost("mobile"))
        assertEquals("wifi", tracker.current)
        assertFalse(tracker.available("wifi"))
    }
    @Test fun reconnectSameNetworkAfterOutageRequiresRecovery() {
        val tracker = UpstreamTracker<String>()
        tracker.available("wifi")
        assertTrue(tracker.lost("wifi"))
        assertNull(tracker.current)
        assertTrue(tracker.available("wifi"))
        tracker.reset()
        assertFalse(tracker.available("mobile"))
    }
}
