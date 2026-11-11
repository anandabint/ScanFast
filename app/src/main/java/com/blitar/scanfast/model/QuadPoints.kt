package com.blitar.scanfast.model

/** Satu titik dengan koordinat NORMALISASI (0..1) terhadap foto sumber (lebar/tinggi). */
data class NormPoint(val x: Float, val y: Float)

/**
 * Empat sudut area dokumen pada foto sumber, urutan: kiri-atas, kanan-atas, kanan-bawah, kiri-bawah.
 * Disimpan normalisasi agar bisa dipakai ulang di editor crop manual pada resolusi berapa pun.
 * [autoDetected] false = fallback crop margin-persen; [manuallyEdited] true = diatur user di editor crop.
 */
data class QuadPoints(
    val topLeft: NormPoint,
    val topRight: NormPoint,
    val bottomRight: NormPoint,
    val bottomLeft: NormPoint,
    val autoDetected: Boolean,
    val manuallyEdited: Boolean = false,
)
