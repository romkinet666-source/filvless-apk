package com.v2ray.ang.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.parseSubscriptionExpiry
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.service.quickPingTarget
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.SubscriptionDevice
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.awaitResponse
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class DiagnosticResult {
    NETWORK_OK, NO_NETWORK, NO_PROFILE, NO_SUBSCRIPTION, SUBSCRIPTION_OK, SUBSCRIPTION_DENIED,
    SUBSCRIPTION_UNREACHABLE, SUBSCRIPTION_REDIRECT, EXPIRED, EXPIRY_CACHED,
    SERVER_OK, SERVER_UNREACHABLE, SERVER_NOT_TESTED, VPN_OFF, VPN_OK, VPN_UNREACHABLE
}
data class DiagnosticStep(val step: Int, val result: DiagnosticResult)

/** Only fixed codes are exported. Never include endpoints, profile names, URLs, IDs or exceptions. */
internal fun diagnosticReport(steps: List<DiagnosticStep>): String =
    "Filvless ${BuildConfig.VERSION_NAME}\nAndroid ${android.os.Build.VERSION.SDK_INT}\n" +
        steps.joinToString("\n") { "${it.step}: ${it.result.name}" }

internal class ConnectionDiagnostics(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    @Suppress("DEPRECATION")
    suspend fun run(publish: suspend (DiagnosticStep) -> Unit) = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.allNetworks.any { net -> cm.getNetworkCapabilities(net)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true }
        publish(DiagnosticStep(0, if (network) DiagnosticResult.NETWORK_OK else DiagnosticResult.NO_NETWORK))
        if (!network) return@withContext
        val guid = MmkvManager.getSelectServer()
        val profile = guid?.let(MmkvManager::decodeServerConfig)
        if (profile == null) { publish(DiagnosticStep(1, DiagnosticResult.NO_PROFILE)); return@withContext }
        val sub = MmkvManager.decodeSubscription(profile.subscriptionId)
        val subscription = if (sub == null || sub.url.isBlank()) DiagnosticResult.NO_SUBSCRIPTION else try {
            val url = sub.url.toHttpUrl()
            require(url.isHttps || sub.allowInsecureUrl)
            val builder = Request.Builder().url(url).header("User-Agent", sub.userAgent?.takeIf { it.isNotBlank() } ?: "v2rayNG/${BuildConfig.VERSION_NAME}")
            SubscriptionDevice.headers().forEach { (key, value) -> builder.header(key, value) }
            JsonUtil.parseHeadersToMap(sub.requestHeaders).forEach { (key, value) -> builder.header(key, value) }
            if (url.username.isNotBlank()) builder.header("Authorization", Credentials.basic(url.username, url.password))
            client.newCall(builder.build()).awaitResponse { response ->
                val expiry = parseSubscriptionExpiry(response.header("subscription-userinfo"))
                when {
                    response.code == 401 || response.code == 403 -> DiagnosticResult.SUBSCRIPTION_DENIED
                    response.isRedirect -> DiagnosticResult.SUBSCRIPTION_REDIRECT
                    !response.isSuccessful -> DiagnosticResult.SUBSCRIPTION_UNREACHABLE
                    expiry != null && expiry <= System.currentTimeMillis() / 1000 -> DiagnosticResult.EXPIRED
                    expiry == null && sub.expiresAtSeconds?.let { it <= System.currentTimeMillis() / 1000 } == true -> DiagnosticResult.EXPIRY_CACHED
                    else -> DiagnosticResult.SUBSCRIPTION_OK
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { DiagnosticResult.SUBSCRIPTION_UNREACHABLE }
        publish(DiagnosticStep(1, subscription))
        val target = when {
            profile.configType == EConfigType.CUSTOM -> quickPingTarget(MmkvManager.decodeServerRaw(guid))
            profile.configType in setOf(EConfigType.HYSTERIA2, EConfigType.WIREGUARD) || profile.network in setOf("quic", "kcp", "hysteria2") -> null
            else -> profile.server?.takeIf { it.isNotBlank() }?.let { host ->
                profile.serverPort?.toIntOrNull()?.takeIf { it in 1..65535 }?.let { host to it }
            }
        }
        val server = if (target == null) DiagnosticResult.SERVER_NOT_TESTED else try {
            // DNS + connect may block platform code; interrupt on cancellation and close the socket.
            runInterruptible { Socket().use { it.connect(InetSocketAddress(target.first, target.second), 2500) } }
            DiagnosticResult.SERVER_OK
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { DiagnosticResult.SERVER_UNREACHABLE }
        publish(DiagnosticStep(2, server))
        publish(DiagnosticStep(3, checkRunningTunnel()))
    }

    private suspend fun checkRunningTunnel(): DiagnosticResult {
        val events = Channel<String>(Channel.UNLIMITED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_STOP_SUCCESS -> events.trySend("IDLE")
                    AppConfig.MSG_CONNECTION_HEALTH -> events.trySend(intent.getStringExtra("content").orEmpty())
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), Utils.receiverFlags())
        return try {
            MessageHelper.sendMsg2ServiceForResult(context, AppConfig.MSG_CHECK_CONNECTION_HEALTH, "") { handled ->
                if (!handled) events.trySend("IDLE")
            }
            withTimeoutOrNull(48_000) {
                var checking = false
                var result: DiagnosticResult? = null
                while (result == null) {
                    when (events.receive()) {
                        "CHECKING" -> checking = true
                        "AVAILABLE" -> if (checking) result = DiagnosticResult.VPN_OK
                        "UNREACHABLE", "WAITING_NETWORK" -> result = DiagnosticResult.VPN_UNREACHABLE
                        "IDLE" -> result = DiagnosticResult.VPN_OFF
                    }
                }
                result
            } ?: DiagnosticResult.VPN_UNREACHABLE
        } finally { context.unregisterReceiver(receiver); events.close() }
    }
}
