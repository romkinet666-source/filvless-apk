package com.v2ray.ang.ui.main

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.*
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.*

class DiagnosticsActivity : BaseComponentActivity() {
    private var steps by mutableStateOf<List<DiagnosticStep>>(emptyList())
    private var running by mutableStateOf(false)
    private var copied by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var task: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); diagnose() }
    private fun diagnose() {
        task?.cancel(); steps = emptyList(); copied = false; failed = false; running = true
        task = lifecycleScope.launch {
            try {
                ConnectionDiagnostics(this@DiagnosticsActivity).run { step ->
                    // Each check publishes immutable snapshots; Compose state is updated on Main.
                    withContext(Dispatchers.Main.immediate) { steps = steps + step }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { running = false }
        }
    }
    @Composable override fun ScreenContent() {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.fv_connection_diagnostics), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.fv_connection_diagnostics_hint), style = MaterialTheme.typography.bodySmall)
                val titles = listOf(R.string.fv_diag_network, R.string.fv_subscription, R.string.fv_diag_server, R.string.fv_diag_tunnel)
                steps.forEach { step ->
                    val status = diagnosticStatus(step.result)
                    val cardColor = when (status) {
                        DiagnosticStatus.GOOD -> Color(0xFF183A2A)
                        DiagnosticStatus.BAD -> Color(0xFF481D29)
                        DiagnosticStatus.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val accent = when (status) {
                        DiagnosticStatus.GOOD -> Color(0xFF7FE1A7)
                        DiagnosticStatus.BAD -> Color(0xFFFFA6B4)
                        DiagnosticStatus.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor)) { Column(Modifier.padding(14.dp)) {
                        Text(stringResource(titles[step.step]), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(5.dp))
                        Text(stringResource(diagnosticText(step.result)), style = MaterialTheme.typography.bodyMedium, color = accent)
                    } }
                }
                if (running) { CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(R.string.fv_health_checking)) }
                if (failed) Text(stringResource(R.string.fv_diag_failed))
                if (!running) {
                    Button(onClick = { diagnose() }) { Text(stringResource(R.string.fv_devices_retry)) }
                    if (steps.isNotEmpty()) TextButton(onClick = {
                        Utils.setClipboard(this@DiagnosticsActivity, diagnosticReport(steps)); copied = true
                    }) { Text(stringResource(if (copied) R.string.fv_diag_copied else R.string.fv_diag_copy)) }
                }
                if (steps.any { it.result == DiagnosticResult.EXPIRED }) Button(onClick = {
                    MmkvManager.encodeSettings("filvless_purchase_return", System.currentTimeMillis().toString())
                    Utils.openUri(this@DiagnosticsActivity, "https://t.me/filvless_bot")
                }) { Text(stringResource(R.string.fv_buy_subscription)) }
                TextButton(onClick = { Utils.openUri(this@DiagnosticsActivity, "https://t.me/Godfather099") }) {
                    Text(stringResource(R.string.fv_support))
                }
                TextButton(onClick = { startActivity(android.content.Intent(this@DiagnosticsActivity, BackgroundWorkActivity::class.java)) }) {
                    Text(stringResource(R.string.fv_background))
                }
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.fv_close)) }
            }
        }
    }
}

private enum class DiagnosticStatus { GOOD, BAD, NEUTRAL }

private fun diagnosticStatus(result: DiagnosticResult): DiagnosticStatus = when (result) {
    DiagnosticResult.NETWORK_OK, DiagnosticResult.SUBSCRIPTION_OK, DiagnosticResult.SERVER_OK, DiagnosticResult.VPN_OK -> DiagnosticStatus.GOOD
    DiagnosticResult.NO_NETWORK, DiagnosticResult.SUBSCRIPTION_DENIED, DiagnosticResult.SUBSCRIPTION_UNREACHABLE,
    DiagnosticResult.EXPIRED, DiagnosticResult.EXPIRY_CACHED, DiagnosticResult.SERVER_UNREACHABLE,
    DiagnosticResult.VPN_UNREACHABLE -> DiagnosticStatus.BAD
    DiagnosticResult.NO_PROFILE, DiagnosticResult.NO_SUBSCRIPTION, DiagnosticResult.SUBSCRIPTION_REDIRECT,
    DiagnosticResult.SERVER_NOT_TESTED, DiagnosticResult.VPN_OFF -> DiagnosticStatus.NEUTRAL
}

internal fun diagnosticText(result: DiagnosticResult): Int = when (result) {
    DiagnosticResult.NETWORK_OK -> R.string.fv_diag_network_ok
    DiagnosticResult.NO_NETWORK -> R.string.fv_diag_no_network
    DiagnosticResult.NO_PROFILE -> R.string.fv_diag_no_profile
    DiagnosticResult.NO_SUBSCRIPTION -> R.string.fv_diag_no_subscription
    DiagnosticResult.SUBSCRIPTION_OK -> R.string.fv_diag_subscription_ok
    DiagnosticResult.SUBSCRIPTION_DENIED -> R.string.fv_diag_denied
    DiagnosticResult.SUBSCRIPTION_UNREACHABLE -> R.string.fv_diag_subscription_error
    DiagnosticResult.SUBSCRIPTION_REDIRECT -> R.string.fv_diag_redirect
    DiagnosticResult.EXPIRED -> R.string.fv_diag_expired
    DiagnosticResult.EXPIRY_CACHED -> R.string.fv_diag_expiry_cached
    DiagnosticResult.SERVER_OK -> R.string.fv_diag_server_ok
    DiagnosticResult.SERVER_UNREACHABLE -> R.string.fv_diag_server_error
    DiagnosticResult.SERVER_NOT_TESTED -> R.string.fv_diag_server_skipped
    DiagnosticResult.VPN_OFF -> R.string.fv_diag_vpn_off
    DiagnosticResult.VPN_OK -> R.string.fv_diag_vpn_ok
    DiagnosticResult.VPN_UNREACHABLE -> R.string.fv_diag_vpn_error
}
