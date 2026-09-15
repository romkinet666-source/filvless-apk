package com.v2ray.ang.util

import org.junit.Assert.*
import org.junit.Test

class SubscriptionDeviceTest {
    @Test fun identityIsStableAndMatchesProviderFormat() {
        val id = subscriptionDeviceId("android-app-scoped-id")
        assertEquals(id, subscriptionDeviceId("android-app-scoped-id"))
        assertTrue(id.matches(Regex("^[a-zA-Z0-9=-]{10,64}$")))
        assertNotEquals(id, subscriptionDeviceId("another-device"))
        assertFalse(id.contains("android-app-scoped-id"))
    }

    @Test fun deviceDescriptionsCannotInjectHeaders() {
        assertEquals("PixelInjected: true", safeHeaderValue("Pixel\r\nInjected: true"))
        assertEquals(128, safeHeaderValue("x".repeat(300)).length)
        assertEquals("", safeHeaderValue("\u0000\u001f\u007f"))
    }
}
