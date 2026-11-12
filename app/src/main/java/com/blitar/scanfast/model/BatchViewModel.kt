package com.blitar.scanfast.model

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blitar.scanfast.processing.BatchProcessor
import com.blitar.scanfast.processing.PageNumbering
import com.blitar.scanfast.storage.MediaStoreOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** State batch (progress + hasil) yang dibagi antara Processing, Review, editor, dan Summary. */
class BatchViewModel(app: Application) : AndroidViewModel(app) {
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var result by mutableStateOf<BatchResult?>(null)
        private set

    /** Progres "Terapkan urutan" di Review (menulis ulang semua PDF). */
    var reordering by mutableStateOf(false)
        private set
    var reorderDone by mutableIntStateOf(0)
        private set
    var reorderTotal by mutableIntStateOf(0)
        private set

    private var job: Job? = null

    /** Idempotent: tidak memulai ulang kalau sedang jalan atau sudah selesai. */
    fun start(session: CaptureSession) {
        if (job != null || result != null) return
        total = session.photos.size
        job = viewModelScope.launch {
            result = BatchProcessor(getApplication()).process(session) { done = it }
        }
    }

    /**
     * Proses ulang satu foto dengan [quad] baru (editor crop), menimpa PDF lama. Melempar exception
     * kalau gagal. Foto yang tadinya gagal di batch dan kini sukses dikoreksi di [result].
     */
    suspend fun reprocess(session: CaptureSession, photo: CapturedPhoto, quad: QuadPoints) {
        val uri = withContext(Dispatchers.IO) {
            BatchProcessor(getApplication()).processSinglePhoto(photo, quad, session.start, session.end)
        }
        markRecovered(photo.number, uri)
    }

    /** Foto yang tadinya gagal dan kini sukses: pindahkan dari gagal ke berhasil. */
    private fun markRecovered(number: Int, uri: Uri) {
        val r = result ?: return
        if (number in r.failedNumbers) {
            result = r.copy(
                successCount = r.successCount + 1,
                failedNumbers = r.failedNumbers - number,
                firstFileUri = r.firstFileUri ?: uri,
            )
        }
    }

    /**
     * Hapus satu halaman: PDF, file sumber, dan thumbnail dihapus, foto dikeluarkan dari sesi.
     * Nomornya dibiarkan kosong; foto lain TIDAK dinomori ulang. Bukan dihitung "gagal".
     */
    suspend fun deletePhoto(session: CaptureSession, photo: CapturedPhoto) {
        val number = photo.number
        val deletedUri = withContext(Dispatchers.IO) {
            val uri = BatchProcessor(getApplication()).deletePdf(session.start, session.end, number)
            photo.deleteFiles()
            uri
        }
        session.removePhoto(photo)
        val r = result ?: return
        val wasFailed = number in r.failedNumbers
        result = r.copy(
            successCount = if (wasFailed) r.successCount else (r.successCount - 1).coerceAtLeast(0),
            failedNumbers = r.failedNumbers - number,
            firstFileUri = if (deletedUri != null && deletedUri == r.firstFileUri) null else r.firstFileUri,
            deletedCount = r.deletedCount + 1,
        )
    }

    /**
     * Terapkan urutan baru: SEMUA foto dinomori ulang berurutan dari `session.start` menurut posisinya
     * (celah nomor ikut tertutup), semua PDF lama di folder sesi dihapus, lalu setiap foto diproses
     * ulang dengan nomor barunya (quad/thumbnail diperbarui). [result] dihitung ulang.
     */
    suspend fun applyOrder(session: CaptureSession) {
        val photos = session.photos.toList()
        reorderTotal = photos.size
        reorderDone = 0
        reordering = true
        try {
            val processor = BatchProcessor(getApplication())
            val newResult = withContext(Dispatchers.IO) {
                processor.deleteAllPdfs(session.start, session.end)
                var success = 0
                var firstUri: Uri? = null
                val failed = mutableListOf<Int>()
                photos.forEachIndexed { index, photo ->
                    photo.number = session.start + index
                    try {
                        val uri = processor.processSinglePhoto(photo, photo.quad, session.start, session.end)
                        if (firstUri == null) firstUri = uri
                        success++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        Log.e(TAG, "Reorder: nomor ${photo.number} gagal (I/O): ${e.message}", e)
                        failed += photo.number
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Reorder: nomor ${photo.number} gagal (runtime): ${e.message}", e)
                        failed += photo.number
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "Reorder: nomor ${photo.number} gagal (memori habis)", e)
                        failed += photo.number
                    }
                    reorderDone = index + 1
                }
                val old = result
                BatchResult(
                    start = session.start,
                    end = session.end,
                    successCount = success,
                    failedNumbers = failed,
                    relativePath = old?.relativePath
                        ?: com.blitar.scanfast.storage.MediaStoreOutput.relativePath(session.start, session.end),
                    firstFileUri = firstUri,
                    deletedCount = old?.deletedCount ?: 0,
                )
            }
            result = newResult
        } finally {
            reordering = false
        }
    }

    /**
     * Foto ulang untuk [photo]: foto baru di [newPath] diproses (deteksi otomatis ulang, quad lama
     * dibuang) dan menimpa PDF bernomor sama. Hanya jika berhasil file lama diganti; kalau gagal file
     * baru dibuang dan foto/PDF lama utuh, lalu exception dilempar.
     */
    suspend fun retake(session: CaptureSession, photo: CapturedPhoto, newPath: String) {
        val fresh = CapturedPhoto(photo.number, newPath)
        val uri = try {
            withContext(Dispatchers.IO) {
                BatchProcessor(getApplication()).processSinglePhoto(fresh, null, session.start, session.end)
            }
        } catch (e: Throwable) {
            File(newPath).delete()
            fresh.thumbPath?.let { File(it).delete() }
            throw e
        }
        val oldFile = photo.filePath
        val oldThumb = photo.thumbPath
        photo.filePath = fresh.filePath
        photo.thumbPath = fresh.thumbPath
        photo.autoQuad = fresh.autoQuad
        photo.quad = fresh.quad
        photo.thumbVersion++
        withContext(Dispatchers.IO) {
            if (oldFile != photo.filePath) File(oldFile).delete()
            if (oldThumb != null && oldThumb != photo.thumbPath) File(oldThumb).delete()
        }
        markRecovered(photo.number, uri)
    }

    /** Petakan nomor di daftar gagal mengikuti perubahan nomor ([mapping] lama → baru). */
    private fun remapFailed(mapping: Map<Int, Int>) {
        val r = result ?: return
        if (r.failedNumbers.none { it in mapping }) return
        result = r.copy(failedNumbers = r.failedNumbers.map { mapping[it] ?: it }.sorted())
    }

    /** Sinkronkan rentang di [result] dengan [session] setelah rentang diperluas. */
    private fun syncRange(session: CaptureSession) {
        val r = result ?: return
        if (r.end == session.end) return
        result = r.copy(
            end = session.end,
            relativePath = com.blitar.scanfast.storage.MediaStoreOutput.relativePath(session.start, session.end),
        )
    }

    /** Ganti nomor [photo] ke [newNumber] yang BELUM dipakai: rename PDF + update model. */
    suspend fun moveNumber(session: CaptureSession, photo: CapturedPhoto, newNumber: Int) {
        val old = photo.number
        require(session.photoWithNumber(newNumber) == null) { "Nomor $newNumber sudah dipakai" }
        withContext(Dispatchers.IO) {
            PageNumbering(MediaStoreOutput(getApplication())).move(session.start, session.end, old, newNumber)
        }
        photo.number = newNumber
        session.sortByNumber()
        remapFailed(mapOf(old to newNumber))
    }

    /** Tukar nomor [a] dan [b]: kedua PDF di-rename atomik (rollback kalau gagal), lalu model. */
    suspend fun swapNumbers(session: CaptureSession, a: CapturedPhoto, b: CapturedPhoto) {
        val na = a.number
        val nb = b.number
        withContext(Dispatchers.IO) {
            PageNumbering(MediaStoreOutput(getApplication())).swap(session.start, session.end, na, nb)
        }
        a.number = nb
        b.number = na
        session.sortByNumber()
        remapFailed(mapOf(na to nb, nb to na))
    }

    /**
     * Kosongkan nomor [n] untuk halaman baru: semua nomor >= [n] naik 1 (rename dari belakang ke depan),
     * rentang bertambah 1 (folder `AKL start-end` ikut pindah). Atomik; gagal = tidak ada yang berubah.
     */
    suspend fun insertSlot(session: CaptureSession, n: Int) {
        val shifted = session.photos.filter { it.number >= n }
        val oldEnd = session.end
        val newEnd = oldEnd + 1
        withContext(Dispatchers.IO) {
            PageNumbering(MediaStoreOutput(getApplication()))
                .shiftUp(session.start, oldEnd, shifted.map { it.number }, newEnd)
        }
        shifted.forEach { it.number += 1 }
        session.extendEnd(newEnd)
        session.sortByNumber()
        remapFailed(shifted.associate { (it.number - 1) to it.number })
        syncRange(session)
    }

    /**
     * Tambah foto baru [path] di nomor [number] (kosong). Kalau [number] di luar rentang, rentang
     * diperluas dulu (folder pindah). Kalau pemrosesan gagal, file baru dibuang dan exception dilempar
     * (rentang yang sudah diperluas tetap; tidak ada PDF yang hilang).
     */
    suspend fun addPhoto(session: CaptureSession, number: Int, path: String) {
        require(session.photoWithNumber(number) == null) { "Nomor $number sudah dipakai" }
        val photo = CapturedPhoto(number, path)
        try {
            withContext(Dispatchers.IO) {
                if (number > session.end) {
                    PageNumbering(MediaStoreOutput(getApplication())).extend(session.start, session.end, number)
                    session.extendEnd(number)
                }
                BatchProcessor(getApplication()).processSinglePhoto(photo, null, session.start, session.end)
            }
        } catch (e: Throwable) {
            File(path).delete()
            photo.thumbPath?.let { File(it).delete() }
            syncRange(session)
            throw e
        }
        session.insertPhoto(photo)
        syncRange(session)
        val uri = com.blitar.scanfast.storage.MediaStoreOutput.relativePath(session.start, session.end)
        result?.let { r ->
            result = r.copy(successCount = r.successCount + 1, relativePath = uri)
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        result = null
        done = 0
        total = 0
        reordering = false
    }

    private companion object {
        const val TAG = "BatchViewModel"
    }
}
