package com.v2ray.ang.root

import java.util.concurrent.TimeUnit

/** Process.waitFor(timeout, unit) is unavailable on Android 7 (API 24–25). */
internal fun Process.waitForCompat(timeout: Long, unit: TimeUnit): Boolean {
    val started = System.nanoTime()
    val limit = unit.toNanos(timeout)
    while (true) {
        try {
            exitValue()
            return true
        } catch (_: IllegalThreadStateException) {
            if (System.nanoTime() - started >= limit) return false
            Thread.sleep(20)
        }
    }
}
