package com.blitar.scanfast.processing

import android.net.Uri
import com.blitar.scanfast.storage.PdfOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.io.OutputStream

/** Penomoran ulang PDF (tukar, geser, perluas) pada penyimpanan palsu di memori. */
class PageNumberingTest {
    /** Folder palsu: kunci "end/nomor" → isi. rename menolak menimpa, seperti MediaStoreOutput. */
    private class FakeOutput(val start: Int = 1, initialEnd: Int) : PdfOutput {
        var end = initialEnd
        val files = linkedMapOf<Int, String>()
        val log = mutableListOf<String>()
        /** Gagalkan rename ke-N (1-based) untuk menguji rollback. */
        var failOnRename = -1
        private var renames = 0
        var failMoveFolder = false

        override fun savePdf(start: Int, end: Int, number: Int, write: (OutputStream) -> Unit): Uri =
            throw UnsupportedOperationException()

        override fun delete(start: Int, end: Int, number: Int): Uri? = null
        override fun deleteAll(start: Int, end: Int): Int = 0

        override fun rename(start: Int, end: Int, from: Int, to: Int): Boolean {
            check(end == this.end) { "folder salah: $end != ${this.end}" }
            renames++
            if (renames == failOnRename) throw IOException("rename gagal (disengaja)")
            val content = files[from] ?: return false
            if (files.containsKey(to)) throw IOException("$to sudah ada")
            files.remove(from)
            files[to] = content
            log += "$from->$to"
            return true
        }

        override fun moveFolder(start: Int, oldEnd: Int, newEnd: Int): Int {
            check(oldEnd == end) { "folder asal salah" }
            if (failMoveFolder) throw IOException("pindah folder gagal (disengaja)")
            end = newEnd
            log += "folder $oldEnd->$newEnd"
            return files.size
        }

        fun snapshot() = files.toSortedMap().toMap()
    }

    private fun fake(vararg nums: Int, end: Int = nums.max()) = FakeOutput(initialEnd = end).apply {
        nums.forEach { files[it] = "isi-$it" }
    }

    @Test
    fun swap_exchangesContentsWithoutLoss() {
        val out = fake(1, 2, 3)
        PageNumbering(out).swap(1, 3, 2, 3)
        assertEquals(mapOf(1 to "isi-1", 2 to "isi-3", 3 to "isi-2"), out.snapshot())
        assertTrue(out.files.keys.none { it < 0 }) // nomor sementara tidak bocor
    }

    @Test
    fun swap_withMissingPdf_movesOnlyExisting() {
        val out = fake(1, 3, end = 3) // nomor 2 tidak punya PDF (halaman gagal)
        PageNumbering(out).swap(1, 3, 1, 2)
        assertEquals(mapOf(2 to "isi-1", 3 to "isi-3"), out.snapshot())
        PageNumbering(out).swap(1, 3, 3, 1) // sekarang 1 kosong, 3 ada
        assertEquals(mapOf(1 to "isi-3", 2 to "isi-1"), out.snapshot())
    }

    @Test
    fun swap_failureMidway_rollsBackEverything() {
        for (failAt in 1..3) {
            val out = fake(1, 2, 3)
            out.failOnRename = failAt
            try {
                PageNumbering(out).swap(1, 3, 1, 3)
                fail("harus gagal")
            } catch (e: IOException) {
                // diharapkan
            }
            assertEquals("gagal di langkah $failAt", mapOf(1 to "isi-1", 2 to "isi-2", 3 to "isi-3"), out.snapshot())
        }
    }

    @Test
    fun shiftUp_renamesFromHighestToLowest_neverOverwrites() {
        val out = fake(1, 2, 3, 4, 5)
        PageNumbering(out).shiftUp(1, 5, listOf(3, 4, 5), newEnd = 6) // sisip di 3
        assertEquals(listOf("5->6", "4->5", "3->4", "folder 5->6"), out.log)
        assertEquals(mapOf(1 to "isi-1", 2 to "isi-2", 4 to "isi-3", 5 to "isi-4", 6 to "isi-5"), out.snapshot())
        assertEquals(6, out.end)
    }

    @Test
    fun shiftUp_withGaps_keepsGapsAndContent() {
        val out = fake(1, 2, 4, 5, end = 5) // 3 kosong (dihapus)
        PageNumbering(out).shiftUp(1, 5, listOf(2, 4, 5), newEnd = 6) // sisip di 2
        assertEquals(mapOf(1 to "isi-1", 3 to "isi-2", 5 to "isi-4", 6 to "isi-5"), out.snapshot())
    }

    @Test
    fun shiftUp_failureAtAnyStep_restoresOriginalState() {
        for (failAt in 1..3) {
            val out = fake(1, 2, 3, 4, 5)
            out.failOnRename = failAt
            try {
                PageNumbering(out).shiftUp(1, 5, listOf(3, 4, 5), newEnd = 6)
                fail("harus gagal")
            } catch (e: IOException) {
            }
            assertEquals("gagal di langkah $failAt", (1..5).associateWith { "isi-$it" }, out.snapshot())
            assertEquals(5, out.end)
        }
    }

    @Test
    fun shiftUp_folderMoveFails_undoesRenames() {
        val out = fake(1, 2, 3)
        out.failMoveFolder = true
        try {
            PageNumbering(out).shiftUp(1, 3, listOf(2, 3), newEnd = 4)
            fail("harus gagal")
        } catch (e: IOException) {
        }
        assertEquals((1..3).associateWith { "isi-$it" }, out.snapshot())
        assertEquals(3, out.end)
    }

    @Test
    fun move_toFreeNumber_renames_andToUsedNumberFailsWithoutLoss() {
        val out = fake(1, 2, 4, end = 4)
        PageNumbering(out).move(1, 4, 2, 3)
        assertEquals(mapOf(1 to "isi-1", 3 to "isi-2", 4 to "isi-4"), out.snapshot())
        try {
            PageNumbering(out).move(1, 4, 1, 4)
            fail("harus gagal")
        } catch (e: IOException) {
        }
        assertEquals(mapOf(1 to "isi-1", 3 to "isi-2", 4 to "isi-4"), out.snapshot())
    }

    @Test
    fun extend_movesFolderOnly() {
        val out = fake(1, 2, 3)
        PageNumbering(out).extend(1, 3, 7)
        assertEquals(7, out.end)
        assertEquals((1..3).associateWith { "isi-$it" }, out.snapshot())
    }
}
