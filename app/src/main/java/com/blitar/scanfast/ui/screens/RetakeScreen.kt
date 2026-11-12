package com.blitar.scanfast.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.blitar.scanfast.camera.VolumeShutterHandler
import com.blitar.scanfast.model.BatchViewModel
import com.blitar.scanfast.model.CaptureViewModel

/**
 * Foto ulang satu halaman. Foto lama dan PDF-nya baru diganti setelah foto baru berhasil diproses;
 * Batal = tidak ada perubahan.
 */
@Composable
fun RetakeScreen(
    number: Int,
    captureViewModel: CaptureViewModel,
    batchViewModel: BatchViewModel,
    volumeShutter: VolumeShutterHandler,
    onClose: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = captureViewModel.currentSession()
    val photo = remember(session, number) { session?.photoWithNumber(number) }
    if (session == null || photo == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    SingleShotScreen(
        title = "Foto ulang — halaman ${photo.number}",
        errorPrefix = "Foto ulang",
        nextFileName = session::nextFileName,
        process = { path -> batchViewModel.retake(session, photo, path) },
        volumeShutter = volumeShutter,
        onClose = onClose,
        onDone = onDone,
        modifier = modifier,
    )
}
