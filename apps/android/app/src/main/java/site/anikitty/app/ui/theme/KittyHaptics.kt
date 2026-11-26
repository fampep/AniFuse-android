// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.theme

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

/** One place for taps, snaps, success, and errors. Quiet when the system haptics switch is off. */
object KittyHaptics {
    enum class Kind { Tick, Confirm, Success, Error }

    private var lastAt = LongArray(Kind.entries.size)

    fun perform(view: View, kind: Kind) {
        val now = SystemClock.uptimeMillis()
        val gap = when (kind) {
            Kind.Tick -> 45L
            Kind.Confirm -> 70L
            Kind.Success -> 180L
            Kind.Error -> 220L
        }
        val slot = kind.ordinal
        if (now - lastAt[slot] < gap) return
        lastAt[slot] = now
        if (!enabled(view.context)) return
        try {
            val vibrator = view.context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                val predefined = predefinedEffect(kind)
                if (predefined != null && Build.VERSION.SDK_INT >= 30 && effectSupported(vibrator, predefined)) {
                    vibrator.vibrate(VibrationEffect.createPredefined(predefined))
                    return
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    val fallback = when (kind) {
                        Kind.Tick -> VibrationEffect.createOneShot(12, 40)
                        Kind.Confirm -> VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
                        Kind.Success -> VibrationEffect.createWaveform(longArrayOf(0, 16, 40, 20), -1)
                        Kind.Error -> VibrationEffect.createWaveform(longArrayOf(0, 16, 36, 16), -1)
                    }
                    vibrator.vibrate(fallback)
                    return
                }
            }
        } catch (_: RuntimeException) {
            // A missing predefined effect must not swallow the click that asked for it.
        }
        val constant = when (kind) {
            Kind.Tick -> HapticFeedbackConstants.CLOCK_TICK
            Kind.Confirm -> HapticFeedbackConstants.KEYBOARD_TAP
            Kind.Success -> HapticFeedbackConstants.CONFIRM
            Kind.Error -> HapticFeedbackConstants.REJECT
        }
        val flags = if (Build.VERSION.SDK_INT >= 33) {
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        } else {
            0
        }
        view.performHapticFeedback(constant, flags)
    }

    private fun predefinedEffect(kind: Kind): Int? {
        if (Build.VERSION.SDK_INT < 29) return null
        return when (kind) {
            Kind.Tick -> VibrationEffect.EFFECT_TICK
            Kind.Confirm -> VibrationEffect.EFFECT_CLICK
            Kind.Success -> if (Build.VERSION.SDK_INT >= 30) VibrationEffect.EFFECT_DOUBLE_CLICK else null
            Kind.Error -> null
        }
    }

    private fun effectSupported(vibrator: Vibrator, effect: Int): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val support = vibrator.areEffectsSupported(effect)
        if (support.isEmpty()) return false
        return support[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES ||
            support[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_UNKNOWN
    }

    private fun enabled(context: Context): Boolean =
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) == 1
}
