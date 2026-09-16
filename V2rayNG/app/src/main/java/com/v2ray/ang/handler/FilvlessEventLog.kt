package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal data class FilvlessEvent(val title: String, val detail: String, val timestamp: Long)

/** Keeps a small local-only timeline. It deliberately contains no links, credentials or IP addresses. */
internal object FilvlessEventLog {
    private const val KEY = "filvless_event_log"
    private const val MAX_EVENTS = 30
    private val type = object : TypeToken<List<FilvlessEvent>>() {}.type

    fun add(title: String, detail: String = "") {
        val events = read().toMutableList()
        events.add(0, FilvlessEvent(title.take(100), detail.take(180), System.currentTimeMillis()))
        MmkvManager.encodeSettings(KEY, Gson().toJson(events.take(MAX_EVENTS)))
    }

    fun read(): List<FilvlessEvent> = runCatching {
        Gson().fromJson<List<FilvlessEvent>>(MmkvManager.decodeSettingsString(KEY).orEmpty(), type).orEmpty()
    }.getOrDefault(emptyList())

    fun clear() = MmkvManager.encodeSettings(KEY, "[]")
}
