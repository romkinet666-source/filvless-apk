package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    suspend fun checkForUpdate(includePreRelease: Boolean = true): CheckUpdateResult = withContext(Dispatchers.IO) {
        val response = HttpUtil.getUrlContent(UrlContentRequest(AppConfig.APP_API_URL, timeout = 10000))
            ?: throw IllegalStateException("Release service unavailable")
        val releases = JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
            ?: throw IllegalStateException("Invalid release response")
        val release = releases.filter { (includePreRelease || !it.prerelease) && universalDownloadUrl(it) != null }
            .maxWithOrNull { a, b -> compareReleaseVersions(a.tagName, b.tagName) }
            ?: return@withContext CheckUpdateResult(false)
        if (compareReleaseVersions(release.tagName, BuildConfig.VERSION_NAME) <= 0)
            return@withContext CheckUpdateResult(false)
        CheckUpdateResult(true, release.tagName.removePrefix("v"), release.body,
            universalDownloadUrl(release), isPreRelease = release.prerelease)
    }
}

internal fun universalDownloadUrl(release: GitHubRelease): String? = release.assets.firstOrNull {
    it.name.startsWith("Filvless-") && it.name.endsWith("-universal.apk") &&
        it.browserDownloadUrl.startsWith("https://github.com/romkinet666-source/filvless-apk/releases/download/")
}?.browserDownloadUrl

internal fun compareReleaseVersions(left: String, right: String): Int {
    fun parts(value: String): Pair<List<Int>, String?> {
        val text = value.removePrefix("v").substringBefore('+')
        val pieces = text.split('-', limit = 2)
        return pieces[0].split('.').map { it.toIntOrNull() ?: 0 } to pieces.getOrNull(1)
    }
    val (a, suffixA) = parts(left)
    val (b, suffixB) = parts(right)
    for (i in 0 until maxOf(a.size, b.size)) {
        val comparison = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
        if (comparison != 0) return comparison
    }
    if (suffixA == suffixB) return 0
    if (suffixA == null) return 1
    if (suffixB == null) return -1
    val aa = suffixA.split('.')
    val bb = suffixB.split('.')
    for (i in 0 until maxOf(aa.size, bb.size)) {
        val x = aa.getOrNull(i) ?: return -1
        val y = bb.getOrNull(i) ?: return 1
        val nx = x.toIntOrNull(); val ny = y.toIntOrNull()
        val comparison = when {
            nx != null && ny != null -> nx.compareTo(ny)
            nx != null -> -1
            ny != null -> 1
            else -> x.compareTo(y)
        }
        if (comparison != 0) return comparison
    }
    return 0
}
