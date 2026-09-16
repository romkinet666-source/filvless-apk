package com.v2ray.ang.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionHealthEventsTest {
    @Test fun initialFailureDoesNotNotify() {
        val events = ConnectionHealthEventState()
        assertNull(events.accept(ConnectionHealth.CHECKING))
        assertNull(events.accept(ConnectionHealth.UNREACHABLE))
    }

    @Test fun confirmedSessionNotifiesOnceForLossAndRecovery() {
        val events = ConnectionHealthEventState()
        assertNull(events.accept(ConnectionHealth.AVAILABLE))
        assertEquals(ConnectionHealthEvent.LOST, events.accept(ConnectionHealth.WAITING_NETWORK))
        assertNull(events.accept(ConnectionHealth.UNREACHABLE))
        assertEquals(ConnectionHealthEvent.RESTORED, events.accept(ConnectionHealth.AVAILABLE))
        assertNull(events.accept(ConnectionHealth.AVAILABLE))
    }

    @Test fun recoveringHandoverDoesNotCreateFalseAlert() {
        val events = ConnectionHealthEventState()
        assertNull(events.accept(ConnectionHealth.AVAILABLE))
        assertNull(events.accept(ConnectionHealth.RECOVERING))
        assertNull(events.accept(ConnectionHealth.CHECKING))
        assertNull(events.accept(ConnectionHealth.AVAILABLE))
    }

    @Test fun idleResetsPreviousSession() {
        val events = ConnectionHealthEventState()
        events.accept(ConnectionHealth.AVAILABLE)
        events.accept(ConnectionHealth.IDLE)
        assertNull(events.accept(ConnectionHealth.UNREACHABLE))
    }
}
