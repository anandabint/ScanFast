package com.blitar.scanfast.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Haptic ringan yang seragam di titik interaksi penting (minSdk 31, semua konstanta tersedia). */
class Haptics(private val view: View) {
    /** Ketukan tombol/navigasi biasa. */
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    /** Aksi selesai/berhasil. */
    fun confirm() { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }

    /** Input ditolak. */
    fun reject() { view.performHapticFeedback(HapticFeedbackConstants.REJECT) }

    fun gestureStart() { view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START) }

    fun gestureEnd() { view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
