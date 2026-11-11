package com.blitar.scanfast.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private val nextPhotoId = AtomicLong(1)

/**
 * Foto hasil capture (JPEG di cache, belum di-crop) beserta nomor halamannya.
 * [id] stabil sepanjang sesi (kunci UI, tidak berubah walau nomor berubah); [number] dan [filePath]
 * bisa berubah (renumbering saat reorder, foto ulang).
 */
class CapturedPhoto(number: Int, filePath: String) {
    val id: Long = nextPhotoId.getAndIncrement()

    /** Nomor halaman = nama PDF. Berubah saat renumbering (reorder). */
    var number by mutableIntStateOf(number)

    /** File foto sumber; diganti saat foto ulang. */
    @Volatile
    var filePath: String = filePath

    /** Quad yang dipakai crop/warp terakhir (otomatis atau hasil edit manual). */
    var quad by mutableStateOf<QuadPoints?>(null)

    /** Quad hasil deteksi/fallback awal, untuk "Reset ke otomatis". */
    @Volatile
    var autoQuad: QuadPoints? = null

    /** Thumbnail hasil crop+enhance (JPEG kecil di cache); null kalau belum/gagal diproses. */
    @Volatile
    var thumbPath: String? = null

    /** Naik tiap thumbnail ditulis ulang, supaya UI memuat ulang. */
    var thumbVersion by mutableIntStateOf(0)

    /** Hapus file sumber + thumbnail milik foto ini. */
    fun deleteFiles() {
        File(filePath).delete()
        thumbPath?.let { File(it).delete() }
    }
}

/** State satu sesi capture: rentang nomor, nomor berjalan, dan path foto sementara. */
class CaptureSession(val start: Int, val end: Int) {
    var current by mutableIntStateOf(start)
        private set
    var photoCount by mutableIntStateOf(0)
        private set

    private val _photos = mutableStateListOf<CapturedPhoto>()
    val photos: List<CapturedPhoto> get() = _photos

    private var fileSeq = 0

    val isFinished: Boolean get() = current > end

    /** Nama file temp unik berikutnya (bukan berdasarkan nomor halaman). */
    fun nextFileName(): String = "p${fileSeq++}.jpg"

    fun addPhoto(filePath: String) {
        if (isFinished) return
        _photos.add(CapturedPhoto(current, filePath))
        photoCount = _photos.size
        current++
    }

    fun skip() {
        if (isFinished) return
        current++
    }

    /** Pindahkan foto dari indeks [from] ke [to] (hanya urutan daftar; nomor belum berubah). */
    fun movePhoto(from: Int, to: Int) {
        if (from == to || from !in _photos.indices || to !in _photos.indices) return
        _photos.add(to, _photos.removeAt(from))
    }

    /** Kembalikan urutan daftar sesuai [ids] (mis. membatalkan reorder). */
    fun restoreOrder(ids: List<Long>) {
        val byId = _photos.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        if (ordered.size != _photos.size) return
        _photos.clear()
        _photos.addAll(ordered)
    }

    /** Keluarkan [photo] dari sesi (nomornya dibiarkan kosong; tidak ada renumbering). */
    fun removePhoto(photo: CapturedPhoto) {
        _photos.remove(photo)
        photoCount = _photos.size
    }

    /** Hapus file foto sementara milik sesi ini. */
    fun deleteFiles() {
        _photos.forEach { it.deleteFiles() }
    }
}
