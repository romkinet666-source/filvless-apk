package com.v2ray.ang.service

import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.awaitResponse
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

/** Explicit local proxy: never silently test the phone's direct connection instead of the core. */
internal object TunnelProbe {
    private val base = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    suspend fun check(port: Int): Boolean {
        if (port !in 1..65535) return false
        val client = base.newBuilder().proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))).build()
        for (url in listOf(SettingsManager.getDelayTestUrl(), SettingsManager.getDelayTestUrl(true)).distinct()) {
            try {
                val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
                if (client.newCall(request).awaitResponse { it.isSuccessful }) return true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The second endpoint avoids a single-site outage false negative. */ }
        }
        return false
    }
}
