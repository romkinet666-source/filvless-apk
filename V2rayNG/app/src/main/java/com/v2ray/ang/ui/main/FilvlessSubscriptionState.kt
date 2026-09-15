package com.v2ray.ang.ui.main

/** Some subscription providers return a notice encoded as a proxy profile. */
internal fun isUnsupportedDeviceNotice(remarks: String): Boolean =
    remarks.contains("Данное устройство не поддерживается", ignoreCase = true) ||
        remarks.contains("This device is not supported", ignoreCase = true)

internal fun connectionElapsedSeconds(startedAt: Long, now: Long): Long =
    if (startedAt <= 0L || now < startedAt) 0L else (now - startedAt) / 1_000L

private val flagPattern = Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}")
private val countryFlags = listOf(
    listOf("германия", "germany") to "🇩🇪", listOf("финляндия", "finland") to "🇫🇮",
    listOf("швеция", "sweden") to "🇸🇪", listOf("нидерланды", "netherlands") to "🇳🇱",
    listOf("франция", "france") to "🇫🇷", listOf("сша", "united states") to "🇺🇸",
    listOf("эстония", "estonia") to "🇪🇪", listOf("латвия", "latvia") to "🇱🇻",
    listOf("польша", "poland") to "🇵🇱", listOf("турция", "turkey") to "🇹🇷",
    listOf("россия", "russia") to "🇷🇺", listOf("казахстан", "kazakhstan") to "🇰🇿",
)

internal fun serverFlag(remarks: String): String = flagPattern.find(remarks)?.value
    ?: countryFlags.firstOrNull { (names, _) -> names.any { remarks.contains(it, ignoreCase = true) } }?.second
    ?: "🌐"

/** The country flag already has its own leading icon in the server row. */
internal fun serverDisplayName(remarks: String): String =
    remarks.replace(flagPattern, "").trim().ifEmpty { remarks }
