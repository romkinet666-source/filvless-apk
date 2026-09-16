package com.v2ray.ang.handler

import android.app.DownloadManager
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.dto.CheckUpdateResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdatePolicyStorageTest {
    @Suppress("DEPRECATION")
    @Test fun changingPolicyKeepsCompletedPackage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = MmkvManager.decodeSettingsString(AppUpdatePolicy.KEY)
        val state = File(context.filesDir, "app-update.json")
        val apk = File(context.getExternalFilesDir(null), "updates/update.apk")
        check(!state.exists())
        apk.parentFile!!.mkdirs()
        apk.writeText("completed fixture")
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = manager.addCompletedDownload("QA", "QA", false, "application/vnd.android.package-archive", apk.path, apk.length(), true)
        try {
            state.writeText(JSONObject().put("id", id).put("version", "99.0.0-preview").toString())
            AppUpdateDownload.setPolicy(context, AppUpdatePolicy.MANUAL)
            assertTrue(apk.exists())
            assertEquals(id, JSONObject(state.readText()).getLong("id"))
            assertEquals(AppUpdatePolicy.MANUAL, AppUpdatePolicy.read())
        } finally {
            manager.remove(id); state.delete(); apk.delete()
            MmkvManager.encodeSettings(AppUpdatePolicy.KEY, original)
        }
    }
    @Test fun manualBlocksBackgroundButAllowsExplicitDownloadAndCancelsQueuedWork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = MmkvManager.decodeSettingsString(AppUpdatePolicy.KEY)
        val state = File(context.filesDir, "app-update.json")
        check(!state.exists()) { "Test requires no pending update" }
        val future = CheckUpdateResult(true, "99.0.0-preview", releaseNotes = "## New\n- Fixture note",
            downloadUrl = "https://github.com/romkinet666-source/filvless-apk/releases/download/v99.0.0-preview/Filvless-99.0.0-preview-universal.apk",
            sha256 = "a".repeat(64), size = 100)
        try {
            AppUpdateDownload.setPolicy(context, AppUpdatePolicy.MANUAL)
            AppUpdateDownload.enqueue(context, future)
            assertFalse(state.exists())
            AppUpdateDownload.enqueue(context, future, userRequested = true)
            assertTrue(state.exists())
            assertTrue(JSONObject(state.readText()).getString("notes").contains("Fixture note"))
            val id = JSONObject(state.readText()).getLong("id")
            AppUpdateDownload.setPolicy(context, AppUpdatePolicy.MANUAL)
            assertFalse(state.exists())
            context.getSystemService(DownloadManager::class.java).query(DownloadManager.Query().setFilterById(id)).use {
                assertFalse(it.moveToFirst())
            }
            AppUpdateDownload.setPolicy(context, AppUpdatePolicy.WIFI_ONLY)
            assertEquals(AppUpdatePolicy.WIFI_ONLY, AppUpdatePolicy.read())
        } finally {
            AppUpdateDownload.setPolicy(context, AppUpdatePolicy.MANUAL)
            MmkvManager.encodeSettings(AppUpdatePolicy.KEY, original)
        }
    }

    @Test fun cancelledVersionStaysCancelledUntilExplicitRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val policy = MmkvManager.decodeSettingsString(AppUpdatePolicy.KEY)
        val cancelled = MmkvManager.decodeSettingsString("filvless_update_cancelled")
        val state = File(context.filesDir, "app-update.json")
        check(!state.exists())
        val future = CheckUpdateResult(true, "98.0.0-preview",
            downloadUrl = "https://github.com/romkinet666-source/filvless-apk/releases/download/v98.0.0-preview/Filvless-98.0.0-preview-universal.apk",
            sha256 = "a".repeat(64), size = 100)
        try {
            AppUpdateDownload.setPolicy(context, AppUpdatePolicy.ANY_NETWORK)
            AppUpdateDownload.enqueue(context, future, userRequested = true)
            val id = JSONObject(state.readText()).getLong("id")
            AppUpdateDownload.cancel(context)
            assertFalse(state.exists())
            context.getSystemService(DownloadManager::class.java).query(DownloadManager.Query().setFilterById(id)).use {
                assertFalse(it.moveToFirst())
            }
            AppUpdateDownload.enqueue(context, future)
            assertFalse(state.exists())
            AppUpdateDownload.enqueue(context, future, userRequested = true)
            assertTrue(state.exists())
            assertEquals("", MmkvManager.decodeSettingsString("filvless_update_cancelled"))
        } finally {
            AppUpdateDownload.cancel(context)
            MmkvManager.encodeSettings(AppUpdatePolicy.KEY, policy)
            MmkvManager.encodeSettings("filvless_update_cancelled", cancelled)
        }
    }
}
