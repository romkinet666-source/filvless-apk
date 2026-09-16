package com.v2ray.ang.service

import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class QuickPingWorkerTest {
    @Test fun cancelledBatchCannotStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val events = java.util.concurrent.atomic.AtomicInteger()
        val worker = RealPingWorkerService(context, listOf("missing-profile"), onlyTcp = true) { events.incrementAndGet() }
        worker.cancel()
        worker.start()
        Thread.sleep(100)
        assertEquals(0, events.get())
    }

    @Test fun repeatedStartDoesNotDuplicateResults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val results = java.util.concurrent.atomic.AtomicInteger()
        val finished = CountDownLatch(1)
        val worker = RealPingWorkerService(context, listOf("missing-profile"), onlyTcp = true) {
            if (it is RealPingEvent.Result) results.incrementAndGet()
            if (it is RealPingEvent.Finish) finished.countDown()
        }
        try {
            worker.start()
            worker.start()
            assertTrue(finished.await(3, TimeUnit.SECONDS))
            assertEquals(1, results.get())
        } finally { worker.cancel() }
    }

    @Test fun measuresFourteenCustomProfilesWithoutStartingXray() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val guids = mutableListOf<String>()
        val finished = CountDownLatch(1)
        val results = ConcurrentHashMap<String, Long>()
        ServerSocket(0, 32).use { socket ->
            try {
                repeat(14) {
                    val guid = UUID.randomUUID().toString()
                    guids.add(guid)
                    MmkvManager.encodeServerConfig(guid, ProfileItem(configType = EConfigType.CUSTOM))
                    MmkvManager.encodeServerRaw(guid, """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"127.0.0.1","port":${socket.localPort}}]}}]}""")
                }
                val start = System.nanoTime()
                val worker = RealPingWorkerService(context, guids, onlyTcp = true) { event ->
                    when (event) {
                        is RealPingEvent.Result -> results[event.guid] = event.delayMillis
                        is RealPingEvent.Finish -> finished.countDown()
                        else -> Unit
                    }
                }
                worker.start()
                try {
                    assertTrue("Quick batch did not finish", finished.await(5, TimeUnit.SECONDS))
                    assertEquals(14, results.size)
                    assertTrue(results.values.all { it >= 1 })
                    println("QUICK_PING_14_MS=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                } finally { worker.cancel() }
            } finally { guids.forEach(MmkvManager::removeServer) }
        }
    }
}
