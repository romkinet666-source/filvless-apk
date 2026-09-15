package com.v2ray.ang.dto

import org.junit.Assert.*
import org.junit.Test

class SubscriptionExpiryTest {
    @Test fun readsExpiryWithoutConfusingTrafficFields() {
        assertEquals(1_800_000_000L, parseSubscriptionExpiry("upload=1; download=2; total=100; expire=1800000000"))
        assertEquals(1_800_000_000L, parseSubscriptionExpiry(" EXPIRE = 1800000000 "))
    }

    @Test fun absentUnlimitedAndMalformedExpiryRemainUnknown() {
        for (value in listOf(null, "", "total=123", "expire=0", "expire=-1", "expire=no", "expire=9999999999999999999999", "expire=99999999999")) {
            assertNull(parseSubscriptionExpiry(value))
        }
    }
}
