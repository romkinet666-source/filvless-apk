package com.v2ray.ang.dto

/** Subscription-Userinfo is provider metadata, not proof of an active VPN connection. */
fun parseSubscriptionExpiry(header: String?): Long? {
    val field = header?.takeIf { it.length <= 4096 }?.split(';')
        ?.map { it.trim().split('=', limit = 2) }
        ?.firstOrNull { it.size == 2 && it[0].trim().equals("expire", ignoreCase = true) }
        ?: return null
    return field[1].trim().toLongOrNull()?.takeIf { it in 1L..32_503_680_000L }
}
