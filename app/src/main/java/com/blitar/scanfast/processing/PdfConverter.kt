package com.blitar.scanfast.processing

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Penulis PDF minimal: satu halaman, satu gambar. Data JPEG di-embed apa adanya
 * (image XObject, /DCTDecode) sehingga ukuran file ≈ ukuran JPEG, tanpa bitmap mentah.
 */
object PdfConverter {
    /** DPI untuk menghitung ukuran fisik halaman dari dimensi pixel. */
    private const val PAGE_DPI = 150f
    private const val JPEG_QUALITY = 92

    /** Tulis [bitmap] (di-encode JPEG kualitas 92 satu kali) sebagai PDF ke [target]. */
    fun write(bitmap: Bitmap, target: File) {
        FileOutputStream(target).use { write(bitmap, it) }
    }

    /** Tulis [bitmap] sebagai PDF ke [out] (stream tidak ditutup). */
    fun write(bitmap: Bitmap, out: OutputStream) {
        val jpeg = ByteArrayOutputStream(bitmap.byteCount / 8).also {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) { "Encode JPEG gagal" }
        }.toByteArray()
        writeJpeg(jpeg, bitmap.width, bitmap.height, out)
    }

    /**
     * Tulis PDF satu halaman yang meng-embed [jpeg] apa adanya.
     * [width]/[height] = dimensi pixel JPEG. Stream [out] tidak ditutup.
     */
    fun writeJpeg(jpeg: ByteArray, width: Int, height: Int, out: OutputStream) {
        require(width > 0 && height > 0) { "Dimensi tidak valid" }
        require(jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) {
            "Bukan data JPEG"
        }
        val pageW = fmt(width * 72f / PAGE_DPI)
        val pageH = fmt(height * 72f / PAGE_DPI)
        val content = "q $pageW 0 0 $pageH 0 0 cm /Im0 Do Q\n".toByteArray(Charsets.ISO_8859_1)

        val w = CountingOutputStream(out)
        val offsets = IntArray(6)
        fun ascii(s: String) = w.write(s.toByteArray(Charsets.ISO_8859_1))

        ascii("%PDF-1.4\n")
        w.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

        offsets[1] = w.count
        ascii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        offsets[2] = w.count
        ascii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        offsets[3] = w.count
        ascii(
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageW $pageH] " +
                "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"
        )
        offsets[4] = w.count
        ascii(
            "4 0 obj\n<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                "/Length ${jpeg.size} >>\nstream\n"
        )
        w.write(jpeg)
        ascii("\nendstream\nendobj\n")
        offsets[5] = w.count
        ascii("5 0 obj\n<< /Length ${content.size} >>\nstream\n")
        w.write(content)
        ascii("endstream\nendobj\n")

        val xrefOffset = w.count
        ascii("xref\n0 6\n")
        ascii("0000000000 65535 f \n")
        for (i in 1..5) ascii(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        ascii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        w.flush()
    }

    private fun fmt(v: Float) = String.format(Locale.US, "%.2f", v)

    /** Menghitung byte yang sudah ditulis (untuk offset xref); tidak menutup stream bawah. */
    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun close() { /* jangan tutup stream milik pemanggil */ }
    }
}
