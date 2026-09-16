package com.v2ray.ang.ui.main

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

class BackgroundWorkActivity : BaseComponentActivity() {
    private var restricted by mutableStateOf(false)
    private var optimized by mutableStateOf(false)
    private var saving by mutableStateOf(false)
    override fun onResume() {
        super.onResume()
        restricted = Build.VERSION.SDK_INT >= 28 && getSystemService(ActivityManager::class.java).isBackgroundRestricted
        val power = getSystemService(PowerManager::class.java)
        optimized = !power.isIgnoringBatteryOptimizations(packageName)
        saving = power.isPowerSaveMode
    }
    private fun openSettings(action: String, packageUri: Boolean = false) {
        val intent = Intent(action).apply { if (packageUri) data = Uri.parse("package:$packageName") }
        try { startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
    @Composable override fun ScreenContent() {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.fv_background), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.fv_background_hint))
                Text(stringResource(if (restricted) R.string.fv_bg_restricted else R.string.fv_bg_not_restricted))
                Text(stringResource(if (optimized) R.string.fv_bg_optimized else R.string.fv_bg_unrestricted))
                Text(stringResource(if (saving) R.string.fv_bg_saver_on else R.string.fv_bg_saver_off))
                Text(stringResource(R.string.fv_bg_limits), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }) {
                    Text(stringResource(R.string.fv_bg_app_settings))
                }
                TextButton(onClick = { openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) {
                    Text(stringResource(R.string.fv_bg_battery_settings))
                }
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.fv_close)) }
            }
        }
    }
}
