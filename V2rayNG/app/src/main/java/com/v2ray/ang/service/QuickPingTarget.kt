package com.v2ray.ang.service

import com.google.gson.JsonParser

/** TCP endpoints exposed by a custom profile; does not claim to test routing or authentication. */
internal fun quickPingTargets(raw: String?): List<Pair<String, Int>> = runCatching {
    val outbounds = JsonParser.parseString(raw).asJsonObject.getAsJsonArray("outbounds")
    outbounds.mapNotNull { element ->
        val outbound = element.asJsonObject
        val protocol = outbound.get("protocol")?.asString
        if (protocol !in setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http")) return@mapNotNull null
        val transport = outbound.getAsJsonObject("streamSettings")?.get("network")?.asString
        if (transport in setOf("quic", "kcp", "hysteria2")) return@mapNotNull null
        val settings = outbound.getAsJsonObject("settings") ?: return@mapNotNull null
        val endpoint = (settings.getAsJsonArray("vnext") ?: settings.getAsJsonArray("servers"))?.firstOrNull()?.asJsonObject ?: settings
        val address = endpoint.get("address")?.asString
        val port = endpoint.get("port")?.asInt
        if (!address.isNullOrBlank() && port != null && port in 1..65535) address to port else null
    }.distinct()
}.getOrDefault(emptyList())

internal fun quickPingTarget(raw: String?): Pair<String, Int>? = quickPingTargets(raw).firstOrNull()
