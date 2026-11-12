package com.blitar.scanfast

import com.blitar.scanfast.model.GUIDE_ASPECT_RATIO
import com.blitar.scanfast.model.MARGIN_PERCENT
import com.blitar.scanfast.model.guideRect
import com.blitar.scanfast.processing.ImageCropper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guide-frame berrasio tetap: margin horizontal tetap, tinggi mengikuti rasio, dipusatkan vertikal. */
class GuideRectTest {
    private val phone = 1080f / 2400f // layar 9:20

    @Test
    fun horizontalMargin_isZero_fullBleed() {
        val g = guideRect(phone)
        assertEquals(0f, MARGIN_PERCENT, 0f)
        assertEquals(0f, g.left, 1e-6f)
        assertEquals(1f, g.right, 1e-6f)
    }

    @Test
    fun portraitScreen_hasFixedPixelRatio_andFixedHorizontalMargin() {
        val g = guideRect(phone)
        assertEquals(MARGIN_PERCENT, g.left, 1e-5f)
        assertEquals(1f - MARGIN_PERCENT, g.right, 1e-5f)
        val wPx = (g.right - g.left) * 1080f
        val hPx = (g.bottom - g.top) * 2400f
        assertEquals(GUIDE_ASPECT_RATIO, hPx / wPx, 1e-3f)
    }

    @Test
    fun portraitScreen_isCenteredVertically_andShorterThanOldFrame() {
        val g = guideRect(phone)
        assertEquals(g.top, 1f - g.bottom, 1e-5f)
        assertTrue("tinggi harus < 1 - 2*margin (kotak lama)", g.bottom - g.top < 1f - 2 * MARGIN_PERCENT)
        assertEquals(0.6885f, g.bottom - g.top, 0.002f) // 1.0 * 1.53 * 0.45
    }

    @Test
    fun wideViewport_clampsHeight_keepsRatio_andCentered() {
        val g = guideRect(1.5f) // landscape: tinggi 0.94*1.53*1.5 > 1 -> dibatasi
        assertEquals(MARGIN_PERCENT, g.top, 1e-5f)
        assertEquals(1f - MARGIN_PERCENT, g.bottom, 1e-5f)
        assertEquals(g.left, 1f - g.right, 1e-5f)
        val wPx = (g.right - g.left) * 1500f
        val hPx = (g.bottom - g.top) * 1000f
        assertEquals(GUIDE_ASPECT_RATIO, hPx / wPx, 1e-3f)
    }

    @Test
    fun fallbackQuad_matchesOverlay_andIsNotMarkedDetected() {
        val aspect = 1468f / 3264f // foto sumber hasil viewport
        val g = guideRect(aspect)
        val q = ImageCropper.marginQuad(aspect)
        assertEquals(g.left, q.topLeft.x, 1e-6f)
        assertEquals(g.top, q.topLeft.y, 1e-6f)
        assertEquals(g.right, q.bottomRight.x, 1e-6f)
        assertEquals(g.bottom, q.bottomRight.y, 1e-6f)
        assertEquals(false, q.autoDetected)
        // Rasio piksel kuadrat fallback pada foto 1468x3264 = rasio guide.
        val hPx = (q.bottomLeft.y - q.topLeft.y) * 3264f
        val wPx = (q.topRight.x - q.topLeft.x) * 1468f
        assertEquals(GUIDE_ASPECT_RATIO, hPx / wPx, 1e-3f)
    }
}
