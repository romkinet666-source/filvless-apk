package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class FilvlessDevicesTest {
    @Test fun onlyOurHttpsSubscriptionCanAuthenticateToGateway() {
        val secret = "test-subscription-0001"
        assertEquals(secret, deviceCredential("https://sub.prostobotyg.ru/$secret"))
        for (url in listOf("http://sub.prostobotyg.ru/$secret", "https://other.example/$secret",
            "https://sub.prostobotyg.ru.evil.example/$secret", "https://user@sub.prostobotyg.ru/$secret",
            "https://sub.prostobotyg.ru:8443/$secret", "https://sub.prostobotyg.ru/$secret/extra",
            "https://sub.prostobotyg.ru/$secret?token=other", "https://sub.prostobotyg.ru/short",
            "https://sub.prostobotyg.ru/%74est-subscription-0001", "https://[")) {
            assertNull(url, deviceCredential(url))
        }
    }

    @Test fun parsesDeviceListWithUnknownLimitAndCurrentDevice() {
        val raw = """{"limit":null,"devices":[{"id":"${"a".repeat(64)}","model":"Pixel","platform":"Android","osVersion":"15","updatedAt":"2026-09-15T18:00:00Z","isCurrent":true}]}"""
        val snapshot = parseDeviceSnapshot(raw)
        assertNull(snapshot.limit)
        assertEquals("Pixel", snapshot.devices.single().model)
        assertTrue(snapshot.devices.single().isCurrent)
    }

    @Test fun malformedOrDuplicateDeviceIdsAreRejected() {
        for (raw in listOf("""{"devices":[{"id":"raw-hwid"}]}""",
            """{"devices":[{"id":"${"a".repeat(64)}"},{"id":"${"a".repeat(64)}"}]}""")) {
            assertThrows(IllegalArgumentException::class.java) { parseDeviceSnapshot(raw) }
        }
    }
}
