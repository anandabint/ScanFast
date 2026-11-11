package com.blitar.scanfast.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.IOException

/** Foto tidak bisa diproses (file hilang/rusak/memori habis). Jatuh ke kategori "gagal" di batch. */
class PhotoProcessingException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Memuat foto original dari cache dengan penanganan kegagalan yang spesifik dan ber-log. */
object PhotoLoader {
    private const val TAG = "PhotoLoader"

    /** Decoder bisa diganti (untuk tes). Default: BitmapFactory.decodeFile. */
    fun load(filePath: String, decoder: (String) -> Bitmap? = { BitmapFactory.decodeFile(it) }): Bitmap {
        val file = File(filePath)
        if (!file.isFile || file.length() == 0L) {
            Log.e(TAG, "File hilang atau kosong: $filePath")
            throw PhotoProcessingException("File tidak ada atau kosong: $filePath")
        }
        val bitmap = try {
            decoder(filePath)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Memori habis saat decode: $filePath", e)
            throw PhotoProcessingException("Memori habis saat membuka foto", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Argumen decode tidak valid: $filePath", e)
            throw PhotoProcessingException("File rusak, tidak bisa dibuka", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Tidak punya akses baca: $filePath", e)
            throw PhotoProcessingException("File tidak bisa dibaca", e)
        }
        if (bitmap == null) {
            Log.e(TAG, "Decode mengembalikan null (file rusak?): $filePath")
            throw PhotoProcessingException("File rusak, tidak bisa dibuka")
        }
        return bitmap
    }
}
