package com.v2ray.ang.ui.main

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** One short pulse for either direction of the power button. */
@Suppress("DEPRECATION")
internal fun connectionHaptic(context: Context, enabled: Boolean) {
    if (!enabled) return
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(55L, 220))
    } else {
        vibrator.vibrate(55L)
    }
}
