package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.util.awaitResponse
import com.v2ray.ang.util.SubscriptionDevice
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class FilvlessDevice(val id: String, val model: String, val platform: String,
    val osVersion: String, val updatedAt: String, val isCurrent: Boolean)
internal data class DeviceSnapshot(val devices: List<FilvlessDevice>, val limit: Int?)
internal class DeviceApiException(val code: String) : Exception(code)

/** Only this provider's links may be sent to the Filvless device gateway. */
internal fun deviceCredential(url: String): String? = runCatching {
    val uri = URI(url.trim())
    if (uri.scheme != "https" || uri.host?.lowercase() != "sub.prostobotyg.ru" ||
        uri.port !in listOf(-1, 443) || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
    uri.rawPath.removePrefix("/").takeIf { it.matches(Regex("[A-Za-z0-9_-]{16,64}")) }
}.getOrNull()

internal fun parseDeviceSnapshot(body: String): DeviceSnapshot {
    val json = JsonParser.parseString(body).asJsonObject
    val rows = json.getAsJsonArray("devices") ?: error("Missing devices")
    require(rows.size() <= 1000)
    fun com.google.gson.JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    val devices = rows.map {
        val row = it.asJsonObject
        val id = row.text("id")
        require(id.matches(Regex("[0-9a-f]{64}")))
        FilvlessDevice(id, row.text("model"), row.text("platform"), row.text("osVersion"),
            row.text("updatedAt"), row.get("isCurrent")?.asBoolean == true)
    }
    require(devices.map { it.id }.distinct().size == devices.size)
    return DeviceSnapshot(devices, json.get("limit")?.takeUnless { it.isJsonNull }?.asInt?.takeIf { it > 0 })
}

internal object FilvlessDevices {
    private const val BASE = "https://apkapi.flynode.ru"
    // No redirects and no HTTP logger: the Authorization header is a subscription secret.
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()

    suspend fun load(credential: String, deleteId: String? = null): DeviceSnapshot = withContext(Dispatchers.IO) {
        require(credential.matches(Regex("[A-Za-z0-9_-]{16,64}")))
        val request = Request.Builder().url(BASE + if (deleteId == null) "/v1/devices" else "/v1/devices/delete")
            .header("Authorization", "Bearer $credential")
            .header("X-Device-Id", SubscriptionDevice.headers().getValue("x-hwid"))
        if (deleteId != null) {
            require(deleteId.matches(Regex("[0-9a-f]{64}")))
            request.post("{\"deviceId\":\"$deleteId\"}".toRequestBody("application/json".toMediaType()))
        }
        client.newCall(request.build()).awaitResponse { response ->
            val body = response.body ?: throw DeviceApiException("network")
            val source = body.source()
            source.request(1_048_577)
            if (source.buffer.size > 1_048_576) throw DeviceApiException("network")
            val text = source.readUtf8()
            if (!response.isSuccessful) {
                val code = runCatching { JsonParser.parseString(text).asJsonObject.get("error").asString }.getOrNull()
                throw DeviceApiException(code ?: "network")
            }
            parseDeviceSnapshot(text)
        }
    }
}
