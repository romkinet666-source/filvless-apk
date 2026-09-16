package com.v2ray.ang.handler

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppUpdateStorageTest {
    @Test fun cleansInstalledPackageButKeepsNewerPendingPackage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = File(context.filesDir, "app-update.json")
        val apk = File(context.getExternalFilesDir(null), "updates/update.apk")
        check(!state.exists()) { "Test requires no pending update" }
        try {
            apk.parentFile!!.mkdirs()
            apk.writeText("test package")
            state.writeText(JSONObject().put("id", -1).put("version", "99.0.0-preview").toString())
            AppUpdateDownload.cleanup(context)
            assertTrue(apk.exists())
            assertTrue(state.exists())
            assertNull(AppUpdateDownload.installationFile(context))
            state.writeText(JSONObject().put("id", -1).put("version", "0.1.0-preview").toString())
            AppUpdateDownload.cleanup(context)
            assertFalse(apk.exists())
            assertFalse(state.exists())
        } finally { state.delete(); apk.delete() }
    }
}
