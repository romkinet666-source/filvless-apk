package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.handler.AppUpdateDownload
import kotlinx.coroutines.runBlocking

class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        Thread {
            try { runBlocking { AppUpdateDownload.cleanup(context.applicationContext) } }
            catch (_: Exception) { /* Cleanup is retried on the next app start. */ }
            finally { pending.finish() }
        }.start()
    }
}
