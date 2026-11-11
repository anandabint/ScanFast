package com.blitar.scanfast.processing

import android.util.Log
import org.opencv.android.OpenCVLoader

/** Inisialisasi OpenCV sekali; kalau gagal, pipeline jatuh ke crop margin tanpa OpenCV. */
object OpenCvInit {
    val ready: Boolean by lazy {
        try {
            OpenCVLoader.initLocal().also { Log.i("OpenCvInit", "initLocal=$it") }
        } catch (t: Throwable) {
            Log.e("OpenCvInit", "OpenCV gagal dimuat", t)
            false
        }
    }
}
