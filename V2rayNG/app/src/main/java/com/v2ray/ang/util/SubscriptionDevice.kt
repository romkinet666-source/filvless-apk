package com.v2ray.ang.util

import android.os.Build
import android.provider.Settings
import com.v2ray.ang.AngApplication
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.MmkvManager
import java.security.MessageDigest
import java.util.UUID

/** Stable app-scoped identity for the subscription provider's device limits. */
object SubscriptionDevice {
    @Synchronized
    fun headers(): Map<String, String> {
        val androidId = Settings.Secure.getString(
            AngApplication.application.contentResolver, Settings.Secure.ANDROID_ID
        )
        val identity = androidId?.takeIf { it.isNotBlank() } ?: run {
            MmkvManager.decodeSettingsString("filvless_device_id")?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also { MmkvManager.encodeSettings("filvless_device_id", it) }
        }
        return mapOf(
            "x-hwid" to subscriptionDeviceId(identity),
            "x-device-os" to "Android",
            "x-ver-os" to safeHeaderValue(Build.VERSION.RELEASE),
            "x-device-model" to safeHeaderValue("${Build.MANUFACTURER} ${Build.MODEL}"),
        )
    }
}

internal fun subscriptionDeviceId(identity: String): String = MessageDigest.getInstance("SHA-256")
    .digest("${BuildConfig.APPLICATION_ID}:$identity".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

internal fun safeHeaderValue(value: String): String = value.filter { it.code in 32..126 }.take(128)
