package com.blitar.scanfast.model

import android.net.Uri

/** Hasil akhir batch untuk SummaryScreen. */
data class BatchResult(
    val start: Int,
    val end: Int,
    val successCount: Int,
    val failedNumbers: List<Int>,
    val relativePath: String,
    val firstFileUri: Uri?,
    /** Halaman yang dihapus manual (bukan gagal); nomornya dibiarkan kosong. */
    val deletedCount: Int = 0,
) {
    val failedCount: Int get() = failedNumbers.size
}
