package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.dto.GitHubRelease
import org.junit.Assert.*
import org.junit.Test

class FilvlessFeaturesTest {
    @Test fun versionsHandlePreviewAndMultiDigitNumbers() {
        assertTrue(compareReleaseVersions("v0.10.0-preview", "0.9.9-preview") > 0)
        assertTrue(compareReleaseVersions("0.5.0", "0.5.0-preview") > 0)
        assertTrue(compareReleaseVersions("0.5.0-rc.10", "0.5.0-rc.2") > 0)
        assertEquals(0, compareReleaseVersions("v0.5.0-preview", "0.5.0-preview"))
        assertTrue(compareReleaseVersions("0.4.1-preview", "0.5.0-preview") < 0)
    }
    @Test fun onlyOwnUniversalApkIsOffered() {
        val root = "https://github.com/romkinet666-source/filvless-apk/releases/download/v0.5.0/"
        val release = GitHubRelease("v0.5.0", "", listOf(
            GitHubRelease.Asset("Filvless-0.5.0-arm64.apk", root + "arm64.apk"),
            GitHubRelease.Asset("Filvless-0.5.0-universal.apk", root + "universal.apk")
        ))
        assertEquals(root + "universal.apk", universalDownloadUrl(release))
        assertNull(universalDownloadUrl(release.copy(assets = listOf(GitHubRelease.Asset("Filvless-0.5.0-universal.apk", "https://other.example/app.apk")))))
    }
    @Test fun routingNormalizesDomainsAndRejectsConflictingLists() {
        assertEquals(listOf("example.com", "test.org"), routingDomains("https://EXAMPLE.com/path\n*.test.org\nexample.com"))
        assertThrows(IllegalArgumentException::class.java) { validateRouting(FilvlessRouting("video.example.com", "example.com")) }
        assertThrows(IllegalArgumentException::class.java) { routingDomains("bad_domain.example") }
        assertThrows(IllegalArgumentException::class.java) { routingDomains("127.0.0.1") }
        assertThrows(IllegalArgumentException::class.java) { routingDomains("http://[") }
    }
    private val raw = """{"inbounds":[{"protocol":"socks"}],"outbounds":[{"tag":"provider-vpn","protocol":"vless"},{"tag":"filvless-direct","protocol":"blackhole"}],"routing":{"rules":[{"network":"tcp,udp","outboundTag":"provider-vpn"}]}}"""
    @Test fun customConfigsGetExplicitRulesWithoutOverwritingProviderRules() {
        val json = JsonParser.parseString(applyFilvlessRouting(raw, FilvlessRouting("vpn.example", "direct.example"))).asJsonObject
        val rules = json.getAsJsonObject("routing").getAsJsonArray("rules")
        assertEquals(3, rules.size())
        assertEquals("provider-vpn", rules[0].asJsonObject.get("outboundTag").asString)
        assertEquals("domain:vpn.example", rules[0].asJsonObject.getAsJsonArray("domain")[0].asString)
        assertEquals("filvless-direct-x", rules[1].asJsonObject.get("outboundTag").asString)
        assertEquals("provider-vpn", rules[2].asJsonObject.get("outboundTag").asString)
        assertTrue(json.getAsJsonArray("inbounds")[0].asJsonObject.getAsJsonObject("sniffing").get("routeOnly").asBoolean)
        assertEquals(raw, applyFilvlessRouting(raw, FilvlessRouting()))
    }
    @Test fun vpnRuleCannotSilentlyBecomeDirect() {
        assertThrows(IllegalArgumentException::class.java) {
            applyFilvlessRouting("""{"outbounds":[{"protocol":"freedom","tag":"direct"}]}""", FilvlessRouting("example.com"))
        }
    }
}
