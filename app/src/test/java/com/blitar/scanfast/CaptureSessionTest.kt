package com.blitar.scanfast

import com.blitar.scanfast.model.CaptureSession
import com.blitar.scanfast.model.CapturedPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Logika daftar foto sesi: urutan (reorder), penghapusan, nama file unik. */
class CaptureSessionTest {
    private fun sessionWithGap(): CaptureSession {
        // rentang 1..6, nomor 3 di-skip -> foto bernomor 1,2,4,5,6
        val s = CaptureSession(1, 6)
        s.addPhoto("a.jpg"); s.addPhoto("b.jpg"); s.skip(); s.addPhoto("c.jpg"); s.addPhoto("d.jpg"); s.addPhoto("e.jpg")
        return s
    }

    @Test
    fun skip_leavesGapInNumbers() {
        assertEquals(listOf(1, 2, 4, 5, 6), sessionWithGap().photos.map { it.number })
    }

    @Test
    fun movePhoto_changesOrderOnly_numbersUntouchedUntilApplied() {
        val s = sessionWithGap()
        s.movePhoto(0, 2) // a,b,c,d,e -> b,c,a,d,e
        assertEquals(listOf("b.jpg", "c.jpg", "a.jpg", "d.jpg", "e.jpg"), s.photos.map { it.filePath })
        assertEquals(listOf(2, 4, 1, 5, 6), s.photos.map { it.number })
    }

    @Test
    fun movePhoto_firstAndLast_edges() {
        val s = sessionWithGap()
        s.movePhoto(4, 0) // terakhir ke pertama
        assertEquals("e.jpg", s.photos.first().filePath)
        s.movePhoto(0, 4) // pertama ke terakhir
        assertEquals("e.jpg", s.photos.last().filePath)
        s.movePhoto(2, 2) // sama: tidak berubah
        s.movePhoto(9, 0) // di luar rentang: diabaikan
        assertEquals(5, s.photos.size)
    }

    @Test
    fun restoreOrder_undoesReorder() {
        val s = sessionWithGap()
        val original = s.photos.map { it.id }
        s.movePhoto(0, 3); s.movePhoto(1, 4)
        assertNotEquals(original, s.photos.map { it.id })
        s.restoreOrder(original)
        assertEquals(original, s.photos.map { it.id })
    }

    @Test
    fun removePhoto_keepsOtherNumbers_noRenumbering() {
        val s = sessionWithGap()
        s.removePhoto(s.photos[1]) // hapus nomor 2
        assertEquals(listOf(1, 4, 5, 6), s.photos.map { it.number })
        assertEquals(4, s.photoCount)
    }

    @Test
    fun nextFileName_isUniqueAndIndependentOfNumber() {
        val s = CaptureSession(1, 3)
        val names = List(5) { s.nextFileName() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun renumberByPosition_closesGaps() {
        // Aturan reorder: nomor baru = start + indeks posisi (dihitung di BatchViewModel.applyOrder).
        val s = sessionWithGap()
        s.movePhoto(0, 4)
        s.photos.forEachIndexed { i, p -> p.number = s.start + i }
        assertEquals(listOf(1, 2, 3, 4, 5), s.photos.map { it.number })
        assertEquals(listOf("b.jpg", "c.jpg", "d.jpg", "e.jpg", "a.jpg"), s.photos.map { it.filePath })
    }

    @Test
    fun emptyNumbers_listsSkippedAndDeleted() {
        val s = CaptureSession(1, 6)
        s.addPhoto("a"); s.skip(); s.addPhoto("c"); s.addPhoto("d")
        s.removePhoto(s.photos.last()) // nomor 4 dihapus
        assertEquals(listOf(2, 4, 5, 6), s.emptyNumbers())
    }

    @Test
    fun insertPhoto_sortsByNumber_andExtendEndOnlyGrows() {
        val s = CaptureSession(1, 3)
        s.addPhoto("a"); s.skip(); s.addPhoto("c")
        s.insertPhoto(CapturedPhoto(2, "b"))
        assertEquals(listOf(1, 2, 3), s.photos.map { it.number })
        s.extendEnd(5); s.extendEnd(4)
        assertEquals(5, s.end)
    }

    @Test
    fun sortByNumber_afterSwap_followsNumbers() {
        val s = CaptureSession(1, 3)
        s.addPhoto("a"); s.addPhoto("b"); s.addPhoto("c")
        s.photos[0].number = 3; s.photos[2].number = 1
        s.sortByNumber()
        assertEquals(listOf("c", "b", "a"), s.photos.map { java.io.File(it.filePath).name })
    }
}
