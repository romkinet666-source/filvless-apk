package com.v2ray.ang.ui.main

data class FilvlessPreferences(
    val expiryReminder: Boolean = true,
    val autoConnect: Boolean = false,
    val haptics: Boolean = true,
    val visualEffects: Boolean = true,
    val autoUpdateSubscriptions: Boolean = false,
    val updateDownloadPolicy: com.v2ray.ang.handler.AppUpdatePolicy = com.v2ray.ang.handler.AppUpdatePolicy.ANY_NETWORK,
    val language: String = "auto",
)

enum class FilvlessPreference(val storageKey: String) {
    EXPIRY_REMINDER("filvless_expiry_reminder"),
    AUTO_UPDATE_SUBSCRIPTIONS("filvless_auto_update_subscriptions"),
    AUTO_CONNECT("filvless_auto_connect"),
    HAPTICS("filvless_haptics"),
    VISUAL_EFFECTS("filvless_visual_effects"),
}

internal fun FilvlessPreferences.withPreference(key: FilvlessPreference, enabled: Boolean): FilvlessPreferences =
    when (key) {
        FilvlessPreference.EXPIRY_REMINDER -> copy(expiryReminder = enabled)
        FilvlessPreference.AUTO_UPDATE_SUBSCRIPTIONS -> copy(autoUpdateSubscriptions = enabled)
        FilvlessPreference.AUTO_CONNECT -> copy(autoConnect = enabled)
        FilvlessPreference.HAPTICS -> copy(haptics = enabled)
        FilvlessPreference.VISUAL_EFFECTS -> copy(visualEffects = enabled)
    }

internal fun shouldAutoConnect(enabled: Boolean, running: Boolean, selectedGuid: String?, providerDenied: Boolean): Boolean =
    enabled && !running && !selectedGuid.isNullOrBlank() && !providerDenied
