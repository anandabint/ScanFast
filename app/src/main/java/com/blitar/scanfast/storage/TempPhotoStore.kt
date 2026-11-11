package com.blitar.scanfast.storage

import android.content.Context
import java.io.File

/**
 * Lokasi penyimpanan foto sementara hasil capture (sebelum crop/PDF di Fase 3).
 *
 * Struktur: `<cacheDir>/scanfast_temp/p<urutan>.jpg` (datar, satu file per foto, nama unik).
 * BatchProcessor (Fase 3) cukup membaca `CapturedPhoto.filePath`, lalu memanggil
 * [clearAll] (atau `CaptureViewModel.clear()`) setelah selesai / dibatalkan.
 */
object TempPhotoStore {
    private const val DIR_NAME = "scanfast_temp"

    fun dir(context: Context): File =
        File(context.cacheDir, DIR_NAME).also { it.mkdirs() }

    /**
     * File foto sumber bernama unik (`p<urutan>.jpg`), BUKAN berdasarkan nomor halaman, supaya
     * renumbering (reorder) dan foto ulang tidak pernah menimpa file milik foto lain.
     */
    fun fileFor(context: Context, fileName: String): File = File(dir(context), fileName)

    /** Thumbnail hasil proses untuk ReviewScreen: `thumb_<nama file sumber>.jpg` (unik per foto). */
    fun thumbFile(dir: File, photoFile: File): File =
        File(dir, "thumb_${photoFile.nameWithoutExtension}.jpg")

    /** Hapus semua file sementara (sisa sesi lama, mis. setelah proses app dimatikan). */
    fun clearAll(context: Context) {
        File(context.cacheDir, DIR_NAME).listFiles()?.forEach { it.delete() }
    }
}
