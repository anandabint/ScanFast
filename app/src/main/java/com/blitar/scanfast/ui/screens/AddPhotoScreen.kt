package com.blitar.scanfast.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.blitar.scanfast.camera.VolumeShutterHandler
import com.blitar.scanfast.model.BatchViewModel
import com.blitar.scanfast.model.CaptureViewModel

/**
 * Tambah foto baru untuk nomor [number] (sudah dipastikan kosong oleh Review: nomor kosong, nomor di
 * luar rentang, atau hasil sisip). Memakai layar capture yang sama dengan Foto ulang.
 */
@Composable
fun AddPhotoScreen(
    number: Int,
    captureViewModel: CaptureViewModel,
    batchViewModel: BatchViewModel,
    volumeShutter: VolumeShutterHandler,
    onClose: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = captureViewModel.currentSession()
    if (session == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    SingleShotScreen(
        title = "Tambah foto — halaman $number",
        errorPrefix = "Tambah foto",
        nextFileName = session::nextFileName,
        process = { path -> batchViewModel.addPhoto(session, number, path) },
        volumeShutter = volumeShutter,
        onClose = onClose,
        onDone = onDone,
        modifier = modifier,
    )
}
