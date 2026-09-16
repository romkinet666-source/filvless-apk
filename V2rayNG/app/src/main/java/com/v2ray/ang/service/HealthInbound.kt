package com.v2ray.ang.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

/** Runtime-only loopback probe listener; custom profiles need not expose SOCKS/HTTP themselves. */
internal fun withHealthInbound(config: String, port: Int): String {
    require(port in 1..65535)
    val json = JsonParser.parseString(config).asJsonObject
    val inbounds = json.getAsJsonArray("inbounds") ?: JsonArray().also { json.add("inbounds", it) }
    inbounds.add(JsonObject().apply {
        addProperty("tag", "filvless-health-${UUID.randomUUID()}")
        addProperty("listen", "127.0.0.1")
        addProperty("port", port)
        addProperty("protocol", "http")
        add("settings", JsonObject())
    })
    return json.toString()
}
