package com.blitar.scanfast.processing

import android.util.Log
import com.blitar.scanfast.storage.PdfOutput

/**
 * Operasi penomoran ulang PDF yang aman: tidak pernah menimpa file, dan kalau satu langkah gagal
 * semua langkah sebelumnya dibatalkan (rollback urutan terbalik) sehingga tidak ada PDF yang hilang
 * atau tertukar setengah jalan. Pure (hanya bicara ke [PdfOutput]) sehingga bisa diuji di JVM.
 */
class PageNumbering(private val output: PdfOutput) {

    /** Ganti nomor [from] → [to]; [to] harus kosong. PDF [from] yang tidak ada (halaman gagal) dilewati. */
    fun move(start: Int, end: Int, from: Int, to: Int) = atomic { undo ->
        step(undo, start, end, from, to)
    }

    /**
     * Tukar nomor [a] dan [b] lewat nomor sementara ([TEMP]): a→TEMP, b→a, TEMP→b.
     * Kalau salah satu tidak punya PDF (halaman gagal), hanya yang punya yang dipindah.
     */
    fun swap(start: Int, end: Int, a: Int, b: Int) = atomic { undo ->
        val aMoved = step(undo, start, end, a, TEMP)
        step(undo, start, end, b, a)
        if (aMoved) step(undo, start, end, TEMP, b)
    }

    /**
     * Naikkan semua nomor di [numbers] sebanyak 1 (urut dari terbesar ke terkecil supaya tujuan
     * selalu kosong), lalu — kalau [newEnd] diberikan — pindahkan folder ke rentang baru.
     * Nomor tujuan tertinggi (max+1) harus belum dipakai PDF lain.
     */
    fun shiftUp(start: Int, end: Int, numbers: Collection<Int>, newEnd: Int? = null) = atomic { undo ->
        for (n in numbers.sortedDescending()) step(undo, start, end, n, n + 1)
        if (newEnd != null && newEnd != end) moveFolder(undo, start, end, newEnd)
    }.also { if (newEnd != null && newEnd != end) cleanup(start, end) }

    /** Perluas rentang (ganti nama folder) tanpa mengubah nomor. */
    fun extend(start: Int, end: Int, newEnd: Int) = atomic { undo ->
        if (newEnd != end) moveFolder(undo, start, end, newEnd)
    }.also { if (newEnd != end) cleanup(start, end) }

    /** Setelah pemindahan sukses: hapus folder lama kalau kosong. Di luar transaksi; gagal = diabaikan. */
    private fun cleanup(start: Int, oldEnd: Int) {
        try {
            output.removeFolderIfEmpty(start, oldEnd)
        } catch (e: Exception) {
            Log.w(TAG, "Bersihkan folder lama gagal: ${e.message}")
        }
    }

    private fun step(undo: MutableList<() -> Unit>, start: Int, end: Int, from: Int, to: Int): Boolean {
        val moved = output.rename(start, end, from, to)
        if (moved) undo += { output.rename(start, end, to, from) }
        return moved
    }

    private fun moveFolder(undo: MutableList<() -> Unit>, start: Int, end: Int, newEnd: Int) {
        output.moveFolder(start, end, newEnd) // gagal di tengah sudah dipulihkan oleh implementasinya
        undo += { output.moveFolder(start, newEnd, end) }
    }

    private inline fun atomic(block: (MutableList<() -> Unit>) -> Unit) {
        val undo = ArrayList<() -> Unit>()
        try {
            block(undo)
        } catch (e: Throwable) {
            for (u in undo.asReversed()) {
                try {
                    u()
                } catch (r: Throwable) {
                    Log.e(TAG, "Rollback gagal: ${r.message}", r)
                }
            }
            throw e
        }
    }

    companion object {
        private const val TAG = "PageNumbering"

        /** Nomor sementara untuk tukar; bukan nomor halaman yang valid. */
        const val TEMP = -1
    }
}
