package com.v2ray.ang.handler

enum class AppUpdatePolicy(val storageValue: String) {
    WIFI_ONLY("wifi"), ANY_NETWORK("any"), MANUAL("manual");

    fun allowsDownload(userRequested: Boolean) = this != MANUAL || userRequested

    companion object {
        const val KEY = "filvless_app_update_download_policy"
        // Preserve the existing automatic download behaviour when upgrading.
        fun from(value: String?) = entries.firstOrNull { it.storageValue == value } ?: ANY_NETWORK
        fun read() = from(MmkvManager.decodeSettingsString(KEY))
    }
}
