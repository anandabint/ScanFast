package com.blitar.scanfast.processing

import android.net.Uri
import com.blitar.scanfast.model.CaptureSession
import com.blitar.scanfast.storage.PdfOutput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream
import java.util.Random

/**
 * Skenario file corrupt pada pipeline foto (JVM murni, tanpa device).
 * `android.graphics.BitmapFactory.decodeFile` di JVM mengembalikan null (isReturnDefaultValues),
 * persis perilaku Android untuk file yang bukan gambar valid.
 */
class CorruptPhotoTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Tujuan PDF palsu; foto corrupt tidak boleh sampai ke sini. */
    private class FailIfCalledOutput : PdfOutput {
        var calls = 0
        override fun savePdf(start: Int, end: Int, number: Int, write: (OutputStream) -> Unit): Uri {
            calls++
            throw AssertionError("savePdf tidak boleh dipanggil untuk foto corrupt (nomor $number)")
        }

        override fun delete(start: Int, end: Int, number: Int): Uri? = null

        override fun deleteAll(start: Int, end: Int): Int = 0

        override fun rename(start: Int, end: Int, from: Int, to: Int): Boolean = false

        override fun moveFolder(start: Int, oldEnd: Int, newEnd: Int): Int = 0
    }

    private fun randomBytesFile(name: String, size: Int = 4096): File =
        tmp.newFile(name).also { f ->
            val bytes = ByteArray(size).also { Random(42).nextBytes(it) }
            bytes[0] = 0x00 // pastikan bukan header JPEG (FF D8)
            f.writeBytes(bytes)
        }

    @Test
    fun loader_randomBytes_throwsPhotoProcessingException() {
        val f = randomBytesFile("1.jpg")
        try {
            PhotoLoader.load(f.absolutePath)
            fail("Seharusnya PhotoProcessingException")
        } catch (e: PhotoProcessingException) {
            assertTrue(e.message!!.contains("rusak"))
        }
    }

    @Test
    fun loader_missingFile_throwsPhotoProcessingException() {
        try {
            PhotoLoader.load(File(tmp.root, "tidak_ada.jpg").absolutePath)
            fail("Seharusnya PhotoProcessingException")
        } catch (e: PhotoProcessingException) {
            assertTrue(e.message!!.contains("tidak ada atau kosong"))
        }
    }

    @Test
    fun loader_emptyFile_throwsPhotoProcessingException() {
        val f = tmp.newFile("kosong.jpg")
        try {
            PhotoLoader.load(f.absolutePath)
            fail("Seharusnya PhotoProcessingException")
        } catch (e: PhotoProcessingException) {
            assertTrue(e.message!!.contains("kosong"))
        }
    }

    @Test
    fun loader_decoderOutOfMemory_isWrappedNotPropagated() {
        val f = randomBytesFile("oom.jpg")
        try {
            PhotoLoader.load(f.absolutePath) { throw OutOfMemoryError("simulasi") }
            fail("Seharusnya PhotoProcessingException")
        } catch (e: PhotoProcessingException) {
            assertTrue(e.cause is OutOfMemoryError)
            assertTrue(e.message!!.contains("Memori habis"))
        }
    }

    @Test
    fun loader_decoderIllegalArgument_isWrapped() {
        val f = randomBytesFile("iae.jpg")
        try {
            PhotoLoader.load(f.absolutePath) { throw IllegalArgumentException("simulasi") }
            fail("Seharusnya PhotoProcessingException")
        } catch (e: PhotoProcessingException) {
            assertTrue(e.cause is IllegalArgumentException)
        }
    }

    @Test
    fun batch_allCorrupt_recordedAsFailed_noCrash() = runBlocking {
        val session = CaptureSession(start = 10, end = 14)
        session.addPhoto(randomBytesFile("10.jpg").absolutePath) // byte acak
        session.addPhoto(File(tmp.root, "11.jpg").absolutePath) // file hilang
        session.addPhoto(tmp.newFile("12.jpg").absolutePath) // file kosong
        val output = FailIfCalledOutput()
        val processor = BatchProcessor(output, thumbDir = { tmp.root })

        val progress = mutableListOf<Int>()
        val result = processor.process(session) { progress += it }

        assertEquals(0, result.successCount)
        assertEquals(listOf(10, 11, 12), result.failedNumbers)
        assertEquals(3, result.failedCount)
        assertEquals(listOf(1, 2, 3), progress) // batch jalan sampai akhir
        assertEquals(0, output.calls)
        assertEquals("Documents/ScanFast/AKL 10-14/", result.relativePath)
    }

    @Test
    fun batch_loaderThrowsOutOfMemory_photoFailed_batchContinues() = runBlocking {
        val session = CaptureSession(start = 1, end = 2)
        session.addPhoto(randomBytesFile("1.jpg").absolutePath)
        session.addPhoto(randomBytesFile("2.jpg").absolutePath)
        // Loader yang melempar langsung OutOfMemoryError (di luar PhotoLoader) juga harus tertangani.
        val processor = BatchProcessor(
            FailIfCalledOutput(), thumbDir = { tmp.root },
            loader = { throw OutOfMemoryError("simulasi") },
        )
        val result = processor.process(session) {}
        assertEquals(listOf(1, 2), result.failedNumbers)
        assertEquals(0, result.successCount)
    }

    @Test
    fun processSinglePhoto_corrupt_throwsPhotoProcessingException_andLeavesPhotoUntouched() {
        val session = CaptureSession(1, 1)
        session.addPhoto(randomBytesFile("1.jpg").absolutePath)
        val photo = session.photos[0]
        val processor = BatchProcessor(FailIfCalledOutput(), thumbDir = { tmp.root })
        try {
            processor.processSinglePhoto(photo, null, 1, 1)
            fail("Seharusnya PhotoProcessingException")
        } catch (e: PhotoProcessingException) {
            assertNotNull(e.message)
        }
        assertEquals(null, photo.quad)
        assertEquals(null, photo.thumbPath)
        assertTrue("File original harus tetap ada", File(photo.filePath).exists())
    }
}
