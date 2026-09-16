package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class AppUpdatePolicyTest {
    @Test fun manualNeverAllowsUnrequestedDownload() {
        assertFalse(AppUpdatePolicy.MANUAL.allowsDownload(false))
        assertTrue(AppUpdatePolicy.MANUAL.allowsDownload(true))
        assertTrue(AppUpdatePolicy.WIFI_ONLY.allowsDownload(false))
        assertTrue(AppUpdatePolicy.ANY_NETWORK.allowsDownload(false))
    }
    @Test fun migrationPreservesPreviousAutomaticBehaviour() {
        assertEquals(AppUpdatePolicy.ANY_NETWORK, AppUpdatePolicy.from(null))
        assertEquals(AppUpdatePolicy.ANY_NETWORK, AppUpdatePolicy.from("unknown"))
        assertEquals(AppUpdatePolicy.WIFI_ONLY, AppUpdatePolicy.from("wifi"))
        assertEquals(AppUpdatePolicy.MANUAL, AppUpdatePolicy.from("manual"))
    }
}
