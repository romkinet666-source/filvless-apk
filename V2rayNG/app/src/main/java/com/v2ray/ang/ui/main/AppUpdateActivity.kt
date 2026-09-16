package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.AppUpdateDownload
import com.v2ray.ang.handler.DownloadState
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.v2ray.ang.handler.UpdateSnooze

class AppUpdateActivity : BaseComponentActivity() {
    private var download by mutableStateOf(DownloadState("checking"))
    private var details by mutableStateOf<com.v2ray.ang.dto.CheckUpdateResult?>(null)
    private var refreshJob: Job? = null
    private var permissionNeeded by mutableStateOf(false)
    private var installerOpened by mutableStateOf(false)
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Keep the APK on cancellation. MY_PACKAGE_REPLACED or the next start cleans successful updates.
        installerOpened = false
        refresh(false)
    }
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionNeeded = !canInstall()
        if (!permissionNeeded) install()
    }
    private fun canInstall() = Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh(savedInstanceState == null && intent.getBooleanExtra("automatic", false))
    }
    private fun refresh(automatic: Boolean, retry: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            try {
                AppUpdateDownload.cleanup(this@AppUpdateActivity)
                download = AppUpdateDownload.status(this@AppUpdateActivity)
                if (download.stage == "none" || retry) {
                    download = DownloadState("checking")
                    val update = UpdateCheckerManager.checkForUpdate(true)
                    details = update
                    AppUpdateDownload.enqueue(this@AppUpdateActivity, update, retry, userRequested = !automatic)
                    download = AppUpdateDownload.status(this@AppUpdateActivity)
                }
                if (details == null && download.notes.isBlank() && download.stage != "none") {
                    try { details = UpdateCheckerManager.checkForUpdate(true).takeIf { it.latestVersion == download.version } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Offline installation of the stored APK remains possible. */ }
                }
                while (download.stage == "downloading" || download.stage == "waiting") {
                    delay(1500)
                    download = AppUpdateDownload.status(this@AppUpdateActivity)
                }
                // Show release details and Later before entering the system installer.
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: com.v2ray.ang.handler.UpdateDownloadException) { download = DownloadState("failed", failure = error.reason) }
            catch (_: java.io.IOException) { download = DownloadState("failed", failure = "network") }
            catch (_: Exception) { download = DownloadState("failed", failure = "download") }
        }
    }
    @Suppress("DEPRECATION")
    private fun install() {
        if (installerOpened) return
        installerOpened = true
        lifecycleScope.launch {
            try {
                AppUpdateDownload.markOffered(this@AppUpdateActivity)
                if (!canInstall()) {
                    permissionNeeded = true
                    installerOpened = false
                    return@launch
                }
                val file = AppUpdateDownload.installationFile(this@AppUpdateActivity)
                    ?: error("Invalid package")
                val uri = FileProvider.getUriForFile(this@AppUpdateActivity, "$packageName.cache", file)
                installer.launch(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { installerOpened = false; download = DownloadState("failed") }
        }
    }
    @Composable override fun ScreenContent() {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(R.string.fv_check_update), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(when (download.stage) {
                    "ready" -> R.string.fv_update_ready
                    "waiting" -> if (download.waitingForWifi) R.string.fv_update_waiting_wifi else R.string.fv_update_waiting_network
                    "downloading" -> R.string.fv_update_downloading
                    "failed" -> when (download.failure) {
                        "space" -> R.string.fv_update_no_space
                        "verification" -> R.string.fv_update_bad_package
                        "network" -> R.string.fv_update_network_error
                        else -> R.string.fv_update_failed
                    }
                    "cancelled" -> R.string.fv_update_cancelled
                    "none" -> R.string.update_already_latest_version
                    else -> R.string.update_checking_for_update
                }))
                if (download.version.isNotBlank()) Text(download.version)
                val size = download.size.takeIf { it > 0 } ?: details?.size ?: 0L
                if (size > 0) Text(stringResource(R.string.fv_update_size, size / 1_000_000.0))
                if (download.stage == "downloading" || download.stage == "waiting") {
                    LinearProgressIndicator(progress = { download.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${download.percent}%")
                    Text(stringResource(R.string.fv_update_background_hint))
                    TextButton(onClick = {
                        refreshJob?.cancel()
                        lifecycleScope.launch {
                            AppUpdateDownload.cancel(this@AppUpdateActivity)
                            download = DownloadState("cancelled")
                        }
                    }) { Text(stringResource(R.string.fv_update_cancel_download)) }
                }
                if (download.stage == "checking") CircularProgressIndicator()
                if (permissionNeeded) {
                    Text(stringResource(R.string.fv_update_permission))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 26) permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    }) { Text(stringResource(R.string.fv_update_allow)) }
                } else if (download.stage == "ready") Button(onClick = { install() }, enabled = !installerOpened) {
                    Text(stringResource(R.string.fv_update_install))
                }
                if (download.stage == "failed" || download.stage == "cancelled") Button(onClick = { refresh(false, retry = true) }) {
                    Text(stringResource(R.string.fv_devices_retry))
                }
                if (download.version.isNotBlank()) TextButton(onClick = {
                    UpdateSnooze.postpone(download.version); finish()
                }) { Text(stringResource(R.string.fv_update_later)) }
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.fv_close)) }
            }
        }
    }
}
