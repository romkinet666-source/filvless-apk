package com.v2ray.ang.handler

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.CheckUpdateResult
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun validUpdateDownload(url: String?, hash: String?, size: Long): Boolean =
    url?.matches(Regex("https://github\\.com/romkinet666-source/filvless-apk/releases/download/v[0-9][A-Za-z0-9._-]*/Filvless-[A-Za-z0-9._-]+-universal\\.apk")) == true &&
        hash?.matches(Regex("[a-fA-F0-9]{64}")) == true && size in 1..250_000_000L

internal data class DownloadState(val stage: String, val version: String = "", val percent: Int = 0, val size: Long = 0, val notes: String = "", val waitingForWifi: Boolean = false)

/** State and APK belong to this app; a file lock also serializes the :bg worker process. */
internal object AppUpdateDownload {
    private fun stateFile(context: Context) = AtomicFile(File(context.filesDir, "app-update.json"))
    private fun apk(context: Context) = File(requireNotNull(context.getExternalFilesDir(null)), "updates/update.apk")
    private fun manager(context: Context) = context.getSystemService(DownloadManager::class.java)
    private fun read(context: Context): JSONObject? = runCatching {
        JSONObject(stateFile(context).readFully().toString(Charsets.UTF_8))
    }.getOrNull()
    private fun write(context: Context, state: JSONObject) {
        val file = stateFile(context)
        val stream = file.startWrite()
        try { stream.write(state.toString().toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private suspend fun <T> locked(context: Context, block: () -> T): T = withContext(Dispatchers.IO) {
        synchronized(AppUpdateDownload) {
            RandomAccessFile(File(context.filesDir, "app-update.lock"), "rw").use { file ->
                file.channel.lock().use { block() }
            }
        }
    }
    private fun clear(context: Context, state: JSONObject?) {
        state?.optLong("id")?.takeIf { it > 0 }?.let { manager(context).remove(it) }
        apk(context).delete()
        stateFile(context).delete()
    }
    suspend fun cleanup(context: Context) = locked(context) {
        val state = read(context)
        if (state != null && compareReleaseVersions(BuildConfig.VERSION_NAME, state.optString("version")) >= 0) clear(context, state)
    }
    suspend fun enqueue(context: Context, update: CheckUpdateResult, retry: Boolean = false, userRequested: Boolean = false) = locked(context) {
        val policy = AppUpdatePolicy.read()
        if (!policy.allowsDownload(userRequested)) return@locked
        if (!update.hasUpdate || compareReleaseVersions(update.latestVersion.orEmpty(), BuildConfig.VERSION_NAME) <= 0) return@locked
        require(validUpdateDownload(update.downloadUrl, update.sha256, update.size))
        val previous = read(context)
        if (previous != null && !retry) {
            // Keep an existing package until installation/cancellation is resolved.
            if (compareReleaseVersions(previous.optString("version"), update.latestVersion.orEmpty()) >= 0 ||
                !previous.optBoolean("failed")) return@locked
        }
        clear(context, previous)
        apk(context).parentFile?.mkdirs()
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle(context.getString(R.string.app_name) + " " + update.latestVersion)
            .setDescription(context.getString(R.string.fv_update_downloading))
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "updates/update.apk")
        if (policy == AppUpdatePolicy.WIFI_ONLY) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        val id = manager(context).enqueue(request)
        try {
            write(context, JSONObject().put("id", id).put("version", update.latestVersion)
                .put("notes", conciseReleaseNotes(update.releaseNotes)).put("wifiOnly", policy == AppUpdatePolicy.WIFI_ONLY).put("url", update.downloadUrl).put("hash", update.sha256).put("size", update.size).put("offered", false))
        } catch (error: Exception) { manager(context).remove(id); throw error }
    }
    suspend fun setPolicy(context: Context, policy: AppUpdatePolicy) {
        val pending = locked(context) {
            check(MmkvManager.encodeSettings(AppUpdatePolicy.KEY, policy.storageValue))
            val state = read(context) ?: return@locked null
            val complete = manager(context).query(DownloadManager.Query().setFilterById(state.optLong("id"))).use {
                it != null && it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
            }
            // A verified/finished package needs no network and remains available for installation.
            if (complete) return@locked null
            val version = state.optString("version")
            val update = CheckUpdateResult(hasUpdate = true, latestVersion = version, releaseNotes = state.optString("notes"),
                downloadUrl = state.optString("url").ifBlank {
                    "https://github.com/romkinet666-source/filvless-apk/releases/download/v$version/Filvless-$version-universal.apk"
                }, sha256 = state.optString("hash"), size = state.optLong("size"))
            clear(context, state)
            update.takeIf { validUpdateDownload(it.downloadUrl, it.sha256, it.size) }
        }
        if (pending != null) enqueue(context, pending)
    }
    suspend fun status(context: Context): DownloadState = locked(context) {
        val state = read(context) ?: return@locked DownloadState("none")
        val version = state.optString("version")
        if (compareReleaseVersions(BuildConfig.VERSION_NAME, version) >= 0) {
            clear(context, state); return@locked DownloadState("none")
        }
        if (state.optBoolean("failed")) return@locked DownloadState("failed", version)
        val result = manager(context).query(DownloadManager.Query().setFilterById(state.getLong("id"))).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return@use DownloadState("failed", version)
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (verify(context, state)) DownloadState("ready", version, 100)
                    else { write(context, state.put("failed", true)); apk(context).delete(); DownloadState("failed", version) }
                }
                DownloadManager.STATUS_FAILED -> DownloadState("failed", version)
                else -> {
                    val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val waiting = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_PAUSED &&
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)) in listOf(DownloadManager.PAUSED_WAITING_FOR_NETWORK, DownloadManager.PAUSED_QUEUED_FOR_WIFI)
                    DownloadState(if (waiting) "waiting" else "downloading", version, ((bytes * 100) / state.getLong("size")).toInt().coerceIn(0, 99))
                }
            }
        }
        result.copy(size = state.optLong("size"), notes = state.optString("notes"),
            waitingForWifi = result.stage == "waiting" && state.optBoolean("wifiOnly"))
    }
    @Suppress("DEPRECATION")
    private fun verify(context: Context, state: JSONObject): Boolean = runCatching {
        val file = apk(context)
        require(file.length() == state.getLong("size"))
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) {
            val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        require(digest.digest().joinToString("") { "%02x".format(it) }.equals(state.getString("hash"), true))
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val downloaded = requireNotNull(pm.getPackageArchiveInfo(file.path, flags))
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(downloaded.packageName == context.packageName && downloaded.versionName == state.getString("version"))
        val newCode = if (Build.VERSION.SDK_INT >= 28) downloaded.longVersionCode else downloaded.versionCode.toLong()
        val oldCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        require(newCode > oldCode)
        val newSigners = if (Build.VERSION.SDK_INT >= 28) downloaded.signingInfo?.apkContentsSigners else downloaded.signatures
        val oldSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        require(!newSigners.isNullOrEmpty() && !oldSigners.isNullOrEmpty() && newSigners.toSet() == oldSigners.toSet())
        true
    }.getOrDefault(false)
    suspend fun claimAutomaticInstall(context: Context): Boolean {
        if (status(context).stage != "ready") return false
        return locked(context) {
            val state = read(context) ?: return@locked false
            if (state.optBoolean("offered") || UpdateSnooze.isActive(state.optString("version"))) false else { write(context, state.put("offered", true)); true }
        }
    }
    suspend fun installationFile(context: Context): File? = locked(context) {
        val state = read(context) ?: return@locked null
        if (verify(context, state)) apk(context) else null
    }
    suspend fun markOffered(context: Context) = locked(context) {
        read(context)?.let { write(context, it.put("offered", true)) }
    }
}
