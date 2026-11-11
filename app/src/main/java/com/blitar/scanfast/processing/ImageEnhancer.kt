package com.blitar.scanfast.processing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Efek "scan" yang mempertahankan warna asli:
 *  1. Lab, hanya channel L diproses (a,b tidak disentuh).
 *  2. Illumination normalization: estimasi background (median blur besar) lalu L / background * TARGET,
 *     sehingga kertas ≈ [WHITE_TARGET] dan bayangan/gradasi cahaya hilang, teks tetap gelap.
 *  3. CLAHE ringan (clip rendah) opsional, lalu unsharp mask ringan.
 * Kalau gagal, mengembalikan [input] apa adanya.
 */
object ImageEnhancer {
    private const val WHITE_TARGET = 246.0 // L 8-bit 246 ≈ RGB 240 (L*=96)
    private const val BG_DOWNSCALE = 4.0 // estimasi background di 1/4 resolusi (cepat)
    private const val BG_MEDIAN = 31 // ≈ 124 px di resolusi asli; teks < 50% area jendela → median = kertas
    private const val CLAHE_CLIP = 0.0 // <= 0 menonaktifkan; CLAHE (bahkan 1.2) menurunkan putih kertas ±15
    private const val CLAHE_TILE = 8.0
    private const val SHARPEN_SIGMA = 1.5
    private const val SHARPEN_AMOUNT = 0.6 // out = (1+a)*img - a*blur

    /** Bitmap baru; [input] tidak di-recycle di sini. Kalau error, return [input]. */
    fun enhance(input: Bitmap): Bitmap {
        if (!OpenCvInit.ready) return input
        return guardedOpenCv("ImageEnhancer", "Enhance", fallback = { input }) { enhanceWithOpenCv(input) }
    }

    private fun enhanceWithOpenCv(input: Bitmap): Bitmap {
        val mats = ArrayList<Mat>()
        fun mat() = Mat().also { mats.add(it) }
        return try {
            val rgba = mat()
            Utils.bitmapToMat(input, rgba)
            val rgb = mat()
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            val lab = mat()
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)

            val channels = ArrayList<Mat>()
            Core.split(lab, channels)
            mats.addAll(channels)

            var l = normalizeIllumination(channels[0], mats)
            if (CLAHE_CLIP > 0) {
                val clahe = Imgproc.createCLAHE(CLAHE_CLIP, Size(CLAHE_TILE, CLAHE_TILE))
                val lEq = mat()
                clahe.apply(l, lEq)
                l = lEq
            }
            channels[0] = l

            val labOut = mat()
            Core.merge(channels, labOut)
            val rgbOut = mat()
            Imgproc.cvtColor(labOut, rgbOut, Imgproc.COLOR_Lab2RGB)

            val blur = mat()
            Imgproc.GaussianBlur(rgbOut, blur, Size(0.0, 0.0), SHARPEN_SIGMA)
            val sharp = mat()
            Core.addWeighted(rgbOut, 1 + SHARPEN_AMOUNT, blur, -SHARPEN_AMOUNT, 0.0, sharp)

            val outRgba = mat()
            Imgproc.cvtColor(sharp, outRgba, Imgproc.COLOR_RGB2RGBA)
            val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outRgba, out)
            out
        } finally {
            mats.forEach { it.release() }
        }
    }

    /** L / background * WHITE_TARGET (8-bit, saturate). Mat sementara dicatat di [mats] untuk dilepas. */
    private fun normalizeIllumination(l: Mat, mats: MutableList<Mat>): Mat {
        fun mat() = Mat().also { mats.add(it) }
        val small = mat()
        Imgproc.resize(
            l, small, Size(l.cols() / BG_DOWNSCALE, l.rows() / BG_DOWNSCALE), 0.0, 0.0, Imgproc.INTER_AREA
        )
        val med = mat()
        // Median (bukan closing/max): closing mengikuti noise terang tekstur kertas sehingga kertas jadi redup.
        Imgproc.medianBlur(small, med, BG_MEDIAN)
        Imgproc.GaussianBlur(med, med, Size(BG_MEDIAN.toDouble(), BG_MEDIAN.toDouble()), 0.0)
        val bg = mat()
        Imgproc.resize(med, bg, Size(l.cols().toDouble(), l.rows().toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        val out = mat()
        Core.divide(l, bg, out, WHITE_TARGET, CvType.CV_8U)
        return out
    }
}
