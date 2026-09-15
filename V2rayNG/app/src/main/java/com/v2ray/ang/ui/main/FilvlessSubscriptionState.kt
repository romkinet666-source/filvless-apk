package com.v2ray.ang.ui.main

/** Some subscription providers return a notice encoded as a proxy profile. */
internal fun isUnsupportedDeviceNotice(remarks: String): Boolean =
    remarks.contains("Данное устройство не поддерживается", ignoreCase = true) ||
        remarks.contains("This device is not supported", ignoreCase = true)

internal fun connectionElapsedSeconds(startedAt: Long, now: Long): Long =
    if (startedAt <= 0L || now < startedAt) 0L else (now - startedAt) / 1_000L
