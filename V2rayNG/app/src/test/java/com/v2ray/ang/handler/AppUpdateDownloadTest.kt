package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class AppUpdateDownloadTest {
    private val url = "https://github.com/romkinet666-source/filvless-apk/releases/download/v0.6.5-preview/Filvless-0.6.5-preview-universal.apk"
    @Test fun acceptsOnlyBoundedVerifiedReleaseAssets() {
        assertTrue(validUpdateDownload(url, "a".repeat(64), 87_000_000))
        assertFalse(validUpdateDownload(url, null, 87_000_000))
        assertFalse(validUpdateDownload(url, "g".repeat(64), 87_000_000))
        assertFalse(validUpdateDownload(url, "a".repeat(64), 0))
        assertFalse(validUpdateDownload(url, "a".repeat(64), 250_000_001))
    }
    @Test fun rejectsOtherReposQueriesAndPathTraversal() {
        for (bad in listOf(url.replace("github.com/", "github.com.evil.test/"),
            url.replace("filvless-apk/releases", "other/releases"), "$url?token=x",
            url.replace("v0.6.5-preview/", "../"), url.replace("https:", "http:"))) {
            assertFalse(bad, validUpdateDownload(bad, "a".repeat(64), 100))
        }
    }
}
