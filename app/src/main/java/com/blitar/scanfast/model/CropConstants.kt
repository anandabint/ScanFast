package com.blitar.scanfast.model

/**
 * Margin kiri-kanan relatif (fraksi dari lebar), SATU sumber untuk overlay preview (GuideFrame)
 * dan fallback crop foto asli (ImageCropper). 0 = kotak menempel ke tepi kiri-kanan (full-bleed).
 * Foto disimpan dengan ViewPort yang sama dengan preview (lihat CameraXController), jadi koordinat
 * normalisasi setara di keduanya. Margin atas-bawah TIDAK diatur di sini: dihitung dari
 * [GUIDE_ASPECT_RATIO] dan dipusatkan vertikal.
 */
const val MARGIN_PERCENT = 0f

/**
 * Rasio guide-frame: TINGGI / LEBAR. 1.53 mendekati F4/folio (proporsi dokumen AKL). Ubah di sini
 * kalau kotak terasa kurang pas; overlay dan fallback crop ikut berubah sekaligus.
 */
const val GUIDE_ASPECT_RATIO = 1.53f

/** Persegi guide dalam koordinat normalisasi (0..1) terhadap viewport/foto. */
data class GuideRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Guide-frame berrasio tetap: lebar ditentukan margin kiri-kanan ([marginPercent]), tinggi mengikuti
 * [ratio] (tinggi/lebar), dipusatkan secara vertikal. [viewportAspect] = lebar/tinggi viewport (layar
 * preview atau foto sumber, keduanya berrasio sama). Kalau kotak lebih tinggi dari area yang tersedia
 * (viewport sangat lebar), tinggi dibatasi ke area dengan margin yang sama dan lebar menyesuaikan.
 */
fun guideRect(
    viewportAspect: Float,
    marginPercent: Float = MARGIN_PERCENT,
    ratio: Float = GUIDE_ASPECT_RATIO,
): GuideRect {
    var wFrac = 1f - 2f * marginPercent
    var hFrac = wFrac * ratio * viewportAspect
    val maxH = 1f - 2f * marginPercent
    if (hFrac > maxH) {
        hFrac = maxH
        wFrac = hFrac / (ratio * viewportAspect)
    }
    val left = (1f - wFrac) / 2f
    val top = (1f - hFrac) / 2f
    return GuideRect(left, top, left + wFrac, top + hFrac)
}
