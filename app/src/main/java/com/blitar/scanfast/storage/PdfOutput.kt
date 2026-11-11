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
}
