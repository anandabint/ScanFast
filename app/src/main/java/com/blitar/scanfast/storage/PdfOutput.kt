package com.blitar.scanfast.storage

import android.net.Uri
import java.io.OutputStream

/** Tujuan penyimpanan PDF; dipisah dari MediaStore agar pipeline bisa diuji di JVM tanpa perangkat. */
interface PdfOutput {
    /** Tulis PDF lewat [write]; mengembalikan Uri file. Melempar IOException kalau gagal. */
    fun savePdf(start: Int, end: Int, number: Int, write: (OutputStream) -> Unit): Uri

    /** Hapus PDF nomor [number]; mengembalikan Uri yang dihapus, null bila tidak ada. */
    fun delete(start: Int, end: Int, number: Int): Uri?

    /** Hapus semua PDF milik app di folder sesi; mengembalikan jumlah yang terhapus. */
    fun deleteAll(start: Int, end: Int): Int

    /**
     * Ganti nama PDF [from] menjadi [to] di folder sesi. Mengembalikan false kalau PDF [from] tidak
     * ada (mis. halaman yang gagal diproses). Melempar IOException kalau [to] sudah ada atau gagal;
     * tidak pernah menimpa file lain.
     */
    fun rename(start: Int, end: Int, from: Int, to: Int): Boolean

    /**
     * Pindahkan semua PDF dari folder `AKL start-oldEnd` ke `AKL start-newEnd` (rentang diperluas).
     * Semua-atau-tidak-sama-sekali: kalau gagal di tengah, yang sudah pindah dikembalikan lalu
     * IOException dilempar. Mengembalikan jumlah file yang dipindah.
     */
    fun moveFolder(start: Int, oldEnd: Int, newEnd: Int): Int
}
