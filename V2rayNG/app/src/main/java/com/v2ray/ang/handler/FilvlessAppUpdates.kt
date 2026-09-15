package com.v2ray.ang.handler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.R
import com.v2ray.ang.ui.main.MainActivity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

object FilvlessAppUpdates {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(6, TimeUnit.HOURS).build()
        RemoteWorkManager.getInstance(context).enqueueUniquePeriodicWork("filvless_app_updates", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                val update = UpdateCheckerManager.checkForUpdate(true)
                val version = update.latestVersion
                if (!update.hasUpdate || version == null || MmkvManager.decodeSettingsString("filvless_notified_version") == version)
                    return Result.success()
                val context = AppLocaleManager.localizedContext(applicationContext)
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
                    (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED))
                    return Result.success()
                val manager = context.getSystemService(NotificationManager::class.java)
                val channel = "filvless_updates"
                if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel,
                    context.getString(R.string.fv_check_update), NotificationManager.IMPORTANCE_DEFAULT))
                val intent = PendingIntent.getActivity(context, 41, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle(context.getString(R.string.fv_update_available, version))
                    .setContentText(context.getString(R.string.fv_download_update))
                    .setContentIntent(intent).setAutoCancel(true).build()
                manager.notify(4101, notification)
                MmkvManager.encodeSettings("filvless_notified_version", version)
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result.retry()
            }
        }
    }
}
