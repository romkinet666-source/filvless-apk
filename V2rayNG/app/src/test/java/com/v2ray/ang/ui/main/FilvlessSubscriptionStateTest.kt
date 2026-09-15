package com.v2ray.ang.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilvlessSubscriptionStateTest {
    @Test fun elapsedTimeUsesDaemonStartAndHandlesMissingOrRebootedClock() {
        assertEquals(0L, connectionElapsedSeconds(0, 100_000))
        assertEquals(0L, connectionElapsedSeconds(100_000, 1_000))
        assertEquals(0L, connectionElapsedSeconds(1_000, 1_999))
        assertEquals(65L, connectionElapsedSeconds(1_000, 66_000))
        assertEquals(3_601L, connectionElapsedSeconds(1_000, 3_602_000))
    }
    @Test fun providerDenialIsNotAConnectableServer() {
        assertTrue(isUnsupportedDeviceNotice("Данное устройство не поддерживается"))
        assertTrue(isUnsupportedDeviceNotice("⚠ THIS DEVICE IS NOT SUPPORTED"))
    }

    @Test fun ordinaryServerNamesAndEmptyNamesAreNotProviderDenials() {
        assertFalse(isUnsupportedDeviceNotice(""))
        assertFalse(isUnsupportedDeviceNotice("Финляндия • VLESS"))
        assertFalse(isUnsupportedDeviceNotice("LTE #1"))
    }
}
