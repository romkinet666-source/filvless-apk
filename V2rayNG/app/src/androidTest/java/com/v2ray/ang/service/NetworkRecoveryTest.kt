package com.v2ray.ang.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in emulator test: changes its Wi-Fi/mobile radios; never run on a user's phone. */
class NetworkRecoveryTest {
    @Test fun recoversAcrossWifiMobileAndOfflineThenHonoursStop() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("networkRecovery") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(VpnService.prepare(context) == null) { "Grant emulator ACTIVATE_VPN app-op first" }
        check(MmkvManager.decodeAllServerList().isEmpty()) { "Requires empty QA installation" }
        val oldSelected = MmkvManager.getSelectServer()
        val guid = UUID.randomUUID().toString()
        val events = LinkedBlockingQueue<String>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getIntExtra("key", 0) == AppConfig.MSG_CONNECTION_HEALTH)
                    events.offer(intent.getStringExtra("content").orEmpty())
                if (intent?.getIntExtra("key", 0) == AppConfig.MSG_STATE_STOP_SUCCESS) events.offer("STOPPED")
            }
        }
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
        fun expect(value: String) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(55)
            val seen = mutableListOf<String>()
            while (System.nanoTime() < deadline) {
                val next = events.poll(1, TimeUnit.SECONDS) ?: continue
                seen.add(next)
                if (next == value) { println("RECOVERY_EXPECT=$value EVENTS=$seen"); return }
            }
            fail("Expected $value, observed $seen")
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), Utils.receiverFlags())
        try {
            shell("svc wifi enable"); shell("svc data enable")
            MmkvManager.encodeServerConfig(guid, ProfileItem(configType = EConfigType.CUSTOM, remarks = "QA network recovery"))
            MmkvManager.encodeServerRaw(guid, """{"outbounds":[{"protocol":"freedom"}],"log":{"loglevel":"warning"}}""")
            LauncherManager.startService(context, guid)
            expect("AVAILABLE")
            val diagnosticSteps = mutableListOf<com.v2ray.ang.handler.DiagnosticStep>()
            kotlinx.coroutines.runBlocking {
                com.v2ray.ang.handler.ConnectionDiagnostics(context).run { diagnosticSteps.add(it) }
            }
            assertEquals(com.v2ray.ang.handler.DiagnosticResult.VPN_OK, diagnosticSteps.last().result)
            assertFalse(com.v2ray.ang.handler.diagnosticReport(diagnosticSteps).contains(guid))
            events.clear()
            shell("svc wifi disable")
            expect("RECOVERING"); expect("AVAILABLE")
            events.clear()
            shell("svc data disable")
            expect("WAITING_NETWORK")
            shell("svc wifi enable")
            expect("RECOVERING"); expect("AVAILABLE")
            LauncherManager.stopService(context)
            expect("STOPPED")
            events.clear()
            shell("svc wifi disable"); shell("svc data enable")
            assertNull("Stopped VPN must not restart", events.poll(4, TimeUnit.SECONDS))
        } finally {
            LauncherManager.stopService(context)
            shell("svc wifi enable"); shell("svc data enable")
            context.unregisterReceiver(receiver)
            MmkvManager.removeServer(guid)
            MmkvManager.setSelectServer(oldSelected.orEmpty())
        }
    }
}
