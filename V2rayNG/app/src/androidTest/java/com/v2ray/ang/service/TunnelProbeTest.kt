package com.v2ray.ang.service

import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils
import java.net.ServerSocket
import kotlinx.coroutines.*
import libv2ray.CoreCallbackHandler
import org.junit.Assert.*
import org.junit.Test

class TunnelProbeTest {
    @Test fun checksHttpThroughRunningCoreAndFailsWithoutCore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CoreNativeManager.initCoreEnv(context)
        val core = CoreNativeManager.newCoreController(object : CoreCallbackHandler {
            override fun startup() = 0L
            override fun shutdown() = 0L
            override fun onEmitStatus(l: Long, s: String?) = 0L
        })
        val oldUrl = MmkvManager.decodeSettingsString(AppConfig.PREF_DELAY_TEST_URL)
        val port = Utils.findRandomFreePort()
        ServerSocket(0).use { endpoint ->
            val response = launch(Dispatchers.IO) {
                endpoint.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
            try {
                MmkvManager.encodeSettings(AppConfig.PREF_DELAY_TEST_URL, "http://127.0.0.1:${endpoint.localPort}/health")
                core.startLoop(withHealthInbound("""{"outbounds":[{"protocol":"freedom"}]}""", port), 0)
                assertTrue(core.isRunning)
                assertTrue(withTimeout(10_000) { TunnelProbe.check(port) })
                response.join()
                core.stopLoop()
                assertFalse(withTimeout(15_000) { TunnelProbe.check(port) })
            } finally {
                if (core.isRunning) core.stopLoop()
                endpoint.close()
                response.cancelAndJoin()
                MmkvManager.encodeSettings(AppConfig.PREF_DELAY_TEST_URL, oldUrl)
            }
        }
    }
}
