package com.v2ray.ang.handler

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import com.v2ray.ang.dto.entities.SubscriptionItem
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SubscriptionReminderWorkerTest {
    @Test fun deliversOnceAndRemovesStaleReminderAfterRenewal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        check(manager.areNotificationsEnabled()) { "Grant notifications on QA emulator" }
        val id = UUID.randomUUID().toString()
        val original = MmkvManager.decodeSettingsBool(SubscriptionReminder.KEY, true)
        val expiry = System.currentTimeMillis() / 1000 + 3 * 86_400 - 60
        val sub = SubscriptionItem(remarks = "Reminder QA", url = "https://example.invalid/private-subscription", expiresAtSeconds = expiry)
        fun worker() = TestListenableWorkerBuilder<SubscriptionReminder.ReminderWorker>(context).build()
        try {
            MmkvManager.encodeSettings(SubscriptionReminder.KEY, true)
            MmkvManager.encodeSubscription(id, sub)
            worker().doWork()
            kotlinx.coroutines.withTimeout(3000) {
                while (manager.activeNotifications.none { it.tag == "filvless_expiry" && it.id == id.hashCode() }) kotlinx.coroutines.delay(50)
            }
            val first = manager.activeNotifications.single { it.tag == "filvless_expiry" && it.id == id.hashCode() }
            worker().doWork()
            val second = manager.activeNotifications.single { it.tag == "filvless_expiry" && it.id == id.hashCode() }
            assertEquals(first.postTime, second.postTime)
            assertEquals(expiry.toString(), MmkvManager.decodeSettingsString("filvless_expiry_notified_$id"))
            sub.expiresAtSeconds = expiry + 30 * 86_400
            MmkvManager.encodeSubscription(id, sub)
            worker().doWork()
            kotlinx.coroutines.withTimeout(3000) {
                while (manager.activeNotifications.any { it.tag == "filvless_expiry" && it.id == id.hashCode() }) kotlinx.coroutines.delay(50)
            }
            assertFalse(manager.activeNotifications.any { it.tag == "filvless_expiry" && it.id == id.hashCode() })
        } finally {
            manager.cancel("filvless_expiry", id.hashCode())
            MmkvManager.removeSubscription(id)
            MmkvManager.encodeSettings("filvless_expiry_notified_$id", "")
            MmkvManager.encodeSettings(SubscriptionReminder.KEY, original)
        }
    }
}
