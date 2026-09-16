package com.v2ray.ang.handler

import android.app.DownloadManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Explicit emulator-only fixture for exercising Android's real installer with a locally built APK. */
class AppUpdateInstallFixtureTest {
    @Suppress("DEPRECATION")
    @Test fun stageLocalApkForInstaller() {
        val args = InstrumentationRegistry.getArguments()
        val version = args.getString("installFixtureVersion")
        assumeTrue("Only runs with an explicit installer fixture", version != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.getExternalFilesDir(null), "update-fixture.apk")
        check(fixture.exists())
        val apk = File(context.getExternalFilesDir(null), "updates/update.apk")
        apk.parentFile!!.mkdirs()
        fixture.copyTo(apk, overwrite = true)
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input -> val buffer = ByteArray(65536); while (true) {
            val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        val id = context.getSystemService(DownloadManager::class.java).addCompletedDownload(
            "Filvless installer fixture", "QA", false, "application/vnd.android.package-archive", apk.path, apk.length(), true)
        File(context.filesDir, "app-update.json").writeText(JSONObject().put("id", id).put("version", version)
            .put("hash", digest.digest().joinToString("") { "%02x".format(it) })
            .put("size", apk.length()).put("offered", false).toString())
        fixture.delete()
    }
}
