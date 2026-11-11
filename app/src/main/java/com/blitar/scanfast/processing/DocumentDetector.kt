package com.blitar.scanfast.processing

import android.util.Log
import com.blitar.scanfast.model.MARGIN_PERCENT
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Deteksi kuadrilateral dokumen: grayscale → blur → Canny → kontur 4 sudut terbesar yang masuk akal. */
object DocumentDetector {
    private const val WORK_LONG_SIDE = 800.0
    private const val MAX_AREA_RATIO = 0.95
    private const val MIN_AREA_OF_GUIDE = 0.25
    private val CANNY_ATTEMPTS = listOf(50.0 to 150.0, 25.0 to 75.0)

    /** @return 4 titik (piksel foto sumber) urut TL, TR, BR, BL; null kalau tidak ada yang layak. */
    fun detect(rgba: Mat): Array<Point>? {
        val scale = min(1.0, WORK_LONG_SIDE / max(rgba.cols(), rgba.rows()))
        val small = Mat()
        val gray = Mat()
        try {
            Imgproc.resize(rgba, small, Size(rgba.cols() * scale, rgba.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

            val imgArea = small.cols().toDouble() * small.rows()
            // Referensi: luas guide-frame; terima >= 0.25x luas guide s/d <= 0.95 luas gambar.
            val guideArea = imgArea * (1 - 2 * MARGIN_PERCENT) * (1 - 2 * MARGIN_PERCENT)
            val minArea = guideArea * MIN_AREA_OF_GUIDE
            val maxArea = imgArea * MAX_AREA_RATIO

            for ((low, high) in CANNY_ATTEMPTS) {
                val quad = findQuad(gray, low, high, minArea, maxArea)
                if (quad != null) return quad.map { Point(it.x / scale, it.y / scale) }.toTypedArray()
            }
            return null
        } finally {
            small.release()
            gray.release()
        }
    }

    private fun findQuad(gray: Mat, low: Double, high: Double, minArea: Double, maxArea: Double): Array<Point>? {
        val edges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Imgproc.Canny(gray, edges, low, high)
            Imgproc.dilate(edges, edges, kernel) // tutup celah kecil di garis tepi
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            var best: Array<Point>? = null
            var bestArea = 0.0
            var maxQuadSeen = 0.0
            for (c in contours) {
                val c2f = MatOfPoint2f(*c.toArray())
                val approx = MatOfPoint2f()
                try {
                    val peri = Imgproc.arcLength(c2f, true)
                    Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                    if (approx.total() != 4L) continue
                    val pts = approx.toArray()
                    val area = Imgproc.contourArea(approx)
                    if (area > maxQuadSeen) maxQuadSeen = area
                    if (area < minArea || area > maxArea || area <= bestArea) continue
                    val ordered = order(pts)
                    if (!isConvexQuad(ordered)) continue
                    best = ordered
                    bestArea = area
                } finally {
                    c2f.release()
                    approx.release()
                }
            }
            Log.d(
                "DocumentDetector",
                "canny=$low/$high contours=${contours.size} maxQuad=${"%.2f".format(maxQuadSeen / (gray.cols().toDouble() * gray.rows()))} " +
                    "best=${"%.2f".format(bestArea / (gray.cols().toDouble() * gray.rows()))}"
            )
            return best
        } finally {
            edges.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    /** Urut TL, TR, BR, BL lewat jumlah dan selisih koordinat. */
    fun order(p: Array<Point>): Array<Point> {
        val tl = p.minBy { it.x + it.y }
        val br = p.maxBy { it.x + it.y }
        val tr = p.minBy { it.y - it.x }
        val bl = p.maxBy { it.y - it.x }
        return arrayOf(tl, tr, br, bl)
    }

    /** Titik berbeda dan tiap sudut dalam 60°..120° (mendekati persegi panjang dalam perspektif). */
    private fun isConvexQuad(q: Array<Point>): Boolean {
        if (q.toSet().size != 4) return false
        for (i in 0 until 4) {
            val a = q[(i + 3) % 4]
            val b = q[i]
            val c = q[(i + 1) % 4]
            val v1x = a.x - b.x; val v1y = a.y - b.y
            val v2x = c.x - b.x; val v2y = c.y - b.y
            val l1 = sqrt(v1x * v1x + v1y * v1y)
            val l2 = sqrt(v2x * v2x + v2y * v2y)
            if (l1 < 1 || l2 < 1) return false
            val cos = ((v1x * v2x + v1y * v2y) / (l1 * l2)).coerceIn(-1.0, 1.0)
            val deg = Math.toDegrees(acos(cos))
            if (deg < 60 || deg > 120) return false
        }
        return true
    }
}
