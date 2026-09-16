package com.v2ray.ang.handler

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.v2ray.ang.dto.entities.SubscriptionItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class SubscriptionWorkerTest {
    @Test fun refreshPreservesServersOnFailureAndHonorsDisabledSetting() = runBlocking {
        val key = "filvless_auto_update_subscriptions"
        val previousEnabled = MmkvManager.decodeSettingsBool(key, false)
        val previousSelected = MmkvManager.getSelectServer()
        val id = UUID.randomUUID().toString()
        val status = AtomicInteger(200)
        val requests = AtomicInteger(0)
        val payload = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val responder = thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { /* consume HTTP headers */ }
                        requests.incrementAndGet()
                        val body = payload.get() ?: if (status.get() == 200)
                            "vless://00000000-0000-4000-8000-000000000001@127.0.0.1:443?security=none&type=tcp#WorkerFixture"
                        else ""
                        val bytes = body.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 ${status.get()} Test\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: java.io.IOException) { if (server.isClosed) break }
            }
        }
        try {
            MmkvManager.encodeSettings(key, true)
            MmkvManager.encodeSubscription(id, SubscriptionItem(
                remarks = "WorkerFixture", url = "http://127.0.0.1:${server.localPort}/sub",
                autoUpdate = true, allowInsecureUrl = true, updateInterval = 360
            ))
            fun worker() = TestListenableWorkerBuilder<SubscriptionUpdater.UpdateTask>(
                ApplicationProvider.getApplicationContext()
            ).setInputData(workDataOf("subId" to id)).build()

            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            val updatedAt = MmkvManager.decodeSubscription(id)!!.lastUpdated
            val servers = MmkvManager.decodeServerList(id).toList()
            assertTrue(updatedAt > 0)
            assertEquals(1, servers.size)
            assertTrue(requests.get() > 0)

            status.set(500)
            assertEquals(ListenableWorker.Result.retry(), worker().doWork())
            assertEquals(updatedAt, MmkvManager.decodeSubscription(id)!!.lastUpdated)
            assertEquals(servers, MmkvManager.decodeServerList(id))

            assertTrue(MmkvManager.decodeSubscription(id)!!.lastUpdateFailed)
            status.set(200)
            for (bad in listOf("", "<html>temporarily unavailable</html>",
                "vless://00000000-0000-4000-8000-000000000002@127.0.0.1:443?security=none&type=tcp#This%20device%20is%20not%20supported")) {
                payload.set(bad)
                assertEquals(ListenableWorker.Result.retry(), worker().doWork())
                assertEquals(servers, MmkvManager.decodeServerList(id))
                assertEquals(updatedAt, MmkvManager.decodeSubscription(id)!!.lastUpdated)
            }
            payload.set(null)
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertFalse(MmkvManager.decodeSubscription(id)!!.lastUpdateFailed)
            MmkvManager.encodeSettings(key, false)
            val before = requests.get()
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals(before, requests.get())
        } finally {
            server.close()
            responder.join(4000)
            MmkvManager.removeSubscription(id)
            previousSelected?.let(MmkvManager::setSelectServer)
            MmkvManager.encodeSettings(key, previousEnabled)
        }
    }
}
