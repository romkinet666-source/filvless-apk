package com.v2ray.ang.service

import org.junit.Assert.*
import org.junit.Test

class QuickPingTargetTest {
    @Test fun selectsProxyInsteadOfDirectAndDns() {
        val raw = """{"outbounds":[{"protocol":"freedom"},{"protocol":"dns"},{"protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443}]}}]}"""
        assertEquals("example.com" to 443, quickPingTarget(raw))
    }
    @Test fun supportsServersAndFlatSettings() {
        assertEquals("example.com" to 8443, quickPingTarget("""{"outbounds":[{"protocol":"trojan","settings":{"servers":[{"address":"example.com","port":8443}]}}]}"""))
        assertEquals("example.com" to 443, quickPingTarget("""{"outbounds":[{"protocol":"vless","settings":{"address":"example.com","port":443}}]}"""))
    }
    @Test fun rejectsUdpInvalidPortsAndBrokenJson() {
        assertNull(quickPingTarget("""{"outbounds":[{"protocol":"vless","streamSettings":{"network":"quic"},"settings":{"address":"example.com","port":443}}]}"""))
        assertNull(quickPingTarget("""{"outbounds":[{"protocol":"vless","settings":{"address":"example.com","port":0}}]}"""))
        assertNull(quickPingTarget("broken"))
        assertNull(quickPingTarget(null))
    }
}
