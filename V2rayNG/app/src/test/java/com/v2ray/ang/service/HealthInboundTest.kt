package com.v2ray.ang.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class HealthInboundTest {
    @Test fun preservesCustomOutboundsBalancerAndExistingListeners() {
        val raw = """{"inbounds":[{"protocol":"tun","tag":"tun"}],"outbounds":[{"tag":"fi1"},{"tag":"fi2"}],"routing":{"balancers":[{"tag":"auto","selector":["fi"]}]},"burstObservatory":{"subjectSelector":["fi"]}}"""
        val before = JsonParser.parseString(raw).asJsonObject
        val after = JsonParser.parseString(withHealthInbound(raw, 19001)).asJsonObject
        for (key in listOf("outbounds", "routing", "burstObservatory")) assertEquals(before[key], after[key])
        assertEquals(before.getAsJsonArray("inbounds")[0], after.getAsJsonArray("inbounds")[0])
        val probe = after.getAsJsonArray("inbounds")[1].asJsonObject
        assertEquals("127.0.0.1", probe["listen"].asString)
        assertEquals("http", probe["protocol"].asString)
        assertEquals(19001, probe["port"].asInt)
    }
}
