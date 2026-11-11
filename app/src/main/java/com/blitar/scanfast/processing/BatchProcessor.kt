package com.blitar.scanfast.processing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.blitar.scanfast.model.BatchResult
import com.blitar.scanfast.model.CaptureSession
import com.blitar.scanfast.model.CapturedPhoto
import com.blitar.scanfast.model.QuadPoints
import com.blitar.scanfast.storage.MediaStoreOutput
import com.blitar.scanfast.storage.PdfOutput
import com.blitar.scanfast.storage.TempPhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * Crop/warp → enhance → PDF → penyimpanan. [process] menjalankan semua foto berurutan (satu gagal
 * tidak menghentikan batch); [processSinglePhoto] dipakai juga oleh editor crop untuk proses ulang.
 *
 * @param output tujuan PDF (MediaStore di app, fake di tes).
 * @param thumbDir folder thumbnail ReviewScreen.
 * @param loader pemuat foto original (bisa diganti di tes).
 */
class BatchProcessor(
    private val output: PdfOutput,
    private val thumbDir: () -> File,
    private val loader: (String) -> Bitmap = { PhotoLoader.load(it) },
) {
    constructor(context: Context) : this(
        MediaStoreOutput(context.applicationContext),
        { TempPhotoStore.dir(context.applicationContext) },
    )

    /** [onProgress] menerima jumlah foto yang sudah diproses (sukses atau gagal). */
    suspend fun process(session: CaptureSession, onProgress: (Int) -> Unit): BatchResult =
        withContext(Dispatchers.IO) {
            val photos = session.photos.toList()
            val failed = mutableListOf<Int>()
            var success = 0
            var firstUri: Uri? = null
            photos.forEachIndexed { index, photo ->
                ensureActive()
                try {
                    val uri = processSinglePhoto(photo, null, session.start, session.end)
                    if (firstUri == null) firstUri = uri
                    success++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) { // termasuk PhotoProcessingException & kegagalan MediaStore
                    Log.e(TAG, "Nomor ${photo.number} gagal (I/O): ${e.message}", e)
                    failed += photo.number
                } catch (e: RuntimeException) { // mis. kegagalan encode/PDF
                    Log.e(TAG, "Nomor ${photo.number} gagal (runtime): ${e.message}", e)
                    failed += photo.number
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Nomor ${photo.number} gagal (memori habis)", e)
                    failed += photo.number
                }
                onProgress(index + 1)
            }
            BatchResult(
                start = session.start,
                end = session.end,
                successCount = success,
                failedNumbers = failed,
                relativePath = MediaStoreOutput.relativePath(session.start, session.end),
                firstFileUri = firstUri,
            )
        }

    /**
     * Proses satu foto dari file original di cache: crop/warp pakai [quad] → enhance → PDF →
     * simpan (menimpa PDF bernama sama) → tulis thumbnail. [quad] null = otomatis
     * (deteksi, fallback margin). Memperbarui `photo.quad`, `autoQuad`, thumbnail.
     * Blocking; panggil dari thread background. File original tidak dihapus.
     *
     * @throws PhotoProcessingException file hilang/rusak/memori habis (foto dicatat gagal)
     * @throws IOException penyimpanan gagal
     */
    fun processSinglePhoto(photo: CapturedPhoto, quad: QuadPoints?, start: Int, end: Int): Uri {
        val src = loader(photo.filePath)
        var cropped: Bitmap? = null
        var enhanced: Bitmap? = null
        try {
            val used = quad ?: ImageCropper.autoQuad(src)
            cropped = ImageCropper.warp(src, used)
            enhanced = ImageEnhancer.enhance(cropped)
            val uri = output.savePdf(start, end, photo.number) { PdfConverter.write(enhanced, it) }
            writeThumbnail(photo, enhanced)
            photo.autoQuad = when {
                quad == null -> used
                photo.autoQuad != null -> photo.autoQuad
                else -> ImageCropper.autoQuad(src)
            }
            photo.quad = used
            return uri
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Memori habis memproses nomor ${photo.number}", e)
            throw PhotoProcessingException("Memori habis saat memproses foto", e)
        } finally {
            if (enhanced != null && enhanced !== cropped) enhanced.recycle()
            cropped?.recycle()
            src.recycle()
        }
    }

    /** Hapus PDF nomor [number] sesi ini dari penyimpanan. Mengembalikan Uri yang dihapus (atau null). */
    fun deletePdf(start: Int, end: Int, number: Int): Uri? = output.delete(start, end, number)

    /** Hapus semua PDF di folder output sesi ini. Mengembalikan jumlah file yang terhapus. */
    fun deleteAllPdfs(start: Int, end: Int): Int = output.deleteAll(start, end)

    /** Thumbnail gagal tidak menggagalkan proses (PDF sudah tersimpan). */
    private fun writeThumbnail(photo: CapturedPhoto, bitmap: Bitmap) {
        try {
            val scale = THUMB_LONG_SIDE.toFloat() / max(bitmap.width, bitmap.height)
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val thumb = Bitmap.createScaledBitmap(bitmap, w, h, true)
            try {
                val file = TempPhotoStore.thumbFile(thumbDir(), File(photo.filePath))
                FileOutputStream(file).use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                photo.thumbPath = file.absolutePath
                photo.thumbVersion++
            } finally {
                if (thumb !== bitmap) thumb.recycle()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Thumbnail gagal (I/O) untuk nomor ${photo.number}", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Thumbnail gagal (argumen) untuk nomor ${photo.number}", e)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Thumbnail gagal (memori) untuk nomor ${photo.number}", e)
        }
    }

    private companion object {
        const val TAG = "BatchProcessor"
        const val THUMB_LONG_SIDE = 300
    }
}
