package com.blitar.scanfast.processing

import android.graphics.Bitmap
import com.blitar.scanfast.model.MARGIN_PERCENT
import com.blitar.scanfast.model.guideRect
import com.blitar.scanfast.model.NormPoint
import com.blitar.scanfast.model.QuadPoints
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Crop dokumen: [autoQuad] menentukan 4 titik (deteksi kontur, fallback guide-frame berrasio tetap),
 * [warp] meluruskan area itu dengan perspective transform (aspect ratio mengikuti quad).
 * Dipisah agar quad bisa diganti manual di editor crop lalu diproses ulang.
 */
object ImageCropper {
    private const val MIN_SIDE_PX = 32

    /**
     * Quad fallback = guide-frame berrasio tetap yang sama dengan overlay kamera ([guideRect]).
     * [aspect] = lebar/tinggi foto sumber. Menandai autoDetected=false.
     */
    fun marginQuad(aspect: Float, p: Float = MARGIN_PERCENT): QuadPoints {
        val g = guideRect(aspect, p)
        return QuadPoints(
            NormPoint(g.left, g.top), NormPoint(g.right, g.top),
            NormPoint(g.right, g.bottom), NormPoint(g.left, g.bottom),
            autoDetected = false,
        )
    }

    /** Quad otomatis untuk [src]: hasil deteksi kalau ada, kalau tidak margin-persen. Tidak melempar. */
    fun autoQuad(src: Bitmap): QuadPoints {
        if (OpenCvInit.ready) {
            val detected = guardedOpenCv(TAG, "Deteksi dokumen", fallback = { null }) { detect(src) }
            if (detected != null) return detected
        }
        return marginQuad(src.width.toFloat() / src.height)
    }

    /** Warp [src] ke persegi panjang dari [quad]. Tanpa OpenCV / error: crop bounding box quad. */
    fun warp(src: Bitmap, quad: QuadPoints): Bitmap {
        if (OpenCvInit.ready) {
            val warped = guardedOpenCv(TAG, "Perspective warp", fallback = { null }) { warpWithOpenCv(src, quad) }
            if (warped != null) return warped
        }
        return cropBounds(src, quad)
    }

    private fun detect(src: Bitmap): QuadPoints? {
        val mat = Mat()
        try {
            Utils.bitmapToMat(src, mat) // RGBA
            val q = DocumentDetector.detect(mat) ?: return null
            val sw = src.width.toFloat()
            val sh = src.height.toFloat()
            fun n(p: Point) = NormPoint((p.x / sw).toFloat(), (p.y / sh).toFloat())
            return QuadPoints(n(q[0]), n(q[1]), n(q[2]), n(q[3]), autoDetected = true)
        } finally {
            mat.release()
        }
    }

    private fun warpWithOpenCv(src: Bitmap, quad: QuadPoints): Bitmap {
        val srcMat = Mat()
        val dstMat = Mat()
        var m: Mat? = null
        var srcPts: MatOfPoint2f? = null
        var dstPts: MatOfPoint2f? = null
        try {
            Utils.bitmapToMat(src, srcMat)
            val sw = src.width.toDouble()
            val sh = src.height.toDouble()
            fun px(p: NormPoint) = Point(p.x * sw, p.y * sh)
            val tl = px(quad.topLeft)
            val tr = px(quad.topRight)
            val br = px(quad.bottomRight)
            val bl = px(quad.bottomLeft)
            val w = max(hypot(tr.x - tl.x, tr.y - tl.y), hypot(br.x - bl.x, br.y - bl.y)).toInt()
            val h = max(hypot(bl.x - tl.x, bl.y - tl.y), hypot(br.x - tr.x, br.y - tr.y)).toInt()
            require(w >= MIN_SIDE_PX && h >= MIN_SIDE_PX) { "Quad terlalu kecil: ${w}x$h" }

            srcPts = MatOfPoint2f(tl, tr, br, bl)
            dstPts = MatOfPoint2f(
                Point(0.0, 0.0), Point(w - 1.0, 0.0), Point(w - 1.0, h - 1.0), Point(0.0, h - 1.0)
            )
            m = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            Imgproc.warpPerspective(
                srcMat, dstMat, m, Size(w.toDouble(), h.toDouble()),
                Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE
            )
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dstMat, out)
            return out
        } finally {
            srcMat.release()
            dstMat.release()
            m?.release()
            srcPts?.release()
            dstPts?.release()
        }
    }

    private fun cropBounds(src: Bitmap, quad: QuadPoints): Bitmap {
        val xs = listOf(quad.topLeft.x, quad.topRight.x, quad.bottomRight.x, quad.bottomLeft.x)
        val ys = listOf(quad.topLeft.y, quad.topRight.y, quad.bottomRight.y, quad.bottomLeft.y)
        val l = (xs.min() * src.width).toInt().coerceIn(0, src.width - 1)
        val t = (ys.min() * src.height).toInt().coerceIn(0, src.height - 1)
        val r = (xs.max() * src.width).toInt().coerceIn(l + 1, src.width)
        val b = (ys.max() * src.height).toInt().coerceIn(t + 1, src.height)
        return Bitmap.createBitmap(src, l, t, min(r - l, src.width - l), min(b - t, src.height - t))
    }

    private const val TAG = "ImageCropper"
}
