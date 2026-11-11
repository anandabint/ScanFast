package com.blitar.scanfast.camera

import android.view.KeyEvent

/**
 * Menjadikan tombol volume sebagai shutter. Dipanggil dari Activity.onKeyDown/onKeyUp.
 * Event hanya di-consume (volume media tidak berubah) saat ada listener terdaftar,
 * yaitu hanya ketika CaptureScreen aktif.
 */
class VolumeShutterHandler {
    @Volatile
    private var listener: (() -> Unit)? = null

    fun register(onShutter: () -> Unit) {
        listener = onShutter
    }

    fun unregister(onShutter: () -> Unit) {
        if (listener === onShutter) listener = null
    }

    /** @return true kalau event di-consume. */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isVolumeKey(keyCode)) return false
        val l = listener ?: return false
        if (event.repeatCount == 0) l()
        return true
    }

    fun onKeyUp(keyCode: Int): Boolean = isVolumeKey(keyCode) && listener != null

    private fun isVolumeKey(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
}
