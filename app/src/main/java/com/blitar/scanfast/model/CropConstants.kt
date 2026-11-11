package com.blitar.scanfast.model

/**
 * Margin crop relatif (fraksi dari lebar/tinggi), SATU sumber untuk overlay preview
 * (GuideFrame) dan crop foto asli (ImageCropper). Foto disimpan dengan ViewPort yang sama
 * dengan preview (lihat CameraXController), jadi persentase ini setara di keduanya.
 */
const val MARGIN_PERCENT = 0.03f
