package com.v2ray.ang.handler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.R
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

internal fun expiryReminderDue(expiry: Long?, now: Long, notifiedExpiry: Long): Boolean =
    expiry != null && expiry > now && expiry - now <= 3 * 86_400L && expiry != notifiedExpiry

object SubscriptionReminder {
    const val KEY = "filvless_expiry_reminder"
    private const val WORK = "filvless_expiry_reminders"
    private const val TAG = "filvless_expiry"
    fun schedule(context: Context, replace: Boolean = false) {
        val work = RemoteWorkManager.getInstance(context)
        if (!MmkvManager.decodeSettingsBool(KEY, true)) {
            work.cancelUniqueWork(WORK)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.tag == TAG }.forEach { manager.cancel(TAG, it.id) }
            return
        }
        work.enqueueUniquePeriodicWork(WORK, if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build())
    }
    class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!MmkvManager.decodeSettingsBool(KEY, true)) return Result.success()
            val context = AppLocaleManager.localizedContext(applicationContext)
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = "filvless_subscription_expiry"
            if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel,
                context.getString(R.string.fv_expiry_reminder), NotificationManager.IMPORTANCE_DEFAULT))
            val now = System.currentTimeMillis() / 1000
            val subscriptions = MmkvManager.decodeSubscriptions().filter { it.subscription.enabled && it.subscription.url.isNotBlank() }
            val eligibleIds = subscriptions.filter {
                val expiry = it.subscription.expiresAtSeconds
                expiry != null && expiry > now && expiry - now <= 3 * 86_400L
            }.map { it.guid.hashCode() }.toSet()
            manager.activeNotifications.filter { it.tag == TAG && it.id !in eligibleIds }.forEach { manager.cancel(TAG, it.id) }
            subscriptions.forEach { sub ->
                val expiry = sub.subscription.expiresAtSeconds
                val key = "filvless_expiry_notified_${sub.guid}"
                val notified = MmkvManager.decodeSettingsString(key)?.toLongOrNull() ?: 0L
                if (!expiryReminderDue(expiry, now, notified)) return@forEach
                val intent = PendingIntent.getActivity(context, sub.guid.hashCode(), Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://t.me/filvless_bot")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(requireNotNull(expiry) * 1000))
                val body = context.getString(R.string.fv_expiry_notification_body, date)
                val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle(context.getString(R.string.fv_expiry_notification_title)).setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(intent)
                    .setAutoCancel(true).setOnlyAlertOnce(true).build()
                if (isStopped || !MmkvManager.decodeSettingsBool(KEY, true)) return@forEach
                try {
                    manager.notify(TAG, sub.guid.hashCode(), notification)
                    MmkvManager.encodeSettings(key, expiry.toString())
                } catch (_: SecurityException) { /* Do not mark delivered when Android denies notifications. */ }
            }
            return Result.success()
        }
    }
}
