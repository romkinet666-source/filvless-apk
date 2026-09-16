package com.v2ray.ang.root

import java.util.concurrent.TimeUnit
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeoutException

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

/** Drain stdout concurrently so neither a full pipe nor missing EOF bypasses the deadline. */
internal fun Process.readOutputWithTimeout(timeout: Long, unit: TimeUnit): String? {
    val deadline = System.nanoTime() + unit.toNanos(timeout)
    val output = FutureTask {
        inputStream.bufferedReader().use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                val remaining = 65_536 - result.length
                if (remaining > 0) result.append(buffer, 0, minOf(count, remaining))
            }
            result.toString()
        }
    }
    Thread(output, "Filvless-process-output").apply { isDaemon = true; start() }
    var completed = false
    try {
        if (!waitForCompat(timeout, unit)) return null
        return output.get((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS).also { completed = true }
    } catch (_: TimeoutException) {
        return null
    } finally {
        if (!completed) {
            destroy()
            output.cancel(true)
        }
    }
}
