package com.blitar.scanfast.processing

import android.util.Log
import org.opencv.core.CvException

/**
 * Menjalankan [block] (kode OpenCV/bitmap) dan menangkap kegagalan SPESIFIK dengan log,
 * lalu memakai [fallback]. Exception lain (bug logika) sengaja tidak ditelan.
 */
internal inline fun <T> guardedOpenCv(tag: String, what: String, fallback: () -> T, block: () -> T): T =
    try {
        block()
    } catch (e: CvException) {
        Log.e(tag, "$what: OpenCV gagal", e)
        fallback()
    } catch (e: IllegalArgumentException) {
        Log.e(tag, "$what: argumen tidak valid", e)
        fallback()
    } catch (e: IllegalStateException) {
        Log.e(tag, "$what: state tidak valid", e)
        fallback()
    } catch (e: UnsatisfiedLinkError) {
        Log.e(tag, "$what: native library tidak termuat", e)
        fallback()
    } catch (e: OutOfMemoryError) {
        Log.e(tag, "$what: memori habis", e)
        fallback()
    }
