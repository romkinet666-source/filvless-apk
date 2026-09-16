package com.v2ray.ang.handler

/** Release text is untrusted display data, never rendered as HTML or executed. */
internal fun conciseReleaseNotes(notes: String?): String = notes.orEmpty().lineSequence()
    .takeWhile { !it.trim().startsWith("## Установка") && !it.trim().startsWith("## Проверено") }
    .map { it.trim().removePrefix("## ").removePrefix("# ") }
    .filter { it.isNotBlank() }.take(9).joinToString("\n").take(1800)

internal fun updateReminderDue(until: Long, now: Long) = until <= now || until - now > 86_400_000L

object UpdateSnooze {
    fun isActive(version: String): Boolean = !updateReminderDue(
        MmkvManager.decodeSettingsString("filvless_update_later_$version")?.toLongOrNull() ?: 0,
        System.currentTimeMillis())
    fun postpone(version: String) {
        if (MmkvManager.decodeSettingsString("filvless_notified_version") == version) MmkvManager.encodeSettings("filvless_notified_version", "")
        MmkvManager.encodeSettings("filvless_update_later_$version", (System.currentTimeMillis() + 86_400_000L).toString())
    }
}
