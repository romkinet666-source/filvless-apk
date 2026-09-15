package com.v2ray.ang.handler

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.IDN
import java.net.URI

internal data class FilvlessRouting(val vpn: String = "", val direct: String = "")

internal fun routingDomains(text: String): List<String> = text.split(Regex("[\\s,;]+"))
    .filter { it.isNotBlank() }.map { raw ->
        val input = if (raw.startsWith("https://") || raw.startsWith("http://")) runCatching { URI(raw).host }.getOrNull()
            ?: throw IllegalArgumentException("Invalid domain") else raw.removePrefix("*.")
        val host = IDN.toASCII(input.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase()
        require(host.length <= 253 && host.contains('.') && host.split('.').all {
            it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
        } && !host.matches(Regex("[0-9.]+"))) { "Invalid domain" }
        host
    }.distinct().also { require(it.size <= 200) { "Too many domains" } }

internal fun validateRouting(settings: FilvlessRouting) {
    val vpn = routingDomains(settings.vpn)
    val direct = routingDomains(settings.direct)
    require(vpn.none { a -> direct.any { b -> a == b || a.endsWith(".$b") || b.endsWith(".$a") } }) {
        "Conflicting domains"
    }
}

internal object FilvlessRoutingStore {
    private const val KEY = "filvless_routing"
    fun read(): FilvlessRouting = runCatching {
        val json = JsonParser.parseString(MmkvManager.decodeSettingsString(KEY, "{}")).asJsonObject
        FilvlessRouting(json.get("vpn")?.asString.orEmpty(), json.get("direct")?.asString.orEmpty())
    }.getOrDefault(FilvlessRouting())
    fun save(settings: FilvlessRouting): Boolean {
        validateRouting(settings)
        val json = JsonObject().apply { addProperty("vpn", settings.vpn); addProperty("direct", settings.direct) }
        return MmkvManager.encodeSettings(KEY, json.toString())
    }
}

/** Overlays explicit user domains on both imported JSON and generated Xray configs. */
internal fun applyFilvlessRouting(raw: String, settings: FilvlessRouting): String {
    validateRouting(settings)
    val vpn = routingDomains(settings.vpn)
    val direct = routingDomains(settings.direct)
    if (vpn.isEmpty() && direct.isEmpty()) return raw
    val json = JsonParser.parseString(raw).asJsonObject
    val outbounds = json.getAsJsonArray("outbounds") ?: error("Missing outbounds")
    fun freshTag(prefix: String): String {
        val tags = outbounds.mapNotNull { it.asJsonObject.get("tag")?.asString }.toSet()
        return generateSequence(prefix) { "$it-x" }.first { it !in tags }
    }
    val proxy = outbounds.map { it.asJsonObject }.firstOrNull {
        it.get("protocol")?.asString in setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http", "wireguard", "hysteria2")
    }
    val proxyTag = if (vpn.isNotEmpty()) {
        requireNotNull(proxy) { "No VPN outbound for user routing" }
        proxy.get("tag")?.asString?.takeIf { it.isNotBlank() }
            ?: freshTag("filvless-vpn").also { proxy.addProperty("tag", it) }
    } else ""
    val directTag = freshTag("filvless-direct")
    if (direct.isNotEmpty()) outbounds.add(JsonObject().apply {
        addProperty("tag", directTag); addProperty("protocol", "freedom"); add("settings", JsonObject())
    })
    val routing = json.getAsJsonObject("routing") ?: JsonObject().also { json.add("routing", it) }
    val rules = JsonArray()
    fun addRule(domains: List<String>, target: String) {
        if (domains.isEmpty()) return
        rules.add(JsonObject().apply {
            addProperty("type", "field")
            addProperty("outboundTag", target)
            add("domain", JsonArray().apply { domains.forEach { add("domain:$it") } })
        })
    }
    addRule(vpn, proxyTag); addRule(direct, directTag)
    routing.getAsJsonArray("rules")?.forEach { rules.add(it) }
    routing.add("rules", rules)
    json.getAsJsonArray("inbounds")?.forEach { element ->
        val inbound = element.asJsonObject
        if (inbound.get("protocol")?.asString in setOf("socks", "http", "tun", "dokodemo-door")) {
            val sniffing = inbound.getAsJsonObject("sniffing") ?: JsonObject().also { inbound.add("sniffing", it) }
            sniffing.addProperty("enabled", true)
            sniffing.addProperty("routeOnly", true)
            val override = sniffing.getAsJsonArray("destOverride") ?: JsonArray().also { sniffing.add("destOverride", it) }
            for (protocol in listOf("http", "tls", "quic")) if (override.none { it.asString == protocol }) override.add(protocol)
        }
    }
    return json.toString()
}
