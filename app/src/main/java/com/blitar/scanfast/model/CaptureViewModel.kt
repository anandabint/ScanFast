package com.blitar.scanfast.model

import androidx.lifecycle.ViewModel

/** Menyimpan sesi capture agar bertahan saat rotasi / balik dari layar lain. */
class CaptureViewModel : ViewModel() {
    private var session: CaptureSession? = null

    /** Sesi existing dipakai ulang kalau rentangnya sama; kalau beda dibuat baru. */
    fun sessionFor(start: Int, end: Int): CaptureSession {
        val s = session
        if (s != null && s.start == start && s.end == end) return s
        s?.deleteFiles()
        return CaptureSession(start, end).also { session = it }
    }

    fun currentSession(): CaptureSession? = session

    /** Buang sesi beserta file foto sementaranya. */
    fun clear() {
        session?.deleteFiles()
        session = null
    }
}
